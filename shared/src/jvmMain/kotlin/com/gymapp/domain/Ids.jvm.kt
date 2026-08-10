package com.gymapp.domain

import java.util.UUID

/** Bkz. `androidMain/.../Ids.android.kt` — tekrarın gerekçesi orada. */
internal actual fun randomUuid(): String = UUID.randomUUID().toString()
