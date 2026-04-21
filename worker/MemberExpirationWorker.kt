package com.gymapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.gymapp.data.repository.MemberRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * DÜZELTME #3 — Masaüstünde üye status'ü hiçbir zaman otomatik güncellenmiyordu.
 *
 * WorkManager her gece 02:00'de çalışır:
 *  - endDate geçmiş tüm ACTIVE üyeleri PASSIVE yapar
 *  - Android 13 Doze mode uyumlu (BATTERY_NOT_LOW kısıtlaması ile)
 */
@HiltWorker
class MemberExpirationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MemberRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val expiredCount = repository.expireOverdueMembers()
        Result.success(
            workDataOf("expired_count" to expiredCount)
        )
    }.getOrElse {
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "member_expiration_check"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<MemberExpirationWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // Zaten zamanlanmışsa yeniden oluşturma
                request
            )
        }
    }
}
