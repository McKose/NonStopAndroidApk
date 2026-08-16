package com.gymapp.data.repository

import com.gymapp.data.TEST_TENANT
import com.gymapp.data.createTestDatabase
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.entity.LedgerEntryEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.data.sync.SyncQueue
import com.gymapp.data.testTenants
import com.gymapp.domain.AppointmentState
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.MemberManualStatus
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.PaymentState
import com.gymapp.domain.StaffRole
import com.gymapp.domain.TrainingType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Hakediş yalnızca gerçekten tüketilen seans için yazılmalı.
 *
 * Düzeltilen hata para kaybettiriyordu ve sessizdi. Seans **tamamlanırken**
 * düşüyor ama kota **randevu alınırken** kontrol ediliyordu: 3 seanslık pakete
 * arka arkaya 6 randevu açılabiliyor (her biri açılış anında `remaining > 0`
 * görüyor) ve altısı da tamamlanabiliyordu.
 *
 * Sayaç 3→2→1→0→0→0 gidiyordu — `decrementSession` sıfırın altına inmiyor — ama
 * hakediş koşulsuz yazıldığı için **altı** kayıt oluşuyordu. 300 TL gelire karşı
 * 240 TL gider; doğrusu 120 TL.
 *
 * Sayaçtaki sessiz taban negatif kotayı önlerken, sorunun tek belirtisini de
 * siliyordu.
 */
class AppointmentCommissionTest {

    private val db: GymDatabase = createTestDatabase()
    private val queue = SyncQueue(db.syncOutboxDao(), testTenants)
    private val ledger = LedgerRepository(db.ledgerDao(), queue, testTenants)
    private val repo = AppointmentRepository(
        database = db,
        appointmentDao = db.appointmentDao(),
        memberDao = db.memberDao(),
        staffDao = db.staffDao(),
        ledgerRepository = ledger,
        syncQueue = queue,
        tenants = testTenants,
    )

    private val uyeId = "uye-1"
    private val egitmenId = "egitmen-1"

    @AfterTest
    fun kapat() {
        db.close()
    }

    /** 3 seanslık 300 TL paket → seans değeri 100 TL. */
    private suspend fun kurulum(kalanSeans: Int?, toplamSeans: Int? = 3) {
        db.memberDao().insertMember(
            MemberEntity(
                id = uyeId, tenantId = TEST_TENANT, fullName = "Ayşe Yılmaz",
                phone = "+905001112233", totalSessions = toplamSeans,
                remainingSessions = kalanSeans,
                startDateMs = 0, endDateMs = Long.MAX_VALUE,
                status = MemberManualStatus.ACTIVE, paymentType = PaymentMethod.CASH,
                installmentCount = 1, packagePriceMinor = 30_000,
                discountMinor = 0, pricePaidMinor = 30_000, paymentStatus = PaymentState.PAID,
                riskLevel = "LOW", createdAtMs = 0, updatedAtMs = 0,
            )
        )
        // Komisyon %40 = 4000 baz puan.
        db.staffDao().insertStaff(
            StaffEntity(
                id = egitmenId, tenantId = TEST_TENANT, fullName = "Mehmet Demir",
                title = "Eğitmen", role = StaffRole.TRAINER, branch = "Fitness",
                commissionBasisPoints = 4000, monthlySalaryMinor = 0,
                phone = "+905002223344", nickname = "mehmet",
                isActive = true, createdAtMs = 0, updatedAtMs = 0,
            )
        )
    }

    /**
     * Randevu açar. Her çağrı **farklı** bir saat dilimi kullanıyor: aynı
     * eğitmene aynı saatte ikinci randevu `countOverlapping` kontrolüne takılır
     * ve sınamak istediğimiz şey o değil.
     */
    private suspend fun randevuAc(dilim: Int): String {
        val saat = 3_600_000L
        return repo.create(
            memberId = uyeId,
            staffId = egitmenId,
            trainingType = TrainingType.FITNESS,
            startTimeMs = dilim * saat,
            endTimeMs = (dilim + 1) * saat,
        ).getOrElse { fail("Randevu açılamadı (dilim $dilim): $it") }
    }

    // ─── Asıl iddia ─────────────────────────────────────────────────────────

    /**
     * Kotası biten üyede randevu tamamlanamıyor ve hakediş yazılmıyor.
     *
     * Bu testin sınadığı şey doğrudan para: reddedilmezse eğitmene, üyenin
     * satın almadığı bir seans için ödeme yapılırdı.
     */
    @Test
    fun `kotasi biten uyede tamamlama reddediliyor`() = runTest {
        // Kota 1 iken İKİ randevu açılıyor: açılış kontrolü ikisini de geçirir
        // (o anda `remaining = 1 > 0`). Hatanın çekirdeği tam olarak bu — kota
        // açılışta bakılıyor, tüketim tamamlamada oluyor.
        kurulum(kalanSeans = 1)
        val ilk = randevuAc(dilim = 1)
        val ikinci = randevuAc(dilim = 2)

        repo.processStatus(ilk, AppointmentState.COMPLETED, notes = null).getOrThrow()
        val sonuc = repo.processStatus(ikinci, AppointmentState.COMPLETED, notes = null)

        assertTrue(sonuc.isFailure, "Kalan seansı bitmiş üyede tamamlama reddedilmeliydi")
        assertEquals(
            1, hakedisKayitSayisi(),
            "Yalnızca tüketilen seans için hakediş yazılmalı",
        )
    }

