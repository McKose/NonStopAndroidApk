plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

/**
 * Platformdan bağımsız iş kuralları.
 *
 * Android uygulaması ve iOS uygulaması **aynı** domain kodunu kullanır: para
 * aritmetiği, fiyatlandırma, üyelik durumu, hakediş oranı ve doğrulamalar burada
 * bir kez yazılır. İki platformda ayrı yazılsalardı kaçınılmaz olarak birbirinden
 * saparlardı — bu projede tam olarak bu tür sapmalar (kesir/yüzde karışması,
 * `-1` sentinel'i, serbest metin durum değerleri) düzeltildi.
 *
 * Bu modülün **hiçbir** Android/iOS API'sine bağımlılığı yok; platforma özgü tek
 * şey UUID üretimi ve o da `expect`/`actual` ile ayrılmış durumda.
 */
kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
                }
            }
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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.gymapp.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
