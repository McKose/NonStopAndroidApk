package com.gymapp.data.repository

import com.gymapp.data.auth.TenantProvider
import com.gymapp.data.auth.requireTenantId
import com.gymapp.data.local.dao.LedgerDao
import com.gymapp.data.local.entity.LedgerEntryEntity
import com.gymapp.data.sync.SyncQueue
import com.gymapp.data.sync.SyncTable
import com.gymapp.domain.Now
import com.gymapp.domain.Ids
import com.gymapp.domain.LedgerCategory
import com.gymapp.domain.LedgerType
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import kotlinx.coroutines.flow.Flow

/**
 * Finans defteri üzerinde tip güvenli işlemler.
 *
 * Kuruş ↔ [Money] dönüşümü burada yapılır; entity katmanı düz `Long` ile,
 * çağıran katmanlar `Money` ile çalışır.
 *
 * Defter append-only olduğu için burada **güncelleme ve silme yoktur**;
 * düzeltme [reverse] ile yapılır.
 *
 * **Burada transaction açılmaz.** Bu repository'nin yazma metotları neredeyse her
 * zaman başka bir repository'nin transaction'ı içinden çağrılıyor (üye kaydı,
 * sipariş, randevu tamamlama). Kural: transaction'ı giriş noktası açar. Doğrudan
 * çağıran tek yer [FinanceRepository] ve orası kendi transaction'ını açıyor.
 */
