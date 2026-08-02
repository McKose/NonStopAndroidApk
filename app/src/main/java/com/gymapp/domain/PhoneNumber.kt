package com.gymapp.domain

/**
 * Telefon numarası normalizasyonu.
 *
 * `gym_members.phone` üzerinde UNIQUE index var, ancak kayıt sırasında numara ham
 * girildiği için `5551112233`, `0555 111 22 33` ve `+90 555 111 22 33` üç **farklı**
 * kayıt olarak geçebiliyordu; yani tekillik kısıtı pratikte hiç çalışmıyordu.
 *
 * Çözüm: veritabanına her zaman E.164 (`+90XXXXXXXXXX`) formatında yazmak.
 */
object PhoneNumber {

    /**
     * Türkiye cep numarasını E.164'e çevirir.
     *
     * Kabul edilen girdiler (boşluk/parantez/tire serbest):
     *  `5551112233`, `05551112233`, `905551112233`, `00905551112233`, `+90 555 111 22 33`
     *
     * @return `+90XXXXXXXXXX` ya da numara geçerli değilse `null`.
     */
    fun normalizeTr(raw: String): String? {
        var digits = raw.filter(Char::isDigit)

        if (digits.startsWith("0090")) digits = digits.removePrefix("0090")
        if (digits.length == 12 && digits.startsWith("90")) digits = digits.removePrefix("90")
        if (digits.length == 11 && digits.startsWith("0")) digits = digits.removePrefix("0")

        // Türkiye cep numaraları 10 hanedir ve "5" ile başlar.
        return if (digits.length == 10 && digits.startsWith("5")) "+90$digits" else null
    }

    /** Ekranda okunabilir biçim: `+90 555 111 22 33`. */
    fun formatForDisplay(e164: String): String {
        val digits = e164.filter(Char::isDigit)
        if (digits.length != 12 || !digits.startsWith("90")) return e164
        val n = digits.removePrefix("90")
        return "+90 ${n.substring(0, 3)} ${n.substring(3, 6)} ${n.substring(6, 8)} ${n.substring(8, 10)}"
    }
}
