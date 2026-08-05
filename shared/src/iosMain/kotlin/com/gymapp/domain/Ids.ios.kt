package com.gymapp.domain

import platform.Foundation.NSUUID

/**
 * `NSUUID` büyük harfli üretir; Android tarafıyla aynı biçimi vermesi için
 * küçük harfe çevriliyor. Kimlikler iki platform arasında senkronize olacağı
 * için biçimin birebir aynı olması önemli.
 */
internal actual fun randomUuid(): String = NSUUID().UUIDString().lowercase()
