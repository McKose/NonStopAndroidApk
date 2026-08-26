package com.gymapp.di

import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.db.createGymDatabase
import com.gymapp.data.auth.CurrentUser
import com.gymapp.data.auth.SessionManager
import com.gymapp.data.local.preferences.AndroidTercihler
import com.gymapp.data.local.preferences.AppPreferences
import com.gymapp.data.sync.ArkaPlanSenkronizasyonu
import com.gymapp.data.sync.WorkManagerSenkronizasyonu
import com.gymapp.data.repository.AppointmentRepository
import com.gymapp.data.repository.FinanceRepository
import com.gymapp.data.repository.LedgerRepository
import com.gymapp.data.repository.MemberRepository
import com.gymapp.data.repository.PackageRepository
import com.gymapp.data.repository.ProductRepository
import com.gymapp.data.repository.StaffRepository
import com.gymapp.data.sync.SyncQueue
import com.gymapp.arayuz.ekranModelleriModulu
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Veritabanı ve DAO'lar.
 *
 * Veritabanının **kurulumu** burada değil, `:shared` modülünde: şema, sorgular ve
 * sürücü ayarları iki platformda ortak. Burada kalan yalnızca Android tarafındaki
 * bağlama — tekil örnek ve uygulama context'i.
 *
 * DAO'lar tek tek tanımlanıyor çünkü tüketiciler (repository'ler) veritabanının
 * tamamını değil yalnızca ihtiyaç duydukları DAO'yu alıyor; bağımlılıklar böylece
 * dar kalıyor.
 */
val databaseModule = module {
    single<GymDatabase> { createGymDatabase(androidContext()) }

    single { get<GymDatabase>().memberDao() }
    single { get<GymDatabase>().packageDao() }
    single { get<GymDatabase>().productDao() }
    single { get<GymDatabase>().appointmentDao() }
    single { get<GymDatabase>().staffDao() }
    single { get<GymDatabase>().orderDao() }
    single { get<GymDatabase>().measurementDao() }
    single { get<GymDatabase>().ledgerDao() }
    single { get<GymDatabase>().stockMovementDao() }
    single { get<GymDatabase>().syncOutboxDao() }
}

/**
 * Repository'ler ve Android'e özgü bağlamalar.
 *
 * `singleOf` kurucu imzasını yansıtarak bağlar: yeni bir bağımlılık eklendiğinde
 * burada ayrıca elle geçirmek gerekmiyor, dolayısıyla liste ile kurucular
 * birbirinden sapamıyor.
 *
 * Ekran modelleri artık burada DEĞİL: on `viewModelOf` satırı `:arayuz`
 * modülündeki [ekranModelleriModulu]'ne taşındı ve aşağıda `includes` ile
 * ekleniyor. iOS kabuğu da aynı ortak listeyi ekleyecek, böylece iki platform
 * arasında kopyalanmış bir bağlama listesi oluşmuyor.
 */
val appModule = module {
    includes(ekranModelleriModulu)

    // Arayüze bağlanıyor: ortak modüldeki Ayarlar ekranı ve modeli yalnızca
    // `AppPreferences` arayüzünü görüyor. Gerçekleme platformun işi.
    single<AppPreferences> { AndroidTercihler(androidContext()) }

    /**
     * Oturumdaki rolün ve personel bağlantısının tek kaynağı.
     *
     * `singleOf` yerine elle yazılıyor çünkü kurucu `SessionManager`'ın
     * tamamını değil yalnızca oturum akışını alıyor — dar bağımlılık bilinçli,
     * bkz. `CurrentUser`.
     */
    single { CurrentUser(session = get<SessionManager>().session, staffDao = get()) }
    single<ArkaPlanSenkronizasyonu> { WorkManagerSenkronizasyonu(androidContext()) }

    singleOf(::LedgerRepository)
    singleOf(::MemberRepository)
    singleOf(::PackageRepository)
    singleOf(::StaffRepository)
    singleOf(::ProductRepository)
    singleOf(::AppointmentRepository)
    singleOf(::FinanceRepository)

    // Gönderim kuyruğu: repository'ler değişen satırları buraya yazacak (Faz 4).
    singleOf(::SyncQueue)
}
