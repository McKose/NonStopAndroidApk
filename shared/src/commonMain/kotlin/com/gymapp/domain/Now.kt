// Tip doğrudan `kotlin.time`'dan alınıyor, `kotlinx.datetime` üzerinden DEĞİL.
//
// kotlinx-datetime 0.7'de `kotlinx.datetime.Clock` yalnızca bir takma ad ve
// Kotlin takma ad üzerinden İÇ İÇE nesneye erişime izin vermiyor: `Clock.System`
// "Unresolved reference 'System'" veriyor. (Companion erişimi çalışıyor, bu
// yüzden `Instant.fromEpochMilliseconds` takma adla da derleniyordu.)
//
// `kotlin.time.Clock` bu Kotlin sürümünde henüz deneysel işaretli.
@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.gymapp.domain

import kotlin.time.Clock

/**
 * Şimdiki an — epoch milisaniye.
 *
 * `System.currentTimeMillis()` JVM'e özgü; ortak kodda derlenmez. Zaman kaynağı
 * tek bir yerde toplandığı için ileride test edilebilir bir saat enjekte etmek de
 * tek noktada yapılabilecek.
 */
object Now {
    fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
