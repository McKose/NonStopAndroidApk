package com.gymapp.arayuz

import com.gymapp.arayuz.giris.GirisEkrani
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Giriş ekranının çizim testi.
 *
 * ### Neden derleme yetmiyor
 * Compose'da derlenen ama çizilmeyen ekran mümkün: kompozisyon anında atılan
 * bir istisna (eksik CompositionLocal, sıfır ölçü, tema dışı kullanım) ancak
 * çalıştırınca görülür. Panel tarafında birebir yaşandı — dört modül eksik
 * yayınlandı, her şey "derli" görünüyordu, sayfa bomboştu.
 *
 * Çizim ve ölçüm yardımcıları [ekraniCiz] / [cizildiginiDogrula] içinde;
 * i3b'de ikinci ekran gelince oraya çıkarıldılar.
 */
class GirisGoruntuTesti {

    @Test
    fun `giris ekrani ciziliyor ve bos degil`() {
        val dosya = ekraniCiz("giris") {
            GirisEkrani(gonderiliyor = false, hata = null, onGiris = { _, _ -> })
        }
        cizildiginiDogrula(dosya)

        // Markanın altını ekranda mı? Simge tema renginde boyanıyor; altın
        // yoksa ya tema uygulanmamış ya simge çizilmemiş demektir.
        val resim = ImageIO.read(dosya) ?: error("PNG geri okunamadı")
        assertTrue(altinVar(resim), "Altın rengi hiçbir pikselde yok — tema uygulanmamış olabilir")
    }

    @Test
    fun `hata metni goruntuyu degistiriyor`() {
        // Aynı ekranın hatalı hâli farklı bir görüntü üretmeli. Üretmiyorsa
        // `hata` parametresi çizime hiç bağlanmamış demektir — derleme bunu
        // yakalayamaz, yalnızca çizim yakalar.
        val temiz = ekraniCiz("giris") {
            GirisEkrani(gonderiliyor = false, hata = null, onGiris = { _, _ -> })
        }.readBytes()

        val hatali = ekraniCiz("giris-hatali") {
            GirisEkrani(
                gonderiliyor = false,
                hata = "E-posta veya şifre hatalı",
                onGiris = { _, _ -> },
            )
        }.readBytes()

        assertTrue(!temiz.contentEquals(hatali), "Hata metni görüntüyü değiştirmedi")
    }

    private fun altinVar(resim: BufferedImage): Boolean =
        (0 until resim.height step 2).any { y ->
            (0 until resim.width step 2).any { x ->
                val p = resim.getRGB(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                r in 180..250 && g in 130..210 && b < 120 && r > g && g > b
            }
        }
}
