package com.gymapp.data.repository

import com.gymapp.data.TEST_TENANT
import com.gymapp.data.createTestDatabase
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.entity.LedgerEntryEntity
import com.gymapp.data.local.entity.aktifKayitlar
import com.gymapp.data.sync.SampleRows
import com.gymapp.data.sync.SyncQueue
import com.gymapp.data.sync.SyncTable
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Üye silindiğinde defterin ne olduğu.
 *
 * ### Düzeltilen hata
 * `deleteMember` yalnızca üye satırını tombstone yapıyordu; deftere **hiç**
 * dokunmuyordu. Yanlışlıkla kaydedilen bir üye silindiğinde ondan doğan
 * tahsilatlar finansta olduğu gibi kalıyordu: salon o parayı almış görünüyor,
 * ay cirosu şişiyor ve kaydı düzeltecek hiçbir ekran bulunmuyordu. Silinen
 * üyenin adı da listelerden kalktığı için o satırların kime ait olduğu bile
 * anlaşılmıyordu.
 *
 * ### Neden koşulsuz iptal de yanlış olurdu
 * Gerçekten ödeme yapmış bir üyenin kaydı silindiğinde ciro sessizce düşerdi.
 * İki durum da doğru; ayırt edebilecek tek şey kullanıcı. Bu yüzden hangi
 * kayıtların iptal edileceği **çağırandan** geliyor ve boş liste "deftere
 * dokunma" demek.
 *
 * Gerçek SQLite üzerinde koşuyor: sınananların bir kısmı ters kaydı bulan
 * SQL'de.
 */
class UyeSilmeDefterTest {

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
     * Her üyeye ayrı telefon.
     *
     * `(tenantId, phone)` **benzersiz** ve örnek satırın telefonu sabit; iki
     * üye eklemek isteyen test, sınadığı şeye hiç gelemeden kısıt hatasıyla
     * düşerdi. JUnit her test için sınıfı yeniden kurduğu için sayaç da
     * testler arasında sıfırlanıyor.
     */
    private var sonrakiTelefon = 0

    /** Bir tahakkuk ve bir tahsilatı olan üye. */
    private suspend fun kayitliUye(id: String = "uye-1"): String {
        db.memberDao().insertMember(
            SampleRows.member.copy(
                id = id,
                tenantId = TEST_TENANT,
                phone = "+9050000000${++sonrakiTelefon}",
                paymentStatus = PaymentState.PENDING,
                paymentType = PaymentMethod.CASH,
                deletedAtMs = null,
            )
        )

        ledger.recordCharge(
            memberId = id,
            amount = Money.ofMajor(1_000.0),
            description = "Paket satışı",
        ).getOrThrow()

        ledger.recordPayment(
            amount = Money.ofMajor(1_000.0),
            method = PaymentMethod.CASH,
            description = "Paket ödemesi",
            memberId = id,
        ).getOrThrow()

        return id
    }

    private suspend fun defter(): List<LedgerEntryEntity> =
        db.ledgerDao().observeBetween(TEST_TENANT, 0, Long.MAX_VALUE).first()

    private suspend fun aktifTahsilatToplami(): Money =
        Money(defter().aktifKayitlar().filter { it.type == LedgerType.PAYMENT }.sumOf { it.amountMinor })

    // ─── Asıl hata ──────────────────────────────────────────────────────────

    /**
     * Seçilen tahsilat iptal ediliyor ve gelirden düşüyor.
     *
     * Kurgu bozulup seçim yok sayılırsa tahsilat 1.000 TL olarak kalır —
     * düzeltilen hatanın tam olarak ürettiği sayı.
     */
    @Test
    fun `secilen kayit uye silinirken iptal ediliyor`() = runTest {
        val uye = kayitliUye()
        val tahsilat = defter().single { it.type == LedgerType.PAYMENT }

        repo.deleteMember(uye, listOf(tahsilat.id)).getOrThrow()

        assertEquals(
            Money.ZERO, aktifTahsilatToplami(),
            "Silinen üyenin tahsilatı finansta duruyor — salon o parayı almış görünüyor",
        )
    }

