package com.gymapp.presentation.members

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymapp.domain.Money

/**
 * Tahsilat onayı — tutarı **gösterir** ve değiştirilebilir kılar.
 *
 * ### Neden var
 * "Ödemeyi Onayla" düğmesi tek dokunuşta, hiçbir tutar göstermeden, kalan
 * borcun **tamamını** tahsil ediyordu. Borç tarih sınırsız hesaplandığı için
 * eski aylardan devreden borç da bugünkü tahsilata ekleniyordu: marttan kalma
 * 1.000 TL borcu olan üye ağustosta 1.200 TL'lik paket alıp "Ödendi"
 * işaretlendiğinde deftere **2.200 TL** giriyordu. Ağustos geliri 1.000 TL
 * şişiyor, kasa o kadar açık veriyor ve bunu gösteren hiçbir ekran
 * bulunmuyordu.
 *
 * Tutarın görünür olması tek başına sorunu büyük ölçüde çözüyor; değiştirilebilir
 * olması ise kısmi tahsilatı mümkün kılıyor. Kısmi tahsilatta üye "Ödendi"
 * sayılmıyor (bkz. `MemberRepository.updatePaymentStatus`), yani kalan borç
 * listelerde uyarı üretmeye devam ediyor.
 */
@Composable
fun PaymentDialog(
    memberName: String,
    outstanding: Money,
    onConfirm: (Money) -> Unit,
    onDismiss: () -> Unit,
) {
    // Varsayılan kalan borcun tamamı: eski davranış, ama artık görünür.
    var tutarMetni by remember(outstanding) { mutableStateOf(outstanding.toString()) }

    val girilen = Money.parseOrNull(tutarMetni)
    val hata: String? = when {
        girilen == null -> "Geçerli bir tutar girin."
        !girilen.isPositive -> "Tutar sıfırdan büyük olmalı."
        girilen > outstanding -> "Kalan borçtan fazla tahsil edilemez."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tahsilat") },
        text = {
            Column {
                Text("$memberName için kalan borç: ₺$outstanding")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = tutarMetni,
                    onValueChange = { tutarMetni = it },
                    label = { Text("Tahsil edilen tutar (₺)") },
                    singleLine = true,
                    isError = hata != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (hata != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(hata, color = MaterialTheme.colorScheme.error)
                }
                if (hata == null && girilen != null && girilen < outstanding) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Kısmi tahsilat: ₺${outstanding - girilen} borç kalmaya devam edecek.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = hata == null,
                onClick = { girilen?.let(onConfirm) },
            ) { Text("Tahsil et") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        },
    )
}
