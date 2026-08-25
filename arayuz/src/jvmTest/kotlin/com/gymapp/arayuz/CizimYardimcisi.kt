package com.gymapp.arayuz

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import com.gymapp.arayuz.tema.GymTema
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertTrue

/**
 * Ekranı gerçekten çizip PNG'ye yazan yardımcı.
 *
 * i1'de tek ekran için yazılmıştı; ekranlar taşındıkça her testte
 * kopyalanacaktı. Ortak yer burası — kopyalansaydı biri düzeltilip diğerleri
 * unutulur ve testler sessizce farklı şeyler ölçmeye başlardı.
 *
 * `ImageComposeScene` Skia ile **ekransız** çiziyor: pencere, X sunucusu ya da
 * xvfb gerekmiyor, dolayısıyla CI'ın Linux koşucusunda da geliştirme
 * ortamında da aynı şekilde koşuyor.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun ekraniCiz(
    ad: String,
    genislik: Int = 420,
    yukseklik: Int = 880,
    icerik: @Composable () -> Unit,
): File {
    val dizin = File("build/goruntuler").apply { mkdirs() }
    val dosya = File(dizin, "$ad.png")

    ImageComposeScene(width = genislik, height = yukseklik).use { sahne ->
        sahne.setContent {
            GymTema(koyu = true) {
                Surface(color = MaterialTheme.colorScheme.background) { icerik() }
            }
        }
        val png = sahne.render().encodeToData(EncodedImageFormat.PNG)
            ?: error("PNG kodlanamadı")
        dosya.writeBytes(png.bytes)
    }
    return dosya
}

/**
 * "Ekran gerçekten çizildi mi" ölçüsü.
 *
 * Piksel piksel karşılaştırma (golden test) BİLİNÇLİ olarak yapılmıyor: yazı
 * tipi ölçümü platformlar arasında bir-iki piksel oynuyor ve test sürekli
 * yalancı kırmızı verirdi. Ölçülen şey daha kaba ama aradığımız hatayı
 * yakalıyor: boş ya da tek renk bir dikdörtgen birkaç renkte kalır, çizilmiş
 * bir ekran (zemin, kenarlar, yazılar, simgeler) onlarca üretir.
 */
fun cizildiginiDogrula(dosya: File, enAzRenk: Int = 20) {
    val resim = ImageIO.read(dosya) ?: error("PNG geri okunamadı: ${dosya.name}")
    val renkler = HashSet<Int>()
    for (y in 0 until resim.height step 4) {
        for (x in 0 until resim.width step 4) {
            renkler.add(resim.getRGB(x, y))
        }
    }
    assertTrue(
        renkler.size >= enAzRenk,
        "${dosya.name}: yalnızca ${renkler.size} renk var — ekran boş görünüyor",
    )
}
