package com.gymapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class MembershipTest {

    private val now = 1_700_000_000_000L
    private val tomorrow = now + TimeUnit.DAYS.toMillis(1)
    private val yesterday = now - TimeUnit.DAYS.toMillis(1)

    @Test
    fun `bitis tarihi gelecekte olan uye aktiftir`() {
        assertEquals(MembershipState.ACTIVE, Membership.stateOf("ACTIVE", tomorrow, now))
    }

    /**
     * Regresyon: durum daha önce gecelik bir WorkManager işiyle güncelleniyordu.
     * İş çalışmadığında süresi dolmuş üye "ACTIVE" görünüyordu.
     */
    @Test
    fun `bitis tarihi gecmis uye arka plan isi calismasa da EXPIRED olur`() {
        assertEquals(MembershipState.EXPIRED, Membership.stateOf("ACTIVE", yesterday, now))
    }

    @Test
    fun `bitis tarihi olmayan uye EXPIRED sayilir`() {
        assertEquals(MembershipState.EXPIRED, Membership.stateOf("ACTIVE", null, now))
    }

    @Test
    fun `dondurulmus uyelik bitis tarihinden bagimsizdir`() {
        assertEquals(MembershipState.FROZEN, Membership.stateOf("FROZEN", tomorrow, now))
        assertEquals(MembershipState.FROZEN, Membership.stateOf("FROZEN", yesterday, now))
    }

    /** Soft-delete PASSIVE yazar; geçerli bir bitiş tarihi üyeyi geri diriltmemeli. */
    @Test
    fun `PASSIVE terminaldir ve tekrar ACTIVE olmaz`() {
        assertEquals(MembershipState.PASSIVE, Membership.stateOf("PASSIVE", tomorrow, now))
    }

    @Test
    fun `durum buyuk kucuk harften bagimsiz okunur`() {
        assertEquals(MembershipState.PASSIVE, Membership.stateOf("passive", tomorrow, now))
    }

    // ─── Seans hakkı ────────────────────────────────────────────────────────

    @Test
    fun `abonman sinirsiz seans demektir`() {
        assertTrue(Membership.isUnlimited(-1))
        assertTrue(Membership.hasSessionsLeft(-1))
    }

    @Test
    fun `seans hakki bitince randevu alinamaz`() {
        assertFalse(Membership.hasSessionsLeft(0))
        assertTrue(Membership.hasSessionsLeft(1))
    }

    @Test
    fun `randevu icin hem aktif uyelik hem seans hakki gerekir`() {
        assertTrue(Membership.canBookSession("ACTIVE", tomorrow, 5, now))
        // seansı bitmiş
        assertFalse(Membership.canBookSession("ACTIVE", tomorrow, 0, now))
        // süresi dolmuş
        assertFalse(Membership.canBookSession("ACTIVE", yesterday, 5, now))
        // abonman + geçerli üyelik
        assertTrue(Membership.canBookSession("ACTIVE", tomorrow, -1, now))
    }
}
