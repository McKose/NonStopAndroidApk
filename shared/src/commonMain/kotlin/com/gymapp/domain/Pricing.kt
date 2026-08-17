package com.gymapp.domain

/**
 * Üyelik fiyat hesaplarının tek doğruluk kaynağı.
 *
 * Daha önce bu mantık [com.gymapp.data.repository.MemberRepository] içine gömülüydü ve
 * taksit seçenekleri UI'da ayrıca sabit kodlanmıştı (ikisi birbirinden sapabiliyordu).
 *
 * **Tek hesap var — [breakdown].** Ekranın gösterdiği kalemler ve kaydedilen
 * tutar aynı çağrıdan çıkıyor; [finalPrice] onun yalnızca toplamını veren ince
 * bir kabuk. Önceden ekran önizlemesi ayrı bir yoldan (TL, `Double`)
 * hesaplanıyor ve kalemleri ekran kendisi üretiyordu — gösterilen aritmetiğin
 * toplamla uyuşmaması bu yüzden mümkündü.
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
     * Tutarın nasıl oluştuğu — kalem kalem.
     *
     *  - negatif iskonto fiyatı artıramaz (0'a çekilir)
     *  - iskonto paket fiyatını aşamaz (sonuç negatife düşemez)
     *  - vade farkı yalnızca kredi kartı taksitinde uygulanır
     *
     * **Neden tek yapı:** Ekran bu kalemleri tek tek yazıyor. Önceden yalnızca
     * toplam hesaplanıyordu ve kalemleri ekran kendisi üretiyordu; ikisi
     * ayrışınca kart, toplamıyla uyuşmayan bir aritmetik gösteriyordu (bkz.
     * [PriceBreakdown.discount]). Artık gösterilen her satır kaydedilen tutarla
     * aynı hesaptan geliyor.
     */
    fun breakdown(
        basePrice: Money,
        discount: Money,
        paymentType: PaymentMethod,
        installmentCount: Int,
    ): PriceBreakdown {
        val safeBase = basePrice.coerceNonNegative()
        val safeDiscount = discount.coerceNonNegative().coerceAtMost(safeBase)
        val net = safeBase - safeDiscount

        val rate = if (paymentType == PaymentMethod.CARD) {
            Rate(INSTALLMENT_SURCHARGE_BASIS_POINTS[installmentCount] ?: 0)
        } else {
            Rate.ZERO
        }

        return PriceBreakdown(
            basePrice = safeBase,
            discount = safeDiscount,
            net = net,
            surchargeRate = rate,
            surcharge = net.applyRate(rate),
        )
    }

    /** Ödenecek nihai tutar (kuruş) — [breakdown] ile aynı hesap. */
    fun finalPrice(
        basePrice: Money,
        discount: Money,
        paymentType: PaymentMethod,
        installmentCount: Int,
    ): Money = breakdown(basePrice, discount, paymentType, installmentCount).total

    // KALDIRILDI: `previewPrice`. Ekran önizlemesini TL (`Double`) üzerinden
    // veren ikinci bir yoldu; artık ekran kalemleri kuruş cinsinden
    // [breakdown] ile alıyor. Geçersiz girdi koruması kaybolmadı: sonsuz/NaN
    // değerler zaten `Decimals.parseOrNull` ve `Money.ofMajor` tarafından
    // sıfıra çekiliyor.

    // KALDIRILDI: `coerceDiscount`. İskontoyu paket fiyatına çekmek zaten
    // [breakdown] içinde yapılıyor ve sonucu `PriceBreakdown.discount` olarak
    // dışarı veriliyor. Ayrı bir yardımcı, aynı kuralın ikinci bir kopyası
    // olurdu — üstelik hiç çağrılmadığı için ekran ham girdiyi gösteriyordu.

    // KALDIRILDI: `surchargePercent`. Oran artık `PriceBreakdown.surchargeRate`
    // ile tutarın kendisiyle birlikte geliyor; ekranın yüzdeyi ayrı bir
    // çağrıyla sorması, gösterilen oranla uygulanan oranın ayrışabilmesi
    // demekti.

    /** Taksit sayısı yalnızca kartlı ödemede anlamlıdır. */
    fun normalizeInstallment(paymentType: PaymentMethod, installmentCount: Int): Int =
        if (paymentType == PaymentMethod.CARD && installmentCount in installmentOptions) {
            installmentCount
        } else {
            1
        }
}

/**
 * Bir üyelik ücretinin kalemleri.
 *
 * [total] saklanmıyor, [net] ve [surcharge] üzerinden türetiliyor: kalemlerin
 * toplamı ile gösterilen toplamın ayrışması mümkün olmasın.
 */
data class PriceBreakdown(
    /** Paket fiyatı (negatifse sıfıra çekilmiş). */
    val basePrice: Money,
    /** Uygulanan iskonto — paket fiyatına çekilmiş hâli, kullanıcının yazdığı ham değer değil. */
    val discount: Money,
    /** İskonto sonrası tutar; vade farkı bunun üzerinden hesaplanır. */
    val net: Money,
    /** Uygulanan vade farkı oranı; kartlı taksit dışında sıfır. */
    val surchargeRate: Rate,
    /** Vade farkı tutarı. */
    val surcharge: Money,
) {
    /** Ödenecek tutar. */
    val total: Money get() = net + surcharge

    /** Kullanıcının yazdığı iskonto paket fiyatını aşmış mı — ekran uyarabilsin diye. */
    fun discountWasCapped(requested: Money): Boolean =
        requested.coerceNonNegative() > discount
}
