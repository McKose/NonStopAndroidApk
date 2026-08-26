plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

/**
 * Masaüstü kabuğu — ortak arayüzü pencere olarak açar.
 *
 * Varlık sebebi test: iOS simülatörü yalnızca macOS'ta çalışıyor; geliştirme
 * Linux'ta, kullanıcı Windows'ta yapılıyor ve ikisinde de simülatör yok.
 * Ekranları GÖRMENİN yolu bu modül:
 *
 *     ./gradlew :masaustu:run          (Windows'ta: gradlew.bat :masaustu:run)
 *
 * Masaüstü, iOS'un birebir kanıtı değil — dokunma, güvenli alanlar ve yaşam
 * döngüsü farklı. Ama "ekran çiziliyor mu, akış çalışıyor mu" sorusunun
 * çoğunu sıfır maliyetle yanıtlıyor; kalanı gerçek cihazda TestFlight ile.
 */
kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":arayuz"))
            // i4a'dan beri ŞART: kabuk artık Koin grafiğini kendisi kuruyor ve
            // bunun için `shared`ın tiplerine doğrudan dokunuyor
            // (`createGymDatabase`, `JvmTercihler`, `SessionManager`,
            // `cekirdekModul`, ...). `arayuz` `shared`ı `implementation` ile
            // tutuyor, yani buraya sızdırmıyor — sızdırmaması da doğru: kabuk
            // kendi kullandığı bağımlılığı kendisi bildirmeli. Aynı gerekçe
            // aşağıdaki material3 satırında da yazılı.
            implementation(project(":shared"))
            implementation(compose.desktop.currentOs)
            // `desktop.currentOs` material3'ü GETİRMİYOR (eski Material'i
            // getiriyor) ve `arayuz`'un material3'ü `implementation` olduğu
            // için buraya sızmıyor — sızmaması da doğru: kabuk kendi
            // kullandığı bağımlılığı kendisi bildirmeli. Main.kt tema ve
            // Surface için material3 kullanıyor.
            implementation(compose.material3)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.gymapp.masaustu.MainKt"
    }
}
