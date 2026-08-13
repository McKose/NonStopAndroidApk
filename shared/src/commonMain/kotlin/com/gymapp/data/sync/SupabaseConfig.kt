package com.gymapp.data.sync

/**
 * Sunucu bağlantı ayarları.
 *
 * Derleme zamanı sabiti değil, **çalışma zamanı yapılandırması**: değerler
 * uygulamaya gömülmüyor, dışarıdan veriliyor. Böylece aynı APK farklı bir
 * projeye bağlanabiliyor ve anahtar değiştirmek yeniden derleme gerektirmiyor.
 *
 * [anonKey] gizli bir şey değil — istemcide bulunması normaldir ve tek başına
 * hiçbir veriye erişemez; her sorgu giriş yapan kullanıcıya göre süzülür.
 * `service_role` anahtarı ise **asla** uygulamaya konulmaz: o anahtar bütün
 * erişim kurallarını baypas eder.
 */
data class SupabaseConfig(
    /** Proje adresi, sondaki eğik çizgi olmadan: `https://xxxx.supabase.co` */
    val url: String,
    val anonKey: String,
) {
    init {
        require(url.isNotBlank()) { "Supabase adresi boş olamaz." }
        require(!url.endsWith("/")) { "Adres sondaki eğik çizgi olmadan verilmeli." }
        require(anonKey.isNotBlank()) { "Supabase anahtarı boş olamaz." }
    }

    /** PostgREST uç noktası: Supabase'in veri API'si bu yol altında. */
    fun tableEndpoint(table: SyncTable): String = "$url/rest/v1/${table.tableName}"

    companion object {
        /**
         * Ayar verilmemişse `null` döndürür — istisna fırlatmaz.
         *
         * Değerler kuruluma özgü ve depoya işlenmiyor; projeyi ilk kez klonlayan
         * birinde boş oluyorlar. Kurucunun `require`'ı o durumda derlemeyi değil
         * **uygulamayı açılışta** düşürürdü ve mesaj da yığın izinde kalırdı.
         * Bunun yerine eksiklik burada sessizce `null`a çevriliyor ve çağıran
         * taraf kullanıcıya ne yapması gerektiğini söyleyen bir uç bağlıyor.
         */
        fun orNull(url: String, anonKey: String): SupabaseConfig? {
            val temizUrl = url.trim().removeSuffix("/")
            if (temizUrl.isBlank() || anonKey.isBlank()) return null
            return SupabaseConfig(temizUrl, anonKey.trim())
        }
    }
}

/**
 * Giriş yapmış kullanıcının erişim jetonu.
 *
 * Ayrı bir arayüz çünkü jetonun nereden geldiği (Supabase Auth, yenileme
 * döngüsü, saklama) senkronizasyonu ilgilendirmiyor — ilgilendiren tek şey
 * isteğe konacak güncel değer.
 *
 * `null` dönmesi "oturum yok" demek; gönderim denenmez ve kayıt kuyrukta kalır.
 */
fun interface AccessTokenProvider {
    suspend fun currentAccessToken(): String?
}
