package com.gymapp.arayuz.paketler

import com.gymapp.domain.PackageCategory
import com.gymapp.domain.TrainingType

/**
 * Paket formunun taşıdığı değerler.
 *
 * Ekran ile çağıran arasındaki sözleşme. Altı alanı tek tek parametre olarak
 * geçirmek de olurdu; tek nesne tercih edildi çünkü aynı altılı hem başlangıç
 * değeri olarak İÇERİ hem kaydetme yükü olarak DIŞARI gidiyor — iki yerde
 * ayrı ayrı sıralanan altı parametre, birinin sırası değiştiğinde sessizce
 * yanlış eşleşirdi.
 *
 * `fiyat` metin olarak duruyor, sayı olarak değil: kullanıcı yazarken alan
 * geçici olarak geçersiz olabiliyor ("12," gibi) ve o ara durumun temsil
 * edilebilmesi gerekiyor. Sayıya çevirme kaydetme anında, `Decimals` ile.
 */
data class PaketFormu(
    val sinirsiz: Boolean = false,
    val seansSayisi: String = "10",
    val tur: TrainingType = TrainingType.FITNESS,
    val kategori: PackageCategory = PackageCategory.INDIVIDUAL,
    val fiyat: String = "",
    val gun: String = "30",
)
