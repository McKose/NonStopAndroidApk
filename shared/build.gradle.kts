plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
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
