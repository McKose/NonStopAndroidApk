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
    private val db: GymDatabase,
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

    suspend fun processOrder(
        memberId: Long?,
        cartItems: Map<Long, Int>,
        products: List<ProductEntity>,
        paymentType: String,
        paymentStatus: String,
        deliveryStatus: String,
        discount: Double = 0.0,
        notes: String? = null
    ): Result<Unit> = runCatching {
        if (cartItems.isEmpty()) throw IllegalStateException("Sepet boş")

        db.withTransaction {
            var totalAmount = 0.0

            cartItems.forEach { (productId, quantity) ->
                val product = products.find { it.id == productId }
                    ?: throw IllegalStateException("Ürün bulunamadı")

                val rowsAffected = productDao.decrementStock(productId, quantity)
                if (rowsAffected == 0) {
                    throw IllegalStateException("${product.name} için yeterli stok yok")
                }

                totalAmount += product.price * quantity
            }

            val finalPrice = (totalAmount - discount).coerceAtLeast(0.0)

            val orderId = orderDao.insertOrder(
                OrderEntity(
                    memberId = memberId,
                    totalPrice = totalAmount,
                    discount = discount,
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
                        memberId = memberId,
                        amount = finalPrice,
                        type = "INCOME",
                        category = "MARKET",
                        description = "Market Satışı - Sipariş #$orderId" +
                                if (memberId == null) " (Misafir)" else "",
                        paymentMethod = paymentType
                    )
                )
            }
        }
    }
}
