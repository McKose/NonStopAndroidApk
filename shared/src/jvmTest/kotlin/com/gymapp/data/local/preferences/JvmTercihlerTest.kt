package com.gymapp.data.local.preferences

import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Masaüstü tercihlerinin gidiş-dönüşü.
 *
 * Ayrı bir test düğümü kullanılıyor ve testten sonra siliniyor: `userRoot`
 * gerçek kullanıcı deposu — üretim düğümüne yazan bir test, geliştiricinin
 * makinesindeki gerçek tercihe çöp bırakırdı.
 */
class JvmTercihlerTest {

    private val dugum = "com/gymapp/test-tercihler"

    @AfterTest
    fun temizle() {
        Preferences.userRoot().node(dugum).removeNode()
    }

    @Test
    fun `yazilan ad yeni ornekten okunuyor`() {
        JvmTercihler(dugum).salonName = "Non Stop GYM Kartepe"

        // Yeni örnek: değerin bellekte değil DEPODA olduğunu kanıtlıyor.
        // Aynı örnekten okumak, hiç yazmayan bir gerçeklemede de geçerdi.
        assertEquals("Non Stop GYM Kartepe", JvmTercihler(dugum).salonName)
    }

    @Test
    fun `hic yazilmamissa varsayilan donuyor`() {
        assertEquals(AppPreferences.VARSAYILAN_SALON_ADI, JvmTercihler(dugum).salonName)
    }
}
