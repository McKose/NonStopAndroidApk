package com.gymapp.domain

import com.gymapp.data.local.entity.PaymentType

/**
 * Üyelik fiyat hesaplarının tek doğruluk kaynağı.
 *
 * Daha önce bu mantık [com.gymapp.data.repository.MemberRepository] içine gömülüydü ve
 * taksit seçenekleri UI'da ayrıca sabit kodlanmıştı (ikisi birbirinden sapabiliyordu).
 */
object Pricing {

    /** Kart ile ödemede taksit sayısına uygulanan vade farkı (yüzde). */
    private val INSTALLMENT_SURCHARGE_PERCENT: Map<Int, Double> = mapOf(
        1 to 0.0,
        2 to 3.0,
        3 to 5.0,
        6 to 10.0,
        9 to 15.0,
        12 to 20.0,
    )

    /** UI'daki taksit seçenekleri buradan beslenir; oran tablosuyla asla sapmaz. */
    val installmentOptions: List<Int> = INSTALLMENT_SURCHARGE_PERCENT.keys.sorted()

    /**
     * Ödenecek nihai tutar.
     *
     * Hesap her tuş vuruşunda önizleme için çağrıldığından bilinçli olarak *fırlatmaz*:
     * geçersiz girdiler güvenli aralığa çekilir.
     *  - negatif iskonto fiyatı artıramaz (0'a çekilir)
     *  - iskonto paket fiyatını aşamaz (sonuç negatife düşemez)
     */
    fun finalPrice(
        basePrice: Double,
        discount: Double,
        paymentType: PaymentType,
        installmentCount: Int,
    ): Double {
        val safeBase = if (basePrice.isFinite() && basePrice > 0.0) basePrice else 0.0
        val safeDiscount = if (discount.isFinite()) discount.coerceIn(0.0, safeBase) else 0.0
        val net = safeBase - safeDiscount

        // Vade farkı yalnızca kredi kartı taksitinde geçerli.
        if (paymentType != PaymentType.CARD) return net

        val surchargePercent = INSTALLMENT_SURCHARGE_PERCENT[installmentCount] ?: 0.0
        return net * (1.0 + surchargePercent / 100.0)
    }
}
