package com.gymapp.masaustu

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.gymapp.arayuz.giris.GirisEkrani
import com.gymapp.arayuz.tema.GymTema

/**
 * i1 iskeleti: pencereyi telefon oranında açar ve giriş ekranını gösterir.
 *
 * Giriş düğmesi henüz hiçbir yere BAĞLI DEĞİL ve bunu gizlemiyor: tıklanınca
 * ekranda tam olarak bunu söylüyor. Sessizce hiçbir şey yapmasaydı, deneyen
 * kişi uygulamanın bozuk olduğunu düşünürdü. Gerçek bağlantı (Koin +
 * ViewModel + Supabase) i3'te, gezinmeyle birlikte geliyor.
 *
 * Pencere 420x880: iPhone 14 Pro'nun mantıksal ekranına yakın bir oran.
 * Ekranlar önce bu darlıkta doğru görünmeli — masaüstü genişliğinde güzel
 * duran bir düzen telefonda taşabilir, tersi taşmaz.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Non Stop GYM",
        state = rememberWindowState(width = 420.dp, height = 880.dp),
    ) {
        var bilgi by remember { mutableStateOf<String?>(null) }

        GymTema(koyu = true) {
            Surface(color = MaterialTheme.colorScheme.background) {
                GirisEkrani(
                    gonderiliyor = false,
                    hata = bilgi,
                    onGiris = { _, _ ->
                        bilgi = "i1 iskeleti: giriş akışı i3'te bağlanacak."
                    },
                )
            }
        }
    }
}
