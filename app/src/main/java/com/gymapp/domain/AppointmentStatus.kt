package com.gymapp.domain

/**
 * Randevu durumları.
 *
 * Şu an veritabanında metin olarak saklanıyor; sabitler burada toplandığı için
 * yazım hatası kaynaklı sessiz eşleşmeme hataları ortadan kalkar.
 * (Faz 1'de `TypeConverter`'lı gerçek bir enum'a dönüştürülecek.)
 */
object AppointmentStatus {
    const val SCHEDULED = "SCHEDULED"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"
    const val POSTPONED = "POSTPONED"

    /** Yalnızca bu durum finansal yan etki (hakediş + seans düşümü) doğurur. */
    fun hasFinancialEffect(status: String): Boolean = status == COMPLETED
}
