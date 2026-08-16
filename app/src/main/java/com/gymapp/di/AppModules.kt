package com.gymapp.di

import com.gymapp.data.local.db.GymDatabase
import com.gymapp.data.local.db.createGymDatabase
import com.gymapp.data.local.preferences.AppPreferences
import com.gymapp.data.sync.ArkaPlanSenkronizasyonu
import com.gymapp.data.repository.AppointmentRepository
import com.gymapp.data.repository.FinanceRepository
import com.gymapp.data.repository.LedgerRepository
import com.gymapp.data.repository.MemberRepository
import com.gymapp.data.repository.PackageRepository
import com.gymapp.data.repository.ProductRepository
import com.gymapp.data.repository.StaffRepository
import com.gymapp.data.sync.SyncQueue
import com.gymapp.presentation.calendar.CalendarViewModel
import com.gymapp.presentation.dashboard.DashboardViewModel
import com.gymapp.presentation.finance.FinanceViewModel
import com.gymapp.presentation.login.LoginViewModel
import com.gymapp.presentation.market.MarketViewModel
import com.gymapp.presentation.market.OrderHistoryViewModel
import com.gymapp.presentation.members.MemberViewModel
import com.gymapp.presentation.packages.PackageViewModel
import com.gymapp.presentation.settings.PersonnelViewModel
import com.gymapp.presentation.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
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
 * Repository'ler, tercihler ve ekran modelleri.
 *
 * `singleOf` / `viewModelOf` kurucu imzasını yansıtarak bağlar: yeni bir bağımlılık
 * eklendiğinde burada ayrıca elle geçirmek gerekmiyor, dolayısıyla liste ile
 * kurucular birbirinden sapamıyor.
 */
val appModule = module {
    single { AppPreferences(androidContext()) }
    single { ArkaPlanSenkronizasyonu(androidContext()) }

    singleOf(::LedgerRepository)
    singleOf(::MemberRepository)
    singleOf(::PackageRepository)
    singleOf(::StaffRepository)
    singleOf(::ProductRepository)
    singleOf(::AppointmentRepository)
    singleOf(::FinanceRepository)

    // Gönderim kuyruğu: repository'ler değişen satırları buraya yazacak (Faz 4).
    singleOf(::SyncQueue)

    viewModelOf(::MemberViewModel)
    viewModelOf(::CalendarViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::MarketViewModel)
    viewModelOf(::OrderHistoryViewModel)
    viewModelOf(::PackageViewModel)
    viewModelOf(::PersonnelViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::FinanceViewModel)
}
