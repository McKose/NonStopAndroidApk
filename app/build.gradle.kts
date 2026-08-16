import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// Sunucu ayarları
// ---------------------------------------------------------------------------
// Proje adresi ve `anon` anahtarı **depoya işlenmiyor**; `local.properties`ten
// okunuyor (o dosya .gitignore'da). Anahtarın kendisi gizli değil — istemcide
// bulunması normaldir ve tek başına hiçbir veriye erişemez, her sorgu giriş
// yapan kullanıcıya göre süzülür. Depoya konmamasının sebebi başka: proje
// adresi ve anahtar kuruluma özgü, kaynak koda değil.
//
// Değerler yoksa derleme **düşmüyor**, boş kalıyor ve uygulama açılışta
// anlaşılır bir hata gösteriyor. Düşürmek, projeyi ilk kez klonlayan birinin
// hiçbir şeyi derleyememesi demek olurdu.
val yerelAyarlar = Properties().apply {
    val dosya = rootProject.file("local.properties")
    if (dosya.exists()) dosya.inputStream().use { load(it) }
}
fun ayar(anahtar: String): String = yerelAyarlar.getProperty(anahtar).orEmpty()

android {
    namespace  = "com.gymapp"
    // Bağımlılık yığınındaki androidx modülleri en az 36'ya karşı derlenmeyi
    // isteyebiliyor; güncel kararlı seviyede kalmak en ucuzu.
    // `targetSdk` bilinçli olarak 35'te kalıyor — compileSdk yalnızca hangi
    // API'lere karşı derlendiğimizi belirler, çalışma zamanı davranışını
    // değiştiren targetSdk'dır.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gymapp"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${ayar("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${ayar("supabase.anonKey")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // `buildConfigField` kullanıldığı için açık: AGP 8'de varsayılan kapalı
        // ve kapalıyken alanlar sessizce üretilmiyor.
        buildConfig = true
    }
}

// `android { kotlinOptions { } }` kullanımdan kalktı; JVM hedefi artık Kotlin
// eklentisinin kendi `compilerOptions` alanından veriliyor.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    // Platformdan bağımsız iş kuralları — iOS uygulaması da aynı modülü kullanacak.
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    // room-ktx kaldırıldı: tek kullanıcısı repository'lerdeki `withTransaction`
    // idi, o da ortak koda taşınırken `GymDatabase.inTransaction` ile
    // değiştirildi. Entity, DAO, veritabanı ve repository'lerin tamamı artık
    // :shared modülünde.
    // Uygulama kapalıyken senkronizasyon. Yalnızca `app` modülünde: iOS
    // karşılığı BGTaskScheduler ve o da kendi platform modülüne girecek.
    // Ortak olan tek şey "tekrar denensin mi" kararı ve o `:shared` içinde.
    implementation(libs.androidx.work.runtime)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    testImplementation(libs.junit)
    // `kotlin.test` — `:shared` zaten bunu kullanıyor, `app` kullanmıyordu.
    // Sebebi basit: `app/src/test` BOŞTU. CI'daki "Uygulama birim testleri"
    // adımı sıfır test koşuyor ve doğal olarak geçiyordu; yeşil olması bir şey
    // sınandığı anlamına gelmiyordu. JUnit4 üzerine oturuyor, ayrı koşucu yok.
    testImplementation(kotlin("test"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}