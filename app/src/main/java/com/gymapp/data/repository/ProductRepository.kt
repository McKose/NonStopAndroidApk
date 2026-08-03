package com.gymapp.data.repository

import androidx.room.withTransaction
import com.gymapp.data.local.dao.OrderDao
import com.gymapp.data.local.dao.ProductDao
import com.gymapp.data.local.dao.StockMovementDao
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.entity.OrderEntity
import com.gymapp.data.local.entity.ProductEntity
import com.gymapp.data.local.entity.StockMovementEntity
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

    fun getAllProducts(): Flow<List<ProductEntity>> = productDao.getAllProducts()

    /**
     * Ürün kimliği → eldeki stok.
     *
     * Stok artık ürün satırındaki mutlak sayaçta değil, hareketlerin toplamında.
     * Hareketler toplanabilir (commutative) olduğu için iki cihaz aynı anda satış
     * yaptığında hiçbir satış kaybolmaz — sayaç modelinde biri diğerini eziyordu.
     */
    fun observeStockByProduct(): Flow<Map<Long, Int>> =
        stockMovementDao.observeOnHandByProduct(tenantId).map { rows ->
            rows.mapNotNull { row ->
                row.productId.toLongOrNull()?.let { it to row.onHand }
            }.toMap()
        }

    /**
     * Ürünü kaydeder; [desiredStock] verilmişse eldeki stoğu o değere **düzeltme
     * hareketiyle** eşitler (mutlak sayaç yazmaz).
     */
    suspend fun saveProduct(product: ProductEntity, desiredStock: Int? = null): Result<Long> =
        runCatching {
            database.withTransaction {
                val productId = if (product.id == 0L) {
                    productDao.insertProduct(product)
                } else {
                    productDao.updateProduct(product)
                    product.id
                }

                if (desiredStock != null) {
                    val current = stockMovementDao.onHand(tenantId, productId.toString())
                    val delta = desiredStock.coerceAtLeast(0) - current
                    if (delta != 0) {
                        val nowMs = System.currentTimeMillis()
                        stockMovementDao.insert(
                            StockMovementEntity(
                                id = Ids.new(),
                                tenantId = tenantId,
                                productId = productId.toString(),
                                quantityDelta = delta,
                                reason = StockMovementReason.CORRECTION,
                                note = "Stok düzeltme",
                                occurredAtMs = nowMs,
                                createdAtMs = nowMs,
                            )
                        )
                    }
                }
                productId
            }
        }

    suspend fun deleteProduct(product: ProductEntity) = productDao.deleteProduct(product)

    /**
     * Siparişi tek transaction içinde işler.
     *
     * Akış: (1) fiyat ve stok **veritabanından** doğrulanır, (2) sipariş yazılır,
     * (3) stok çıkış hareketleri ve tahsilat kaydı siparişe bağlanır. Herhangi bir
     * adımda hata olursa hiçbiri kalmaz.
     *
     * Stok kontrolü ile düşüm aynı transaction içinde olduğundan, iki eşzamanlı
     * satış aynı stoğu satamaz.
     */
    suspend fun processOrder(
        memberId: Long?,
        cartItems: Map<Long, Int>,
        paymentType: String,
        paymentStatus: String,
        deliveryStatus: String,
        discount: Double = 0.0,
        notes: String? = null
    ): Result<Long> = runCatching {
        require(cartItems.isNotEmpty()) { "Sepet boş." }

        database.withTransaction {
            var total = Money.ZERO

            // 1) Doğrulama — fiyat ve stok DB'den okunur, UI listesine güvenilmez.
            cartItems.forEach { (productId, quantity) ->
                require(quantity > 0) { "Geçersiz ürün adedi." }

                val product = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Ürün bulunamadı (#$productId).")

                val onHand = stockMovementDao.onHand(tenantId, productId.toString())
                if (onHand < quantity) {
                    throw IllegalStateException("${product.name} için yeterli stok yok (elde: $onHand).")
                }

                total += Money.ofMajor(product.price) * quantity
            }

            val safeDiscount = Money.ofMajor(discount).coerceNonNegative().coerceAtMost(total)
            val finalPrice = total - safeDiscount

            // 2) Sipariş
            val orderId = orderDao.insertOrder(
                OrderEntity(
                    memberId = memberId,
                    totalPrice = total.asDouble,
                    discount = safeDiscount.asDouble,
                    finalPrice = finalPrice.asDouble,
                    paymentType = paymentType,
                    paymentStatus = paymentStatus,
                    deliveryStatus = deliveryStatus,
                    notes = notes
                )
            )

            // 3) Stok çıkışları — siparişe bağlı, geri alınabilir olması için ayrı kayıt.
            val nowMs = System.currentTimeMillis()
            stockMovementDao.insertAll(
                cartItems.map { (productId, quantity) ->
                    StockMovementEntity(
                        id = Ids.new(),
                        tenantId = tenantId,
                        productId = productId.toString(),
                        quantityDelta = -quantity,
                        reason = StockMovementReason.SALE,
                        orderId = orderId.toString(),
                        occurredAtMs = nowMs,
                        createdAtMs = nowMs,
                    )
                }
            )

            // 4) Tahsilat — yalnızca ödeme alındıysa (nakit esaslı).
            if (paymentStatus == "PAID" && finalPrice.isPositive) {
                ledgerRepository.recordPayment(
                    amount = finalPrice,
                    method = runCatching { PaymentMethod.valueOf(paymentType) }
                        .getOrDefault(PaymentMethod.CASH),
                    description = buildString {
                        append("Market satışı - Sipariş #").append(orderId)
                        if (memberId == null) append(" (Misafir)")
                    },
                    category = LedgerCategory.MARKET,
                    memberId = memberId?.toString(),
                    orderId = orderId.toString(),
                    occurredAtMs = nowMs,
                ).getOrThrow()
            }

            orderId
        }
    }
}
