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

    /**
     * Tur tamamlandı.
     *
     * [pushed] gönderilen, [pulled] indirilen kayıt sayısı. İkisi ayrı: "hiçbir
     * şey göndermedim ama on satır indirdim" ile "hiçbir şey olmadı" kullanıcı
     * için farklı.
     */
    data class Done(val pushed: Int, val pulled: Int, val atMs: Long) : SyncState

    /**
     * Tur erken bitti ya da bazı kayıtlar reddedildi.
     *
     * Hata "başarısız" değil "eksik" anlamında: gönderilenler gitti, kalanlar
     * kuyrukta. Kullanıcıya gösterilecek mesaj da bu ayrımı taşımalı.
     */
    data class Problem(
        val reason: String,
        val pushed: Int,
        val pulled: Int,
        val failed: Int,
        /**
         * Aynı işi bir süre sonra tekrar denemenin anlamı var mı?
         *
         * "Sorun" tek bir şey değil ve ikisini ayırmamak arka planda pahalıya
         * mal olur:
         *  - **Ağ yok / sunucu yanıt vermiyor** → geçici. Tekrar denenmezse
         *    kullanıcı uygulamayı açana kadar veri gitmez.
         *  - **Sunucu kayıtları reddetti** (yetki, bozuk satır) → kalıcı. Tekrar
         *    denemek her seferinde aynı sonucu verir; arka planda süresiz
         *    tekrarlamak boşuna pil harcar ve sorunu da gizler.
         *
         * Ekranda gösterilen metin ikisi için de "sorun" diyor; ayrım yalnızca
         * otomatik tekrar kararını veren taraf için gerekli.
         */
        val retryable: Boolean,
    ) : SyncState

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
 * ### Önce gönderim, sonra indirme
 * Sıra bilinçli. İndirme, gönderim bekleyen satırları atlıyor (yerel değişiklik
 * sunucudakinden yenidir); önce gönderilirse o satırlar kuyruktan düşer ve
 * güncel hâlleriyle inerler. Ters sırada aynı satır bir tur boyunca atlanır ve
 * indirme bir tur geriden gelirdi.
 *
 * Gönderim geçici bir hatayla durduysa indirme hiç denenmiyor: sebep neredeyse
 * her zaman ağ ve dokuz tablo için dokuz zaman aşımı beklemenin karşılığı yok.
 *
 * ### Yazma anında neden tetiklenmiyor
 * Kuyruğa alma, satırı değiştiren yazmayla **aynı transaction** içinde yapılıyor.
 * O anda tetiklenen bir tur, henüz işlenmemiş (commit edilmemiş) kaydı göremez;
 * boşuna koşar ve değişiklik bir sonraki tetiklemeye kalır. Bu yüzden tetikleme
 * dışarıdan: girişten sonra, uygulama önplandayken düzenli aralıklarla ve elle.
 */
class SyncCoordinator(
    private val runner: SyncRunner,
    private val puller: PullRunner,
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
                // İndirme denenmiyor — aynı ağ, aynı sonuç.
                _state.value = SyncState.Problem(
                    reason = "Bağlantı sorunu — bekleyen değişiklikler gönderilemedi.",
                    pushed = pushed,
                    pulled = 0,
                    failed = failed,
                    retryable = true,
                )
                return
            }

            // Bu turda hiçbir kayıt düşmediyse bir sonraki tur da aynı sonucu
            // verir: ya kuyruk boş ya da kalanlar geri çekilme süresini bekliyor.
            if (outcome.pushed == 0) {
                bitir(tenantId, pushed, failed)
                return
            }
        }

        // Tur sınırına dayandı: kuyruk hâlâ dolu olabilir ama durmak gerekiyor.
        // Sessizce "bitti" demek yanlış olurdu — bir sonraki tetikleme devam eder.
        _state.value = SyncState.Problem(
            reason = "Çok fazla bekleyen değişiklik var; gönderim sürüyor.",
            pushed = pushed,
            pulled = 0,
            failed = failed,
            // Kuyruk hâlâ dolu: bu bir hata değil, "işim bitmedi". Tekrar
            // denenmezse kalan kayıtlar uygulamanın bir sonraki açılışını
            // bekler.
            retryable = true,
        )
    }

    /** Gönderim bittikten sonra indirme ve son durumun bildirilmesi. */
    private suspend fun bitir(tenantId: String, pushed: Int, failed: Int) {
        val cekme = puller.pullAll(tenantId)

        _state.value = when {
            cekme.stopped -> SyncState.Problem(
                reason = "İndirme tamamlanamadı: ${cekme.reason ?: "-"}",
                pushed = pushed,
                pulled = cekme.applied,
                failed = failed,
                retryable = true,
            )

            failed > 0 -> SyncState.Problem(
                reason = "$failed kayıt sunucu tarafından reddedildi.",
                pushed = pushed,
                pulled = cekme.applied,
                failed = failed,
                // Gönderim ve indirme tamamlandı; kalan tek şey sunucunun
                // reddettiği kayıtlar. Tekrar denemek aynı reddi alır.
                retryable = false,
            )

            else -> SyncState.Done(pushed, cekme.applied, now())
        }
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
