package com.gymapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Sunucuya gönderilmeyi bekleyen satırların kuyruğu.
 *
 * **Neden kuyruk, neden "son senkronizasyondan sonra değişenler" değil:**
 * `updatedAtMs > sonSenkron` sorgusu cihaz saatine güvenir. Saat geri alınırsa
 * (kullanıcı elle değiştirir, yaz saati, NTP düzeltmesi) o aralıktaki değişiklikler
 * sessizce atlanır ve bir daha hiç gönderilmez. Kuyrukta ise kayıt açıkça durur:
 * gönderilene kadar silinmez.
 *
 * **Neden yalnızca işaretçi (tablo + satır kimliği), neden veri kopyası değil:**
 * Kuyruk anlık görüntü tutsaydı, bir satır arka arkaya üç kez değiştiğinde üç
 * kopya birikirdi ve sunucuya art arda üç yazma giderdi. Son yazan kazanan
 * (last-write-wins) bir modelde ara durumların değeri yok — gönderim anında
 * satırın güncel hâli okunur. Ayrıca her entity'yi JSON'a çevirmek gerekmiyor.
 *
 * Silme de bu modele uyuyor: silinen satırlar tombstone (`deletedAtMs`) olarak
 * yerinde duruyor, dolayısıyla işaretçi hâlâ geçerli bir satırı gösteriyor.
 *
 * `(tenantId, entityTable, entityId)` tekil: aynı satır için birden fazla bekleyen
 * kayıt oluşmaz. Çakışmada **ilk** kayıt korunur, böylece [enqueuedAtMs] satırın
 * ilk değiştiği anı gösterir ve kuyruk FIFO sırasını koruyabilir.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["tenantId", "entityTable", "entityId"], unique = true),
        Index(value = ["tenantId", "enqueuedAtMs"]),
    ],
)
data class SyncOutboxEntity(
    @PrimaryKey val id: String,
    val tenantId: String,

    /** Kaynak tablo adı — [com.gymapp.data.sync.SyncTable] değerlerinden biri. */
    val entityTable: String,
    val entityId: String,

    /** Satırın ilk değiştiği an; kuyruk bu sırayla boşaltılır. */
    val enqueuedAtMs: Long,

    /**
     * Kayıt kuyruğa girdikten sonra satırın **kaç kez daha** değiştiği.
     *
     * Gönderim sırasında satır tekrar değişirse gönderilen içerik eskimiş olur;
     * o kaydın kuyruktan düşmemesi gerekiyor. Ayırt edici olarak [enqueuedAtMs]
     * kullanılamıyor çünkü o bilinçli olarak **sabit** — FIFO sırası ona
     * dayanıyor ve tazelenseydi sık düzenlenen bir satır kuyruğun sonuna
     * atılarak aç kalırdı.
     *
     * Önceden ayrı bir sayaç yoktu ve `SyncEngine` `enqueuedAtMs`'in
     * tazelendiğini VARSAYIYORDU. Varsayım yanlıştı (`IGNORE` eskiyi korur), yani
     * koruma hiç çalışmıyordu: gönderim penceresinde yapılan değişiklik kuyruktan
     * sessizce düşüyor, kullanıcı "eşitlendi" görüyor ve iki cihaz kalıcı olarak
     * ayrışıyordu.
     */
    val revision: Int = 0,

    /**
     * Kaç kez gönderilmeye çalışıldı.
     *
     * Geri çekilme (backoff) ve "bu kayıt sürekli patlıyor" teşhisi için.
     * Kalıcı olarak başarısız olan bir kayıt kuyruğu tıkamamalı; sürekli artan
     * sayaç bunu görünür kılıyor.
     */
    val attemptCount: Int = 0,
    val lastAttemptAtMs: Long? = null,

    /** Son hatanın mesajı — kullanıcıya değil, teşhise yönelik. */
    val lastError: String? = null,
)
