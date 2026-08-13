package com.gymapp.data.sync

import com.gymapp.data.auth.TenantProvider
import com.gymapp.domain.Now
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Bir senkronizasyon turunu koşturan uç.
 *
 * [SyncEngine] doğrudan alınmıyor; araya bu arayüz giriyor ki koordinatörün
 * kendi mantığı (tekrar turları, tek seferlik koşma, durum bildirimi) motorun
 * ve dolayısıyla veritabanının olmadığı bir ortamda sınanabilsin.
 */
fun interface SyncRunner {
    suspend fun syncOnce(tenantId: String): SyncOutcome
}

/**
 * Senkronizasyonun dışarıya görünen durumu.
 *
 * Arayüzde gösterilmek için var. Görünmez bir senkronizasyon, çalışmadığında da
 * çalışıyormuş gibi görünür — bu projede kaçınılmaya çalışılan tam olarak bu:
 * kullanıcı verisinin sunucuya gitmediğini fark edemezse sorun sessizce büyür.
 */
sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState

    /** Kuyruk boşaldı. [pushed] bu turda gönderilen kayıt sayısı. */
    data class Done(val pushed: Int, val atMs: Long) : SyncState

    /**
     * Tur erken bitti ya da bazı kayıtlar reddedildi.
     *
     * Hata "başarısız" değil "eksik" anlamında: gönderilenler gitti, kalanlar
     * kuyrukta. Kullanıcıya gösterilecek mesaj da bu ayrımı taşımalı.
     */
    data class Problem(val reason: String, val pushed: Int, val failed: Int) : SyncState

    /** Giriş yapılmamış; gönderilecek bir şey aranmıyor bile. */
    data object NoSession : SyncState
}

/**
 * Kuyruğu boşaltana kadar motoru çalıştırır.
 *
 * [SyncEngine] tek çağrıda en fazla bir grup işliyor; kuyruğu bitirmek çağıranın
 * işi. Bu sınıf o çağıran.
 *
 * ### Neden tek seferlik (single-flight)
 * Aynı anda iki tur koşarsa ikisi de aynı kayıtları okur: biri gönderirken
 * diğeri aynı satırı tekrar gönderir ve `attemptCount` sayaçları anlamsızlaşır.
 * Kilit alınamadığında ikinci çağrı beklemiyor — koşan tura "bitince bir tur daha
 * at" diyip dönüyor. Beklemek, toplu bir kayıt sırasında onlarca askıda coroutine
 * biriktirirdi; isteği bayrağa indirgemek aynı sonucu tek turla veriyor.
 *
 * ### Neden tur sayısı sınırlı
 * Döngü "bu turda hiç gönderim olmadı" görünce duruyor; [maxRounds] ise ikinci
 * bir emniyet. Kuyruktan düşürme mantığındaki bir hata sonsuz döngü üretebilirdi
 * ve bu, pili bitiren ama hiçbir belirti vermeyen türden bir hata olurdu.
 *
 * ### Yazma anında neden tetiklenmiyor
 * Kuyruğa alma, satırı değiştiren yazmayla **aynı transaction** içinde yapılıyor.
 * O anda tetiklenen bir tur, henüz işlenmemiş (commit edilmemiş) kaydı göremez;
 * boşuna koşar ve değişiklik bir sonraki tetiklemeye kalır. Bu yüzden tetikleme
 * dışarıdan: girişten sonra, uygulama önplandayken düzenli aralıklarla ve elle.
 */
class SyncCoordinator(
    private val runner: SyncRunner,
    private val tenants: TenantProvider,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Now.epochMillis() },
    private val maxRounds: Int = DEFAULT_MAX_ROUNDS,
) {

    private val mutex = Mutex()
    private val rerunRequested = MutableStateFlow(false)

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Arka planda tetikler; sonucu [state] üzerinden izlenir. */
    fun requestSync() {
        scope.launch { syncNow() }
    }

    /**
     * Kuyruğu boşaltmayı dener ve son durumu döndürür.
     *
     * Zaten koşan bir tur varsa yenisi başlatılmıyor; koşan turun bitince bir kez
     * daha denemesi isteniyor. Böylece "senkronizasyon sırasında eklenen kayıt"
     * beklemede kalmıyor.
     */
    suspend fun syncNow(): SyncState {
        val tenantId = tenants.currentTenantId()
        if (tenantId == null) {
            _state.value = SyncState.NoSession
            return SyncState.NoSession
        }

        if (!mutex.tryLock()) {
            rerunRequested.value = true
            return _state.value
        }

        try {
            do {
                rerunRequested.value = false
                drain(tenantId)
            } while (rerunRequested.value)
        } finally {
            mutex.unlock()
        }
        return _state.value
    }

    private suspend fun drain(tenantId: String) {
        _state.value = SyncState.Running

        var pushed = 0
        var failed = 0

        repeat(maxRounds) {
            val outcome = runner.syncOnce(tenantId)
            pushed += outcome.pushed
            failed += outcome.failed

            if (outcome.stopped) {
                // Geçici hata: ağ yok ya da sunucu yanıt vermiyor. Kalanlar
                // kuyrukta; bir sonraki tetiklemede kaldığı yerden devam eder.
                _state.value = SyncState.Problem(
                    reason = "Bağlantı sorunu — bekleyen değişiklikler gönderilemedi.",
                    pushed = pushed,
                    failed = failed,
                )
                return
            }

            // Bu turda hiçbir kayıt düşmediyse bir sonraki tur da aynı sonucu
            // verir: ya kuyruk boş ya da kalanlar geri çekilme süresini bekliyor.
            if (outcome.pushed == 0) {
                _state.value = if (failed > 0) {
                    SyncState.Problem(
                        reason = "$failed kayıt sunucu tarafından reddedildi.",
                        pushed = pushed,
                        failed = failed,
                    )
                } else {
                    SyncState.Done(pushed, now())
                }
                return
            }
        }

        // Tur sınırına dayandı: kuyruk hâlâ dolu olabilir ama durmak gerekiyor.
        // Sessizce "bitti" demek yanlış olurdu — bir sonraki tetikleme devam eder.
        _state.value = SyncState.Problem(
            reason = "Çok fazla bekleyen değişiklik var; gönderim sürüyor.",
            pushed = pushed,
            failed = failed,
        )
    }

    private companion object {
        /**
         * Bir tetiklemede en fazla kaç grup işlenir.
         *
         * Grup başına 50 kayıtla 2000 kayıt eder; normal kullanımda asla
         * ulaşılmaz. Sonsuz döngüye karşı emniyet olarak var.
         */
        const val DEFAULT_MAX_ROUNDS = 40
    }
}
