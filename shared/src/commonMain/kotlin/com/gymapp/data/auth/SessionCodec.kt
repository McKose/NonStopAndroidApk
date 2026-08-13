package com.gymapp.data.auth

import com.gymapp.domain.StaffRole
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Oturumun saklanabilir metne çevrilmesi.
 *
 * Elle yazılmış — `@Serializable` yerine. Sebebi projenin geri kalanıyla aynı
 * tercih (bkz. `RowPayloads`, `SupabaseAuthApi`): üretilen serileştirici, alan
 * adlarını sınıfın alan adlarına bağlar. Bir alan yeniden adlandırıldığında
 * saklanan eski metin sessizce okunamaz hâle gelirdi ve belirtisi "kullanıcı
 * bir güncellemeden sonra tekrar giriş yapmak zorunda kaldı" olurdu — kimsenin
 * hata olarak raporlamayacağı türden.
 *
 * Burada alan adları açıkça yazılı ve okuma tarafı eksik alana dayanıklı: bozuk
 * ya da tanınmayan bir metin istisna değil `null` üretiyor, çağıran da bunu
 * "oturum yok" sayıp giriş ekranına düşüyor.
 */
internal object SessionCodec {

    fun encode(session: Session): String = buildJsonObject {
        put("accessToken", session.accessToken)
        put("refreshToken", session.refreshToken)
        put("expiresAtMs", session.expiresAtMs)
        put("userId", session.userId)
        put("email", session.email)
        put("tenantId", session.tenantId)
        put("role", session.role.name)
    }.toString()

    /**
     * Metni oturuma çevirir; okunamıyorsa `null`.
     *
     * Zorunlu alanlardan biri eksikse oturum kurulmuyor. Yarım bir oturum —
     * jetonu olan ama salon kimliği olmayan gibi — her isteği sessizce
     * başarısız kılardı.
     */
    fun decode(text: String): Session? = runCatching {
        val json = Json.parseToJsonElement(text).jsonObject
        fun field(key: String): String? =
            json[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

        Session(
            accessToken = field("accessToken") ?: return null,
            refreshToken = field("refreshToken") ?: return null,
            expiresAtMs = field("expiresAtMs")?.toLongOrNull() ?: return null,
            userId = field("userId") ?: return null,
            email = field("email").orEmpty(),
            tenantId = field("tenantId") ?: return null,
            // Tanınmayan rol en dar yetkiye düşüyor; aynı kural girişte de var.
            role = runCatching { StaffRole.valueOf(field("role").orEmpty()) }
                .getOrDefault(StaffRole.TRAINER),
        )
    }.getOrNull()
}
