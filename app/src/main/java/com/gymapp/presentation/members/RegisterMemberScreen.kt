package com.gymapp.presentation.members

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import com.gymapp.domain.Decimals
import com.gymapp.domain.Money
import com.gymapp.domain.PaymentMethod
import com.gymapp.domain.PaymentState
import com.gymapp.domain.SessionCarryOver
import com.gymapp.domain.labelTr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterMemberScreen(
    onNavigateBack: () -> Unit,
    isRenewal: Boolean = false,
    memberId: String = "",
    viewModel: MemberViewModel = koinViewModel()
) {
    val formState by viewModel.formState.collectAsState()
    val packages by viewModel.packages.collectAsState()

    LaunchedEffect(isRenewal, memberId) {
        if (isRenewal && memberId.isNotBlank()) {
            viewModel.loadMemberForRenewal(memberId)
        } else if (!isRenewal) {
            viewModel.resetForm()
        }
    }

    LaunchedEffect(formState.submitSuccess) {
        if (formState.submitSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isRenewal) "Paket Yenileme" else "Yeni Üye Kaydı") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ─── Kişisel Bilgiler ──────────────────────────────────────────
            SectionTitle("Kişisel Bilgiler")

            OutlinedTextField(
                value = formState.fullName,
                onValueChange = viewModel::onFullNameChange,
                label = { Text("Ad Soyad *") },
                isError = formState.fullNameError != null,
                supportingText = formState.fullNameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = formState.phone,
                onValueChange = viewModel::onPhoneChange,
                label = { Text("Telefon *") },
                isError = formState.phoneError != null,
                supportingText = formState.phoneError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                prefix = { Text("+90 ") }
            )

            // ─── Sağlık Bilgileri ──────────────────────────────────────────
            if (!isRenewal) {
                SectionTitle("Sağlık Bilgileri")
                
                OutlinedTextField(
                    value = formState.healthRisks,
                    onValueChange = viewModel::onHealthRisksChange,
                    label = { Text("Sağlık Riskleri / Hastalıklar") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Örn: Tansiyon, Şeker...") }
                )

                OutlinedTextField(
                    value = formState.healthNotes,
                    onValueChange = viewModel::onHealthNotesChange,
                    label = { Text("Sağlık Notları") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ─── Paket Seçimi ──────────────────────────────────────────────
            SectionTitle("Paket Seçimi")

            var packageExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = packageExpanded,
                onExpandedChange = { packageExpanded = it }
            ) {
                OutlinedTextField(
                    value = formState.selectedPackage?.name ?: "Paket Seçiniz *",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Üyelik Paketi") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(packageExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = packageExpanded,
                    onDismissRequest = { packageExpanded = false }
                ) {
                    packages.forEach { pkg ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(pkg.name, fontWeight = FontWeight.Bold)
                                    Text("${pkg.type.labelTr()} - ₺${Money(pkg.basePriceMinor)}", style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = {
                                viewModel.onPackageSelected(pkg)
                                packageExpanded = false
                            }
                        )
                    }
                }
            }

            // ─── Kalan seanslar ────────────────────────────────────────────
            //
            // Yalnızca yenilemede ve devredecek SAYILABİLİR bir hak varken
            // görünüyor. Sınırsız pakette (`null`) devredecek bir sayı yok,
            // sıfır kalan seansta ise seçimin iki şıkkı da aynı sonucu verir —
            // ikisinde de kullanıcıya sorulacak bir şey olmadığı için soru hiç
            // sorulmuyor. Her zaman göstermek, çoğu yenilemede anlamsız bir
            // karar dayatmak olurdu.
            val kalanSeans = formState.currentRemainingSessions
            if (formState.isRenewal && kalanSeans != null && kalanSeans > 0) {
                SectionTitle("Kalan Seanslar")

                Text(
                    text = "Üyenin $kalanSeans seansı kullanılmadı.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SessionCarryOver.entries.forEach { secim ->
                        FilterChip(
                            selected = formState.carryOver == secim,
                            onClick = { viewModel.onCarryOverChange(secim) },
                            label = { Text(secim.labelTr()) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Seçimin sonucu sayıyla yazılıyor: "Devret" ile "İptal et"
                // arasındaki farkın ne olduğu, seçim yapılmadan önce görünsün.
                val yeniPaketSeans = formState.selectedPackage?.sessionCount
                Text(
                    text = when {
                        yeniPaketSeans == null -> "Seçilen paket sınırsız; kalan seanslar devredilemez."
                        formState.carryOver == SessionCarryOver.CARRY ->
                            "Yeni toplam: ${yeniPaketSeans + kalanSeans} seans."
                        else -> "Yeni toplam: $yeniPaketSeans seans."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            OutlinedTextField(
                value = formState.discount,
                onValueChange = viewModel::onDiscountChange,
                label = { Text("İskonto (₺)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // ─── Ödeme Bilgileri ───────────────────────────────────────────
            SectionTitle("Ödeme")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PaymentMethod.entries.forEach { type ->
                    FilterChip(
                        selected = formState.paymentType == type,
                        onClick = { viewModel.onPaymentTypeChange(type) },
                        label = { Text(type.labelTr()) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Ödeme Durumu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ödeme Yapıldı mı?", modifier = Modifier.weight(1f))
                Switch(
                    checked = formState.paymentStatus == PaymentState.PAID,
                    onCheckedChange = {
                        viewModel.onPaymentStatusChange(
                            if (it) PaymentState.PAID else PaymentState.PENDING
                        )
                    }
                )
            }

            if (formState.paymentType == PaymentMethod.CARD) {
                var expanded by remember { mutableStateOf(false) }
                val installmentOptions = viewModel.installmentOptions

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = "${formState.installmentCount} Taksit",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Taksit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        installmentOptions.forEach { count ->
                            DropdownMenuItem(
                                text = { Text("$count Taksit") },
                                onClick = {
                                    viewModel.onInstallmentChange(count)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // ─── Fiyat Önizlemesi ──────────────────────────────────────────
            if (formState.previewPrice > 0.0 || formState.selectedPackage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Paket Fiyatı")
                            Text("₺${Money(formState.selectedPackage?.basePriceMinor ?: 0L)}")
                        }
                        if ((Decimals.parseOrDefault(formState.discount)) > 0.0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("İskonto", color = MaterialTheme.colorScheme.error)
                                Text("-₺${formState.discount}", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ödenecek Tutar", fontWeight = FontWeight.Bold)
                            Text(
                                text = "₺${"%.2f".format(formState.previewPrice)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = formState.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Genel Notlar") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                maxLines = 4
            )

            formState.submitError?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = viewModel::submitRegistration,
                enabled = !formState.isSubmitting,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (formState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isRenewal) "Paketi Yenile" else "Üyeyi Kaydet", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )
    HorizontalDivider()
}
