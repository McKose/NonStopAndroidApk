package com.gymapp.data.local.db

import androidx.room.Room
import java.io.File

/**
 * Masaüstünde veritabanını açar.
 *
 * Varsayılan yol kullanıcının ev dizini altında `.nonstopgym/`: uygulamanın
 * çalıştırıldığı klasöre yazmak yanlış olurdu — geliştirme sırasında proje
 * ağacını kirletir, kurulu bir uygulamada ise yazma izni olmayabilir.
 *
 * Şema, migrasyonlar ve sürücü ayarları burada DEĞİL: hepsi ortak
 * [buildGymDatabase] içinde ve üç platformda da aynı. Buradaki tek platform
 * kararı dosyanın nereye konacağı.
 *
 * @param dosyaYolu Testlerin ve farklı kurulumların kendi yerini verebilmesi
 *        için dışarıdan geçilebilir.
 */
fun createGymDatabase(
    dosyaYolu: String = varsayilanVeritabaniYolu(),
): GymDatabase {
    File(dosyaYolu).parentFile?.mkdirs()
    return Room.databaseBuilder<GymDatabase>(name = dosyaYolu).buildGymDatabase()
}

private fun varsayilanVeritabaniYolu(): String =
    File(File(System.getProperty("user.home"), ".nonstopgym"), GYM_DATABASE_NAME).absolutePath
