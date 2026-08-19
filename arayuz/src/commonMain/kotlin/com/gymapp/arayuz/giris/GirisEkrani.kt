package com.gymapp.arayuz.giris

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Giriş ekranı — `app`'teki `LoginScreen`'in ortak modüle taşınmış hâli.
 *
 * ### Durum dışarıdan geliyor (state hoisting)
 * Android'deki özgün ekran ViewModel'ini Koin'den kendisi çekiyordu. Burada
 * bilinçli olarak çekmiyor: gönderim durumu ve hata parametre, tıklama geri
 * çağrı. Böylece aynı ekran Android'de ViewModel'e, masaüstünde basit bir
 * duruma, testte sabit değerlere bağlanabiliyor — ekran hiçbirini bilmiyor.
 * ViewModel bağlantısı i3'te, gezinmeyle birlikte kurulacak.
 *
 * (i3'e kadar `app` kendi kopyasını kullanmaya devam ediyor. İki kopya kısa
 * ömürlü ve bilinçli: i1'in amacı zinciri uçtan uca kanıtlamak, uygulamayı
 * değiştirmek değil.)
 *
 * Üstteki dambıl ikonu süsten fazlası: `material-icons-extended`'ın
 * multiplatform'da yaşayıp yaşamadığının turnusolü (bkz. docs/ios-plani.md).
 * Bu derleniyorsa 13 ekrandaki 52 ikon kullanımı da derlenecek demektir.
 */
@Composable
fun GirisEkrani(
    gonderiliyor: Boolean,
    hata: String?,
    onGiris: (eposta: String, sifre: String) -> Unit,
) {
    var eposta by remember { mutableStateOf("") }
    var sifre by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.FitnessCenter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Non Stop",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Gym Management",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Kullanıcı adı değil e-posta: kimlik doğrulama Supabase Auth'ta ve
        // orada hesaplar e-postayla açılıyor. Klavye türü de buna göre; adres
        // yazarken büyük harfe geçen bir klavye her denemede sessizce yanlış
        // giriş üretirdi.
        OutlinedTextField(
            value = eposta,
            onValueChange = { eposta = it },
            label = { Text("E-posta") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = sifre,
            onValueChange = { sifre = it },
            label = { Text("Şifre") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        if (hata != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                hata,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onGiris(eposta, sifre) },
            enabled = !gonderiliyor && eposta.isNotBlank() && sifre.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            if (gonderiliyor) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Giriş Yap")
            }
        }
    }
}
