package com.gymapp.data.repository

import androidx.room.withTransaction
import com.gymapp.data.local.dao.OrderDao
import com.gymapp.data.local.dao.ProductDao
import com.gymapp.data.local.dao.StockMovementDao
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.entity.OrderEntity
import com.gymapp.data.local.entity.ProductEntity
import com.gymapp.data.local.entity.StockMovementEntity
import com.gymapp.domain.Now
import com.gymapp.domain.DeliveryStatus
import com.gymapp.domain.Ids
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.StockMovementReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val database: GymDatabase,
    private val productDao: ProductDao,
    private val orderDao: OrderDao,
    private val stockMovementDao: StockMovementDao,
    private val ledgerRepository: LedgerRepository,
) {
    private val tenantId = Ids.DEFAULT_TENANT

    fun getAllProducts(): Flow<List<ProductEntity>> = productDao.observeAll(tenantId)

    /**
     * Ürün kimliği → eldeki stok.
     *
     * Stok ürün satırındaki sayaçta değil, hareketlerin toplamında. Hareketler
     * toplanabilir (commutative) olduğu için iki cihaz aynı anda satış yaptığında
     * hiçbir satış kaybolmaz — sayaç modelinde biri diğerini eziyordu.
     */
    fun observeStockByProduct(): Flow<Map<String, Int>> =
        stockMovementDao.observeOnHandByProduct(tenantId).map { rows ->
            rows.associate { it.productId to it.onHand }
        }

    /**
     * Ürünü kaydeder; [desiredStock] verilmişse eldeki stoğu o değere **düzeltme
     * hareketiyle** eşitler (mutlak sayaç yazmaz).
     *
     * @param productId `null` ise yeni ürün oluşturulur.
     */
    suspend fun saveProduct(
        productId: String?,
        name: String,
        category: String,
        price: Money,
        desiredStock: Int? = null,
    ): Result<String> = runCatching {
        require(name.isNotBlank()) { "Ürün adı boş olamaz." }

        database.withTransaction {
            val nowMs = Now.epochMillis()
            val id = productId ?: Ids.new()

            val existing = productId?.let { productDao.getById(it) }
            if (existing == null) {
                productDao.insert(
                    ProductEntity(
                        id = id,
                        tenantId = tenantId,
                        name = name.trim(),
                        category = category.trim(),
                        priceMinor = price.coerceNonNegative().minor,
                        createdAtMs = nowMs,
                        updatedAtMs = nowMs,
                    )
                )
            } else {
                productDao.update(
                    existing.copy(
                        name = name.trim(),
                        category = category.trim(),
                        priceMinor = price.coerceNonNegative().minor,
                        updatedAtMs = nowMs,
                    )
                )
            }

            if (desiredStock != null) {
                val current = stockMovementDao.onHand(tenantId, id)
                val delta = desiredStock.coerceAtLeast(0) - current
                if (delta != 0) {
                    stockMovementDao.insert(
                        StockMovementEntity(
                            id = Ids.new(),
                            tenantId = tenantId,
                            productId = id,
                            quantityDelta = delta,
                            reason = StockMovementReason.CORRECTION,
                            note = "Stok düzeltme",
                            occurredAtMs = nowMs,
                            createdAtMs = nowMs,
                        )
                    )
                }
            }
            id
        }
    }

    /** Ürünü tombstone ile siler; geçmiş hareketler ve satışlar öksüz kalmaz. */
    suspend fun deleteProduct(productId: String) =
        productDao.softDelete(productId, Now.epochMillis())

    /**
     * Siparişi tek transaction içinde işler.
     *
     * Akış: (1) fiyat ve stok **veritabanından** doğrulanır, (2) sipariş yazılır,
     * (3) stok çıkış hareketleri ve tahsilat siparişe bağlanır. Herhangi bir adımda
     * hata olursa hiçbiri kalmaz.
     *
     * Kontrol ile düşüm aynı transaction'da olduğundan iki eşzamanlı satış aynı
     * stoğu satamaz.
     */
    suspend fun processOrder(
        memberId: String?,
        cartItems: Map<String, Int>,
        paymentMethod: PaymentMethod,
        paymentStatus: String,
        deliveryStatus: DeliveryStatus,
        discount: Money = Money.ZERO,
        notes: String? = null
    ): Result<String> = runCatching {
        require(cartItems.isNotEmpty()) { "Sepet boş." }

        database.withTransaction {
            var total = Money.ZERO

            // 1) Doğrulama — fiyat ve stok DB'den okunur, UI listesine güvenilmez.
            cartItems.forEach { (productId, quantity) ->
                require(quantity > 0) { "Geçersiz ürün adedi." }

                val product = productDao.getById(productId)
                    ?: throw IllegalStateException("Ürün bulunamadı.")

                val onHand = stockMovementDao.onHand(tenantId, productId)
                if (onHand < quantity) {
                    throw IllegalStateException("${product.name} için yeterli stok yok (elde: $onHand).")
                }

                total += Money(product.priceMinor) * quantity
            }

            val safeDiscount = discount.coerceNonNegative().coerceAtMost(total)
            val finalPrice = total - safeDiscount

            // 2) Sipariş
            val nowMs = Now.epochMillis()
            val orderId = Ids.new()
            orderDao.insert(
                OrderEntity(
                    id = orderId,
                    tenantId = tenantId,
                    memberId = memberId,
                    totalPriceMinor = total.minor,
                    discountMinor = safeDiscount.minor,
                    finalPriceMinor = finalPrice.minor,
                    paymentMethod = paymentMethod,
                    paymentStatus = paymentStatus,
                    deliveryStatus = deliveryStatus,
                    dateMs = nowMs,
                    notes = notes,
                    createdAtMs = nowMs,
                    updatedAtMs = nowMs,
                )
            )

            // 3) Stok çıkışları — siparişe bağlı, geri alınabilir olması için ayrı kayıt.
            stockMovementDao.insertAll(
                cartItems.map { (productId, quantity) ->
                    StockMovementEntity(
                        id = Ids.new(),
                        tenantId = tenantId,
                        productId = productId,
                        quantityDelta = -quantity,
                        reason = StockMovementReason.SALE,
                        orderId = orderId,
                        occurredAtMs = nowMs,
                        createdAtMs = nowMs,
                    )
                }
            )

            // 4) Tahsilat — yalnızca ödeme alındıysa (nakit esaslı).
            if (paymentStatus == "PAID" && finalPrice.isPositive) {
                ledgerRepository.recordPayment(
                    amount = finalPrice,
                    method = paymentMethod,
                    description = buildString {
                        append("Market satışı - Sipariş #").append(orderId.take(8))
                        if (memberId == null) append(" (Misafir)")
                    },
                    category = LedgerCategory.MARKET,
                    memberId = memberId,
                    orderId = orderId,
                    occurredAtMs = nowMs,
                ).getOrThrow()
            }

            orderId
        }
    }
}
