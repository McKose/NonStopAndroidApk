package com.gymapp.data.sync

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

    companion object {
        private val byTableName = entries.associateBy { it.tableName }

        /** Kuyruktan okunan metni enum'a çevirir; tanınmayan tablo için `null`. */
        fun fromTableName(name: String): SyncTable? = byTableName[name]
    }
}