class LedgerRepository(
    private val ledgerDao: LedgerDao,
    private val syncQueue: SyncQueue,
    private val tenants: TenantProvider,
) {

    // ─── Yazma ──────────────────────────────────────────────────────────────

    /** Üyeye borç tahakkuk ettirir (paket satışı, yenileme). */
    suspend fun recordCharge(
        memberId: String,
        amount: Money,
        description: String,
        category: LedgerCategory = LedgerCategory.MEMBERSHIP,
        occurredAtMs: Long = Now.epochMillis(),
        tenantId: String = tenants.requireTenantId(),
    ): Result<String> = record(
        type = LedgerType.CHARGE,
        category = category,
        amount = amount,
        description = description,
        occurredAtMs = occurredAtMs,
        tenantId = tenantId,
        memberId = memberId,
    )

    /** Tahsilat kaydeder. */
    suspend fun recordPayment(
        amount: Money,
        method: PaymentMethod,
        description: String,
        category: LedgerCategory = LedgerCategory.MEMBERSHIP,
        memberId: String? = null,
        orderId: String? = null,
        occurredAtMs: Long = Now.epochMillis(),
        tenantId: String = tenants.requireTenantId(),
    ): Result<String> = record(
        type = LedgerType.PAYMENT,
        category = category,
        amount = amount,
        description = description,
        occurredAtMs = occurredAtMs,
        tenantId = tenantId,
        memberId = memberId,
        orderId = orderId,
        paymentMethod = method,
    )

    /** Gider kaydeder (hakediş, maaş, kira, fatura). */
    suspend fun recordExpense(
        amount: Money,
        category: LedgerCategory,
        description: String,
        method: PaymentMethod = PaymentMethod.CASH,
        staffId: String? = null,
        appointmentId: String? = null,
        occurredAtMs: Long = Now.epochMillis(),
        tenantId: String = tenants.requireTenantId(),
    ): Result<String> = record(
        type = LedgerType.EXPENSE,
        category = category,
        amount = amount,
        description = description,
        occurredAtMs = occurredAtMs,
        tenantId = tenantId,
        staffId = staffId,
        appointmentId = appointmentId,
        paymentMethod = method,
    )

    // KALDIRILDI: `reverse` (tek kayıt). Tek kaydı iptal etmek artık tek
    // elemanlı bir listeyle [reverseMany]'den geçiyor. İki yol tutmak, aynı
    // kuralın (ters kaydın kendisi iptal edilemez, iptal idempotenttir) iki
    // ayrı yerde yaşaması demekti ve biri değişince diğeri sessizce
    // ayrışırdı — üstelik tek kayıtlı yolun hiç çağıranı yoktu.

    /**
     * Seçilen kayıtları tek işlemde ters kayıtla iptal eder.
     *
     * Kayıtlar **silinmiyor**; her biri için aynı tutarda, [LedgerEntryEntity.reversesId]
     * alanı dolu yeni bir satır yazılıyor. Böylece denetim izi korunuyor ve
     * toplamlar sıfırlanıyor.
     *
     * Hatalı kaydın düzeltilmesi için: kullanıcı hangi kayıtların iptal
     * edileceğini **seçiyor**, tek tek ya da toplu. Seçim dışında kalanlar
     * olduğu gibi duruyor.
     *
     * Bilinmeyen bir kimlik **hata**: kısmen uygulanmış bir düzeltme, hiç
     * uygulanmamış olandan kötüdür — kullanıcı "N kayıt iptal edildi" görür
     * ama hangilerinin atlandığını bilmez. Zaten iptal edilmiş kayıtlar ise
     * sessizce atlanıyor (işlem idempotent); iki cihazdan aynı düzeltme
     * yapıldığında ikincisi tutarı bir kez daha düşürmemeli.
     *
     * @return gerçekten yazılan ters kayıt sayısı
     */
    suspend fun reverseMany(
        entryIds: Collection<String>,
        reason: String,
        occurredAtMs: Long = Now.epochMillis(),
    ): Result<Int> = runCatching {
        // `distinct`: aynı kimlik iki kez geçerse ikincisi bir öncekinin
        // yazdığı ters kaydı görmeden ikinci bir ters kayıt üretebilirdi ve
        // kayıt toplamlardan iki kez düşerdi.
        val hedefler = entryIds.distinct().mapNotNull { iptalEdilebilir(it) }
        tersKayitlariYaz(hedefler, reason, occurredAtMs)
    }

    /**
     * Üyenin tüm aktif tahsilatlarını ters kayıtla iptal eder.
     *
     * "Ödemeyi geri al" akışının karşılığı: eski kurguda `updatePaymentStatus(id, false)`
     * hiçbir şey yapmıyordu çünkü geri alma yolu yoktu.
     *
     * @return iptal edilen kayıt sayısı
     */
    suspend fun reversePaymentsForMember(
        memberId: String,
        reason: String,
        occurredAtMs: Long = Now.epochMillis(),
        tenantId: String = tenants.requireTenantId(),
    ): Result<Int> = runCatching {
        tersKayitlariYaz(
            ledgerDao.activePaymentsForMember(tenantId, memberId), reason, occurredAtMs,
        )
    }

    /** Bir randevunun doğurduğu tüm aktif kayıtları iptal eder (randevu geri alınınca). */
    suspend fun reverseForAppointment(
        appointmentId: String,
        reason: String,
        occurredAtMs: Long = Now.epochMillis(),
    ): Result<Int> = runCatching {
        tersKayitlariYaz(
            ledgerDao.activeEntriesForAppointment(appointmentId), reason, occurredAtMs,
        )
    }

    // ─── Ters kaydın ortak yolu ─────────────────────────────────────────────

    /**
     * Kaydı iptal edilebiliyorsa döndürür, zaten iptal edilmişse `null`.
     *
     * Ters kaydın kendisini iptal etmek **hata**: iki ters kayıt birbirini
     * götürür ve tutar toplamlara geri döner — yani "iptali iptal etmek"
     * sessizce çalışır gibi görünürdü. Düzeltmenin düzeltmesi yeni bir kayıt
     * girmek olmalı, defter geriye doğru oyulmamalı.
     */
    private suspend fun iptalEdilebilir(entryId: String): LedgerEntryEntity? {
        val original = ledgerDao.getById(entryId)
            ?: throw IllegalArgumentException("Finans kaydı bulunamadı.")
        require(original.reversesId == null) { "Ters kayıt tekrar iptal edilemez." }
        return original.takeUnless { ledgerDao.isReversed(entryId) }
    }

    /**
     * Verilen kayıtların ters kayıtlarını yazar ve kuyruğa alır.
     *
     * Ters kayıt orijinalin kopyası: aynı tutar, aynı yön, aynı kategori —
     * yalnızca kimliği yeni ve [LedgerEntryEntity.reversesId] kaynağa bağlı.
     * Tutarı eksiyle yazmak da toplamları sıfırlardı ama "tutar daima
     * pozitif, yön `type` ile ifade edilir" kuralını bozardı (bkz. [record]).
     *
     * Çağıranların hepsi [occurredAtMs] için **şimdi**yi geçiyor: muhasebe ters
     * kaydı cari döneme işler. Orijinalin dönemine yazılsaydı kapanmış bir ayın
     * toplamı geriye dönük değişirdi.
     *
     * @return yazılan ters kayıt sayısı
     */
    private suspend fun tersKayitlariYaz(
        kayitlar: List<LedgerEntryEntity>,
        reason: String,
        occurredAtMs: Long,
    ): Int {
        if (kayitlar.isEmpty()) return 0

        val reversals = kayitlar.map { original ->
            original.copy(
                id = Ids.new(),
                description = "İPTAL — $reason",
                occurredAtMs = occurredAtMs,
                reversesId = original.id,
                createdAtMs = Now.epochMillis(),
            )
        }
        ledgerDao.insertAll(reversals)
        // Her kayıt kendi tenant'ıyla kuyruğa alınıyor. Hepsine ilk kaydın
        // tenant'ını vermek bugün doğru sonucu üretirdi (tek kiracı var) ama
        // çok kiracılıya geçişte kayıtları yanlış hesaba yazardı.
        reversals.forEach {
            syncQueue.enqueue(SyncTable.LEDGER_ENTRIES, it.id, it.tenantId, it.createdAtMs)
        }
        return reversals.size
    }

    // ─── Okuma ──────────────────────────────────────────────────────────────

    // KALDIRILDI: `observeBetween`, `observeIncome`, `observeExpense`.
    //
    // Üçü de hiç çağrılmıyordu: finans okumaları `FinanceRepository` üzerinden
    // geçiyor ve dönem toplamları `FinanceViewModel` içinde, ekranda gösterilen
    // listenin **aynı** kalemlerinden hesaplanıyor.
    //
    // Bugün iki yol aynı sonucu veriyor (`observeNetTotal` da ters kayıtları
    // düşüyor, `type` süzgeci de tahakkuku ciro dışında tutuyor). Sorun bunun
    // sürmesini kimsenin garanti etmemesi: "ciro"nun ikinci bir tanımı, biri
    // değişince sessizce ayrışacak biçimde duruyordu. Toplamlar zaten listeden
    // türetilmek zorunda — ekran o listeyi göstermek için yüklüyor — dolayısıyla
    // ikinci tanımın kazandırdığı bir şey de yoktu.
    //
    // Dayandıkları `LedgerDao.observeNetTotal` sorgusu da bunlarla birlikte
    // kalktı; üye bakiyesi ayrı bir sorgudan (`outstandingBalanceMinor`) geliyor.

    fun observeForMember(
        memberId: String,
        tenantId: String = tenants.requireTenantId(),
    ): Flow<List<LedgerEntryEntity>> = ledgerDao.observeForMember(tenantId, memberId)

    /**
     * Üyenin kalan borcu. Pozitif değer borcu gösterir.
     *
     * Ödeme durumu artık kolonda saklanmıyor; bu değerden türetiliyor.
     */
    suspend fun outstandingBalance(
        memberId: String,
        tenantId: String = tenants.requireTenantId(),
    ): Money = Money(ledgerDao.outstandingBalanceMinor(tenantId, memberId))

    /** Üyenin borcu kapandı mı? */
    suspend fun isSettled(
        memberId: String,
        tenantId: String = tenants.requireTenantId(),
    ): Boolean = outstandingBalance(memberId, tenantId).minor <= 0L

    // ─── Ortak yazma yolu ───────────────────────────────────────────────────

    private suspend fun record(
        type: LedgerType,
        category: LedgerCategory,
        amount: Money,
        description: String,
        occurredAtMs: Long,
        tenantId: String,
        memberId: String? = null,
        staffId: String? = null,
        orderId: String? = null,
        appointmentId: String? = null,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
    ): Result<String> = runCatching {
        // Tutar daima pozitif; yön `type` ile ifade edilir.
        require(amount.isPositive) { "Finans kaydı tutarı sıfırdan büyük olmalıdır." }
        require(description.isNotBlank()) { "Finans kaydı açıklaması boş olamaz." }

        val entry = LedgerEntryEntity(
            id = Ids.new(),
            tenantId = tenantId,
            type = type,
            category = category,
            amountMinor = amount.minor,
            paymentMethod = paymentMethod,
            memberId = memberId,
            staffId = staffId,
            orderId = orderId,
            appointmentId = appointmentId,
            description = description.trim(),
            occurredAtMs = occurredAtMs,
            createdAtMs = Now.epochMillis(),
        )
        ledgerDao.insert(entry)
        syncQueue.enqueue(SyncTable.LEDGER_ENTRIES, entry.id, tenantId, entry.createdAtMs)
        entry.id
    }
}
