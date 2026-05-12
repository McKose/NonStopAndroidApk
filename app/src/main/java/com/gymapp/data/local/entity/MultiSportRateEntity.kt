package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * MultiSport sabit ücret geçmişi.
 *
 * Kullanım: Bir MultiSport ödemesi (appointment) oluşturulduğunda, o günkü tarih için
 * `effectiveFromMs <= date < supersededByMs` koşulunu sağlayan kayıt kullanılır.
 * Ücret güncellendiğinde eski kayıt silinmez; sadece `supersededByMs` alanı
 * yeni kaydın effectiveFromMs'i olarak kapanır → geçmiş ödemeler etkilenmez.
 */
@Entity(
    tableName = "multisport_rates",
    indices = [Index(value = ["effectiveFromMs"])]
)
data class MultiSportRateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val effectiveFromMs: Long = System.currentTimeMillis(),
    /** null ise hâlâ geçerli olan cari kayıttır. */
    val supersededByMs: Long? = null,
    val note: String? = null
)
