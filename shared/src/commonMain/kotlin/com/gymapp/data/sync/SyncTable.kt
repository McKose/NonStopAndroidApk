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
enum class SyncTable(val tableName: String) {
    MEMBERS("gym_members"),
    PACKAGES("gym_packages"),
    PRODUCTS("products"),
    APPOINTMENTS("appointments"),
    STAFF("staff"),
    ORDERS("orders"),
    MEASUREMENTS("measurements"),
    LEDGER_ENTRIES("ledger_entries"),
    STOCK_MOVEMENTS("stock_movements");

    companion object {
        private val byTableName = entries.associateBy { it.tableName }

        /** Kuyruktan okunan metni enum'a çevirir; tanınmayan tablo için `null`. */
        fun fromTableName(name: String): SyncTable? = byTableName[name]
    }
}
