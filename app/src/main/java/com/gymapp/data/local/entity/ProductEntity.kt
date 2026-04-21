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
    val stockCount: Int,
    val imageUrl: String? = null,
    val isActive: Boolean = true
)
