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
}

// KALDIRILDI: `DEFAULT_TENANT = "default"` sabiti.
//
// Salon kimliği artık oturumdan geliyor (`TenantProvider`) ve sunucudaki
// `gyms.id` ile aynı değer. Sabit, tek salonlu kurulumda çalışıyor gibi
// görünüyordu ama sunucuya gönderimde reddedilirdi: `tenant_id` orada `uuid`
// tipinde ve `"default"` geçerli bir uuid değil. Sabiti bırakmak, bir çağrı
// yerinin yanlışlıkla ona düşmesi ve o satırların hiçbir zaman senkronize
// olmaması riskini canlı tutardı — sessizce.

/**
 * Rastgele UUID — domain katmanındaki **tek** platforma özgü nokta.
 *
 * Android'de `java.util.UUID`, iOS'ta `NSUUID` kullanılır; ikisi de aynı biçimde
 * (RFC 4122, küçük harfli) metin üretir.
 */
internal expect fun randomUuid(): String
