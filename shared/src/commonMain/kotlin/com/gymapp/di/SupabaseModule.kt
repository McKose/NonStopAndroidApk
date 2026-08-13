package com.gymapp.di

import com.gymapp.data.auth.AuthApi
import com.gymapp.data.auth.InMemorySessionStore
import com.gymapp.data.auth.SessionManager
import com.gymapp.data.auth.SessionStore
import com.gymapp.data.auth.SupabaseAuthApi
import com.gymapp.data.auth.TenantProvider
import com.gymapp.data.sync.AccessTokenProvider
import com.gymapp.data.sync.DisabledRemoteDataSource
import com.gymapp.data.sync.LocalRowPayloadProvider
import com.gymapp.data.sync.RemoteDataSource
import com.gymapp.data.sync.RowPayloadProvider
import com.gymapp.data.sync.SupabaseConfig
import com.gymapp.data.sync.MissingConfigAuthApi
import com.gymapp.data.sync.SupabaseRemoteDataSource
import com.gymapp.data.sync.SyncEngine
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Sunucu bağlantısının ve oturumun bağlanması.
 *
 * Ortak modülde duruyor, platform tarafında değil: burada bağlanan şeylerin
 * hiçbiri Android'e ya da iOS'a özgü değil ve iOS uygulaması aynı grafiği
 * kullanacak. Platform tarafına kalan tek şey ayarları okumak.
 *
 * ### Ayar yoksa ne oluyor
 * [url] ve [anonKey] `local.properties`ten geliyor ve depoya işlenmiyor;
 * projeyi ilk kez klonlayan birinde boşlar. O durumda grafik yine kuruluyor ama
 * kimlik doğrulama ve gönderim uçları yerine ne yapılması gerektiğini söyleyen
 * karşılıkları bağlanıyor. Alternatif — eksik ayarda hata fırlatmak — uygulamayı
 * açılışta düşürürdü ve sebep yığın izinde kalırdı.
 *
 * ### Oturum neden tek nesne
 * [SessionManager] hem [AccessTokenProvider] hem [TenantProvider] olarak
 * bağlanıyor ve **aynı** örnek. İki ayrı örnek olsaydı biri giriş yapmış diğeri
 * yapmamış olurdu: gönderim jetonu bulur, satırlar salonsuz yazılırdı.
 */
fun supabaseModule(url: String, anonKey: String): Module {
    val config = SupabaseConfig.orNull(url, anonKey)

    return module {
        // Motor derleme sırasında değil çalışma zamanında seçiliyor: Android'de
        // OkHttp, iOS'ta Darwin. İkisi de `:shared` tarafında bağımlılık olarak
        // duruyor, dolayısıyla burada hedefe özgü bir şey yazmak gerekmiyor.
        single { HttpClient() }

        single<SessionStore> { InMemorySessionStore() }

        single<AuthApi> {
            if (config == null) MissingConfigAuthApi() else SupabaseAuthApi(config, get())
        }

        single { SessionManager(authApi = get(), store = get()) }
        single<AccessTokenProvider> { get<SessionManager>() }
        single<TenantProvider> { get<SessionManager>() }

        single<RowPayloadProvider> { LocalRowPayloadProvider(get()) }

        single<RemoteDataSource> {
            if (config == null) {
                DisabledRemoteDataSource()
            } else {
                SupabaseRemoteDataSource(
                    config = config,
                    httpClient = get(),
                    tokens = get(),
                    payloads = get(),
                )
            }
        }

        single { SyncEngine(outboxDao = get(), remote = get(), tenants = get()) }
    }
}
