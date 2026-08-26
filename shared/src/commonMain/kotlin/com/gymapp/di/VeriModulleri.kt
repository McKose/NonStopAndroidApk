package com.gymapp.di

import com.gymapp.data.auth.CurrentUser
import com.gymapp.data.auth.SessionManager
import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.preferences.AppPreferences
import com.gymapp.data.repository.AppointmentRepository
import com.gymapp.data.repository.FinanceRepository
import com.gymapp.data.repository.LedgerRepository
import com.gymapp.data.repository.MemberRepository
import com.gymapp.data.repository.PackageRepository
import com.gymapp.data.repository.ProductRepository
import com.gymapp.data.repository.StaffRepository
import com.gymapp.data.sync.ArkaPlanSenkronizasyonu
import com.gymapp.data.sync.SyncQueue
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Veritabanı ve DAO'lar.
 *
 * Veritabanının kendisi **dışarıdan** geliyor, burada kurulmuyor: açılış yolu
 * platforma özgü. Android'de uygulama context'i gerekiyor, iOS'ta `Documents`
 * dizini, masaüstünde düz bir dosya yolu. Şema, sorgular ve migrasyonlar
 * ortak — onlar `GymDatabase` içinde.
 *
 * DAO'lar tek tek tanımlanıyor çünkü tüketiciler (repository'ler)
 * veritabanının tamamını değil yalnızca ihtiyaç duydukları DAO'yu alıyor;
 * bağımlılıklar böylece dar kalıyor.
 */
fun veritabaniModulu(veritabani: GymDatabase): Module = module {
    single { veritabani }

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
 * Repository'ler ve platform dikişleri.
 *
 * Bu modül **hiçbir platforma bağlı değil**; platforma bağlı olan iki şey
 * ([tercihler] ve [arkaPlan]) parametre olarak geliyor. Kabuk kendi
 * gerçeklemesini üretip veriyor:
 *
 *   Android    AndroidTercihler(context)  WorkManagerSenkronizasyonu(context)
 *   iOS        IosTercihler()             SenkronizasyonYok
 *   Masaüstü   JvmTercihler()             SenkronizasyonYok
 *
 * Bu üç satır daha önce `app/di/AppModules.kt` içindeydi ve `androidContext()`
 * çağırıyordu — yani repository listesi Android'e çakılıydı. iOS kabuğu aynı
 * yedi repository'yi kendi tarafında yeniden bağlamak zorunda kalırdı ve iki
 * liste zamanla birbirinden sapardı: yeni bir repository eklendiğinde biri
 * güncellenir, diğerinde hata ancak çalışma anında
 * `NoDefinitionFoundException` olarak görünürdü.
 *
 * `singleOf` kurucu imzasını yansıtarak bağlar: bir repository'ye yeni bir
 * bağımlılık eklendiğinde burada ayrıca elle geçirmek gerekmiyor.
 */
fun cekirdekModul(
    tercihler: AppPreferences,
    arkaPlan: ArkaPlanSenkronizasyonu,
): Module = module {
    single { tercihler }
    single { arkaPlan }

    /**
     * Oturumdaki rolün ve personel bağlantısının tek kaynağı.
     *
     * `singleOf` yerine elle yazılıyor çünkü kurucu `SessionManager`'ın
     * tamamını değil yalnızca oturum akışını alıyor — dar bağımlılık bilinçli,
     * bkz. `CurrentUser`.
     */
    single { CurrentUser(session = get<SessionManager>().session, staffDao = get()) }

    singleOf(::LedgerRepository)
    singleOf(::MemberRepository)
    singleOf(::PackageRepository)
    singleOf(::StaffRepository)
    singleOf(::ProductRepository)
    singleOf(::AppointmentRepository)
    singleOf(::FinanceRepository)

    // Gönderim kuyruğu: repository'ler değişen satırları buraya yazıyor.
    singleOf(::SyncQueue)
}
