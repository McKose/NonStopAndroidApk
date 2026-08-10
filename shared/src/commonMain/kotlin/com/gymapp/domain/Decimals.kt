package com.gymapp.domain

/**
 * Kullanıcının yazdığı ondalık sayıyı ayrıştırır.
 *
 * Kotlin'in `toDoubleOrNull` metodu yalnızca noktayı ondalık ayıracı kabul eder.
 * Uygulamanın dili Türkçe, klavyesi de öyle: kullanıcı "75,5" yazdığında
 * `toDoubleOrNull` `null` döner ve çağıran taraf bunu `?: 0.0` ile **sessizce**
 * sıfıra çeviriyordu. Yani virgülle yazılan iskonto uygulanmıyor, virgülle
 * yazılan kilo 0 kaydediliyordu — hiçbir uyarı çıkmadan.
 *
 * Buradaki ayrıştırıcı iki ayıracı da anlar ve belirsizliği [Money.parseOrNull]
 * ile **aynı** kuralla çözer; para ile ölçüm alanları farklı davranmaz.
 */
object Decimals {

    /**
     * @return sayı ya da girdi anlamsızsa `null`. Sonsuz/NaN de `null` sayılır.
     */
    fun parseOrNull(input: String): Double? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val lastDot = trimmed.lastIndexOf('.')
        val lastComma = trimmed.lastIndexOf(',')

        val normalized = when {
            // İki ayıraç birden: sonuncusu ondalık ayıracıdır.
            lastDot >= 0 && lastComma >= 0 ->
                if (lastComma > lastDot) {
                    trimmed.replace(".", "").replace(',', '.')  // 1.234,56
                } else {
                    trimmed.replace(",", "")                    // 1,234.56
                }

            lastComma >= 0 -> trimmed.replace(',', '.')         // 1234,56

            lastDot >= 0 -> {
                // Tek başına nokta belirsiz: "1.500" hem 1500 hem 1,5 olabilir.
                // Tam üç haneli son grup Türkçe binlik okumasıyla çözülür; tam
                // kısım sıfırla başlıyorsa gruplama imkânsızdır ("0.500" = 0,5).
                val integerPart = trimmed.substring(0, lastDot)
                val decimals = trimmed.length - lastDot - 1
                val leadingDigit = integerPart.firstOrNull { it.isDigit() }
                val looksGrouped = trimmed.count { it == '.' } > 1 ||
                    (decimals == 3 && leadingDigit != null && leadingDigit != '0')
                if (looksGrouped) trimmed.replace(".", "") else trimmed
            }

            else -> trimmed
        }

        val value = normalized.toDoubleOrNull() ?: return null
        return if (value.isFinite()) value else null
    }

    /** Girdi anlamsızsa [fallback]. Sessiz sıfırlama yerine açık varsayılan. */
    fun parseOrDefault(input: String, fallback: Double = 0.0): Double =
        parseOrNull(input) ?: fallback
}
