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

// ---------------------------------------------------------------------------
// Sürüm: etiketten türer, elle yazılmaz
// ---------------------------------------------------------------------------
// `versionCode` ve `versionName` sabit yazılıydı (1 / "1.0"). Dağıtımda bu iki
// sessiz hataya açık: aynı `versionCode` ile ikinci bir sürüm yayınlamak
// mümkün değil (mağaza reddeder, cihaz güncellemeyi görmez) ve elle bumping
// unutulduğunda hiçbir şey şikâyet etmez.
//
// Artık ikisi de yayın etiketinden geliyor: `-PsurumAdi=1.2.0`. Yani APK'nın
// içindeki sürüm ile yayınlanan etiket aynı kaynaktan; ayrışmaları mümkün değil.
//
// Etiket verilmediğinde (günlük derlemeler, PR'lar) sürüm bilinçli olarak
// "0.0.0-gelistirme": yayın olmadığı APK'nın kendisinden anlaşılıyor.
val surumAdi: String =
    (findProperty("surumAdi") as String?)?.trim()?.takeIf { it.isNotEmpty() }
        ?: "0.0.0-gelistirme"

/**
 * `X.Y.Z` → tek artan tam sayı: `X*10000 + Y*100 + Z`.
 *
 * Ara sürüm numaraları 100'ün altında kaldığı sürece sıralama korunuyor
 * (1.2.0 → 10200, 1.2.1 → 10201, 1.3.0 → 10300). Sınır aşılırsa derleme
 * düşüyor: sessizce geriye giden bir `versionCode` üretmek, güncellemenin
 * cihazda hiç görünmemesi demek.
 *
 * Biçim tutmuyorsa da düşüyor. Yayın etiketi verilmişse onu okuyamamak
 * yayınlanan sürümle APK'nın içindeki sürümün ayrışması riskidir; varsayılana
 * dönmek o riski sessizce almak olurdu.
 */
fun surumKoduHesapla(ad: String): Int {
    if (ad == "0.0.0-gelistirme") return 1

    val parcalar = ad.split(".")
    require(parcalar.size == 3) {
        "surumAdi 'X.Y.Z' biçiminde olmalı, alınan: '$ad'"
    }
    val (buyuk, kucuk, yama) = parcalar.map { parca ->
        parca.toIntOrNull() ?: throw GradleException(
            "surumAdi parçaları sayı olmalı, alınan: '$ad'"
        )
    }
    require(kucuk < 100 && yama < 100) {
        "surumAdi ara numaraları 100'ün altında olmalı (aksi hâlde versionCode " +
            "sıralaması bozulur), alınan: '$ad'"
    }
    require(buyuk >= 0 && kucuk >= 0 && yama >= 0) {
        "surumAdi negatif olamaz, alınan: '$ad'"
    }

    val kod = buyuk * 10_000 + kucuk * 100 + yama
    // Android `versionCode`u en az 1 olmalı. Biçim süzgecinden geçen ama sıfır
    // üreten tek değer "0.0.0"; yayın sürümü olarak anlamı da yok.
    require(kod >= 1) {
        "surumAdi en az '0.0.1' olmalı ('0.0.0' geçerli bir versionCode üretmiyor)"
    }
    return kod
}

// ---------------------------------------------------------------------------
// Yayın imzalama anahtarı
// ---------------------------------------------------------------------------
// Anahtar depoda DEĞİL. İki kaynaktan okunuyor:
//   - CI: ortam değişkenleri (depo gizli anahtarlarından geliyor)
//   - geliştirici makinesi: `keystore.properties` (.gitignore'da)
//
// Eksikse yayın yapısı **imzasız** kalıyor ve dosya adı bunu söylüyor
// (`app-release-unsigned.apk`). Hata ayıklama anahtarına düşmek bilinçli olarak
// YAPILMIYOR: debug anahtarıyla imzalı bir "yayın" APK'sı kurulur, çalışır ve
// yayın gibi görünür — ama gerçek yayın anahtarıyla bir daha asla
// güncellenemez. Sessiz ve geri dönüşü olmayan bir hata olurdu.
val imzaAyarlari = Properties().apply {
    val dosya = rootProject.file("keystore.properties")
    if (dosya.exists()) dosya.inputStream().use { load(it) }
}

fun imzaDegeri(ortamAdi: String, dosyaAnahtari: String): String? =
    (System.getenv(ortamAdi) ?: imzaAyarlari.getProperty(dosyaAnahtari))
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

val yayinAnahtarYolu = imzaDegeri("YAYIN_KEYSTORE_YOLU", "storeFile")
val yayinAnahtarSifresi = imzaDegeri("YAYIN_KEYSTORE_SIFRESI", "storePassword")
val yayinAnahtarAdi = imzaDegeri("YAYIN_ANAHTAR_ADI", "keyAlias")
val yayinAnahtarAnahtarSifresi = imzaDegeri("YAYIN_ANAHTAR_SIFRESI", "keyPassword")

// Eksik olanların listesi **yapılandırma zamanında** hesaplanıyor.
//
// `org.gradle.configuration-cache=true` açık: görev gövdesinde (`doFirst`)
// `file()` gibi bir `Project` üyesini çağırmak yapılandırma önbelleğini bozar
// ("invocation of 'Task.project' at execution time is unsupported"). Bu yüzden
// görevin içine yalnızca hazır bir metin listesi taşınıyor.
val yayinImzaEksikleri: List<String> = buildList {
    if (yayinAnahtarYolu == null) add("YAYIN_KEYSTORE_YOLU")
    if (yayinAnahtarSifresi == null) add("YAYIN_KEYSTORE_SIFRESI")
    if (yayinAnahtarAdi == null) add("YAYIN_ANAHTAR_ADI")
    if (yayinAnahtarAnahtarSifresi == null) add("YAYIN_ANAHTAR_SIFRESI")
    if (yayinAnahtarYolu != null && !file(yayinAnahtarYolu).exists()) {
        add("anahtar dosyası bulunamadı: $yayinAnahtarYolu")
    }
}

