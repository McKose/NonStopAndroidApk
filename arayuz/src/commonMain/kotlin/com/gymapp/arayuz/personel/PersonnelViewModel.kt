package com.gymapp.arayuz.personel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.data.auth.CurrentUser
import com.gymapp.data.repository.StaffRepository
import com.gymapp.data.sync.SyncTable
import com.gymapp.domain.Money
import com.gymapp.domain.Rate
import com.gymapp.domain.StaffRole
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Bir kez tüketilen kullanıcı bildirimleri. */
sealed interface PersonnelEvent {
    data object Saved : PersonnelEvent
    data object Deleted : PersonnelEvent
    data class Failed(val message: String) : PersonnelEvent
}

class PersonnelViewModel(
    private val repository: StaffRepository,
    currentUser: CurrentUser,
) : ViewModel() {

    val staffList: Flow<List<StaffEntity>> = repository.getAllStaff()

    /**
     * Bu kullanıcı personel kaydı yazabilir mi?
     *
     * Sunucudaki kural personel tablosunu yalnızca salon sahibine açıyor
     * (migrasyon `0004`). Burada da kontrol edilmesinin sebebi, yetkisiz
     * kullanıcının işi **yapıp** sonucu ancak senkronizasyon turunda öğrenmesini
     * önlemek: kayıt yerelde başarılı görünür, kuyruğa girer ve sunucudan kalıcı
     * 403 ile döner. Yani kayıp değil ama kullanıcı açısından sessiz.
     *
     * Rol oturumdan geliyor (`gym_users.role`), cihazdaki bir tercihten değil —
     * ve **tepkili**: önceki hâli ViewModel kurulurken bir kez okunuyordu.
     * Toplayan yokken de doğru olması gerektiği için `Eagerly`; başlangıç
     * değeri en dar yetki.
     */
    val canWrite: StateFlow<Boolean> = currentUser.role
        .map { SyncTable.STAFF.isWritableBy(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _events = Channel<PersonnelEvent>(Channel.BUFFERED)
    val events: Flow<PersonnelEvent> = _events.receiveAsFlow()

    /**
     * Personel ekler veya günceller.
     *
     * Sonuç **her zaman** bildirilir; önceki sürümde diyalog listenin uzunluğu
     * değişince kapanıyordu, dolayısıyla güncelleme başarılı olsa da kapanmıyor,
     * başarısız ekleme ise fark edilmiyordu.
     *
     * @param staffId `null` ise yeni personel.
     * @param commissionPercent hakediş **yüzdesi** (40 = %40); baz puana dönüşüm
     *        [Rate] içinde tek noktada yapılır.
     */
    fun saveStaff(
        staffId: String? = null,
        name: String,
        title: String,
        branch: String,
        commissionPercent: Double,
        salary: Double,
        phone: String,
        nickname: String,
        role: StaffRole,
        authUserId: String? = null,
        isActive: Boolean = true,
    ) {
        // Ekranda düğme gizleniyor ama kontrol burada da var: gizlenmiş bir
        // düğme kural değil, yalnızca görüntü. Yeni bir çağrı yolu eklendiğinde
        // (ör. derin bağlantı, toplu içe aktarma) yalnızca ekrana bakan bir
        // koruma sessizce atlanırdı.
        if (!canWrite.value) {
            viewModelScope.launch { _events.send(PersonnelEvent.Failed(YETKI_YOK)) }
            return
        }

        viewModelScope.launch {
            repository.saveStaff(
                staffId = staffId,
                fullName = name,
                title = title,
                role = role,
                branch = branch,
                commissionBasisPoints = Rate.ofPercent(commissionPercent).basisPoints,
                monthlySalary = Money.ofMajor(salary),
                phone = phone,
                nickname = nickname,
                authUserId = authUserId?.takeIf { it.isNotBlank() },
                isActive = isActive,
            ).fold(
                onSuccess = { _events.send(PersonnelEvent.Saved) },
                onFailure = {
                    _events.send(PersonnelEvent.Failed(it.message ?: "Personel kaydedilemedi."))
                },
            )
        }
    }

    fun deleteStaff(staffId: String) {
        if (!canWrite.value) {
            viewModelScope.launch { _events.send(PersonnelEvent.Failed(YETKI_YOK)) }
            return
        }
        viewModelScope.launch {
            repository.deleteStaff(staffId).fold(
                onSuccess = { _events.send(PersonnelEvent.Deleted) },
                onFailure = {
                    _events.send(PersonnelEvent.Failed(it.message ?: "Personel silinemedi."))
                },
            )
        }
    }

    private companion object {
        const val YETKI_YOK = "Personel kaydını yalnızca salon sahibi değiştirebilir."
    }
}
