// `Clock` artık `kotlin.time.Clock`'a takma ad (kotlinx-datetime 0.7);
// o tip henüz deneysel işaretli olduğu için opt-in gerekiyor.
@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.gymapp.domain

import kotlinx.datetime.Clock

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
