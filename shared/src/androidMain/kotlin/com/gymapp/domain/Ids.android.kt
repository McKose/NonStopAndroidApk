package com.gymapp.domain

import java.util.UUID

/**
 * NOT: Aynı gövde `jvmMain` altında da var.
 *
 * Önce `jvmSharedMain` adında bir ara kaynak kümesiyle tek yere indirilmişti, ama
 * elle `dependsOn` kenarı eklemek Kotlin'in **varsayılan hiyerarşi şablonunu**
 * tamamen devre dışı bırakıyor — o şablon da `iosMain`'i üç iOS hedefine bağlayan
 * şey. Sonuç: iOS karşılıkları hiçbir hedefe bağlanmıyor ve Kotlin/Native
 * derlemesi düşüyordu. Bir satırlık tekrar, kırık bir hiyerarşiden ucuz.
 */
internal actual fun randomUuid(): String = UUID.randomUUID().toString()
