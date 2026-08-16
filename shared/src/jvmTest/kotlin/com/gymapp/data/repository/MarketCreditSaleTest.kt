package com.gymapp.data.repository

import com.gymapp.data.TEST_TENANT
import com.gymapp.data.createTestDatabase
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.sync.SyncQueue
import com.gymapp.data.testTenants
import com.gymapp.domain.DeliveryStatus
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Veresiye market satışı: ürün stoktan çıkıyorsa karşılığında borç doğmalı.
 *
 * Düzeltilen hata sessiz ve geri dönüşsüzdü. `processOrder` defter kaydını
 * **yalnızca ödeme alındıysa** yazıyordu; "Ödenmedi" seçildiğinde hiçbir defter
 * kaydı oluşmuyordu. Üstelik bakiye sorgusu market kategorisini toptan elediği
 * için, kayıt yazılsa bile borç görünmeyecekti.
 *
 * Sonuç: 3 protein bar × 150 TL veresiye satıldığında ürünler stoktan kalıcı
 * olarak düşüyor, sipariş satırı yazılıyor, üyenin bakiyesi **sıfır** görünüyor
 * ve ödeme geldiğinde kaydedecek hiçbir ekran bulunmuyordu. 450 TL iz
 * bırakmadan kayboluyordu.
 *
 * Gerçek SQLite üzerinde koşuyor: sınananların çoğu SQL semantiğinde.
 */
class MarketCreditSaleTest {

    private val db: GymDatabase = createTestDatabase()
    private val queue = SyncQueue(db.syncOutboxDao(), testTenants)
    private val ledger = LedgerRepository(db.ledgerDao(), queue, testTenants)
    private val repo = ProductRepository(
        database = db,
        productDao = db.productDao(),
        orderDao = db.orderDao(),
        stockMovementDao = db.stockMovementDao(),
        ledgerRepository = ledger,
        syncQueue = queue,
        tenants = testTenants,
    )

    private val uye = "uye-1"

    @AfterTest
    fun kapat() {
        db.close()
    }

    /** 150 TL'lik ürün, stoğu [adet]. */
    private suspend fun urunHazirla(adet: Int): String =
        repo.saveProduct(
            productId = null,
            name = "Protein Bar",
            category = "Supplement",
            price = Money.ofMajor(150.0),
            desiredStock = adet,
        ).getOrElse { fail("Ürün kurulamadı: $it") }

    // ─── Veresiye ───────────────────────────────────────────────────────────

    @Test
    fun `veresiye satis uyeye borc doguruyor`() = runTest {
        val urun = urunHazirla(adet = 10)

        repo.processOrder(
            memberId = uye,
            cartItems = mapOf(urun to 3),
            paymentMethod = PaymentMethod.CASH,
            paymentStatus = "PENDING",
            deliveryStatus = DeliveryStatus.POST_DELIVERY,
        ).getOrElse { fail("Satış başarısız: $it") }

        assertEquals(
            Money.ofMajor(450.0),
            ledger.outstandingBalance(uye),
            "Veresiye satış üyeye borç yazmalı; yoksa para iz bırakmadan kaybolur",
        )
    }

    /**
     * Borç, normal tahsilatla kapanabiliyor.
     *
     * Asıl mesele bu: borç görünse ama tahsil edilemese sorun çözülmüş olmazdı.
     * Tahsilat `MEMBERSHIP` kategorisinde yazılıyor ve bakiyede sayılıyor.
     */
    @Test
    fun `veresiye borcu tahsil edilebiliyor`() = runTest {
        val urun = urunHazirla(adet = 10)
        repo.processOrder(
            memberId = uye,
            cartItems = mapOf(urun to 3),
            paymentMethod = PaymentMethod.CASH,
            paymentStatus = "PENDING",
            deliveryStatus = DeliveryStatus.POST_DELIVERY,
        ).getOrThrow()

        ledger.recordPayment(
            amount = Money.ofMajor(450.0),
            method = PaymentMethod.CASH,
            description = "Market borcu tahsilatı",
            memberId = uye,
        ).getOrThrow()

        assertEquals(Money.ZERO, ledger.outstandingBalance(uye), "Borç kapanmalı")
    }

