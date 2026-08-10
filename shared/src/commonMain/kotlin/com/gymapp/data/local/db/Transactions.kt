package com.gymapp.data.local.db

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Ortak koddan kullanılabilen transaction sarmalayıcısı.
 *
 * Room'un `database.withTransaction { }` uzantısı `room-ktx` içinde ve yalnızca
 * Android'de var; repository katmanı ortak koda taşınırken tek engel buydu.
 * KMP tarafındaki karşılığı yazar bağlantısını alıp üzerinde bir transaction
 * açmak.
 *
 * Neden `immediateTransaction` (SQLite `BEGIN IMMEDIATE`) ve neden gecikmeli
 * (deferred) değil: bu projedeki transaction'ların hepsi **önce okuyup sonra
 * yazıyor** — stok kontrolü sonrası stok düşümü, randevu çakışma kontrolü
 * sonrası randevu yazımı gibi. Gecikmeli transaction yazma kilidini ilk yazma
 * anına kadar almadığı için iki eşzamanlı işlem aynı stoğu okuyup ikisi de
 * satabilir; kilidi baştan alan IMMEDIATE bunu engeller. Kontrol ile yazımın
 * aynı kilidin altında olması bu kodun doğruluk şartı, performans tercihi değil.
 *
 * Sarmalayıcı bilinçli olarak alıcısız (receiver'sız) bir blok alıyor: çağıran
 * kod Room'un transaction kapsamı tipini hiç görmüyor, dolayısıyla Room'un o
 * tarafı değişse bile çağrı yerlerine dokunmak gerekmiyor.
 */
suspend fun <R> GymDatabase.inTransaction(block: suspend () -> R): R =
    useWriterConnection { connection ->
        connection.immediateTransaction { block() }
    }
