package com.gymapp.domain

/**
 * Üyelik fiyat hesaplarının tek doğruluk kaynağı.
 *
 * Daha önce bu mantık [com.gymapp.data.repository.MemberRepository] içine gömülüydü ve
 * taksit seçenekleri UI'da ayrıca sabit kodlanmıştı (ikisi birbirinden sapabiliyordu).
 *
 * İki giriş noktası var ve ikisi **aynı oran tablosundan** beslenir:
 *  - [finalPrice] — kuruş cinsinden, **kaydedilen** tutar
 *  - [previewPrice] — her tuş vuruşunda çağrılan ekran önizlemesi
 */
object Pricing {

    /** Kart ile ödemede taksit sayısına uygulanan vade farkı (baz puan; 300 = %3). */
    private val INSTALLMENT_SURCHARGE_BASIS_POINTS: Map<Int, Int> = mapOf(
        1 to 0,
        2 to 300,
        3 to 500,
        6 to 1_000,
        9 to 1_500,
        12 to 2_000,
    )

    /** UI'daki taksit seçenekleri buradan beslenir; oran tablosuyla asla sapmaz. */
    val installmentOptions: List<Int> = INSTALLMENT_SURCHARGE_BASIS_POINTS.keys.sorted()

    /**
     * Ödenecek nihai tutar (kuruş).
     *
     *  - negatif iskonto fiyatı artıramaz (0'a çekilir)
     *  - iskonto paket fiyatını aşamaz (sonuç negatife düşemez)
     *  - vade farkı yalnızca kredi kartı taksitinde uygulanır
     */
    fun finalPrice(
        basePrice: Money,
        discount: Money,
        paymentType: PaymentMethod,
        installmentCount: Int,
    ): Money {
        val safeBase = basePrice.coerceNonNegative()
        val safeDiscount = discount.coerceNonNegative().coerceAtMost(safeBase)
        val net = safeBase - safeDiscount

        if (paymentType != PaymentMethod.CARD) return net

        val surcharge = INSTALLMENT_SURCHARGE_BASIS_POINTS[installmentCount] ?: 0
        return net + net.applyRate(Rate(surcharge))
    }

    /**
     * Ekran önizlemesi (TL).
     *
     * Bilinçli olarak *fırlatmaz*: her tuş vuruşunda çağrıldığı için geçersiz
     * girdiler güvenli aralığa çekilir.
     */
    fun previewPrice(
        basePrice: Double,
        discount: Double,
        paymentType: PaymentMethod,
        installmentCount: Int,
    ): Double = finalPrice(
        basePrice = Money.ofMajor(if (basePrice.isFinite()) basePrice else 0.0),
        discount = Money.ofMajor(if (discount.isFinite()) discount else 0.0),
        paymentType = paymentType,
        installmentCount = installmentCount,
    ).asDouble

    /** İskonto girdisi paket fiyatını aşamaz; ekran ve kayıt aynı kuralı kullanır. */
    fun coerceDiscount(discount: Double, basePrice: Double): Double {
        if (!discount.isFinite() || discount <= 0.0) return 0.0
        val safeBase = if (basePrice.isFinite() && basePrice > 0.0) basePrice else 0.0
        return minOf(discount, safeBase)
    }

    /** Taksit sayısı yalnızca kartlı ödemede anlamlıdır. */
    fun normalizeInstallment(paymentType: PaymentMethod, installmentCount: Int): Int =
        if (paymentType == PaymentMethod.CARD && installmentCount in installmentOptions) {
            installmentCount
        } else {
            1
        }

    /** Yüzde cinsinden vade farkı — yalnızca gösterim için. */
    fun surchargePercent(installmentCount: Int): Double =
        (INSTALLMENT_SURCHARGE_BASIS_POINTS[installmentCount] ?: 0) / 100.0
}
