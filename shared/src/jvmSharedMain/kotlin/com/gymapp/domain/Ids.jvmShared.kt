package com.gymapp.domain

import java.util.UUID

/**
 * Android ve JVM ortak karşılığı.
 *
 * İki hedef de `java.util.UUID` kullanıyor; ayrı dosyalarda dursalardı aynı satır
 * iki yerde durur ve biri değişince diğeri sessizce geride kalırdı. `jvmSharedMain`
 * ara kaynak kümesi tam olarak bunun için var.
 */
internal actual fun randomUuid(): String = UUID.randomUUID().toString()