val yayinImzasiVar: Boolean = yayinImzaEksikleri.isEmpty()

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
        versionCode   = surumKoduHesapla(surumAdi)
        versionName   = surumAdi
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${ayar("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${ayar("supabase.anonKey")}\"")
    }

    // ---------------------------------------------------------------------
    // Hata ayıklama imzası: SABİT ve depoda
    // ---------------------------------------------------------------------
    // Varsayılan davranışta AGP, `~/.android/debug.keystore` yoksa kendisi
    // üretiyor. Geliştiricinin makinesinde bu dosya bir kez üretilip kalıyor,
    // ama CI koşucusu her tur sıfırdan başlıyor: her koşu **farklı** bir imza
    // üretiyordu.
    //
    // Sonucu telefonda görünüyor: yeni APK'yı eskisinin üzerine kurmak
    // `INSTALL_FAILED_UPDATE_INCOMPATIBLE` ile düşüyor ve tek çıkış yolu
    // kaldırıp yeniden kurmak — o da **uygulama verisini siliyor**. Yani iki CI
    // koşusu arasında veritabanı göçünü telefonda denemek mümkün değildi:
    // denenecek eski veri her kurulumda yok oluyordu.
    //
    // Anahtar gizli değil ve gizli olması da beklenmiyor: yalnızca hata ayıklama
    // yapısını imzalıyor, Play Store'a hiçbir şey yüklemiyor ve şifresi
    // Android'in kendi varsayılanıyla aynı ("android"). Yayın imzası buna
    // bağlanmayacak; o anahtar depoya değil depo gizli anahtarlarına girer.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        // Yalnızca anahtar gerçekten varsa tanımlanıyor. Boş değerlerle
        // tanımlanmış bir imza yapılandırması, derlemeyi anlaşılmaz bir
        // "keystore password must not be null" hatasıyla düşürürdü.
        if (yayinImzasiVar) {
            create("yayin") {
                storeFile = file(yayinAnahtarYolu!!)
                storePassword = yayinAnahtarSifresi
                keyAlias = yayinAnahtarAdi
                keyPassword = yayinAnahtarAnahtarSifresi
            }
        }
    }

    buildTypes {
        release {
            // R8 bilinçli olarak kapalı. Açmak Room/Koin/kotlinx.serialization
            // için kural yazmayı gerektiriyor ve eksik bir kural çalışma
            // zamanında, yalnızca yayın yapısında patlıyor — yani en pahalı
            // yerde. Açılacaksa kendi doğrulama turuyla açılmalı.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Yoksa `signingConfig` atanmıyor: AGP çıktıyı
            // `app-release-unsigned.apk` diye adlandırıyor ve imzasız olduğu
            // dosya adından anlaşılıyor.
            signingConfig = if (yayinImzasiVar) signingConfigs.getByName("yayin") else null
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

// Yayın yapısı derlenirken imza durumu yazılıyor.
//
// Yapılandırma sırasında değil, yalnızca `assembleRelease` koşarken: her
// `./gradlew` çağrısında uyarı basmak, uyarıyı okunmaz hâle getirir. İmzasız bir
// APK üretildiğinde bunun sebebi görünür olmalı — dosya adı da söylüyor ama
// sebebini (hangi değerin eksik olduğunu) yalnızca burası söyleyebilir.
// Görevin içine yalnızca hazır metinler taşınıyor (yapılandırma önbelleği için).
val imzaDurumMesaji: String = if (yayinImzasiVar) {
    "Yayın imzası: VAR (anahtar: $yayinAnahtarAdi), sürüm $surumAdi"
} else {
    """
    UYARI: yayın imzası YOK — çıktı `app-release-unsigned.apk` olacak.
      Eksik: ${yayinImzaEksikleri.joinToString(", ")}
      Hata ayıklama anahtarına bilinçli olarak DÜŞÜLMÜYOR: debug anahtarıyla
      imzalı bir yayın APK'sı gerçek yayın anahtarıyla bir daha güncellenemez.
      Kurulum: docs/yayin.md
    """.trimIndent()
}
val imzaVarMi: Boolean = yayinImzasiVar

tasks.matching { it.name == "assembleRelease" }.configureEach {
    doFirst {
        if (imzaVarMi) logger.lifecycle(imzaDurumMesaji) else logger.warn(imzaDurumMesaji)
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

    // Ortak arayüz (Compose Multiplatform). Ekranlar buraya taşındıkça `app`
    // inceliyor; sonunda geriye yalnızca Android kabuğu kalacak (MainActivity,
    // GymApplication, WorkManager dikişi).
    // Gezinme grafiği de artık burada (i3e-2): `androidx.navigation` doğrudan
    // bağımlılık olmaktan çıktı, :arayuz `api` ile getiriyor.
    implementation(project(":arayuz"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
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
    // Yalnızca `androidContext()` ve `by inject()` için. Compose tarafındaki
    // `koinViewModel()` artık :arayuz'da ve ortak `koin-compose-viewmodel`
    // kullanıyor, dolayısıyla `koin-androidx-compose` KALDIRILDI.
    implementation(libs.koin.android)
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