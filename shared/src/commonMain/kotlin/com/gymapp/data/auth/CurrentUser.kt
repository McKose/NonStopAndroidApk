package com.gymapp.data.auth

import com.gymapp.data.local.dao.StaffDao
import com.gymapp.domain.StaffRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Oturumdaki kullanıcının personel kaydıyla bağlantısı.
 *
 * Ayrı bir tip çünkü üç durum üç ayrı sonuç doğuruyor ve ikisini boş bir metinle
 * temsil etmek bugüne kadar hepsini aynı şeye çeviriyordu (bkz. [CurrentUser]).
 */
sealed interface StaffLink {

    /** Giriş yapılmamış. */
    data object NoSession : StaffLink

    /**
     * Giriş var ama bu hesaba bağlı **aktif personel kaydı yok**.
     *
     * Kurulum eksikliği, hata değil: personel kartındaki "Supabase kullanıcı
     * kimliği" alanı boş bırakılmış ya da yanlış yapıştırılmış. Salon sahibi
     * gibi ders vermeyen bir kullanıcıda ise tamamen normal.
     */
    data object Unlinked : StaffLink

    /** Randevu ve hakediş kayıtlarındaki `staffId` bu. */
    data class Linked(val staffId: String) : StaffLink
}

/**
 * Oturumdaki kullanıcının rolü ve personel bağlantısı — **tek kaynak**.
 *
 * ### Neden yeni bir tip
 * Rol daha önce iki yerde tutuluyordu: sunucudan gelen [Session.role] ve girişte
 * ona kopyalanan bir `SharedPreferences` alanı. Kopya üç ayrı yoldan sapabiliyordu:
 *
 * 1. **Kopya yalnızca girişte yazılıyordu.** Uygulama açılışında oturum
 *    `SessionManager.restore()` ile geri yükleniyor ama o yol tercihe hiç
 *    dokunmuyor. Sunucuda rolü düşürülen bir kullanıcı, cihazda giriş ekranından
 *    geçmediği sürece eski yetkisiyle çalışmaya devam ediyordu.
 * 2. **Jeton yenilemesi reddedildiğinde** `SessionManager` oturumu düşürüyor,
 *    tercih ise eski rolü tutmaya devam ediyordu.
 * 3. **Tepkili değildi.** `prefs.currentUserRole` düz bir okuma; ekranlar bu
 *    değeri ya `combine` bloğunun içinde (yani ancak başka bir akış yayın
 *    yaptığında) ya da ViewModel kurulurken **bir kez** okuyordu.
 *
 * Artık kopya yok: rol yalnızca oturumdan okunuyor ve akış olarak veriliyor.
 *
 * ### Personel kimliği neden burada
 * Randevu ve hakediş kayıtları yerel `staff.id` taşıyor, oturum ise
 * `auth.users.id`. Eşleme daha önce **girişte bir kez** yapılıyor ve sonucu boş
 * metin olabiliyordu. Boş metin sessiz bir yalandı: "bu kullanıcının hiçbir
 * dersi yok" ile "bu kullanıcının kim olduğunu bilmiyoruz" aynı ekrana çıkıyordu
 * — eğitmen boş bir pano görüp uygulamanın verisini kaybettiğini sanıyordu.
 *
 * Eşleme artık akış: personel kartı sonradan doldurulduğunda ya da senkronizasyon
 * turu personel satırını indirdiğinde pano kendiliğinden düzeliyor, kullanıcının
 * çıkıp yeniden girmesi gerekmiyor.
 *
 * ### Neden `SessionManager` değil de akışı
 * İhtiyaç duyulan tek şey oturumun kendisi. Dar bağımlılık, [TenantProvider] ile
 * aynı gerekçeyle: bu tip giriş yaptıramaz, çıkış yaptıramaz, jetona erişemez.
 */
class CurrentUser(
    private val session: StateFlow<Session?>,
    private val staffDao: StaffDao,
) {

    /**
     * Oturumdaki rol; oturum yoksa **en dar** yetki.
     *
     * Oturumsuz durumda yönetici yetkisine düşmek, çıkış ile giriş arasındaki
     * karede yetkili ekranların çizilmesi demek olurdu.
     */
    val role: Flow<StaffRole> =
        session.map { it?.role ?: StaffRole.TRAINER }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val staffLink: Flow<StaffLink> = session
        .flatMapLatest { oturum ->
            if (oturum == null) {
                flowOf(StaffLink.NoSession)
            } else {
                staffDao.observeByAuthUserId(oturum.tenantId, oturum.userId)
                    .map { staff ->
                        if (staff == null) StaffLink.Unlinked else StaffLink.Linked(staff.id)
                    }
            }
        }
        .distinctUntilChanged()
}
