package com.gymapp.arayuz.personel

import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.StaffRole

/**
 * Personel diyaloğunun hedefi.
 *
 * `personel == null` yeni kayıt demek. Ayrı bir sarmalayıcı, çünkü ekranın
 * ayırt etmesi gereken **üç** hâl var: diyalog kapalı (`null`), yeni kayıt
 * (`PersonelFormHedefi(null)`) ve düzenleme. Tek bir `StaffEntity?` bunları
 * ikiye indirger ve "yeni kayıt" ile "kapalı" ayrımı kaybolurdu.
 */
data class PersonelFormHedefi(val personel: StaffEntity?)

/**
 * Personel diyaloğundan dönen değerler.
 *
 * `commissionPercent` **yüzde** taşıyor, baz puan değil: kullanıcı ekrana
 * yüzde yazıyor ve çevrim tek noktada, kaydetme tarafında yapılıyor.
 */
data class PersonelFormu(
    val name: String,
    val title: String,
    val branch: String,
    val commissionPercent: Double,
    val salary: Double,
    val phone: String,
    val nickname: String,
    val role: StaffRole,
    val authUserId: String?,
)
