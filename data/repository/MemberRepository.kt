package com.gymapp.data.repository

import com.gymapp.data.local.dao.MemberDao
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.MemberStatus
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.data.local.entity.PaymentType
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DÜZELTME #2 — Komisyon hesabı artık Repository katmanında, UI'dan izole şekilde yapılıyor.
 *
 * Desktop'ta bu hesap members/page.tsx içinde (UI) yapılıyordu.
 * Farklı entry point'lerden yapılan işlemler tutarsız fiyatlar üretiyordu.
 */
@Singleton
class MemberRepository @Inject constructor(
    private val memberDao: MemberDao
) {
    // ─── Komisyon tablosu (Desktop'tan alındı, config'e taşındı) ──────────────
    private val commissionRates = mapOf(
        1  to 0.0,
        2  to 3.0,
        3  to 5.0,
        6  to 10.0,
        9  to 15.0,
        12 to 20.0
    )

    /**
     * Komisyon dahil nihai fiyatı hesaplar.
     * KART ödemesi ve taksit varsa komisyon eklenir; NAKİT her zaman 0% komisyon.
     */
    fun calculateFinalPrice(
        basePrice: Double,
        paymentType: PaymentType,
        installmentCount: Int
    ): Double {
        if (paymentType == PaymentType.CASH) return basePrice
        val rate = commissionRates.getOrDefault(installmentCount, 0.0)
        return basePrice * (1.0 + rate / 100.0)
    }

    // ─── Yeni üye kaydı ───────────────────────────────────────────────────────

    /**
     * @return Result.success(memberId) veya Result.failure(exception)
     *
     * Telefon numarası benzersizlik kontrolü burada yapılır.
     * Desktop'ta bu kontrol yoktu → duplicate kayıt oluşabiliyordu.
     */
    suspend fun registerMember(
        fullName: String,
        phone: String,
        email: String?,
        birthDateMs: Long?,
        selectedPackage: PackageEntity?,
        paymentType: PaymentType,
        installmentCount: Int,
        notes: String?
    ): Result<Long> = runCatching {

        // Telefon tekrar kontrolü
        val existing = memberDao.getMemberByPhone(phone)
        if (existing != null) {
            throw IllegalArgumentException("Bu telefon numarası ($phone) zaten kayıtlı. Üye ID: ${existing.id}")
        }

        val nowMs = System.currentTimeMillis()
        val endDateMs = selectedPackage?.let {
            nowMs + TimeUnit.DAYS.toMillis(it.validityDays.toLong())
        }

        // DÜZELTME #1 — ABONMAN → -1 (sınırsız), DERS_PAKETI → gerçek sayı
        val remainingSessions = when {
            selectedPackage == null -> 0
            selectedPackage.sessionCount == -1 -> -1  // ABONMAN: sınırsız
            else -> selectedPackage.sessionCount       // DERS_PAKETI: belirli sayı
        }

        // DÜZELTME #2 — Komisyon hesabı UI'dan taşındı
        val finalPrice = selectedPackage?.let {
            calculateFinalPrice(it.basePrice, paymentType, installmentCount)
        } ?: 0.0

        val member = MemberEntity(
            fullName          = fullName.trim(),
            phone             = phone.trim(),
            email             = email?.trim()?.takeIf { it.isNotBlank() },
            birthDateMs       = birthDateMs,
            activePackageId   = selectedPackage?.id,
            remainingSessions = remainingSessions,
            startDateMs       = nowMs,
            endDateMs         = endDateMs,
            status            = MemberStatus.ACTIVE.name,
            paymentType       = paymentType.name,
            installmentCount  = installmentCount,
            pricePaid         = finalPrice,
            notes             = notes?.trim()?.takeIf { it.isNotBlank() },
            createdAtMs       = nowMs,
            updatedAtMs       = nowMs
        )

        memberDao.insertMember(member)
    }

    // ─── Sorgu metodları ──────────────────────────────────────────────────────

    fun getAllMembers(): Flow<List<MemberEntity>> = memberDao.getAllMembers()
    fun getActiveMembers(): Flow<List<MemberEntity>> = memberDao.getActiveMembers()
    fun searchMembers(query: String): Flow<List<MemberEntity>> = memberDao.searchMembers(query)

    /** DÜZELTME #3 — WorkManager bu metodu çağırır */
    suspend fun expireOverdueMembers(): Int = memberDao.expireOverdueMembers()
}