    /** Kayıt SİLİNMİYOR: ters kayıt yazılıyor, denetim izi duruyor. */
    @Test
    fun `iptal kaydi silmiyor ters kayit yaziyor`() = runTest {
        val uye = kayitliUye()
        val tahsilat = defter().single { it.type == LedgerType.PAYMENT }

        repo.deleteMember(uye, listOf(tahsilat.id)).getOrThrow()

        val tumu = defter()
        assertNotNull(
            tumu.find { it.id == tahsilat.id },
            "Asıl kayıt defterden silinmiş — denetim izi kayboldu",
        )
        val ters = tumu.single { it.reversesId == tahsilat.id }
        assertEquals(tahsilat.amountMinor, ters.amountMinor, "Ters kaydın tutarı tutmuyor")
        assertEquals(tahsilat.type, ters.type, "Ters kaydın yönü değişmiş")
        assertTrue(ters.description.startsWith("İPTAL —"), "Ters kayıt işaretlenmemiş")
    }

    /** Ters kaydın açıklaması üyenin adını taşıyor. */
    @Test
    fun `ters kayit silinen uyenin adini tasiyor`() = runTest {
        val uye = kayitliUye()
        val tahsilat = defter().single { it.type == LedgerType.PAYMENT }

        repo.deleteMember(uye, listOf(tahsilat.id)).getOrThrow()

        val ters = defter().single { it.reversesId == tahsilat.id }
        assertTrue(
            ters.description.contains(SampleRows.member.fullName),
            "Ters kayıt kime ait olduğunu söylemiyor: ${ters.description}. " +
                "Üye listeden kalktığı için bu satırın başka kaynağı yok.",
        )
    }

    /**
     * Seçilmeyen kayıt olduğu gibi kalıyor.
     *
     * "Tek tek silme" bunun üstüne kurulu: kullanıcı yalnızca hatalı satırı
     * işaretliyor, gerçek tahsilat yerinde duruyor.
     */
    @Test
    fun `secilmeyen kayit deftere dokunulmadan kaliyor`() = runTest {
        val uye = kayitliUye()
        val tahakkuk = defter().single { it.type == LedgerType.CHARGE }
        val tahsilat = defter().single { it.type == LedgerType.PAYMENT }

        repo.deleteMember(uye, listOf(tahsilat.id)).getOrThrow()

        val aktif = defter().aktifKayitlar().map { it.id }
        assertEquals(
            listOf(tahakkuk.id), aktif,
            "Seçilmeyen kayıt da iptal edilmiş — kullanıcının seçimi yok sayılıyor",
        )
    }

    /** Boş seçim eski davranış: defter olduğu gibi kalıyor. */
    @Test
    fun `bos secimde deftere hic dokunulmuyor`() = runTest {
        val uye = kayitliUye()
        val onceki = defter().map { it.id }.toSet()

        repo.deleteMember(uye).getOrThrow()

        assertEquals(onceki, defter().map { it.id }.toSet(), "Deftere kayıt eklenmiş")
    }

    /** Üye her durumda siliniyor; defter seçimi silmenin kendisini etkilemiyor. */
    @Test
    fun `uye her iki durumda da siliniyor`() = runTest {
        val secimli = kayitliUye("uye-1")
        val tahsilat = defter().single { it.type == LedgerType.PAYMENT }
        repo.deleteMember(secimli, listOf(tahsilat.id)).getOrThrow()
        assertNotNull(db.memberDao().getMemberById(secimli)?.deletedAtMs, "Üye silinmemiş")

        val secimsiz = kayitliUye("uye-2")
        repo.deleteMember(secimsiz).getOrThrow()
        assertNotNull(db.memberDao().getMemberById(secimsiz)?.deletedAtMs, "Üye silinmemiş")
    }

    // ─── Gönderim kuyruğu ───────────────────────────────────────────────────

