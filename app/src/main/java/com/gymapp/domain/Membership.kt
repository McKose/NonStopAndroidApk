package com.gymapp.domain

/**
 * Üyeliğin *görünen* durumu.
 *
 * [MemberManualStatus] kayıtlı (manuel) durumu tutar; bu enum ise kayıtlı durum +
 * bitiş tarihinden **türetilen** durumu ifade eder.
 */
enum class MembershipState { ACTIVE, EXPIRED, FROZEN, PASSIVE }

object Membership {

    /**
     * Üyeliğin gerçek durumu.
     *
     * Bitiş tarihi geçmiş üyeler için arka plan işine (WorkManager) gerek yok:
     * durum okuma anında türetilir. Bu sayede iş hiç çalışmasa bile ekranlar
     * doğru veriyi gösterir.
     *
     * Not: [MemberManualStatus.ARCHIVED] bilinçli olarak *terminal*dir — geçerli
     * bir bitiş tarihi olsa dahi arşivlenmiş üye tekrar ACTIVE'e dönmez.
     */
    fun stateOf(
        storedStatus: MemberManualStatus,
        endDateMs: Long?,
        nowMs: Long,
    ): MembershipState = when (storedStatus) {
        MemberManualStatus.ARCHIVED -> MembershipState.PASSIVE
        MemberManualStatus.FROZEN -> MembershipState.FROZEN
        MemberManualStatus.ACTIVE -> if (endDateMs == null || endDateMs < nowMs) {
            MembershipState.EXPIRED
        } else {
            MembershipState.ACTIVE
        }
    }

    /**
     * Üye bugün randevu alabilir mi? (üyelik geçerli **ve** seans hakkı var)
     *
     * @param remainingSessions `null` ise sınırsız paket.
     */
    fun canBookSession(
        storedStatus: MemberManualStatus,
        endDateMs: Long?,
        remainingSessions: Int?,
        nowMs: Long,
    ): Boolean = stateOf(storedStatus, endDateMs, nowMs) == MembershipState.ACTIVE &&
        SessionQuota.hasSessionsLeft(remainingSessions)
}

/** Ekranda gösterilen Türkçe karşılık; veritabanına asla yazılmaz. */
fun MembershipState.labelTr(): String = when (this) {
    MembershipState.ACTIVE -> "Aktif"
    MembershipState.EXPIRED -> "Süresi doldu"
    MembershipState.FROZEN -> "Donduruldu"
    MembershipState.PASSIVE -> "Arşivlendi"
}
