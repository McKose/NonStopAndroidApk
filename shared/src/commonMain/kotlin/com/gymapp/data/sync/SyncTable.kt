package com.gymapp.data.sync

import com.gymapp.domain.StaffRole

/**
 * Senkronize edilen tablolar.
 *
 * Kuyruk kaydı tablo adını metin olarak taşıyor (veritabanına yazılan değer bu),
 * ama çağıran kod düz metin yerine bu enum'u kullanır: yanlış yazılmış bir tablo
 * adı derleme hatası olur, çalışma zamanında sessizce kaybolan bir kayıt değil.
 *
 * [tableName] değerleri entity'lerdeki `@Entity(tableName = ...)` ile birebir
 * aynı olmak zorunda; sunucu tarafındaki tablolar da bu adlarla açılacak.
 */
enum class SyncTable(
    val tableName: String,
    /**
     * Sunucudan değişiklik çekerken sıralama ve su işareti için kullanılan kolon.
     *
     * Çoğu tabloda `updated_at_ms`. Defter ve stok hareketleri **append-only**:
     * düzeltme güncelleme ile değil ters kayıtla yapıldığı için o tablolarda
     * `updated_at_ms` kolonu yok ve satırın tek zaman ekseni `created_at_ms`.
     * Sabit `updated_at_ms` yazılsaydı o iki tablo için her istek sunucudan
     * "böyle bir kolon yok" hatasıyla dönerdi — ve hata kimsenin bakmadığı bir
     * yerde kalırdı.
     */
    val deltaColumn: String = "updated_at_ms",
) {
    MEMBERS("gym_members"),
    PACKAGES("gym_packages"),
    PRODUCTS("products"),
    APPOINTMENTS("appointments"),
    STAFF("staff"),
    ORDERS("orders"),
    MEASUREMENTS("measurements"),
    LEDGER_ENTRIES("ledger_entries", deltaColumn = "created_at_ms"),
    STOCK_MOVEMENTS("stock_movements", deltaColumn = "created_at_ms");

    /**
     * Bu tabloya yazabilen roller.
     *
     * **Sunucudaki kuralın kopyası, kaynağı değil.** Gerçek kural
     * `supabase/migrations/0004_role_based_access.sql` içinde ve tek yetkili o:
     * burası yanlış olsa bile sunucu yazmayı reddeder. Buradaki kopyanın işi,
     * kullanıcıya reddedileceği bir işi hiç yaptırmamak — yoksa kullanıcı fiyatı
     * değiştirir, kayıt kuyruğa girer ve ancak senkronizasyon turunda kalıcı
     * 403 olarak geri döner.
     *
     * İkisinin ayrışması sessiz bir hata olurdu: uygulama düğmeyi açık bırakır,
     * kullanıcı işini yapar, kayıt kuyrukta ölür. Bu yüzden [SyncTableRolesTest]
     * migrasyon dosyasını okuyup buradaki değerlerle karşılaştırıyor — biri
     * değişip diğeri değişmezse test düşüyor.
     *
     * Kurucu parametresi değil hesaplanan özellik: enum sabitleri, dosya
     * düzeyindeki `val`lardan ve companion'dan ÖNCE ilkleniyor. Ortak bir küme
     * sabiti kurucuya verilseydi, sabitin ilklenme sırası platforma göre değişir
     * ve en kötü ihtimalle boş küme okunurdu — yani "hiç kimse yazamaz".
     */
    val writableBy: Set<StaffRole>
        get() = when (this) {
            // Personel satırı maaş ve hakediş oranı taşıyor, ayrıca hangi hesaba
            // bağlı olduğunu. Salon sahibinin işi.
            STAFF -> setOf(StaffRole.ADMIN)

            // Fiyat listesi. Eğitmenin değiştirmesi için sebep yok; satış
            // yapabilmesi için de gerekmiyor, satış `orders` tarafında.
            PACKAGES, PRODUCTS -> setOf(StaffRole.ADMIN, StaffRole.MANAGER)

            // Günlük iş: üçü de yapabilmeli, yoksa uygulama işe yaramaz.
            MEMBERS, APPOINTMENTS, ORDERS, MEASUREMENTS,
            LEDGER_ENTRIES, STOCK_MOVEMENTS ->
                setOf(StaffRole.ADMIN, StaffRole.MANAGER, StaffRole.TRAINER)
        }

    /** Bu rol bu tabloya yazabilir mi? */
    fun isWritableBy(role: StaffRole): Boolean = role in writableBy

    companion object {
        private val byTableName = entries.associateBy { it.tableName }

        /** Kuyruktan okunan metni enum'a çevirir; tanınmayan tablo için `null`. */
        fun fromTableName(name: String): SyncTable? = byTableName[name]
    }
}