    /** Veresiye satışta da stok gerçekten düşüyor. */
    @Test
    fun `veresiye satista stok dusuyor`() = runTest {
        val urun = urunHazirla(adet = 10)
        repo.processOrder(
            memberId = uye,
            cartItems = mapOf(urun to 3),
            paymentMethod = PaymentMethod.CASH,
            paymentStatus = "PENDING",
            deliveryStatus = DeliveryStatus.POST_DELIVERY,
        ).getOrThrow()

        assertEquals(7, db.stockMovementDao().onHand(TEST_TENANT, urun))
    }

    /**
     * Misafire veresiye satılamıyor.
     *
     * Borcun yazılacağı bir hesap yok; izin verilseydi ürün stoktan çıkar ve
     * alacak hiçbir yere kaydedilemezdi — yani düzeltilen hatanın aynısı.
     */
    @Test
    fun `misafire veresiye satilamiyor`() = runTest {
        val urun = urunHazirla(adet = 10)

        val sonuc = repo.processOrder(
            memberId = null,
            cartItems = mapOf(urun to 1),
            paymentMethod = PaymentMethod.CASH,
            paymentStatus = "PENDING",
            deliveryStatus = DeliveryStatus.POST_DELIVERY,
        )

        assertTrue(sonuc.isFailure, "Misafire veresiye satış reddedilmeliydi")
        assertEquals(
            10, db.stockMovementDao().onHand(TEST_TENANT, urun),
            "Reddedilen satış stoğa dokunmamalı",
        )
    }

    // ─── Ödenmiş satış ──────────────────────────────────────────────────────

    /**
     * Ödenmiş satış borç doğurmuyor.
     *
     * Kasada anında kapanan bir satış; ciroya girer ama üyenin hesabında alacak
     * bırakmaz. Bu, market tahsilatının paket borcunu kapatmaması kuralının
     * (`LedgerBalanceTest`) diğer yüzü.
     */
    @Test
    fun `odenmis satis borc dogurmuyor`() = runTest {
        val urun = urunHazirla(adet = 10)

        repo.processOrder(
            memberId = uye,
            cartItems = mapOf(urun to 3),
            paymentMethod = PaymentMethod.CASH,
            paymentStatus = "PAID",
            deliveryStatus = DeliveryStatus.POST_DELIVERY,
        ).getOrThrow()

        assertEquals(Money.ZERO, ledger.outstandingBalance(uye))
    }

    /** Misafire peşin satış serbest. */
    @Test
    fun `misafire pesin satis yapilabiliyor`() = runTest {
        val urun = urunHazirla(adet = 10)

        val sonuc = repo.processOrder(
            memberId = null,
            cartItems = mapOf(urun to 1),
            paymentMethod = PaymentMethod.CASH,
            paymentStatus = "PAID",
            deliveryStatus = DeliveryStatus.POST_DELIVERY,
        )

        assertTrue(sonuc.isSuccess, "Misafire peşin satış engellenmemeli: ${sonuc.exceptionOrNull()}")
        assertEquals(9, db.stockMovementDao().onHand(TEST_TENANT, urun))
    }

    /**
     * Stok yetmiyorsa satış olmuyor ve defterde iz kalmıyor.
     *
     * Sipariş, stok ve defter aynı transaction'da; biri düşerse hiçbiri kalmamalı.
     */
    @Test
    fun `stok yetmezse hicbir sey yazilmiyor`() = runTest {
        val urun = urunHazirla(adet = 2)

        val sonuc = repo.processOrder(
            memberId = uye,
            cartItems = mapOf(urun to 5),
            paymentMethod = PaymentMethod.CASH,
            paymentStatus = "PENDING",
            deliveryStatus = DeliveryStatus.POST_DELIVERY,
        )

        assertTrue(sonuc.isFailure, "Stok yetmezken satış olmamalı")
        assertEquals(2, db.stockMovementDao().onHand(TEST_TENANT, urun), "Stok korunmalı")
        assertEquals(Money.ZERO, ledger.outstandingBalance(uye), "Defterde iz kalmamalı")
    }
}
