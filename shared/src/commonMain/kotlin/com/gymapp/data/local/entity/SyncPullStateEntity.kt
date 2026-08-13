package com.gymapp.data.local.entity

import androidx.room.Entity

/**
 * Bir tablonun sunucudan nereye kadar okunduğu.
 *
 * Su işareti tablo başına tutuluyor, tek bir genel değer olarak değil: tablolar
 * bağımsız ilerliyor ve biri hata alıp durduğunda diğerlerinin ilerlemesi
 * kaybolmamalı. Tek değer olsaydı en geride kalan tablo hepsini geriye çekerdi
 * ve her turda aynı satırlar tekrar tekrar inerdi.
 *
 * `tenantId` anahtarın parçası: aynı cihazda başka bir salona giriş yapıldığında
 * o salonun ilerlemesi ayrı tutulur.
 */
@Entity(tableName = "sync_pull_state", primaryKeys = ["tenantId", "entityTable"])
data class SyncPullStateEntity(
    val tenantId: String,
    val entityTable: String,
    val lastPulledAtMs: Long,
)
