package com.gymapp.data.local.db

import androidx.sqlite.SQLiteException

/**
 * Tekillik kısıtının ihlal edilip edilmediği.
 *
 * Uygulama kodu tekilliği (telefon, kullanıcı adı) önceden kontrol ediyor, ama
 * kontrol ile yazma arasında bir yarış her zaman mümkün; son söz veritabanındaki
 * UNIQUE index'te. Bu yüzden kısıt hatası yakalanıp kullanıcıya anlaşılır bir
 * mesaja çevriliyor.
 *
 * **Neden tip değişti:** Veritabanı gömülü SQLite sürücüsüne geçtiğinde hata tipi
 * de değişti. Android çatısının `SQLiteConstraintException`'ı yalnızca eski
 * (framework tabanlı) yolda fırlatılıyor; sürücü tabanlı Room `androidx.sqlite`
 * paketindeki ortak tipi fırlatıyor. Eski `catch` blokları bu yüzden artık
 * tetiklenmiyor ve çakışma durumunda kullanıcıya dostça mesaj yerine ham hata
 * gidiyordu.
 *
 * Mesaj içeriğine bakmak ideal değil, ancak SQLite kısıt türünü ayrı bir hata
 * koduyla ayırt etmiyor; bu, taşınabilir tek ayrım noktası.
 */
fun Throwable.isUniqueConstraintViolation(): Boolean =
    this is SQLiteException &&
        message?.contains("UNIQUE constraint failed", ignoreCase = true) == true
