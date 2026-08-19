package com.gymapp.arayuz.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Koyu şema varsayılan taraf: marka siyah-altın ve salondaki ekran gün boyu
 * koyu temada duruyor. Açık şema yine de var — telefonu açık temada kullanan
 * personel, okunmaz bir uygulamayla cezalandırılmamalı.
 */
private val KoyuSema = darkColorScheme(
    primary = Altin,
    onPrimary = AltinUstuMetin,
    secondary = AltinKoyu,
    onSecondary = Metin,
    tertiary = AltinParlak,
    background = Zemin,
    onBackground = Metin,
    surface = Yuzey,
    onSurface = Metin,
    surfaceVariant = ZeminIki,
    onSurfaceVariant = Soluk,
    outline = Kenar,
    error = Hata,
)

private val AcikSema = lightColorScheme(
    primary = AltinKoyu,
    onPrimary = Metin,
    secondary = Altin,
    tertiary = AltinParlak,
)

private val Yazi = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
)

/**
 * Ortak tema.
 *
 * Android'in "dinamik renk" özelliği (duvar kâğıdından palet türetme) burada
 * BİLİNÇLİ olarak yok: hem yalnızca Android 12+ üzerinde var — iOS ve masaüstü
 * için anlamsız — hem de markanın rengini kullanıcının duvar kâğıdına
 * devrediyordu. Salonun uygulaması salonun renklerinde açılmalı.
 */
@Composable
fun GymTema(
    koyu: Boolean = isSystemInDarkTheme(),
    icerik: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (koyu) KoyuSema else AcikSema,
        typography = Yazi,
        content = icerik,
    )
}
