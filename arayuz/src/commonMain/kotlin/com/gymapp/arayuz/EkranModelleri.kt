package com.gymapp.arayuz

import com.gymapp.arayuz.ayarlar.SettingsViewModel
import com.gymapp.arayuz.finans.FinanceViewModel
import com.gymapp.arayuz.giris.LoginViewModel
import com.gymapp.arayuz.market.MarketViewModel
import com.gymapp.arayuz.market.OrderHistoryViewModel
import com.gymapp.arayuz.paketler.PackageViewModel
import com.gymapp.arayuz.pano.DashboardViewModel
import com.gymapp.arayuz.personel.PersonnelViewModel
import com.gymapp.arayuz.takvim.CalendarViewModel
import com.gymapp.arayuz.uyeler.MemberViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Ekran modellerinin bağlamaları — **ortak**, yani Android ve iOS aynı listeyi
 * kullanıyor.
 *
 * Daha önce bu on satır `app/di/AppModules.kt` içindeydi. Sınıflar ortak modüle
 * taşınınca liste de taşındı; aksi hâlde iOS kabuğu aynı on satırı kendi tarafında
 * yeniden yazmak zorunda kalır ve iki kopya zamanla birbirinden sapardı — yeni bir
 * ekran modeli eklendiğinde biri güncellenip diğeri unutulurdu ve hata yalnızca
 * o platformda, çalışma anında `NoDefinitionFoundException` olarak görünürdü.
 *
 * ### Burada ne YOK
 * Repository'ler, tercihler, veritabanı ve arka plan senkronizasyonu burada
 * değil. Bir kısmı platforma özgü (`AndroidTercihler` uygulama context'i,
 * `WorkManagerSenkronizasyonu` WorkManager istiyor), bir kısmı da arayüzün işi
 * değil. Kabuk kendi modülünü kurup bunu `includes(...)` ile ekliyor.
 *
 * `viewModelOf` kurucu imzasını yansıtarak bağlar: bir ekran modeline yeni bir
 * bağımlılık eklendiğinde burada ayrıca elle geçirmek gerekmiyor, dolayısıyla
 * liste ile kurucular birbirinden sapamıyor.
 */
val ekranModelleriModulu: Module = module {
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
