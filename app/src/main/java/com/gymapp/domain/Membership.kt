package com.gymapp.domain

/**
 * Üyeliğin *görünen* durumu.
 *
 * [com.gymapp.data.local.entity.MemberStatus] kayıtlı (manuel) durumu tutar;
 * bu enum ise kayıtlı durum + bitiş tarihinden **türetilen** durumu ifade eder.
 */
enum class MembershipState { ACTIVE, EXPIRED, FROZEN, PASSIVE }

object Membership {

    /** Seans sayısı bu değerse paket sınırsızdır (abonman). */
    const val UNLIMITED_SESSIONS: Int = -1

    /**
     * Üyeliğin gerçek durumu.
     *
     * Bitiş tarihi geçmiş üyeler için artık gecelik bir arka plan işine (WorkManager) gerek yok:
     * durum okuma anında türetilir. Bu sayede iş hiç çalışmasa bile ekranlar doğru veriyi gösterir.
     *
     * Not: `PASSIVE` bilinçli olarak *terminal*dir — soft-delete bu değeri yazar,
     * dolayısıyla geçerli bir bitiş tarihi olsa dahi üye tekrar ACTIVE'e dönmez.
     */
    fun stateOf(storedStatus: String, endDateMs: Long?, nowMs: Long): MembershipState =
        when (storedStatus.uppercase()) {
            "PASSIVE" -> MembershipState.PASSIVE
            "FROZEN" -> MembershipState.FROZEN
            else -> if (endDateMs == null || endDateMs < nowMs) {
                MembershipState.EXPIRED
            } else {
                MembershipState.ACTIVE
            }
        }

    /** Paket sınırsız mı? */
    fun isUnlimited(sessionCount: Int): Boolean = sessionCount == UNLIMITED_SESSIONS

    /** Üyenin randevuya girecek hakkı var mı? */
    fun hasSessionsLeft(remainingSessions: Int): Boolean =
        isUnlimited(remainingSessions) || remainingSessions > 0

    /** Üye bugün randevu alabilir mi? (üyelik geçerli **ve** seans hakkı var) */
    fun canBookSession(
        storedStatus: String,
        endDateMs: Long?,
        remainingSessions: Int,
        nowMs: Long,
    ): Boolean = stateOf(storedStatus, endDateMs, nowMs) == MembershipState.ACTIVE &&
        hasSessionsLeft(remainingSessions)
}
