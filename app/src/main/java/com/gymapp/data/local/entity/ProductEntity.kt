package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String, // Ayarlar'dan eklenecek
    val price: Double,
    /**
     * ARTIK KULLANILMIYOR — eldeki stok `stock_movements` toplamından türetiliyor.
     * Kolon yalnızca şema geçişi tamamlanana kadar duruyor; entity cutover'da düşecek.
     */
    val stockCount: Int,
    val imageUrl: String? = null,
    val isActive: Boolean = true
)
