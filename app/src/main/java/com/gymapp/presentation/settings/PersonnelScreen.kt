package com.gymapp.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.CommissionRate
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonnelScreen(
    onNavigateBack: () -> Unit,
    viewModel: PersonnelViewModel = hiltViewModel()
) {
    val staffList by viewModel.staffList.collectAsState(initial = emptyList())
    val error by viewModel.error.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedStaff by remember { mutableStateOf<StaffEntity?>(null) }

    LaunchedEffect(error) {
        if (error == null && !showAddDialog && selectedStaff == null) {
            // Success case logic if needed
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personel Yönetimi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Personel Ekle")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(staffList) { staff ->
                PersonnelItem(staff, onClick = { selectedStaff = staff })
            }
        }
    }

    if (showAddDialog) {
        AddStaffDialog(
            error = error,
            onDismiss = { 
                showAddDialog = false
                viewModel.clearError()
            },
            onConfirm = { name, title, branch, commissionFraction, salary, phone, nickname, role ->
                viewModel.addStaff(name, title, branch, commissionFraction, salary, phone, nickname, role)
            }
        )
    }

    if (selectedStaff != null) {
        EditStaffDialog(
            staff = selectedStaff!!,
            error = error,
            onDismiss = {
                selectedStaff = null
                viewModel.clearError()
            },
            onConfirm = { updatedStaff ->
                viewModel.updateStaff(updatedStaff)
                selectedStaff = null
            }
        )
    }

    // Auto-close add dialog on success
    LaunchedEffect(staffList.size) {
        if (showAddDialog) showAddDialog = false
    }
}

@Composable
fun PersonnelItem(staff: StaffEntity, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(staff.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${staff.title} | ${staff.branch}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                    Spacer(Modifier.width(4.dp))
                    Text(staff.phone, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text("₺${staff.monthlySalary}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Hakediş: %${CommissionRate.toPercentDisplay(staff.commissionRate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStaffDialog(
    staff: StaffEntity,
    error: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (StaffEntity) -> Unit
) {
    var name by remember { mutableStateOf(staff.fullName) }
    var title by remember { mutableStateOf(staff.title) }
    var branch by remember { mutableStateOf(staff.branch) }
    var rate by remember { mutableStateOf(CommissionRate.toPercentDisplay(staff.commissionRate).toString()) }
    var salary by remember { mutableStateOf(staff.monthlySalary.toString()) }
    var phone by remember { mutableStateOf(staff.phone) }
    var nickname by remember { mutableStateOf(staff.nickname) }
    var password by remember { mutableStateOf(staff.password) }
    var role by remember { mutableStateOf(staff.role) }
    var roleExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Personel Düzenle") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ad Soyad *") }, modifier = Modifier.fillMaxWidth())
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Kullanıcı Adı *") }, modifier = Modifier.weight(1f))
                    
                    ExposedDropdownMenuBox(
                        expanded = roleExpanded,
                        onExpandedChange = { roleExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = role,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Rol") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roleExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                            listOf("antrenör", "yönetici", "admin").forEach { r ->
                                DropdownMenuItem(text = { Text(r) }, onClick = { role = r; roleExpanded = false })
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Şifre") },
                    modifier = Modifier.fillMaxWidth(),
                    // Şifre omuz üstünden okunabiliyordu.
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Ünvan") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = branch, onValueChange = { branch = it }, label = { Text("Branş") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Telefon *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = salary, onValueChange = { salary = it }, label = { Text("Maaş") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Hakediş %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(staff.copy(
                    fullName = name.trim(),
                    title = title.trim(),
                    branch = branch.trim(),
                    // DÜZELTME: "Hakediş %" alanı daha önce hourlyRate'e yazıyordu; hakediş
                    // hesabı commissionRate'i okuduğu için oran her zaman 0 kalıyordu.
                    commissionRate = CommissionRate.fromPercentInput(rate.toDoubleOrNull()),
                    monthlySalary = salary.toDoubleOrNull() ?: 0.0,
                    phone = phone.trim(),
                    nickname = nickname.trim(),
                    password = password,
                    role = role
                ))
            }) { Text("Güncelle") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStaffDialog(
    error: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Double, Double, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var salary by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("antrenör") }
    var roleExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Personel") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ad Soyad *") }, modifier = Modifier.fillMaxWidth())
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("Kullanıcı Adı *") }, modifier = Modifier.weight(1f))
                    
                    ExposedDropdownMenuBox(
                        expanded = roleExpanded,
                        onExpandedChange = { roleExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = role,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Rol") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roleExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                            listOf("antrenör", "yönetici").forEach { r ->
                                DropdownMenuItem(text = { Text(r) }, onClick = { role = r; roleExpanded = false })
                            }
                        }
                    }
                }

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Ünvan (Eğitmen, Resepsiyon vb.)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = branch, onValueChange = { branch = it }, label = { Text("Branş (Fitness, Reformer vb.)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Telefon *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = salary, onValueChange = { salary = it }, label = { Text("Maaş") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Hakediş %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    name.trim(),
                    title.trim(),
                    branch.trim(),
                    // Yüzde girdisi kesre çevrilerek commissionRate'e yazılır.
                    CommissionRate.fromPercentInput(rate.toDoubleOrNull()),
                    salary.toDoubleOrNull() ?: 0.0,
                    phone.trim(),
                    nickname.trim(),
                    role
                )
            }) { Text("Ekle") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}