    /**
     * Kota bittikten sonraki tamamlamalar hakediş üretmiyor.
     *
     * Hatanın tam senaryosu: 3 seanslık pakete 6 randevu. Üçü tamamlanır ve üç
     * hakediş yazılır; dördüncüsü reddedilir.
     */
    @Test
    fun `kota kadar hakedis yaziliyor, fazlasi degil`() = runTest {
        kurulum(kalanSeans = 3)

        // Altı randevunun HEPSİ açılabiliyor: her biri açılış anında
        // `remaining = 3 > 0` görüyor. Tüketim ise tamamlamada.
        val randevular = (1..6).map { randevuAc(dilim = it) }

        var tamamlanan = 0
        var reddedilen = 0
        for (randevu in randevular) {
            if (repo.processStatus(randevu, AppointmentState.COMPLETED, notes = null).isSuccess) {
                tamamlanan++
            } else {
                reddedilen++
            }
        }

        assertEquals(3, tamamlanan, "Yalnızca kota kadar randevu tamamlanabilmeli")
        assertEquals(3, reddedilen, "Kota bitince tamamlama reddedilmeli")
        assertEquals(3, hakedisKayitSayisi(), "Kota kadar hakediş yazılmalı")
        assertEquals(
            Money.ofMajor(120.0), hakedisToplamTutari(),
            "300 TL gelire karşı 120 TL hakediş; hatalı hâlde 240 TL yazılıyordu",
        )
        assertEquals(0, db.memberDao().getMemberById(uyeId)?.remainingSessions)
    }

    // ─── Sınırsız paket ─────────────────────────────────────────────────────

    /**
     * Sınırsız (abonman) pakette tamamlama engellenmiyor.
     *
     * Kontrol `null` kotayı "bitmiş" saysaydı abonmanlı üyenin hiçbir randevusu
     * tamamlanamazdı — düzeltmenin en olası yan hasarı bu.
     */
    @Test
    fun `sinirsiz pakette tamamlama engellenmiyor`() = runTest {
        kurulum(kalanSeans = null, toplamSeans = null)
        val randevu = randevuAc(dilim = 1)

        val sonuc = repo.processStatus(randevu, AppointmentState.COMPLETED, notes = null)

        assertTrue(sonuc.isSuccess, "Sınırsız pakette tamamlama engellenmemeli: ${sonuc.exceptionOrNull()}")
    }

    // ─── Geri alma ──────────────────────────────────────────────────────────

    /**
     * Tamamlama geri alınınca seans iade ediliyor ve tekrar tamamlanabiliyor.
     *
     * Yanlışlıkla tamamlanan bir randevu geri alındığında üyenin hakkı geri
     * gelmezse, düzeltme kullanıcıya seans kaybettirirdi.
     */
    @Test
    fun `geri alinan randevu seansi iade ediyor`() = runTest {
        kurulum(kalanSeans = 1)
        val randevu = randevuAc(dilim = 1)

        repo.processStatus(randevu, AppointmentState.COMPLETED, notes = null).getOrThrow()
        assertEquals(0, db.memberDao().getMemberById(uyeId)?.remainingSessions)

        repo.processStatus(randevu, AppointmentState.CANCELLED, notes = null).getOrThrow()
        assertEquals(1, db.memberDao().getMemberById(uyeId)?.remainingSessions, "Seans iade edilmeli")

        // İade edildiğine göre yeniden tamamlanabilmeli.
        val tekrar = repo.processStatus(randevu, AppointmentState.COMPLETED, notes = null)
        assertTrue(tekrar.isSuccess, "İadeden sonra tekrar tamamlanabilmeli: ${tekrar.exceptionOrNull()}")
    }

    // ─── Yardımcılar ────────────────────────────────────────────────────────

    /**
     * Defterdeki hakediş kayıtları.
     *
     * Test için DAO'ya yeni sorgu eklenmedi; üretimde zaten var olan
     * `observeBetween` kullanılıyor. Test-özel bir sorgu, üretimde koşmayan bir
     * kod yolunu doğrulamak olurdu.
     */
    private suspend fun hakedisKayitlari(): List<LedgerEntryEntity> =
        db.ledgerDao()
            .observeBetween(TEST_TENANT, startMs = 0L, endMs = Long.MAX_VALUE)
            .first()
            .filter { it.category == LedgerCategory.COMMISSION }

    private suspend fun hakedisKayitSayisi(): Int = hakedisKayitlari().size

    private suspend fun hakedisToplamTutari(): Money =
        Money(hakedisKayitlari().sumOf { it.amountMinor })
}
