package com.gymapp.data.sync

/**
 * HTTP durum kodunun gönderim sonucuna çevrilmesi.
 *
 * Motorun davranışını belirleyen yer burası, bu yüzden istemcinin içine gömülü
 * değil ayrı ve tek tek test edilebilir durumda. Yanlış bir eşleme sessizdir ve
 * iki yönde de zarar verir:
 *  - gerçekten geçici bir hataya "kalıcı" demek → kayıt bir daha hiç denenmez
 *    gibi görünür (kuyrukta kalır ama hep hata sayılır, kimse fark etmez),
 *  - kalıcı bir hataya "geçici" demek → tur her seferinde o kayıtta durur ve
 *    arkasındaki her şey süresiz bekler.
 */
internal fun pushResultForStatus(status: Int, body: String?): PushResult {
    val detay = body?.take(300)?.takeIf { it.isNotBlank() }
    return when {
        status in 200..299 -> PushResult.Success

        // Jeton yok, süresi dolmuş ya da geçersiz. Aynı isteği hemen tekrarlamak
        // aynı sonucu verir, ama oturum yenilendikten sonra başarılı olur — yani
        // kaydın kendisiyle ilgili kalıcı bir sorun yok. Geri çekilme, yenileme
        // için gereken zamanı zaten tanıyor.
        status == 401 -> PushResult.Retryable("Oturum geçersiz (401)")

        // Erişim kuralları reddetti: kullanıcı bu satırın salonuna bağlı değil.
        // Aynı istek aynı sonucu verecek; kayıt kuyrukta kalıp işaretlensin ki
        // "bu kullanıcı gym_users'a eklenmemiş" teşhis edilebilsin.
        status == 403 -> PushResult.Permanent("Erişim reddedildi (403)${detay.ekle()}")

        // İstek zaman aşımına uğradı ya da hız sınırına takıldı; ikisi de geçer.
        status == 408 || status == 429 -> PushResult.Retryable("Sunucu meşgul ($status)")

        // Tablo yok, gövde bozuk, kısıt ihlali. Tekrar denemek düzeltmez.
        status in 400..499 -> PushResult.Permanent("İstek reddedildi ($status)${detay.ekle()}")

        // Sunucu tarafı arıza — geçer.
        status in 500..599 -> PushResult.Retryable("Sunucu hatası ($status)")

        else -> PushResult.Permanent("Beklenmeyen durum kodu ($status)${detay.ekle()}")
    }
}

private fun String?.ekle(): String = if (this == null) "" else ": $this"
