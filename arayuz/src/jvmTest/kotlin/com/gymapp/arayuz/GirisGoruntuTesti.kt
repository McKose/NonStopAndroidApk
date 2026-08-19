package com.gymapp.arayuz

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import com.gymapp.arayuz.giris.GirisEkrani
import com.gymapp.arayuz.tema.GymTema
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Giriş ekranını GERÇEKTEN çizer ve PNG olarak kaydeder.
 *
 * ### Neden derleme yetmiyor
 * Compose'da derlenen ama çizilmeyen ekran mümkün: kompozisyon anında atılan
 * bir istisna (eksik CompositionLocal, sıfır ölçü, tema dışı kullanım) ancak
 * çalıştırınca görülür. Panel tarafında birebir yaşandı — dört modül eksik
 * yayınlandı, her şey "derli" görünüyordu, sayfa bomboştu.
 *
 * ### Neden pencere yok
 * `ImageComposeScene` Skia ile ekransız çiziyor; X sunucusu, pencere, xvfb
 * gerekmiyor. Bu yüzden test Linux CI koşucusunda ve bu geliştirme ortamında
 * aynı şekilde koşuyor. Üretilen PNG'ler CI'da yapıt olarak yükleniyor —
 * ekranın nasıl göründüğü, çalıştırmadan bakılabilir bir çıktı.
 *
 * ### İddialar bilinçli olarak kaba
 * Piksel piksel karşılaştırma (golden test) YAPILMIYOR: yazı tipi ölçümü
 * platformlar arasında bir-iki piksel oynar ve test sürekli yalancı kırmızı
 * verirdi. Ölçülen üç şey, ekranın "boş ya da tek renk bir dikdörtgen"
 * olmadığını kanıtlamaya yetiyor: yeterince farklı renk, markanın altını,
 * hata durumunda değişen görüntü.
 */
@OptIn(ExperimentalComposeUiApi::class)
class GirisGoruntuTesti {

    private val genislik = 420
    private val yukseklik = 880

    private fun ekraniCiz(ad: String, gonderiliyor: Boolean, hata: String?): File {
        val dizin = File("build/goruntuler").apply { mkdirs() }
        val dosya = File(dizin, "$ad.png")

        ImageComposeScene(width = genislik, height = yukseklik).use { sahne ->
            sahne.setContent {
                GymTema(koyu = true) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    ) {
                        GirisEkrani(
                            gonderiliyor = gonderiliyor,
                            hata = hata,
                            onGiris = { _, _ -> },
                        )
                    }
                }
            }
            val goruntu = sahne.render()
            val png = goruntu.encodeToData(EncodedImageFormat.PNG)
                ?: error("PNG kodlanamadı")
            dosya.writeBytes(png.bytes)
        }
        return dosya
    }

    @Test
    fun `giris ekrani ciziliyor ve bos degil`() {
        val dosya = ekraniCiz("giris", gonderiliyor = false, hata = null)
        val resim = ImageIO.read(dosya) ?: error("PNG geri okunamadı")

        // Farklı renk sayısı: boş/tek renk bir görüntü birkaç renkte kalır,
        // çizilmiş bir form (zemin + alan kenarları + yazılar + ikon) onlarca
        // üretir. Eşik bilinçli düşük — amaç görüntü karşılaştırmak değil,
        // "hiçbir şey çizilmedi" durumunu yakalamak.
        val renkler = HashSet<Int>()
        for (y in 0 until resim.height step 4) {
            for (x in 0 until resim.width step 4) {
                renkler.add(resim.getRGB(x, y))
            }
        }
        assertTrue(renkler.size >= 20, "Yalnızca ${renkler.size} renk var — ekran boş görünüyor")

        // Markanın altını ekranda mı? İkon tema renginde boyanıyor; altın
        // yoksa ya tema uygulanmamış ya ikon çizilmemiş demektir.
        val altinVar = (0 until resim.height step 2).any { y ->
            (0 until resim.width step 2).any { x ->
                val p = resim.getRGB(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                r in 180..250 && g in 130..210 && b < 120 && r > g && g > b
            }
        }
        assertTrue(altinVar, "Altın rengi hiçbir pikselde yok — tema uygulanmamış olabilir")
    }

    @Test
    fun `hata metni goruntuyu degistiriyor`() {
        // Aynı ekranın hatalı hâli farklı bir görüntü üretmeli. Üretmiyorsa
        // `hata` parametresi çizime hiç bağlanmamış demektir — derleme bunu
        // yakalayamaz, yalnızca çizim yakalar.
        val temiz = ekraniCiz("giris", gonderiliyor = false, hata = null).readBytes()
        val hatali = ekraniCiz("giris-hatali", gonderiliyor = false,
            hata = "E-posta veya şifre hatalı").readBytes()
        assertTrue(!temiz.contentEquals(hatali), "Hata metni görüntüyü değiştirmedi")
    }
}
