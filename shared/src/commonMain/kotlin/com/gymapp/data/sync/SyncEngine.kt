package com.gymapp.data.sync

import com.gymapp.data.local.dao.SyncOutboxDao
import com.gymapp.data.local.entity.SyncOutboxEntity
import com.gymapp.domain.Ids
import com.gymapp.domain.Now

/**
 * Gönderim kuyruğunu boşaltan motor.
 *
 * Sorumluluğu **yalnızca kuyruk yönetimi**: sıra, toplu okuma, geri çekilme,
 * başarı hâlinde düşürme, hata hâlinde işaretleme. Ne gönderileceği
 * [RemoteDataSource]'un işi — o sınır, sunucu ve kimlik doğrulama kararı
 * verilmeden motorun yazılabilmesi için var.
 *
 * ### Sıra neden korunuyor
 * Kayıtlar `enqueuedAtMs` sırasıyla işleniyor. Satırlar arasında bağımlılık var
 * (sipariş üyeye, randevu eğitmene bakıyor); sırayı bozmak sunucuda henüz var
 * olmayan bir satıra referans göndermek demek olurdu.
 *
 * ### Geçici hatada tur neden duruyor
 * Ağ yoksa sıradaki kayıtlar da başarısız olacak. Hepsini denemek `attemptCount`
 * sayaçlarını boş yere şişirir ve geri çekilme süresini yanlış yere uzatır. Tur
 * durur, kayıtlar kuyrukta kalır, sonraki turda baştan denenir.
 *
 * ### Kalıcı hatada tur neden devam ediyor
 * Sunucunun reddettiği tek bir kayıt, arkasındaki her şeyi süresiz bekletmemeli.
 * Kayıt kuyrukta kalıyor ve `lastError` ile işaretleniyor; `attemptCount` sürekli
 * arttığı için "bu kayıt sürekli patlıyor" teşhis edilebilir hâle geliyor.
 * Sessizce silmek veri kaybı olurdu.
 */
class SyncEngine(
    private val outboxDao: SyncOutboxDao,
    private val remote: RemoteDataSource,
    private val now: () -> Long = { Now.epochMillis() },
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val baseBackoffMs: Long = DEFAULT_BASE_BACKOFF_MS,
    private val maxBackoffMs: Long = DEFAULT_MAX_BACKOFF_MS,
) : SyncRunner {

    /**
     * Kuyruktan bir tur işler.
     *
     * Tek çağrıda kuyruğun tamamını boşaltmaya çalışmaz: en fazla [batchSize]
     * kayıt işler. Kuyruğu bitirmek çağıranın işi ve o çağıran
     * [SyncCoordinator] — turları o yönetiyor.
     *
     * Salon kimliği **parametre**, motorun kendi bulduğu bir değer değil: hangi
     * salon için koşulduğuna karar vermek tur yönetiminin parçası ve motorun
     * oturumdan haberdar olması onu gereksizce oturuma bağlardı.
     */
    override suspend fun syncOnce(tenantId: String): SyncOutcome {
        var pushed = 0
        var failed = 0
        var skipped = 0

        for (entry in outboxDao.peek(tenantId, batchSize)) {
            val nowMs = now()

            if (!isDue(entry, nowMs)) {
                skipped++
                continue
            }

            val table = SyncTable.fromTableName(entry.entityTable)
            if (table == null) {
                // Tanınmayan tablo: eski bir sürümün bıraktığı kayıt olabilir.
                // Tekrar denemek aynı sonucu vereceği için kalıcı hata sayılıyor,
                // ama silinmiyor — sessizce kaybolması teşhisi imkânsız kılardı.
                outboxDao.recordFailure(entry.id, nowMs, "Tanınmayan tablo: ${entry.entityTable}")
                failed++
                continue
            }

            when (val result = remote.push(table, entry.entityId)) {
                is PushResult.Success -> {
                    // Yalnızca kayıt hâlâ aynıysa düşür: gönderim sürerken satır
                    // tekrar değişmişse `enqueuedAtMs` yenilenmiş olur ve silme
                    // eşleşmez, böylece yeni değişiklik kuyrukta kalır.
                    if (outboxDao.removeIfUnchanged(entry.id, entry.enqueuedAtMs) > 0) {
                        pushed++
                    } else {
                        skipped++
                    }
                }

                is PushResult.Retryable -> {
                    outboxDao.recordFailure(entry.id, nowMs, result.reason)
                    return SyncOutcome(pushed, failed + 1, skipped, stopped = true)
                }

                is PushResult.Permanent -> {
                    outboxDao.recordFailure(entry.id, nowMs, result.reason)
                    failed++
                }
            }
        }

        return SyncOutcome(pushed, failed, skipped)
    }

    /**
     * Kayıt tekrar denenebilir mi?
     *
     * Hata almış kayıtlar üstel geri çekilmeyle bekletiliyor: sunucu ya da ağ
     * sorunluyken saniyede bir denemek ne sorunu çözer ne de pili.
     */
    private fun isDue(entry: SyncOutboxEntity, nowMs: Long): Boolean {
        if (entry.attemptCount <= 0) return true
        val lastAttempt = entry.lastAttemptAtMs ?: return true
        return nowMs - lastAttempt >= backoffFor(entry.attemptCount)
    }

    /**
     * Üstel geri çekilme, [maxBackoffMs] ile sınırlı.
     *
     * Kaydırma yerine çarparak ilerliyor: `attemptCount` büyüdüğünde `shl` taşar
     * ve negatif bir bekleme süresi üretir — yani sürekli patlayan bir kayıt
     * geri çekilmeyi tamamen devre dışı bırakırdı.
     */
    private fun backoffFor(attemptCount: Int): Long {
        var delay = baseBackoffMs
        repeat(attemptCount - 1) {
            if (delay >= maxBackoffMs) return maxBackoffMs
            delay *= 2
        }
        return delay.coerceAtMost(maxBackoffMs)
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 50
        const val DEFAULT_BASE_BACKOFF_MS = 5_000L
        const val DEFAULT_MAX_BACKOFF_MS = 30L * 60 * 1000
    }
}
