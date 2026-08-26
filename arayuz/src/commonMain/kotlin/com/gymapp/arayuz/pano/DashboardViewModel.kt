package com.gymapp.arayuz.pano

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.access.AppDestination
import com.gymapp.data.auth.CurrentUser
import com.gymapp.data.auth.StaffLink
import com.gymapp.data.local.entity.AppointmentEntity
import com.gymapp.data.local.entity.MemberEntity
import com.gymapp.data.local.entity.ProductEntity
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.data.repository.AppointmentRepository
import com.gymapp.data.repository.MemberRepository
import com.gymapp.data.repository.ProductRepository
import com.gymapp.data.repository.StaffRepository
import com.gymapp.domain.Membership
import com.gymapp.domain.MembershipState
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentState
import com.gymapp.domain.Periods
import com.gymapp.domain.StaffRole
import kotlinx.coroutines.flow.*
import com.gymapp.domain.Now
import com.gymapp.domain.TarihBicimi

private const val LOW_STOCK_THRESHOLD = 5

/** [DashboardViewModel] içindeki ara akışların tipleri; ekrana çıkmıyorlar. */
private data class OturumBilgisi(val role: StaffRole, val link: StaffLink)

private data class StokGorunumu(
    val products: List<ProductEntity>,
    val stockByProduct: Map<String, Int>,
)

data class DashboardUiState(
    val userRole: StaffRole = StaffRole.TRAINER,
    val totalMembers: Int = 0,
    val activeMembers: Int = 0,
    val dailyAppointments: List<AppointmentEntity> = emptyList(),
    val members: List<MemberEntity> = emptyList(),
    val staffList: List<StaffEntity> = emptyList(),
    val criticalAlerts: List<String> = emptyList(),
    /**
     * Eğitmen giriş yaptı ama hesabı bir personel kaydına bağlı değil.
     *
     * Ayrı bir bayrak, çünkü sonucu boş bir panodan ibaret **değil**: ekranın
     * bunu söylemesi gerekiyor. Bkz. [StaffLink.Unlinked].
     */
    val personelBaglantisiYok: Boolean = false,
    val isLoading: Boolean = false
) {
    /** Eğitmen yalnızca kendi randevularını ve üyelerini görür. */
    val isTrainer: Boolean get() = userRole == StaffRole.TRAINER

    /** Ekranın hangi kısayolları çizeceği tek yerden geliyor. */
    fun gorebilir(destination: AppDestination): Boolean = destination.isVisibleTo(userRole)
}

