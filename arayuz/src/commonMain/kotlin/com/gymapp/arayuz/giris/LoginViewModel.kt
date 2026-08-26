package com.gymapp.arayuz.giris

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.auth.AuthResult
import com.gymapp.data.auth.SessionManager
import com.gymapp.data.sync.ArkaPlanSenkronizasyonu
import com.gymapp.data.sync.SyncCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Giriş — kimlik doğrulama Supabase Auth'ta.
 *
 * ### Yerel şifre kontrolü kaldırıldı
 * Önceden kullanıcı adı ve `staff.password` karşılaştırılıyordu, ayrıca ayarlardaki
 * salon şifresiyle çalışan bir `admin` yolu vardı. İkisi de kaldırıldı çünkü ikisi
 * de sunucudan bağımsızdı: o yolla açılan oturumun bir salon kimliği olmuyordu ve
 * o oturumda girilen hiçbir veri sunucuya gönderilemiyordu — üstelik sessizce.
 * Artık tek kimlik kaynağı var.
 *
 * ### İlk giriş internet istiyor
 * Bilinçli bir bedel: kimlik doğrulama sunucuda yapılıyor. İlk başarılı girişten
 * sonra oturum cihazda kaldığı için günlük kullanım çevrimdışı sürüyor.
 *
 * ### Hata mesajları neden ayrı ayrı
 * Her başarısızlık türü kullanıcıyı **farklı bir işe** yönlendiriyor. Tek bir
 * "giriş başarısız" mesajı, sorun aslında sunucudaki eksik bir `gym_users`
 * satırıyken kullanıcıyı şifresini aramaya gönderirdi; bu gerçek bir kurulumda
 * yaşandı.
 */
class LoginViewModel(
    private val sessions: SessionManager,
    private val sync: SyncCoordinator,
    private val arkaPlan: ArkaPlanSenkronizasyonu,
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()

    fun login(email: String, password: String, onLoginSuccess: () -> Unit) {
        if (_isSubmitting.value) return // çift tıklama koruması

        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null // önceki denemenin hatası ekranda kalmasın
            try {
                when (val sonuc = sessions.signIn(email, password)) {
                    is AuthResult.Success -> {
                        // Oturumdan türeyen yerel durum yazılmıyor: rol ve
                        // personel kimliği artık `CurrentUser` üzerinden
                        // doğrudan oturumdan okunuyor. Buradaki kopyalama, geri
                        // yüklenen oturumlarda hiç çalışmadığı için rolün
                        // sunucudakinden sapabildiği yerdi.
                        //
                        // Girişten önce çevrimdışı biriken değişiklikler var
                        // olabilir; oturum açılır açılmaz gönderilmeye başlansın.
                        // Beklenmiyor: kullanıcıyı kuyruk boşalana kadar giriş
                        // ekranında tutmanın bir karşılığı yok.
                        sync.requestSync()
                        // Bundan sonra uygulama kapalıyken de gönderim
                        // sürsün. Çıkışta iptal ediliyor.
                        arkaPlan.baslat()
                        onLoginSuccess()
                    }

                    is AuthResult.InvalidCredentials -> _error.value =
                        "E-posta veya şifre hatalı. (${sonuc.reason})"

                    is AuthResult.NoGym -> _error.value =
                        "Hesabınız bir salona bağlı değil. Yöneticinizin sizi " +
                            "Supabase panelinden salona eklemesi gerekiyor."

                    is AuthResult.MultipleGyms -> _error.value =
                        "Hesabınız birden fazla salona bağlı; bu sürüm tek salon " +
                            "destekliyor. Yöneticinizle görüşün."

                    is AuthResult.Failed -> _error.value = sonuc.reason
                }
            } finally {
                _isSubmitting.value = false
            }
        }
    }

}
