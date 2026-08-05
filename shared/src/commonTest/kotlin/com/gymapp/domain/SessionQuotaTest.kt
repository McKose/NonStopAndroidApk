package com.gymapp.domain

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Seans kotası artık `-1` sihirli sayısıyla değil `null` ile ifade ediliyor.
 *
 * Eski kurguda sınırsız paketler ekranda "-1 Seans" olarak görünüyordu ve
 * `-1` değeri kimi yerde "sınırsız", kimi yerde "tanımsız" anlamına geliyordu.
 */
class SessionQuotaTest {

    @Test
    fun `null kota sinirsiz demektir`() {
        assertTrue(SessionQuota.isUnlimited(null))
        assertFalse(SessionQuota.isUnlimited(0))
        assertFalse(SessionQuota.isUnlimited(10))
    }

    @Test
    fun `sinirsiz kotada her zaman seans hakki vardir`() {
        assertTrue(SessionQuota.hasSessionsLeft(null))
    }

    @Test
    fun `kota bitince seans hakki kalmaz`() {
        assertTrue(SessionQuota.hasSessionsLeft(1))
        assertFalse(SessionQuota.hasSessionsLeft(0))
    }

    // ─── Tüketim ────────────────────────────────────────────────────────────

    @Test
    fun `seans tuketimi kotayi bir azaltir`() {
        assertEquals(9, SessionQuota.consume(10))
        assertEquals(0, SessionQuota.consume(1))
    }

    @Test
    fun `sinirsiz kota tuketimde degismez`() {
        assertNull(SessionQuota.consume(null))
    }

    @Test
    fun `kota sifirin altina inmez`() {
        assertEquals(0, SessionQuota.consume(0))
    }

    // ─── İade ───────────────────────────────────────────────────────────────

    @Test
    fun `randevu geri alininca seans iade edilir`() {
        assertEquals(6, SessionQuota.restore(remaining = 5, total = 10))
    }

    @Test
    fun `iade kota tavanini asamaz`() {
        assertEquals(10, SessionQuota.restore(remaining = 10, total = 10))
        assertEquals(12, SessionQuota.restore(remaining = 12, total = 10))
    }

    @Test
    fun `sinirsiz kotada iade etkisizdir`() {
        assertNull(SessionQuota.restore(remaining = null, total = null))
    }

    @Test
    fun `tavan bilinmiyorsa iade yapilir`() {
        assertEquals(6, SessionQuota.restore(remaining = 5, total = null))
    }

    // ─── Tüket/iade döngüsü ─────────────────────────────────────────────────

    @Test
    fun `tuketim ve iade birbirini goturur`() {
        val total = 10
        val afterConsume = SessionQuota.consume(total)
        assertEquals(total, SessionQuota.restore(afterConsume, total))
    }
}
