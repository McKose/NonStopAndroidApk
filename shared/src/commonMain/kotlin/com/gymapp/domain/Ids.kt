package com.gymapp.domain

/**
 * Birincil anahtar üretimi.
 *
 * `@PrimaryKey(autoGenerate = true)` `Long` anahtarlar çok cihazlı senkronizasyonda
 * **çalışmaz**: iki cihaz çevrimdışıyken kayıt eklediğinde ikisi de aynı `id`
 * değerini üretir ve sunucuda çakışırlar. Kimlik bu yüzden istemcide üretilen
 * UUID'dir — çevrimdışı üretilen kayıtlar birleştirilirken çakışmaz.
 */
object Ids {
    fun new(): String = randomUuid()

    /** Tek salonlu kurulumda kullanılan varsayılan kiracı kimliği. */
    const val DEFAULT_TENANT: String = "default"
}

/**
 * Rastgele UUID — domain katmanındaki **tek** platforma özgü nokta.
 *
 * Android'de `java.util.UUID`, iOS'ta `NSUUID` kullanılır; ikisi de aynı biçimde
 * (RFC 4122, küçük harfli) metin üretir.
 */
internal expect fun randomUuid(): String
