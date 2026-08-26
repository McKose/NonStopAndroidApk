plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

/**
 * Ortak arayüz: ekranlar, tema ve (i3'ten itibaren) gezinme.
 *
 * Ekranlar Android'de Jetpack Compose ile yazılmıştı; Compose Multiplatform
 * aynı paket adlarını kullandığı için buraya taşınırken import'lar değişmiyor.
 * Modül üç yerde çalışıyor:
 *
 *   - Android: `app` bu modüle bağlanacak (i3)
 *   - iOS:     `iosApp` kabuğu framework üzerinden açacak (i4)
 *   - JVM:     `masaustu` kabuğu pencere olarak açıyor — iOS simülatörü
 *              olmayan makinelerde ekranları görmenin yolu bu
 *
 * ### Bu modülde ne YOK
 * Veri erişimi ve iş kuralı yok — onlar `shared`'da ve orada kalıyor. Bu ayrım
 * bilinçli: arayüzü üç platformda sınamak ucuz, iş kuralını üç platformda
 * sınamak pahalı.
 *
 * ### İki katman, tek modül (i3e)
 * Modül artık yalnızca ekranlardan oluşmuyor; ekran modelleri (ViewModel) de
 * burada. State hoisting bozulmadı — EKRANLAR hâlâ durumu parametre olarak
 * alıyor ve hiçbir ViewModel çağırmıyor; görüntü testlerinin çalışabilmesinin
 * sebebi de bu. Ekran modeli o durumu ÜRETEN ayrı katman, tüketen değil.
 *
 * Ekran modellerinin `shared`'a değil buraya konmasının sebebi: `shared`'ın
 * Compose bağımlılığı yok ve olmamalı (veri katmanı orada), ekran modelleri ise
 * Compose'a bağlı (`ViewModel`, ve Ayarlar'da `mutableStateOf`).
 */
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    jvm()

    // iOS hedefleri i1'de yalnızca DERLENİYOR (klib) — framework'e i4'te,
    // iOS kabuğu gelince bağlanacak. Derlemeyi şimdiden kurmanın sebebi,
    // Compose Multiplatform'un Kotlin/Native tarafındaki bir uyumsuzluğu
    // 13 ekran taşındıktan sonra değil, ilk PR'da görmek.
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain.dependencies {
            // Ekranlar varlıkları (`PackageEntity`), para/tarih biçimlendirmesini
            // ve etiketleri (`labelTr`) buradan alıyor. `api` DEĞİL
            // `implementation`: kabuklar (app, masaustu, iosApp) `shared`'a
            // zaten kendileri bağlanıyor — ikinci bir yol açmak, bağımlılığın
            // nereden geldiğini belirsizleştirirdi.
            implementation(project(":shared"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            // Ekranlar 52 yerde Material ikonu kullanıyor ve bu artefaktın
            // multiplatform'da yaşayıp yaşamadığı bilinen bir risk
            // (bkz. docs/ios-plani.md, madde 2). Giriş ekranındaki tek ikon
            // o riskin turnusolü: tutmuyorsa ilk PR'da, tek dosyada görülür.
            implementation(compose.materialIconsExtended)

            // Ekran modelleri (i3e). `ViewModel` + `viewModelScope` buradan
            // geliyor; `api` DEĞİL çünkü kabukların ekran modellerini doğrudan
            // görmesi gerekmiyor — gezinme grafiği de bu modülde (i3e-2).
            implementation(libs.jb.lifecycle.viewmodel.compose)

            // Ekran modellerinin Koin tanımları (`ekranModelleriModulu`) bu
            // modülde yaşıyor: tanım ile kurucu yan yana durunca ikisi
            // birbirinden sapamıyor. Repository ve platform bağlamaları
            // kabuklarda kalıyor.
            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
        }

        // Görüntü testi: ekran gerçek Skia ile ekransız (headless) çiziliyor
        // ve PNG olarak kaydediliyor. Pencere ya da X sunucusu GEREKMİYOR —
        // CI'ın Linux koşucusunda da, bu geliştirme ortamında da koşuyor.
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.desktop.currentOs)
        }
    }
}

android {
    namespace = "com.gymapp.arayuz"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

/**
 * Düşen testin yığın izini CI günlüğüne bas.
 *
 * Gradle varsayılanı yalnızca istisnanın TÜRÜNÜ ve satır numarasını yazıyor.
 * `ClassNotFoundException` gibi hatalarda tek işe yarar bilgi mesajın kendisi
 * (eksik sınıfın adı) ve o mesaj kayboluyor. Ayrıntı yalnızca HTML raporunda
 * duruyor, o da koşucuyla birlikte siliniyor — bir hata bu yüzden günlükten
 * teşhis edilemedi.
 */
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
        showCauses = true
    }
}
