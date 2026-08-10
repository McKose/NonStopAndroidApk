package com.gymapp.data.sync

/**
 * Bir gönderim denemesinin sonucu.
 *
 * Geçici ve kalıcı hatanın ayrılması motorun davranışını belirliyor, o yüzden
 * `Boolean` ya da fırlatılan bir istisna yerine açık bir tip:
 *  - **geçici** hata (ağ yok, sunucu 5xx, zaman aşımı) → sonraki turda aynı kayıt
 *    tekrar denenmeli, sıra bozulmamalı;
 *  - **kalıcı** hata (kayıt sunucu tarafından reddedildi, şema uyuşmuyor) →
 *    tekrar denemek aynı sonucu verir; o kayıt kuyruğun tamamını tıkamamalı.
 *
 * İkisini ayırmayan bir tasarımda tek bozuk kayıt bütün senkronizasyonu süresiz
 * durdururdu.
 */
sealed interface PushResult {
    data object Success : PushResult

    /** Tekrar denenebilir; kayıt kuyrukta kalır ve tur burada durur. */
    data class Retryable(val reason: String) : PushResult

    /** Tekrar denemek aynı sonucu verir; kayıt kuyrukta kalır ama tur devam eder. */
    data class Permanent(val reason: String) : PushResult
}

/**
 * Sunucu ucu.
 *
 * Arayüz bilinçli olarak **satırın içeriğini taşımıyor**, yalnızca hangi tablodaki
 * hangi satırın gönderileceğini söylüyor. Nedeni: gönderilecek verinin şekli
 * (JSON alanları, kimlik doğrulama başlıkları, satır bazlı güvenlik) sunucu
 * seçimine bağlı ve o karar henüz verilmedi. Motor bu karardan bağımsız
 * yazılabilsin diye sınır buradan geçiyor.
 *
 * Uygulayan taraf satırı yerelden okur; kuyruk kaydı zaten bir işaretçi olduğu
 * için gönderim anında satırın **güncel** hâli okunur.
 *
 * Silme ayrı bir işlem değil: silinen satırlar tombstone (`deletedAtMs`) olarak
 * yerinde duruyor, dolayısıyla silme de sıradan bir güncelleme olarak gidiyor.
 */
interface RemoteDataSource {
    suspend fun push(table: SyncTable, entityId: String): PushResult
}
