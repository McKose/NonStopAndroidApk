package com.gymapp.domain

import java.util.UUID

/**
 * Birincil anahtar üretimi.
 *
 * `@PrimaryKey(autoGenerate = true)` `Long` anahtarlar çok cihazlı senkronizasyonda
 * **çalışmaz**: iki cihaz çevrimdışıyken kayıt eklediğinde ikisi de aynı `id`
 * değerini üretir ve sunucuda çakışırlar. Kimlik bu yüzden istemcide üretilen
 * UUID'dir — çevrimdışı üretilen kayıtlar birleştirilirken çakışmaz.
 */
object Ids {
    fun new(): String = UUID.randomUUID().toString()

    /** Tek salonlu kurulumda kullanılan varsayılan kiracı kimliği. */
    const val DEFAULT_TENANT: String = "default"
}
