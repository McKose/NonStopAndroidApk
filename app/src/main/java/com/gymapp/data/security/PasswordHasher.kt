package com.gymapp.data.security

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Basit şifre hash'leme yardımcısı. Şifreler rastgele bir salt ile birlikte
 * SHA-256 kullanılarak hash'lenir ve `salt:hash` formatında saklanır.
 */
object PasswordHasher {

    private const val ALGORITHM = "SHA-256"
    private const val SALT_LENGTH = 16

    fun hash(password: String): String {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        return encode(salt) + ":" + encode(sha256(salt + password.toByteArray(Charsets.UTF_8)))
    }

    fun verify(password: String, stored: String): Boolean {
        if (stored.isBlank()) return false
        // Geriye dönük uyumluluk: eski kayıtlar düz metin olabilir.
        val parts = stored.split(":")
        if (parts.size != 2) return stored == password
        val salt = decode(parts[0]) ?: return false
        val expected = decode(parts[1]) ?: return false
        val actual = sha256(salt + password.toByteArray(Charsets.UTF_8))
        return constantTimeEquals(expected, actual)
    }

    private fun sha256(input: ByteArray): ByteArray =
        MessageDigest.getInstance(ALGORITHM).digest(input)

    private fun encode(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

    private fun decode(value: String): ByteArray? = try {
        android.util.Base64.decode(value, android.util.Base64.NO_WRAP)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}
