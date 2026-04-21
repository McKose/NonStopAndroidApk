package com.gymapp.presentation.members

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gymapp.data.local.entity.PaymentType

/**
 * Üye Kayıt Ekranı — Jetpack Compose + Material3
 *
 * Android 13 (API 33) özellikleri:
 *  - Scoped Storage: Dosya erişimi için MediaStore API kullanılıyor (fotoğraf için)
 *  - Notification permission: POST_NOTIFICATIONS izni runtime'da isteniyor (ayrı worker'da)
 *  - Predictive Back: BackHandler ile yönetiliyor
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterMemberScreen(
    onNavigateBack: () -> Unit,
    viewModel: MemberViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsState()

    // Başarı sonrası geri dön
    LaunchedEffect(formState.submitSuccess) {
        if (formState.submitSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yeni Üye Kaydı") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
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
                value         = formState.fullName,
                onValueChange = viewModel::onFullNameChange,
                label         = { Text("Ad Soyad *") },
                isError       = formState.fullNameError != null,
                supportingText = formState.fullNameError?.let { { Text(it) } },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            OutlinedTextField(
                value         = formState.phone,
                onValueChange = viewModel::onPhoneChange,
                label         = { Text("Telefon *") },
                isError       = formState.phoneError != null,
                supportingText = formState.phoneError?.let { { Text(it) } },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                prefix        = { Text("+90 ") }
            )

            OutlinedTextField(
                value         = formState.email,
                onValueChange = viewModel::onEmailChange,
                label         = { Text("E-posta (opsiyonel)") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            // ─── Ödeme Bilgileri ───────────────────────────────────────────
            SectionTitle("Ödeme")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaymentType.entries.forEach { type ->
                    FilterChip(
                        selected  = formState.paymentType == type,
                        onClick   = { viewModel.onPaymentTypeChange(type) },
                        label     = { Text(if (type == PaymentType.CASH) "Nakit" else "Kart") },
                        modifier  = Modifier.weight(1f)
                    )
                }
            }

            // Taksit seçimi — sadece KART ödemesinde göster
            if (formState.paymentType == PaymentType.CARD) {
                var expanded by remember { mutableStateOf(false) }
                val installmentOptions = listOf(1, 2, 3, 6, 9, 12)

                ExposedDropdownMenuBox(
                    expanded        = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value         = "${formState.installmentCount} Taksit",
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Taksit") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier      = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded    = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        installmentOptions.forEach { count ->
                            DropdownMenuItem(
                                text    = { Text("$count Taksit") },
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
            if (formState.previewPrice > 0.0) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier            = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment   = Alignment.CenterVertically
                    ) {
                        Text("Toplam Ücret")
                        Text(
                            text       = "₺${"%.2f".format(formState.previewPrice)}",
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ─── Notlar ───────────────────────────────────────────────────
            OutlinedTextField(
                value         = formState.notes,
                onValueChange = viewModel::onNotesChange,
                label         = { Text("Notlar") },
                modifier      = Modifier.fillMaxWidth().height(100.dp),
                maxLines      = 4
            )

            // ─── Hata mesajı ──────────────────────────────────────────────
            formState.submitError?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text     = error,
                        color    = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // ─── Kaydet butonu ─────────────────────────────────────────────
            Button(
                onClick  = viewModel::submitRegistration,
                enabled  = !formState.isSubmitting,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (formState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Üyeyi Kaydet", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.titleSmall,
        color      = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier.padding(top = 8.dp)
    )
    HorizontalDivider()
}
