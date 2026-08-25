package com.gymapp.arayuz.uyeler

import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.domain.Decimals
import com.gymapp.domain.Money
import com.gymapp.domain.Now
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.PaymentState
import com.gymapp.domain.PriceBreakdown
import com.gymapp.domain.Pricing
import com.gymapp.domain.SessionCarryOver

/**
 * Üye kayıt / paket yenileme formunun taşıdığı değerler.
 *
 * `app`'teki `RegisterFormState`'in taşınmış hâli. ViewModel'in içinde
 * duruyordu; ekran ortak modüle taşınırken buraya geldi çünkü ekranın
 * sözleşmesi bu: yirmi bir alanın hepsi çiziliyor ya da bir çizim kararını
 * etkiliyor.
 *
 * Türetilmiş değerler (fiyat kalemleri, kırpılan iskonto) alan DEĞİL, `get()`
 * — sebebi aşağıda kendi yerlerinde yazılı.
 */
data class UyeKayitFormu(
    val fullName: String = "",
    val phone: String = "",
    val email: String = "",
    val paymentType: PaymentMethod = PaymentMethod.CASH,
    val installmentCount: Int = 1,
    val selectedPackage: PackageEntity? = null,
    val discount: String = "0",
    val paymentStatus: PaymentState = PaymentState.PAID,
    // `System.currentTimeMillis()` JVM'e özgüydü; ortak modülde derlenmez.
    val paymentDateMs: Long? = Now.epochMillis(),
    val healthRisks: String = "",
    val healthNotes: String = "",
    val notes: String = "",
    // Validation
    val fullNameError: String? = null,
    val phoneError: String? = null,
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val submitError: String? = null,
    val isRenewal: Boolean = false,
    val memberId: String? = null,
    /**
     * Yenilemede kalan seansların ne olacağı.
     *
     * Varsayılan [SessionCarryOver.CARRY]: üye o seansların parasını ödemiş.
     * Seçim ekranda açıkça duruyor, yani varsayılan bir politika dayatması değil
     * yalnızca imlecin başladığı yer.
     */
    val carryOver: SessionCarryOver = SessionCarryOver.CARRY,
    /**
     * Üyenin şu anki kalan seansı; `null` sınırsız paket ya da bilinmiyor.
     *
     * Ekranda seçimin gösterilip gösterilmeyeceğini bu belirliyor: devredecek
     * sayılabilir bir hak yoksa kullanıcıya sorulacak bir şey de yok.
     */
    val currentRemainingSessions: Int? = null,
) {
    /**
     * Ücretin kalemleri — **saklanmıyor, durumdan türetiliyor**.
     *
     * Önceden `previewPrice` bir alandı ve fiyatı etkileyen dört ayrı işleyicinin
     * her biri onu yeniden hesaplamakla yükümlüydü. Beşinci bir işleyici eklemek
     * (ya da mevcut birinde hesabı unutmak) ekranda eski tutarın kalması demekti;
     * türetilmiş değerde o hata mümkün değil.
     */
    val breakdown: PriceBreakdown
        get() = Pricing.breakdown(
            basePrice = Money(selectedPackage?.basePriceMinor ?: 0L),
            discount = Money.ofMajor(Decimals.parseOrDefault(discount)),
            paymentType = paymentType,
            installmentCount = installmentCount,
        )

    /**
     * Yazılan iskonto paket fiyatını aşıyor mu?
     *
     * Aşınca sessizce kırpmak, kartta "1.000 − 5.000 = 0" gibi kendi içinde
     * tutarsız bir aritmetik bırakıyordu. Kırpma duruyor ama artık görünür.
     */
    val discountCapped: Boolean
        get() = breakdown.discountWasCapped(Money.ofMajor(Decimals.parseOrDefault(discount)))
}
