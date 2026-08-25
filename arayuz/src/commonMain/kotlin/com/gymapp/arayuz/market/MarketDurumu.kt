package com.gymapp.arayuz.market

import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.ProductEntity
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentState

/**
 * Market ekranının durumu — `app`'teki `MarketUiState`'in taşınmış hâli.
 *
 * Ekranla birlikte geldi çünkü sepet tutarları burada, `get()` olarak
 * hesaplanıyor ve ekranın hepsini okuması gerekiyor. Sınıfta platforma özgü
 * hiçbir şey yok; `FinanceUiState`'in aksine (o `LocalDate.now()` kullandığı
 * için `app`'te kaldı) taşınmasının önünde engel yoktu.
 */
data class MarketDurumu(
    val products: List<ProductEntity> = emptyList(),
    val members: List<MemberEntity> = emptyList(),
    val cart: Map<String, Int> = emptyMap(), // ProductId -> Quantity
    val selectedMemberId: String? = null,
    val paymentType: String = "CASH",
    val paymentStatus: PaymentState = PaymentState.PAID,
    val deliveryStatus: String = "POST_DELIVERY",
    val discount: Double = 0.0,
    val notes: String = "",
    /** Ürün kimliği → eldeki stok (hareketlerin toplamı). */
    val stockByProduct: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val isCheckingOut: Boolean = false,
) {
    fun stockOf(productId: String): Int = stockByProduct[productId] ?: 0

    // ─── Sepet tutarları ────────────────────────────────────────────────────
    //
    // Burada, ekranda değil. Ekran bu hesabı İKİ ayrı yerde `Double` ile
    // tekrarlıyordu; gerçek tutar ise kuruş tam sayısıyla hesaplanıyor
    // (`ProductRepository.processOrder`). İki ayrı aritmetik, gösterilen ile
    // çekilen tutarın sapması demekti.
    //
    // İfadeler deponunkiyle birebir aynı — sapmanın kaynağı buydu.

    /** Sepetin iskonto öncesi tutarı. */
    val cartTotal: Money
        get() = Money(
            cart.entries.sumOf { (id, adet) ->
                (products.find { it.id == id }?.priceMinor ?: 0L) * adet
            }
        )

    /**
     * Uygulanan iskonto — sepeti **aşamaz**.
     *
     * Ekran kırpmıyordu, depo kırpıyordu. 50 TL'lik sepete 80 TL iskonto
     * girildiğinde ekran "₺-30,00" gösteriyor, kaydedilen siparişin nihai tutarı
     * ise 0 oluyordu. Üstelik `processOrder` tahsilatı yalnızca tutar pozitifken
     * yazdığı için, "ÖDENDİ" işaretli o sipariş **hiç tahsilat yazmıyordu**:
     * ürünler stoktan çıkıyor, gelir sıfır kalıyordu.
     */
    val cartDiscount: Money
        get() = Money.ofMajor(discount).coerceNonNegative().coerceAtMost(cartTotal)

    /** Tahsil edilecek tutar. */
    val cartFinal: Money
        get() = cartTotal - cartDiscount
}
