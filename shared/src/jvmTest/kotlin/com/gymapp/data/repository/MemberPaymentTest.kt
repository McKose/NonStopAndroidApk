package com.gymapp.data.repository

import com.gymapp.data.TEST_TENANT
import com.gymapp.data.createTestDatabase
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.sync.SampleRows
import com.gymapp.data.sync.SyncQueue
import com.gymapp.data.testTenants
import com.gymapp.domain.LedgerType
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.PaymentState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tahsilat, **girilen tutar kadar** yazılmalı — geçmişten devreden borcun
 * tamamı kadar değil.
 *
 * ### Düzeltilen hata
 * "Ödemeyi Onayla" düğmesi tek dokunuşta, hiçbir tutar göstermeden, kalan
 * borcun tamamını tahsil ediyordu. Borç tarih sınırsız hesaplandığı için eski
 * aylardan devreden borç bugünkü tahsilata ekleniyordu.
 *
 * **Senaryo:** marttan kalma 1.000 TL borcu olan üye ağustosta 1.200 TL'lik
 * paket alıyor ve kasada 1.200 TL ödüyor. Personel "Ödendi" işaretliyor;
 * deftere **2.200 TL** tahsilat giriyor. Ağustos geliri 1.000 TL şişiyor, kasa
 * o kadar açık veriyor ve farkı gösteren hiçbir ekran yok.
 *
 * Gerçek SQLite üzerinde koşuyor: sınananların çoğu bakiye SQL'inde.
 */
class MemberPaymentTest {

    private val db: GymDatabase = createTestDatabase()
    private val queue = SyncQueue(db.syncOutboxDao(), testTenants)
    private val ledger = LedgerRepository(db.ledgerDao(), queue, testTenants)
    private val repo = MemberRepository(
        database = db,
        memberDao = db.memberDao(),
        ledgerRepository = ledger,
        measurementDao = db.measurementDao(),
        syncQueue = queue,
        tenants = testTenants,
    )

    @AfterTest
    fun kapat() {
        db.close()
    }

    /**
     * Marttan 1.000 TL, ağustostan 1.200 TL borcu olan üye.
     *
     * Üye satırı doğrudan yazılıyor: sınanan şey tahsilat yolu, kayıt yolu
     * değil. `registerMember` paket zorunlu kıldığı için kurgu gereksiz yere
     * paket/fiyat ayrıntısına bağlanırdı.
     */
    private suspend fun borcluUye(): String {
        val id = "uye-1"
        db.memberDao().insertMember(
            SampleRows.member.copy(
                id = id,
                tenantId = TEST_TENANT,
                paymentStatus = PaymentState.PENDING,
                paymentType = PaymentMethod.CASH,
                deletedAtMs = null,
            )
        )

        ledger.recordCharge(
            memberId = id,
            amount = Money.ofMajor(1_000.0),
            description = "Mart paketi",
            occurredAtMs = MART,
        ).getOrThrow()

        ledger.recordCharge(
            memberId = id,
            amount = Money.ofMajor(1_200.0),
            description = "Ağustos paketi",
            occurredAtMs = AGUSTOS,
        ).getOrThrow()

        return id
    }

    /** Bu üye için deftere yazılmış tahsilatların toplamı. */
    private suspend fun tahsilatToplami(memberId: String): Money =
        Money(
            db.ledgerDao().observeBetween(TEST_TENANT, 0, Long.MAX_VALUE).first()
                .filter { it.memberId == memberId && it.type == LedgerType.PAYMENT }
                .sumOf { it.amountMinor }
        )

    // ─── Asıl hata ──────────────────────────────────────────────────────────

    /**
     * Girilen tutar kadar tahsilat yazılıyor.
     *
     * Kurgu bozulup tutar yok sayılırsa toplam 2.200 TL olur ve test düşer —
     * düzeltilen hatanın tam olarak ürettiği sayı.
     */
    @Test
    fun `tahsilat girilen tutar kadar yaziliyor`() = runTest {
        val uye = borcluUye()
        assertEquals(
            Money.ofMajor(2_200.0), repo.outstandingBalance(uye),
            "Kurgu bozuk: iki dönemin borcu birikmiş olmalıydı",
        )

        repo.updatePaymentStatus(uye, isPaid = true, amount = Money.ofMajor(1_200.0)).getOrThrow()

        assertEquals(
            Money.ofMajor(1_200.0), tahsilatToplami(uye),
            "Yalnızca kasada alınan tutar deftere girmeli; devreden borç bugünkü gelire eklenemez",
        )
    }

