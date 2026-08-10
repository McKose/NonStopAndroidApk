package com.gymapp.data.sync

import com.gymapp.data.local.dao.SyncOutboxDao
import com.gymapp.data.local.entity.SyncOutboxEntity
import com.gymapp.domain.Ids
import com.gymapp.domain.Now
import kotlinx.coroutines.flow.Flow

/**
 * Değişen satırları gönderim kuyruğuna alır.
 *
 * **Çağrı kuralı: kuyruğa alma, satırı değiştiren yazmayla aynı transaction
 * içinde olmalıdır.** Ayrı transaction'larda olsalardı araya giren bir hata iki
 * yönde de bozuk durum bırakırdı:
 *  - satır yazıldı, kuyruğa girmedi → değişiklik sunucuya hiç gitmez, üstelik
 *    kimse fark etmez (kayıp sessizdir),
 *  - kuyruğa girdi, satır yazılmadı → var olmayan bir değişiklik gönderilmeye
 *    çalışılır ve kuyruk tıkanır.
 *
 * Repository'lerdeki yazma yolları zaten `GymDatabase.inTransaction` kullanıyor;
 * kuyruğa alma o bloğun içinde yapılır.
 *
 * İlgili kural: **transaction'ı giriş noktası açar.** Repository'ler birbirini
 * çağırıyor (üye → defter, sipariş → defter, randevu → defter); iç çağrılar kendi
 * transaction'ını açmaz, dıştakinin içinde koşar.
 */
class SyncQueue(
    private val outboxDao: SyncOutboxDao,
) {

    /** Tek bir satırı kuyruğa alır. */
    suspend fun enqueue(
        table: SyncTable,
        entityId: String,
        tenantId: String = Ids.DEFAULT_TENANT,
        nowMs: Long = Now.epochMillis(),
    ) {
        outboxDao.enqueue(
            SyncOutboxEntity(
                id = Ids.new(),
                tenantId = tenantId,
                entityTable = table.tableName,
                entityId = entityId,
                enqueuedAtMs = nowMs,
            )
        )
    }

    /** Aynı tablodan birden çok satır (ör. bir siparişin stok hareketleri). */
    suspend fun enqueueAll(
        table: SyncTable,
        entityIds: Collection<String>,
        tenantId: String = Ids.DEFAULT_TENANT,
        nowMs: Long = Now.epochMillis(),
    ) {
        entityIds.forEach { enqueue(table, it, tenantId, nowMs) }
    }

    /** Bekleyen değişiklik sayısı; arayüzde "senkronize edilmedi" göstergesi için. */
    fun observePendingCount(tenantId: String = Ids.DEFAULT_TENANT): Flow<Int> =
        outboxDao.observePendingCount(tenantId)

    suspend fun pendingCount(tenantId: String = Ids.DEFAULT_TENANT): Int =
        outboxDao.pendingCount(tenantId)
}
