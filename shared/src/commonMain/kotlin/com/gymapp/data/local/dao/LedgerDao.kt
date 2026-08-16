package com.gymapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.gymapp.data.local.entity.LedgerEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Finans defteri erişimi.
 *
 * Defter append-only olduğu için burada bilinçli olarak `@Update`/`@Delete` yoktur.
 * Toplamlar veritabanında hesaplanır; eski `FinanceViewModel` tüm kayıtları belleğe
 * çekip istemcide filtreliyordu.
 */
@Dao
interface LedgerDao {

    @Insert
    suspend fun insert(entry: LedgerEntryEntity)

    @Insert
    suspend fun insertAll(entries: List<LedgerEntryEntity>)

    @Query("SELECT * FROM ledger_entries WHERE id = :id")
    suspend fun getById(id: String): LedgerEntryEntity?

    /** Dönem kayıtları — yarı açık aralık `[start, end)`. */
    @Query("""
        SELECT * FROM ledger_entries
        WHERE tenantId = :tenantId AND occurredAtMs >= :startMs AND occurredAtMs < :endMs
        ORDER BY occurredAtMs DESC
    """)
    fun observeBetween(tenantId: String, startMs: Long, endMs: Long): Flow<List<LedgerEntryEntity>>

    @Query("""
        SELECT * FROM ledger_entries
        WHERE tenantId = :tenantId AND memberId = :memberId
        ORDER BY occurredAtMs DESC
    """)
    fun observeForMember(tenantId: String, memberId: String): Flow<List<LedgerEntryEntity>>

    /**
     * Bir dönemdeki net tutar.
     *
     * Ters kayıtlar ve iptal ettikleri kayıtlar birbirini götürsün diye,
     * ters kaydı bulunan kayıtlar toplamdan düşülür.
     */
    @Query("""
        SELECT COALESCE(SUM(e.amountMinor), 0) FROM ledger_entries e
        WHERE e.tenantId = :tenantId
          AND e.type = :type
          AND e.occurredAtMs >= :startMs AND e.occurredAtMs < :endMs
          AND e.reversesId IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM ledger_entries r WHERE r.reversesId = e.id
          )
    """)
    fun observeNetTotal(tenantId: String, type: String, startMs: Long, endMs: Long): Flow<Long>

    /**
     * Üyenin bakiyesi (kuruş): tahakkuk − tahsilat.
     *
     * Pozitif değer borcu gösterir. Ödeme durumu artık ayrı bir kolonda
     * saklanmıyor; defterden türetiliyor.
     *
     * MARKET kayıtları hariç. Market satışı kasada anında kapanıyor ve hiç
     * CHARGE üretmiyor, yalnızca PAYMENT üretiyor; üyeye bağlı bir market
     * tahsilatı buraya girseydi üyenin **paket borcunu** azaltırdı. Yani üye
     * 100 TL'lik protein bar aldığında paket borcu 100 TL düşerdi.
     * [activePaymentsForMember] ile aynı süzgeci kullanmak zorunda: biri
     * diğerinden farklı bir kayıt kümesine baksaydı "ödemeyi geri al"
     * sonrasında bakiye eski hâline dönmezdi.
     */
    /**
     * Üyenin kalan borcu.
     *
     * ### Market kayıtlarında tip ayrımı bilinçli
     * Kural "market hariç" değil, **"üyenin hesabını ilgilendiren hareketler"**:
     *
     *  - **Market TAHSİLATI sayılmıyor.** Kasada anında kapanan bir satış;
     *    ciroya girer ama üyenin hesabında hiç alacak doğurmamıştır, dolayısıyla
     *    bir borcu da kapatamaz. Sayılsaydı üye 100 TL'lik su alınca paket borcu
     *    100 TL düşerdi — düzeltilmiş ve `LedgerBalanceTest` ile sabitlenmiş bir
     *    hata.
     *  - **Market BORCU sayılıyor.** Veresiye satış tam olarak üyeye açılmış bir
     *    alacaktır. Önceden kategori toptan elendiği için bu kayıt bakiyeye hiç
     *    girmiyordu; üstelik `processOrder` onu hiç yazmıyordu bile — ürün
     *    stoktan çıkıyor, borç hiçbir yerde görünmüyor ve tahsil edecek ekran
     *    bulunmuyordu.
     *
     * Borç sonradan normal tahsilatla kapanıyor: o kayıt `MEMBERSHIP`
     * kategorisinde yazılıyor ve burada sayılıyor.
     */
    @Query("""
        SELECT COALESCE(SUM(
            CASE WHEN e.type = 'CHARGE' THEN e.amountMinor
                 WHEN e.type = 'PAYMENT' THEN -e.amountMinor
                 ELSE 0 END
        ), 0)
        FROM ledger_entries e
        WHERE e.tenantId = :tenantId
          AND e.memberId = :memberId
          AND NOT (e.category = 'MARKET' AND e.type = 'PAYMENT')
          AND e.reversesId IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM ledger_entries r WHERE r.reversesId = e.id
          )
    """)
    suspend fun outstandingBalanceMinor(tenantId: String, memberId: String): Long

    /** Bir kaydın daha önce ters kayıtla iptal edilip edilmediği. */
    @Query("SELECT EXISTS(SELECT 1 FROM ledger_entries WHERE reversesId = :entryId)")
    suspend fun isReversed(entryId: String): Boolean

    /**
     * Üyenin henüz iptal edilmemiş **paket** tahsilatları.
     *
     * Ödeme geri alındığında bunlar ters kayıtla iptal edilir.
     *
     * MARKET hariç: "paket ödemesini geri al" işlemi üyenin market
     * alışverişlerini de iptal ediyordu — kasada kapanmış satışlar ciro
     * toplamından siliniyor, üyenin borcu şişiyordu. Süzgeç
     * [outstandingBalanceMinor] ile birebir aynı olmak zorunda.
     */
    @Query("""
        SELECT * FROM ledger_entries e
        WHERE e.tenantId = :tenantId
          AND e.memberId = :memberId
          AND e.type = 'PAYMENT'
          AND e.category <> 'MARKET'
          AND e.reversesId IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM ledger_entries r WHERE r.reversesId = e.id
          )
        ORDER BY e.occurredAtMs DESC
    """)
    suspend fun activePaymentsForMember(tenantId: String, memberId: String): List<LedgerEntryEntity>

    /** Bir randevudan doğan, henüz iptal edilmemiş hakediş kayıtları. */
    @Query("""
        SELECT * FROM ledger_entries e
        WHERE e.appointmentId = :appointmentId
          AND e.reversesId IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM ledger_entries r WHERE r.reversesId = e.id
          )
    """)
    suspend fun activeEntriesForAppointment(appointmentId: String): List<LedgerEntryEntity>

    /**
     * Sunucudan gelen satırı yazar: yoksa ekler, varsa üzerine yazar.
     *
     * Çekme tarafının tek yazma yolu. `@Insert` ile ayrı bir `@Update` yerine
     * tek çağrı olması bilinçli: hangisinin gerektiğine karar vermek için önce
     * okumak gerekirdi ve o okuma ile yazma arasında satır değişebilirdi.
     *
     * Yerelde gönderim bekleyen satırlar buraya hiç gelmiyor; o ayıklama
     * `PullEngine` içinde yapılıyor (yerel değişiklik sunucudakinden yenidir).
     */
    @Upsert
    suspend fun upsertFromServer(row: LedgerEntryEntity)
}
