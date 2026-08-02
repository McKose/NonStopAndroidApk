package com.gymapp.data.repository

import androidx.room.withTransaction
import com.gymapp.data.local.dao.OrderDao
import com.gymapp.data.local.dao.ProductDao
import com.gymapp.data.local.dao.TransactionDao
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.entity.OrderEntity
import com.gymapp.data.local.entity.ProductEntity
import com.gymapp.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val database: GymDatabase,
    private val productDao: ProductDao,
    private val orderDao: OrderDao,
    private val transactionDao: TransactionDao
) {
    fun getAllProducts(): Flow<List<ProductEntity>> = productDao.getAllProducts()

    suspend fun saveProduct(product: ProductEntity) {
        if (product.id == 0L) productDao.insertProduct(product)
        else productDao.updateProduct(product)
    }

    suspend fun deleteProduct(product: ProductEntity) = productDao.deleteProduct(product)

    /**
     * Siparişi **tek bir transaction** içinde işler.
     *
     * Önceki sürümdeki iki hata giderildi:
     *  1. Stok düşümü transaction dışındaydı; ortadaki bir kalemde stok yetmezse önceki
     *     kalemlerin stoğu düşmüş ama sipariş oluşmamış oluyordu (rollback yoktu).
     *  2. Stok kontrolü UI'dan gelen bayat listeye göre yapılıyordu; artık koşullu
     *     `UPDATE ... WHERE stockCount >= :qty` ile veritabanı üzerinde atomik yapılıyor,
     *     böylece iki eşzamanlı satış aynı stoğu satamaz.
     *
     * @return oluşturulan siparişin kimliği
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
            var totalAmount = 0.0

            cartItems.forEach { (productId, quantity) ->
                require(quantity > 0) { "Geçersiz ürün adedi." }

                // Fiyat da DB'den okunur: UI listesi eski fiyatı taşıyor olabilir.
                val product = productDao.getProductById(productId)
                    ?: throw IllegalStateException("Ürün bulunamadı (#$productId).")

                val updatedRows = productDao.decreaseStock(productId, quantity)
                if (updatedRows == 0) {
                    // Fırlatınca transaction geri sarılır; önceki kalemlerin stoğu da iade edilir.
                    throw IllegalStateException("${product.name} için yeterli stok yok.")
                }

                totalAmount += product.price * quantity
            }

            val safeDiscount = discount.coerceIn(0.0, totalAmount)
            val finalPrice = totalAmount - safeDiscount

            val orderId = orderDao.insertOrder(
                OrderEntity(
                    memberId = memberId,
                    totalPrice = totalAmount,
                    discount = safeDiscount,
                    finalPrice = finalPrice,
                    paymentType = paymentType,
                    paymentStatus = paymentStatus,
                    deliveryStatus = deliveryStatus,
                    notes = notes
                )
            )

            if (paymentStatus == "PAID") {
                transactionDao.insertTransaction(
                    TransactionEntity(
                        memberId = memberId ?: 0L,
                        amount = finalPrice,
                        type = "INCOME",
                        category = "MARKET",
                        description = buildString {
                            append("Market Satışı - Sipariş #").append(orderId)
                            if (memberId == null) append(" (Misafir)")
                        },
                        paymentMethod = paymentType
                    )
                )
            }

            orderId
        }
    }
}