class DashboardViewModel(
    private val memberRepository: MemberRepository,
    private val appointmentRepository: AppointmentRepository,
    private val staffRepository: StaffRepository,
    private val productRepository: ProductRepository,
    currentUser: CurrentUser,
) : ViewModel() {

    /**
     * Rol ve personel bağlantısı tek akışta.
     *
     * Önceden ikisi de `combine` bloğunun **içinde** `SharedPreferences`'tan
     * okunuyordu. Tepkisiz bir kaynağı tepkili bir bloktan okumak, değeri
     * yalnızca başka bir akış yayın yaptığında tazelemek demek: rol değişse bile
     * pano, üye ya da randevu tablosunda bir şey değişene kadar eski yetkiyle
     * çiziliyordu.
     */
    private val oturum = combine(currentUser.role, currentUser.staffLink, ::OturumBilgisi)

    /**
     * Ürün tanımı ve stok tek akışta.
     *
     * Birleştirmenin sebebi yalnızca sayı: altı kaynağı `combine` etmek
     * `Array<Any?>` alan aşırı yüklemeye düşürüyor ve her öğeyi elle cast
     * ettiriyor. O desen bu projede bir kez zaten temizlendi (bkz.
     * `MarketViewModel`) — dizideki sıra değiştiğinde derleyici susuyor, hata
     * çalışma anında `ClassCastException` olarak çıkıyor. İkisi ikili hâlinde
     * birleşince geriye beş tipli kaynak kalıyor.
     */
    private val stok = combine(
        productRepository.getAllProducts(),
        productRepository.observeStockByProduct(),
        ::StokGorunumu,
    )

    val uiState: StateFlow<DashboardUiState> = combine(
        memberRepository.getAllMembers(),
        appointmentRepository.observeAll(),
        staffRepository.getAllStaff(),
        stok,
        oturum,
    ) { members, appointments, staff, stokGorunumu, oturumBilgisi ->
        val products = stokGorunumu.products
        val stockByProduct = stokGorunumu.stockByProduct
        val userRole = oturumBilgisi.role
        val staffLink = oturumBilgisi.link

        val now = Now.epochMillis()
        val today = Periods.dayOf(now)

        val isTrainer = userRole == StaffRole.TRAINER
        val staffId = (staffLink as? StaffLink.Linked)?.staffId

        // Eğitmenin kapsamı personel kimliğine dayanıyor; kimlik yoksa kapsam
        // **tanımsız**. Önceden bu durumda boş metinle karşılaştırma yapılıyordu
        // ve sonuç sessizce boş bir liste oluyordu — "dersin yok" ile "kim
        // olduğunu bilmiyoruz" aynı ekrana çıkıyordu. Artık ekran ayrımı
        // söylüyor (`personelBaglantisiYok`).
        val kendiRandevulari = if (isTrainer && staffId != null) {
            appointments.filter { it.staffId == staffId }
        } else if (isTrainer) {
            emptyList()
        } else {
            appointments
        }

        val filteredAppointments = kendiRandevulari.filter { it.startTimeMs in today }

        val filteredMembers = if (isTrainer) {
            val memberIds = kendiRandevulari.map { it.memberId }.toSet()
            members.filter { it.id in memberIds }
        } else {
            members
        }

        val alerts = mutableListOf<String>()

        // Kritik stok — yalnızca yönetim görür.
        if (!isTrainer) {
            products.forEach { product ->
                val onHand = stockByProduct[product.id] ?: 0
                if (onHand < LOW_STOCK_THRESHOLD) {
                    alerts.add("Kritik Stok: ${product.name} ($onHand adet kaldı)")
                }
            }
        }

        // Uyarılar da **kapsam içindeki** üyelerden üretiliyor.
        //
        // Önceden sayaçlar eğitmene göre süzülüyor ama uyarılar tüm üye
        // listesinden çıkıyordu. Sonuç kendi içinde çelişkili bir ekrandı:
        // "0 aktif üye" yazarken altında salonun tamamının borç listesi
        // duruyordu — üstelik tutarlarıyla. Eğitmene Finans ekranını kapatıp
        // aynı bilgiyi panoda vermek, kısıtı anlamsız kılıyordu.
        val threeDaysLater = now + Periods.daysInMillis(3)
        filteredMembers.forEach { member ->
            val endDate = member.endDateMs ?: return@forEach
            val isActive = Membership.stateOf(member.status, endDate, now) == MembershipState.ACTIVE
            if (isActive && endDate <= threeDaysLater) {
                val formatted = TarihBicimi.gunAy(endDate)
                alerts.add("Üyelik Bitiyor: ${member.fullName} ($formatted)")
            }
        }

        // Bekleyen ödemeler — arşivlenmiş üyeler için uyarı üretme.
        filteredMembers.filter {
            it.paymentStatus == PaymentState.PENDING &&
                Membership.stateOf(it.status, it.endDateMs, now) != MembershipState.PASSIVE
        }.forEach {
            alerts.add("Ödeme Bekliyor: ${it.fullName} (₺${Money(it.pricePaidMinor)})")
        }

        DashboardUiState(
            userRole = userRole,
            totalMembers = filteredMembers.size,
            // Üyelik durumu bitiş tarihinden türetiliyor; süresi dolmuş üye
            // arka plan işi çalışmasa bile "aktif" sayılmıyor.
            activeMembers = filteredMembers.count {
                Membership.stateOf(it.status, it.endDateMs, now) == MembershipState.ACTIVE
            },
            dailyAppointments = filteredAppointments,
            // Ekrana **kapsam içindeki** üyeler veriliyor. Önceden sayaçlar
            // süzülmüş listeden, listenin kendisi süzülmemiş olandan geliyordu.
            members = filteredMembers,
            staffList = staff,
            criticalAlerts = alerts,
            personelBaglantisiYok = isTrainer && staffLink is StaffLink.Unlinked,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true)
    )
}