    /** Kısmi tahsilattan sonra kalan borç sürüyor. */
    @Test
    fun `kismi tahsilattan sonra borc suruyor`() = runTest {
        val uye = borcluUye()

        repo.updatePaymentStatus(uye, isPaid = true, amount = Money.ofMajor(1_200.0)).getOrThrow()

        assertEquals(
            Money.ofMajor(1_000.0), repo.outstandingBalance(uye),
            "Marttan devreden borç kapanmamalı",
        )
    }

    /**
     * Kısmi tahsilatta üye "Ödendi" sayılmıyor.
     *
     * Sayılsaydı kalan 1.000 TL hiçbir listede uyarı üretmez ve tahsil
     * edilecek para sessizce unutulurdu — hatanın en pahalı sonucu bu olurdu.
     */
    @Test
    fun `kismi tahsilatta uye odendi sayilmiyor`() = runTest {
        val uye = borcluUye()

        repo.updatePaymentStatus(uye, isPaid = true, amount = Money.ofMajor(1_200.0)).getOrThrow()

        assertEquals(
            PaymentState.PENDING,
            db.memberDao().getMemberById(uye)?.paymentStatus,
            "Borcu süren üye ödemiş sayılamaz",
        )
    }

    /** Borcun tamamı ödenince üye "Ödendi" oluyor. */
    @Test
    fun `borcun tamami odenince uye odendi oluyor`() = runTest {
        val uye = borcluUye()

        repo.updatePaymentStatus(uye, isPaid = true, amount = Money.ofMajor(2_200.0)).getOrThrow()

        assertEquals(Money.ZERO, repo.outstandingBalance(uye))
        assertEquals(PaymentState.PAID, db.memberDao().getMemberById(uye)?.paymentStatus)
    }

    // ─── Sınırlar ───────────────────────────────────────────────────────────

    /** Kalan borçtan fazlası tahsil edilemiyor. */
    @Test
    fun `borctan fazla tahsil edilemiyor`() = runTest {
        val uye = borcluUye()

        val sonuc = repo.updatePaymentStatus(uye, isPaid = true, amount = Money.ofMajor(5_000.0))

        assertTrue(sonuc.isFailure, "Borçtan fazla tahsilat reddedilmeliydi")
        assertEquals(
            Money.ZERO, tahsilatToplami(uye),
            "Reddedilen tahsilat deftere iz bırakmamalı",
        )
    }

    /** Sıfır tutarlı tahsilat anlamsız; reddediliyor. */
    @Test
    fun `sifir tutarli tahsilat reddediliyor`() = runTest {
        val uye = borcluUye()

        assertTrue(
            repo.updatePaymentStatus(uye, isPaid = true, amount = Money.ZERO).isFailure,
            "Sıfır tutarlı tahsilat reddedilmeliydi",
        )
    }

    /**
     * Tutar verilmezse eski davranış sürüyor: kalan borcun tamamı.
     *
     * Geriye dönük uyumluluk değil bilinçli bir varsayılan — ekran artık tutarı
     * gösterip açıkça geçiyor, ama tutar verilmeyen bir çağrı da tanımsız
     * kalmamalı.
     */
    @Test
    fun `tutar verilmezse kalan borcun tamami tahsil ediliyor`() = runTest {
        val uye = borcluUye()

        repo.updatePaymentStatus(uye, isPaid = true).getOrThrow()

        assertEquals(Money.ofMajor(2_200.0), tahsilatToplami(uye))
        assertEquals(Money.ZERO, repo.outstandingBalance(uye))
    }

    private companion object {
        const val MART = 1_741_000_000_000L
        const val AGUSTOS = 1_754_000_000_000L
    }
}