    /**
     * Ters kayıt kuyruğa giriyor.
     *
     * Girmezse düzeltme yalnızca bu cihazda olur: panelde ve diğer telefonda
     * tahsilat yaşamaya devam eder ve iki taraf kalıcı olarak ayrışır.
     */
    @Test
    fun `ters kayit gonderim kuyruguna giriyor`() = runTest {
        val uye = kayitliUye()
        val tahsilat = defter().single { it.type == LedgerType.PAYMENT }

        repo.deleteMember(uye, listOf(tahsilat.id)).getOrThrow()

        val ters = defter().single { it.reversesId == tahsilat.id }
        val bekleyen = db.syncOutboxDao()
            .pendingIds(TEST_TENANT, SyncTable.LEDGER_ENTRIES.tableName)
        assertTrue(
            ters.id in bekleyen,
            "Ters kayıt kuyrukta yok — düzeltme sunucuya hiç gitmez",
        )
    }

    // ─── İkinci kez iptal ───────────────────────────────────────────────────

    /**
     * Zaten iptal edilmiş kayıt ikinci kez iptal edilmiyor.
     *
     * İki cihazdan aynı düzeltme yapıldığında ikincisi tutarı bir kez daha
     * düşürürse toplam eksiye kayar ve nereden geldiği hiçbir yerde yazmaz.
     */
    @Test
    fun `iptal islemi idempotent`() = runTest {
        val uye = kayitliUye()
        val tahsilat = defter().single { it.type == LedgerType.PAYMENT }

        assertEquals(1, ledger.reverseMany(listOf(tahsilat.id), "ilk").getOrThrow())
        assertEquals(
            0, ledger.reverseMany(listOf(tahsilat.id), "ikinci").getOrThrow(),
            "Aynı kayıt ikinci kez iptal edildi",
        )
        assertEquals(1, defter().count { it.reversesId == tahsilat.id })
    }

    /**
     * Aynı kimlik listede iki kez geçse de bir kez iptal ediliyor.
     *
     * Ekran seçimi küme tutuyor ama bu repository çağrısının tek koruması o
     * değil: `Collection` alan bir API'ye tekrarlı liste gelmesi mümkün ve
     * ikinci ters kayıt tutarı toplamlardan iki kez düşürürdü.
     */
    @Test
    fun `tekrarlanan kimlik bir kez iptal ediliyor`() = runTest {
        val uye = kayitliUye()
        val tahsilat = defter().single { it.type == LedgerType.PAYMENT }

        assertEquals(
            1, ledger.reverseMany(listOf(tahsilat.id, tahsilat.id), "çift").getOrThrow(),
        )
        assertEquals(1, defter().count { it.reversesId == tahsilat.id })
    }

    /** Ters kaydın kendisi iptal edilemiyor: iptalin iptali tutarı geri getirirdi. */
    @Test
    fun `ters kayit tekrar iptal edilemiyor`() = runTest {
        val uye = kayitliUye()
        val tahsilat = defter().single { it.type == LedgerType.PAYMENT }
        ledger.reverseMany(listOf(tahsilat.id), "ilk").getOrThrow()
        val ters = defter().single { it.reversesId == tahsilat.id }

        assertTrue(
            ledger.reverseMany(listOf(ters.id), "iptalin iptali").isFailure,
            "Ters kayıt iptal edilebildi — tutar toplamlara geri döner",
        )
    }

    /**
     * Bilinmeyen kimlik işlemin tamamını düşürüyor.
     *
     * Kısmen uygulanmış bir düzeltme, hiç uygulanmamış olandan kötü:
     * kullanıcı "iptal edildi" görür ama hangilerinin atlandığını bilmez.
     */
    @Test
    fun `bilinmeyen kimlik tum islemi dusuruyor`() = runTest {
        val uye = kayitliUye()
        val tahsilat = defter().single { it.type == LedgerType.PAYMENT }

        val sonuc = repo.deleteMember(uye, listOf(tahsilat.id, "olmayan-kayit"))

        assertTrue(sonuc.isFailure, "Bilinmeyen kimlik sessizce atlandı")
        assertTrue(
            defter().none { it.reversesId != null },
            "İşlem düştüğü hâlde ters kayıt yazılmış — transaction geri alınmamış",
        )
        assertNull(
            db.memberDao().getMemberById(uye)?.deletedAtMs,
            "İşlem düştüğü hâlde üye silinmiş — silme ile düzeltme aynı " +
                "transaction'da değil",
        )
    }
}
