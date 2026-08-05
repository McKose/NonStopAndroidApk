package com.gymapp.domain

import java.util.UUID

internal actual fun randomUuid(): String = UUID.randomUUID().toString()
