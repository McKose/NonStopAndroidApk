package com.gymapp.presentation.members

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gymapp.data.local.entity.MeasurementEntity

/**
 * Yeni ölçüm girişi. Kayıt/yenilemeden çağrılabildiği gibi üye detayından da açılır.
 * @param measurementId > 0 ise mevcut ölçüm düzenlenir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementEntryScreen(
    memberId: Long,
    measurementId: Long = 0L,
    onDone: () -> Unit,
    viewModel: MemberViewModel = hiltViewModel()
) {
    val isEdit = measurementId > 0L
    var existing by remember { mutableStateOf<MeasurementEntity?>(null) }

    LaunchedEffect(measurementId) {
        if (isEdit) {
            existing = viewModel.getMeasurementById(measurementId)
        }
    }

    // Alanlar — düzenleme modunda yüklenen entity ile ön-doldur
    var height by remember(existing) { mutableStateOf(existing?.height?.takeIf { it > 0 }?.toString() ?: "") }
    var weight by remember(existing) { mutableStateOf(existing?.weight?.takeIf { it > 0 }?.toString() ?: "") }
    var shoulder by remember(existing) { mutableStateOf(existing?.shoulder?.takeIf { it > 0 }?.toString() ?: "") }
    var chest by remember(existing) { mutableStateOf(existing?.chest?.takeIf { it > 0 }?.toString() ?: "") }
    var waist by remember(existing) { mutableStateOf(existing?.waist?.takeIf { it > 0 }?.toString() ?: "") }
    var hips by remember(existing) { mutableStateOf(existing?.hips?.takeIf { it > 0 }?.toString() ?: "") }
    var leg by remember(existing) { mutableStateOf(existing?.leg?.takeIf { it > 0 }?.toString() ?: "") }
    var arm by remember(existing) { mutableStateOf(existing?.arm?.takeIf { it > 0 }?.toString() ?: "") }
    var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Ölçüm Düzenle" else "Ölçüm Girişi") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                "Ölçüm değerlerini tamamlamak zorunda değilsin; boş bırakılan alanlar kaydedilmez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            MeasurementField("Boy (cm)", height) { height = it }
            MeasurementField("Kilo (kg)", weight) { weight = it }
            MeasurementField("Omuz (cm)", shoulder) { shoulder = it }
            MeasurementField("Göğüs (cm)", chest) { chest = it }
            MeasurementField("Karın (cm)", waist) { waist = it }
            MeasurementField("Kalça (cm)", hips) { hips = it }
            MeasurementField("Bacak (cm)", leg) { leg = it }
            MeasurementField("Kol (cm)", arm) { arm = it }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notlar") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Button(
                onClick = {
                    val entity = MeasurementEntity(
                        id = existing?.id ?: 0L,
                        memberId = memberId,
                        dateMs = existing?.dateMs ?: System.currentTimeMillis(),
                        height = height.toDoubleOr0(),
                        weight = weight.toDoubleOr0(),
                        shoulder = shoulder.toDoubleOr0(),
                        chest = chest.toDoubleOr0(),
                        waist = waist.toDoubleOr0(),
                        hips = hips.toDoubleOr0(),
                        leg = leg.toDoubleOr0(),
                        arm = arm.toDoubleOr0(),
                        notes = notes.trim()
                    )
                    if (isEdit) viewModel.updateMeasurement(entity)
                    else viewModel.addMeasurement(entity)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isEdit) "Güncelle" else "Kaydet", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MeasurementField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun String.toDoubleOr0(): Double =
    this.replace(',', '.').toDoubleOrNull() ?: 0.0
