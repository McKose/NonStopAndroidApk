plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

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
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}