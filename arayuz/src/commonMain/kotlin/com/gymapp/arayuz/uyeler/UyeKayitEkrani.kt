package com.gymapp.arayuz.uyeler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymapp.data.local.entity.PackageEntity
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.PaymentState
import com.gymapp.domain.SessionCarryOver
import com.gymapp.domain.labelTr

/**
 * Üye kaydı ve paket yenileme — `app`'teki `RegisterMemberScreen`'in
 * ortak modüle taşınmış hâli.
 *
 * ### Neden tek nesne + on üç geri çağrı
 * Form durumu [UyeKayitFormu] olarak bütün hâlde giriyor, değişiklikler
 * alan alan çıkıyor. Asimetri bilinçli: doğrulama (ad boş mu, telefon
 * geçerli mi) ve türetilmiş fiyat, durumu ÜRETEN tarafta; ekran yalnızca
 * "kullanıcı bu alana bunu yazdı" diyor. Ekran `form.copy(...)` yapıp geri
 * verseydi doğrulamayı da üstlenmesi ya da doğrulamanın sessizce atlanması
 * gerekirdi.
 *
 * ### Ekranın kendi tuttuğu tek şey
 * Açılır listelerin (paket, taksit) açık/kapalı hâli. Menünün açık olması
 * uygulamanın verisi değil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UyeKayitEkrani(
    form: UyeKayitFormu,
    paketler: List<PackageEntity>,
    taksitSecenekleri: List<Int>,
    yenileme: Boolean,
    onGeri: () -> Unit,
    onAdSoyad: (String) -> Unit,
    onTelefon: (String) -> Unit,
    onEposta: (String) -> Unit,
    onSaglikRiskleri: (String) -> Unit,
    onSaglikNotlari: (String) -> Unit,
    onPaketSecildi: (PackageEntity) -> Unit,
    onDevir: (SessionCarryOver) -> Unit,
    onIskonto: (String) -> Unit,
    onOdemeTuru: (PaymentMethod) -> Unit,
    onOdemeDurumu: (PaymentState) -> Unit,
    onTaksit: (Int) -> Unit,
    onNotlar: (String) -> Unit,
    onKaydet: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (yenileme) "Paket Yenileme" else "Yeni Üye Kaydı") },
                navigationIcon = {
                    IconButton(onClick = onGeri) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { bosluk ->
        Column(
            modifier = Modifier
                .padding(bosluk)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            BolumBasligi("Kişisel Bilgiler")

            OutlinedTextField(
                value = form.fullName,
                onValueChange = onAdSoyad,
                label = { Text("Ad Soyad *") },
                isError = form.fullNameError != null,
                supportingText = form.fullNameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.phone,
                onValueChange = onTelefon,
                label = { Text("Telefon *") },
                isError = form.phoneError != null,
                supportingText = form.phoneError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                prefix = { Text("+90 ") },
            )

            // E-posta alanı formda **yoktu**: durum, işleyici, kayıt ve
            // yenilemede geri yükleme yazılmıştı ama girilecek kutu hiç
            // eklenmemişti. Her üye boş e-postayla kaydediliyordu. Zorunlu
            // değil — telefon zaten zorunlu ve tekillik onun üzerinden.
            OutlinedTextField(
                value = form.email,
                onValueChange = onEposta,
                label = { Text("E-posta") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            if (!yenileme) {
                BolumBasligi("Sağlık Bilgileri")

                OutlinedTextField(
                    value = form.healthRisks,
                    onValueChange = onSaglikRiskleri,
                    label = { Text("Sağlık Riskleri / Hastalıklar") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Örn: Tansiyon, Şeker...") },
                )

                OutlinedTextField(
                    value = form.healthNotes,
                    onValueChange = onSaglikNotlari,
                    label = { Text("Sağlık Notları") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            BolumBasligi("Paket Seçimi")

            var paketAcik by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = paketAcik,
                onExpandedChange = { paketAcik = it },
            ) {
                OutlinedTextField(
                    value = form.selectedPackage?.name ?: "Paket Seçiniz *",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Üyelik Paketi") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(paketAcik) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = paketAcik,
                    onDismissRequest = { paketAcik = false },
                ) {
                    paketler.forEach { paket ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(paket.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${paket.type.labelTr()} - ₺${Money(paket.basePriceMinor)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            },
                            onClick = {
                                onPaketSecildi(paket)
                                paketAcik = false
                            },
                        )
                    }
                }
            }

            // Kalan seans seçimi yalnızca yenilemede ve devredecek SAYILABİLİR
            // bir hak varken görünüyor. Sınırsız pakette (`null`) devredecek bir
            // sayı yok, sıfır kalan seansta ise seçimin iki şıkkı da aynı sonucu
            // verir — ikisinde de kullanıcıya sorulacak bir şey olmadığı için
            // soru hiç sorulmuyor. Her zaman göstermek, çoğu yenilemede anlamsız
            // bir karar dayatmak olurdu.
            val kalanSeans = form.currentRemainingSessions
            if (form.isRenewal && kalanSeans != null && kalanSeans > 0) {
                BolumBasligi("Kalan Seanslar")

                Text(
                    text = "Üyenin $kalanSeans seansı kullanılmadı.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SessionCarryOver.entries.forEach { secim ->
                        FilterChip(
                            selected = form.carryOver == secim,
                            onClick = { onDevir(secim) },
                            label = { Text(secim.labelTr()) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Seçimin sonucu sayıyla yazılıyor: "Devret" ile "İptal et"
                // arasındaki farkın ne olduğu, seçim yapılmadan önce görünsün.
                val yeniPaketSeans = form.selectedPackage?.sessionCount
                Text(
                    text = when {
                        yeniPaketSeans == null ->
                            "Seçilen paket sınırsız; kalan seanslar devredilemez."
                        form.carryOver == SessionCarryOver.CARRY ->
                            "Yeni toplam: ${yeniPaketSeans + kalanSeans} seans."
                        else -> "Yeni toplam: $yeniPaketSeans seans."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedTextField(
                value = form.discount,
                onValueChange = onIskonto,
                label = { Text("İskonto (₺)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            BolumBasligi("Ödeme")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PaymentMethod.entries.forEach { tur ->
                    FilterChip(
                        selected = form.paymentType == tur,
                        onClick = { onOdemeTuru(tur) },
                        label = { Text(tur.labelTr()) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ödeme Yapıldı mı?", modifier = Modifier.weight(1f))
                Switch(
                    checked = form.paymentStatus == PaymentState.PAID,
                    onCheckedChange = {
                        onOdemeDurumu(if (it) PaymentState.PAID else PaymentState.PENDING)
                    },
                )
            }

            if (form.paymentType == PaymentMethod.CARD) {
                var taksitAcik by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = taksitAcik,
                    onExpandedChange = { taksitAcik = it },
                ) {
                    OutlinedTextField(
                        value = "${form.installmentCount} Taksit",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Taksit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(taksitAcik) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = taksitAcik,
                        onDismissRequest = { taksitAcik = false },
                    ) {
                        taksitSecenekleri.forEach { adet ->
                            DropdownMenuItem(
                                text = { Text("$adet Taksit") },
                                onClick = {
                                    onTaksit(adet)
                                    taksitAcik = false
                                },
                            )
                        }
                    }
                }
            }

            // Kalemlerin tamamı `Pricing.breakdown` içinden geliyor: gösterilen
            // aritmetik ile kaydedilen tutar aynı hesabın çıktısı. Önceden
            // iskonto satırı kullanıcının yazdığı **ham** metni basıyordu; paket
            // fiyatını aşan bir iskontoda kart "1.000 − 5.000 = 0" gibi kendi
            // içinde tutarsız bir hesap gösteriyordu.
            if (form.selectedPackage != null) {
                val fiyat = form.breakdown
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        FiyatSatiri("Paket Fiyatı", "₺${fiyat.basePrice}")

                        if (fiyat.discount.isPositive) {
                            FiyatSatiri(
                                etiket = "İskonto",
                                deger = "-₺${fiyat.discount}",
                                renk = MaterialTheme.colorScheme.error,
                            )
                        }

                        // Vade farkı önceden hiçbir yerde yazmıyordu: kullanıcı
                        // taksit seçince toplam sebepsiz yükseliyor görünüyordu.
                        if (fiyat.surcharge.isPositive) {
                            FiyatSatiri(
                                etiket = "Vade Farkı (%${fiyat.surchargeRate.percentLabel})",
                                deger = "+₺${fiyat.surcharge}",
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Ödenecek Tutar", fontWeight = FontWeight.Bold)
                            Text(
                                text = "₺${fiyat.total}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        if (form.discountCapped) {
                            Text(
                                text = "İskonto paket fiyatını aşamaz; " +
                                    "en fazla ₺${fiyat.basePrice} uygulandı.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = form.notes,
                onValueChange = onNotlar,
                label = { Text("Genel Notlar") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                maxLines = 4,
            )

            form.submitError?.let { hata ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = hata,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Button(
                onClick = onKaydet,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (form.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        if (yenileme) "Paketi Yenile" else "Üyeyi Kaydet",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BolumBasligi(metin: String) {
    Text(
        text = metin,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
    HorizontalDivider()
}

/**
 * Fiyat kartındaki bir kalem.
 *
 * Dört yerde aynı `Row` + `SpaceBetween` + iki `Text` kalıbı vardı; renk
 * dışında hiçbir farkları yoktu.
 */
@Composable
private fun FiyatSatiri(
    etiket: String,
    deger: String,
    renk: Color = Color.Unspecified,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(etiket, color = renk)
        Text(deger, color = renk)
    }
}
