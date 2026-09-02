package com.gymapp.domain

/**
 * Yeni şifrenin kabul edilip edilmeyeceği.
 *
 * Ayrı bir tip, `Boolean` değil: reddedilen her durumun **farklı** bir sebebi
 * var ve kullanıcıya hangisi olduğunu söylemek gerekiyor. Tek bir "geçersiz"
 * mesajı, sekiz karakteri tutturamayan biriyle iki kutuyu farklı dolduran
 * birine aynı şeyi söylerdi.
 */
sealed interface SifreSonucu {
    /** @param sifre sunucuya gidecek değer — girilenle **birebir aynı**. */
    data class Gecerli(val sifre: String) : SifreSonucu

    data class Gecersiz(val mesaj: String) : SifreSonucu
}

/**
 * Şifre değiştirme kuralları.
 *
 * ### Kural neden burada
 * Sunucu da kendi asgari uzunluğunu uyguluyor ama hatası İngilizce ve
 * ancak ağ turundan sonra geliyor. Buradaki kontrol güvenlik değil, geri
 * bildirim: kullanıcı yazarken görsün.
 *
 * Aynı kuralların bir kopyası web panelinde de var (`web/admin/sifre.js`) ve
 * testleri aynı örnekleri kullanıyor — tarayıcı Kotlin çalıştırmıyor,
 * dolayısıyla kopya kaçınılmaz; sapması sessiz olmasın diye örnekler ortak.
 */
object SifreKurali {

    /**
     * En az uzunluk. Supabase'in varsayılanı 6; burası bilinçli olarak daha
     * katı.
     *
     * Sebebi bu akışa özgü: şifrelerin bir kısmı `personel-davet`in ürettiği
     * geçici şifreler ve kullanıcı onları "kendi seçtiği kolay bir şeyle"
     * değiştirmeye geliyor. Altı karakter, salonun ortak kullandığı bir
     * cihazda tahmin edilebilir bir şey yazmayı fazla kolaylaştırıyor.
     */
    const val EN_AZ_UZUNLUK = 8

    /**
     * @param yeni yeni şifre
     * @param tekrar doğrulama kutusuna yazılan
     * @param mevcut kullanıcının o anki şifresi — yenisiyle aynı olmasın diye
     */
    fun dogrula(yeni: String, tekrar: String, mevcut: String): SifreSonucu {
        if (mevcut.isEmpty()) {
            return SifreSonucu.Gecersiz("Mevcut şifrenizi girin.")
        }
        if (yeni.length < EN_AZ_UZUNLUK) {
            return SifreSonucu.Gecersiz("Yeni şifre en az $EN_AZ_UZUNLUK karakter olmalı.")
        }

        // Baştaki/sondaki boşluk KIRPILMIYOR, reddediliyor.
        //
        // Kırpmak sessiz bir tuzak olurdu: kullanıcı "abc123 " yazar, sunucuya
        // "abc123" gider, sonra giriş ekranında yazdığının aynısını yazar ve
        // "şifre yanlış" cevabını alır — kaybettiği karakteri hiçbir yerde
        // göremeden. Şifreyi sessizce değiştirmek yerine söylüyoruz.
        if (yeni != yeni.trim()) {
            return SifreSonucu.Gecersiz("Şifrenin başında veya sonunda boşluk olamaz.")
        }
        if (yeni != tekrar) {
            return SifreSonucu.Gecersiz("İki şifre birbirini tutmuyor.")
        }
        // Sunucu da bunu reddediyor ama mesajı İngilizce ve ağ turundan sonra
        // geliyor.
        if (yeni == mevcut) {
            return SifreSonucu.Gecersiz("Yeni şifre eskisiyle aynı olamaz.")
        }

        return SifreSonucu.Gecerli(yeni)
    }
}
