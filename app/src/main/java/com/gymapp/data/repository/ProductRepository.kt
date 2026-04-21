package com.gymapp.data.repository

import com.gymapp.data.local.dao.OrderDao
import com.gymapp.data.local.dao.ProductDao
import com.gymapp.data.local.dao.TransactionDao
import com.gymapp.data.local.entity.OrderEntity
import com.gymapp.data.local.entity.ProductEntity
import com.gymapp.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
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
        var totalAmount = 0.0
        
        // 1. Stok Kontrolü ve Fiyat Hesaplama
        cartItems.forEach { (productId, quantity) ->
            val product = products.find { it.id == productId } 
                ?: throw Exception("Ürün bulunamadı")
            
            if (product.stockCount < quantity) {
                throw Exception("${product.name} için yeterli stok yok")
            }
            
            totalAmount += product.price * quantity
            
            // 2. Stok Güncelle
            productDao.updateProduct(product.copy(stockCount = product.stockCount - quantity))
        }

        val finalPrice = totalAmount - discount

        // 3. Sipariş Kaydı Oluştur
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

        // 4. Eğer ödeme yapıldıysa Finans'a işle
        if (paymentStatus == "PAID") {
            transactionDao.insertTransaction(
                TransactionEntity(
                    memberId = memberId ?: -1L,
                    amount = finalPrice,
                    type = "INCOME",
                    category = "MARKET",
                    description = "Market Satışı - Sipariş #$orderId ${if (memberId == null) "(Guest)" else ""}",
                    paymentMethod = paymentType
                )
            )
        }
    }
}
