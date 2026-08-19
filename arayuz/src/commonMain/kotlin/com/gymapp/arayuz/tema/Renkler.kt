package com.gymapp.arayuz.tema

import androidx.compose.ui.graphics.Color

/**
 * Marka paleti — `web/site.css` ile aynı değerler.
 *
 * Android uygulamasının eski teması Android Studio şablonundan kalma
 * mor/pembeydi ve markayla ilgisi yoktu. Ortak tema sıfırdan kurulurken
 * doğrusu siteyle aynı dili konuşması: salonun rengi altın ve siyah, uygulama
 * da öyle görünmeli. Değerler siteden KOPYALANDI çünkü tek doğru kaynak orası —
 * palet değişecekse önce sitede değişir, burası onu izler.
 */
val Altin = Color(0xFFD9A441)
val AltinParlak = Color(0xFFF0C46A)
val AltinKoyu = Color(0xFFA97C22)

/** Altının üstüne yazılan metin — koyu, siteyle aynı (#17130a). */
val AltinUstuMetin = Color(0xFF17130A)

val Zemin = Color(0xFF0B0B0D)
val ZeminIki = Color(0xFF121317)
val Yuzey = Color(0xFF17181D)
val Kenar = Color(0xFF26282F)
val Metin = Color(0xFFF2F3F5)
val Soluk = Color(0xFFA2A7B0)

/** Hata rengi; Material'ın varsayılanına yakın ama koyu zeminde okunur. */
val Hata = Color(0xFFF2B8B5)
