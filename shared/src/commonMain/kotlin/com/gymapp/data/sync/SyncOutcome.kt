package com.gymapp.data.sync

/**
 * Bir senkronizasyon turunun sonucu.
 *
 * @param pushed sunucuya yazılan ve kuyruktan düşen kayıt sayısı
 * @param failed hata alan kayıt sayısı (kuyrukta kalırlar)
 * @param skipped bu turda atlananlar: geri çekilme süresi dolmamış olanlar ve
 *        gönderim sırasında satırı tekrar değişenler
 * @param stopped tur geçici bir hata yüzünden erken bitti mi
 */
data class SyncOutcome(
    val pushed: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val stopped: Boolean = false,
)
