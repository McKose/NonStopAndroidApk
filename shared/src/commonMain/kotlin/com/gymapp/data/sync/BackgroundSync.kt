package com.gymapp.data.sync

/**
 * Arka plan turunun, iş zamanlayıcısının anlayacağı sonucu.
 *
 * Android'de `WorkManager`, iOS'ta `BGTaskScheduler` karşılığı var; ikisi de
 * "bitti / tekrar dene" ayrımını istiyor. Karar burada, ortak kodda veriliyor:
 * platform tarafında verilseydi iki uygulama aynı durumda farklı davranabilir
 * ve fark ancak "iOS'ta veri gitmiyor" diye ortaya çıkardı.
 */
enum class BackgroundSyncResult {
    /** İş tamamlandı; bir sonraki planlı turu beklemek yeterli. */
    DONE,

    /** Geçici engel. Zamanlayıcı, kendi geri çekilme süresiyle tekrar denemeli. */
    RETRY,
}

/**
 * Senkronizasyon durumunun arka plan karşılığı.
 *
 * ### Neden [SyncState.NoSession] "tekrar dene" değil
 * Giriş yapılmamışsa tekrar denemek hiçbir şeyi değiştirmez: oturum arka planda
 * açılmıyor. `RETRY` dönmek, zamanlayıcıyı üstel geri çekilmeyle sürekli
 * uyandırırdı ve her uyanışta yapılacak iş "hâlâ oturum yok" demekten ibaret
 * olurdu. Çıkış yapmış bir cihazda bu, tamamen boşa harcanan pil.
 *
 * ### Neden [SyncState.Running] "tekrar dene" değil
 * Başka bir tur zaten koşuyor demektir (ör. kullanıcı aynı anda uygulamayı
 * açmış). Koordinatör tek seferlik çalışıyor ve koşan tur bitince kuyruğu
 * boşaltacak; arka plan işinin ayrıca tekrar denemesi aynı işi ikinci kez
 * planlamak olurdu.
 *
 * ### Neden [SyncState.Idle] "bitti"
 * Tur hiç başlamamış anlamına geliyor ve pratikte [SyncCoordinator.syncNow]
 * çağrıldıktan sonra görülmüyor. Yine de `RETRY` dönmemesi bilinçli: beklenmeyen
 * bir durumda sessizce sonsuz tekrar döngüsüne girmektense hiçbir şey yapmamak
 * yeğ. Sorun varsa bir sonraki planlı turda yine denenir.
 *
 * ### [SyncState.Problem] ikiye ayrılıyor
 * Ayrımı durum nesnesi taşıyor ([SyncState.Problem.retryable]); burada yeniden
 * karar verilmiyor. Gerekçe: hangi sorunun geçici olduğunu bilen tek yer turu
 * koşturan koordinatör — dışarıdan mesaj metnine bakarak çıkarmak kırılgan
 * olurdu.
 */
fun SyncState.backgroundResult(): BackgroundSyncResult = when (this) {
    is SyncState.Problem -> if (retryable) BackgroundSyncResult.RETRY else BackgroundSyncResult.DONE
    SyncState.Idle,
    SyncState.Running,
    SyncState.NoSession,
    is SyncState.Done,
    -> BackgroundSyncResult.DONE
}
