plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Platformdan bağımsız iş kuralları **ve** veri katmanı.
 *
 * Android uygulaması ve ileride yazılacak iOS uygulaması aynı domain kodunu,
 * aynı şemayı ve aynı sorguları kullanır. İki platformda ayrı yazılsalardı
 * kaçınılmaz olarak saparlardı — bu projede düzeltilen hataların önemli bir
 * kısmı (kesir/yüzde karışması, `-1` sentinel'i, serbest metin durum değerleri)
 * zaten bu tür sapmalardı.
 *
 * Platforma özgü kalan tek şeyler: UUID üretimi ve veritabanı dosyasının
 * açılması; ikisi de `expect`/`actual` ile ayrılmış durumda.
 */
kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    /**
     * Masaüstü JVM hedefi — **uygulama için değil, veritabanı testleri için**.
     *
     * Room sorgularının ve transaction davranışının doğruluğu ancak gerçek bir
     * SQLite üzerinde koşarak doğrulanabiliyor. Bunu Android birim testlerinde
     * yapmak mümkün değil: `sqlite-bundled`'ın Android sürümü yerel kütüphaneleri
     * APK içinde taşıyor ve masaüstü JVM'de yüklenemiyor. iOS simülatöründe
     * koşmak ise mümkün ama macOS koşucusu gerektirir ve dakika kotasından 10 kat
     * düşer — her dilimde koşacak testler için kabul edilemez.
     *
     * JVM hedefinde `sqlite-bundled` kendi yerel kütüphanesiyle geliyor,
     * dolayısıyla testler Linux koşucusunda 1x ücretle koşuyor.
     */
    jvm()

    // Kotlin/Native hedefleri: gerçek cihaz (arm64) ve simülatör (Apple Silicon + Intel).
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "GymShared"
            // Statik framework: Xcode tarafında ek gömme adımı gerektirmez.
            isStatic = true
        }
    }

    sourceSets {
        // NOT: android + jvm için elle bir ara kaynak kümesi (`dependsOn`)
        // kurulmamalı. Elle eklenen her `dependsOn` kenarı Kotlin'in varsayılan
        // hiyerarşi şablonunu tamamen devre dışı bırakıyor; o şablon da
        // `iosMain`'i üç iOS hedefine bağlayan şey. Denendi: `randomUuid`
        // karşılığı tek yere indi ama iOS karşılıkları hiçbir hedefe bağlanmadı.
        // Tek satırlık tekrar bu bedelden ucuz.
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            api(libs.koin.core)
            // Repository'ler dışarıya `Flow` döndürüyor; tip imzada göründüğü
            // için `api`. `implementation` olsaydı uygulama tarafı bu tipi
            // yalnızca başka bir bağımlılığın tesadüfen getirmesi sayesinde
            // görürdü.
            api(libs.kotlinx.coroutines.core)
            // Entity ve DAO tipleri modül dışına açıldığı için `api`.
            api(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
        }

        // HTTP motoru platforma özgü: Android'de OkHttp, iOS'ta Darwin (NSURLSession),
        // testlerin koştuğu JVM'de yine OkHttp.
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        jvmMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        // Veritabanı testleri yalnızca JVM'de: gerçek SQLite gerektiriyorlar ve
        // ortak kaynak kümesine konsalardı iOS testlerinde de koşup macOS
        // koşucusunu 10x ücretle meşgul ederlerdi.
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            // Sahte HTTP motoru: durum kodu -> PushResult eşlemesini ağ olmadan,
            // her kodu tek tek vererek sınamak için.
            implementation(libs.ktor.client.mock)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

/**
 * Room'un kod üretimi her hedef için ayrı çalışır; ortak kaynak kümesine bir kez
 * eklemek yetmiyor.
 */
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosX64", libs.androidx.room.compiler)
}

android {
    namespace = "com.gymapp.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
