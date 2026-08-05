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
        assertEquals(
            MembershipState.ACTIVE,
            Membership.stateOf(MemberManualStatus.ACTIVE, tomorrow, now)
        )
    }

    /**
     * Regresyon: durum daha önce gecelik bir WorkManager işiyle güncelleniyordu.
     * İş çalışmadığında süresi dolmuş üye "ACTIVE" görünüyordu. Durum artık okuma
     * anında türetildiği için o iş tamamen kaldırıldı.
     */
    @Test
    fun `bitis tarihi gecmis uye arka plan isi calismasa da EXPIRED olur`() {
        assertEquals(
            MembershipState.EXPIRED,
            Membership.stateOf(MemberManualStatus.ACTIVE, yesterday, now)
        )
    }

    @Test
    fun `bitis tarihi olmayan uye EXPIRED sayilir`() {
        assertEquals(
            MembershipState.EXPIRED,
            Membership.stateOf(MemberManualStatus.ACTIVE, null, now)
        )
    }

    @Test
    fun `dondurulmus uyelik bitis tarihinden bagimsizdir`() {
        assertEquals(
            MembershipState.FROZEN,
            Membership.stateOf(MemberManualStatus.FROZEN, tomorrow, now)
        )
        assertEquals(
            MembershipState.FROZEN,
            Membership.stateOf(MemberManualStatus.FROZEN, yesterday, now)
        )
    }

    /** Arşivleme terminaldir; geçerli bir bitiş tarihi üyeyi geri diriltmemeli. */
    @Test
    fun `arsivlenmis uye tekrar ACTIVE olmaz`() {
        assertEquals(
            MembershipState.PASSIVE,
            Membership.stateOf(MemberManualStatus.ARCHIVED, tomorrow, now)
        )
    }

    // ─── Randevu uygunluğu ──────────────────────────────────────────────────

    @Test
    fun `randevu icin hem aktif uyelik hem seans hakki gerekir`() {
        assertTrue(
            Membership.canBookSession(MemberManualStatus.ACTIVE, tomorrow, 5, now)
        )
        // seansı bitmiş
        assertFalse(
            Membership.canBookSession(MemberManualStatus.ACTIVE, tomorrow, 0, now)
        )
        // süresi dolmuş
        assertFalse(
            Membership.canBookSession(MemberManualStatus.ACTIVE, yesterday, 5, now)
        )
        // abonman (sınırsız kota) + geçerli üyelik
        assertTrue(
            Membership.canBookSession(MemberManualStatus.ACTIVE, tomorrow, null, now)
        )
    }

    @Test
    fun `dondurulmus uye seans hakki olsa da randevu alamaz`() {
        assertFalse(
            Membership.canBookSession(MemberManualStatus.FROZEN, tomorrow, 5, now)
        )
    }
}
