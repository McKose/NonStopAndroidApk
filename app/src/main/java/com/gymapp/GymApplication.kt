package com.gymapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt için `@HiltAndroidApp` zorunlu.
 *
 * KALDIRILDI: WorkManager kurulumu. Tek işçi olan `MemberExpirationWorker` bir
 * önceki dilimde kalktı (üyelik durumu artık okuma anında bitiş tarihinden
 * türetiliyor); geriye yalnızca hiçbir işçisi olmayan bir fabrika, manifest'te
 * kapatılmış bir auto-init ve iki kullanılmayan bağımlılık kalmıştı.
 *
 * Faz 4'teki senkronizasyon kuyruğu (outbox) gerçekten bir işçiye ihtiyaç
 * duyduğunda bu kurulum o zaman, kullanan kodla birlikte geri gelecek.
 */
@HiltAndroidApp
class GymApplication : Application()
