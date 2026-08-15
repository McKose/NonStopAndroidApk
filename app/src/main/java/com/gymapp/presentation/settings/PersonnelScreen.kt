package com.gymapp.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import org.koin.androidx.compose.koinViewModel
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.Decimals
import com.gymapp.domain.Money
import com.gymapp.domain.PhoneNumber
import com.gymapp.domain.Rate
import com.gymapp.domain.StaffRole
import com.gymapp.presentation.common.ReadOnlyNotice
import com.gymapp.domain.labelTr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonnelScreen(
    onNavigateBack: () -> Unit,
    viewModel: PersonnelViewModel = koinViewModel()
) {
    val staffList by viewModel.staffList.collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val canWrite = viewModel.canWrite

    /** `null` = diyalog kapalı; `StaffEntity?` içeren değer = düzenleme (veya yeni kayıt). */
    var editing by remember { mutableStateOf<StaffFormTarget?>(null) }
    var pendingDelete by remember { mutableStateOf<StaffEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PersonnelEvent.Saved -> editing = null
                is PersonnelEvent.Failed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            // Yetkisi olmayanda düğme hiç çizilmiyor; sebebi aşağıdaki şeritte.
            if (canWrite) {
                FloatingActionButton(onClick = { editing = StaffFormTarget(null) }) {
                    Icon(Icons.Default.Add, contentDescription = "Personel Ekle")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!canWrite) {
                item {
                    ReadOnlyNotice(
                        "Personel listesini görüntüleyebilirsiniz. Değiştirmek " +
                            "salon sahibi yetkisi gerektiriyor."
                    )
                }
            }
            items(staffList, key = { it.id }) { staff ->
                PersonnelItem(
                    staff = staff,
                    // Yetki yoksa satır düzenleme diyaloğunu açmıyor ve silme
                    // simgesi çizilmiyor.
                    onClick = { if (canWrite) editing = StaffFormTarget(staff) },
                    onDelete = if (canWrite) ({ pendingDelete = staff }) else null,
                )
            }
        }
    }

    editing?.let { target ->
        StaffDialog(
            staff = target.staff,
            onDismiss = { editing = null },
            onConfirm = { form ->
                viewModel.saveStaff(
                    staffId = target.staff?.id,
                    name = form.name,
                    title = form.title,
                    branch = form.branch,
                    commissionPercent = form.commissionPercent,
                    salary = form.salary,
                    phone = form.phone,
                    nickname = form.nickname,
                    role = form.role,
                    password = form.password,
                    authUserId = form.authUserId,
                )
            }
        )
    }

    pendingDelete?.let { staff ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Personeli sil") },
            text = { Text("${staff.fullName} listeden kaldırılacak. Geçmiş randevu ve hakediş kayıtları korunur.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteStaff(staff.id)
                    pendingDelete = null
                }) { Text("Sil", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Vazgeç") }
            }
        )
    }
}

/** Diyaloğun hedefi: `staff == null` ise yeni kayıt. */
private data class StaffFormTarget(val staff: StaffEntity?)

/** Diyalogdan dönen form değerleri. */
private data class StaffForm(
    val name: String,
    val title: String,
    val branch: String,
    val commissionPercent: Double,
    val salary: Double,
    val phone: String,
    val nickname: String,
    val role: StaffRole,
    val password: String?,
    val authUserId: String?,
)

@Composable
private fun PersonnelItem(staff: StaffEntity, onClick: () -> Unit, onDelete: (() -> Unit)?) {
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
                Text(
                    "${staff.role.labelTr()} | ${staff.branch.ifBlank { "-" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        PhoneNumber.formatForDisplay(staff.phone),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "₺${Money(staff.monthlySalaryMinor)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Hakediş: %${Rate(staff.commissionBasisPoints).asPercent}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (onDelete != null) IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.Red)
            }
        }
    }
}

/**
 * Ekleme ve düzenleme için **tek** diyalog.
 *
 * Önceden neredeyse birebir aynı iki diyalog vardı; alan eklendiğinde biri
 * güncellenip diğeri unutuluyordu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffDialog(
    staff: StaffEntity?,
    onDismiss: () -> Unit,
    onConfirm: (StaffForm) -> Unit
) {
    var name by remember { mutableStateOf(staff?.fullName ?: "") }
    var title by remember { mutableStateOf(staff?.title ?: "") }
    var branch by remember { mutableStateOf(staff?.branch ?: "") }
    var rate by remember {
        mutableStateOf(staff?.let { Rate(it.commissionBasisPoints).asPercent.toString() } ?: "")
    }
    var salary by remember {
        mutableStateOf(staff?.let { Money(it.monthlySalaryMinor).asDouble.toString() } ?: "")
    }
    var phone by remember { mutableStateOf(staff?.phone ?: "") }
    var nickname by remember { mutableStateOf(staff?.nickname ?: "") }
    var password by remember { mutableStateOf("") }
    var authUserId by remember { mutableStateOf(staff?.authUserId ?: "") }
    var role by remember { mutableStateOf(staff?.role ?: StaffRole.TRAINER) }
    var roleExpanded by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() && nickname.isNotBlank() && phone.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (staff == null) "Yeni Personel" else "Personel Düzenle") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ad Soyad *") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("Kullanıcı Adı *") },
                        modifier = Modifier.weight(1f)
                    )

                    ExposedDropdownMenuBox(
                        expanded = roleExpanded,
                        onExpandedChange = { roleExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = role.labelTr(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Rol") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roleExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = roleExpanded,
                            onDismissRequest = { roleExpanded = false }
                        ) {
                            // Seçenekler enum'dan üretiliyor; ekranda Türkçe etiket,
                            // veritabanında enum adı. Serbest metin rolü kalmadı.
                            StaffRole.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.labelTr()) },
                                    onClick = {
                                        role = option
                                        roleExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (staff == null) "Şifre (boşsa varsayılan)" else "Yeni Şifre (boşsa değişmez)") },
                    modifier = Modifier.fillMaxWidth(),
                    // Şifre omuz üstünden okunabiliyordu.
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Ünvan (Eğitmen, Resepsiyon vb.)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Branş (Fitness, Reformer vb.)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefon *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                // Bu personeli Supabase hesabına bağlayan alan.
                //
                // Elle yapıştırılıyor çünkü uygulama `auth.users` tablosunu
                // okuyamıyor — erişim kuralları buna izin vermiyor ve vermesi de
                // istenmez. Boş bırakıldığında kişi giriş yapabilir ama "bugün
                // benim derslerim" listesi boş görünür; bağlantı olmadan
                // randevulardaki `staffId` ile eşleşme kurulamaz.
                OutlinedTextField(
                    value = authUserId,
                    onValueChange = { authUserId = it },
                    label = { Text("Supabase kullanıcı kimliği") },
                    supportingText = {
                        Text("Panel → Authentication → Users → kullanıcının UID değeri")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = salary,
                        onValueChange = { salary = it },
                        label = { Text("Maaş") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rate,
                        onValueChange = { rate = it },
                        label = { Text("Hakediş %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    onConfirm(
                        StaffForm(
                            name = name.trim(),
                            title = title.trim(),
                            branch = branch.trim(),
                            // Alan **yüzde** alır; baz puana çevrim tek noktada.
                            commissionPercent = Decimals.parseOrDefault(rate),
                            salary = Decimals.parseOrDefault(salary),
                            phone = phone.trim(),
                            nickname = nickname.trim(),
                            role = role,
                            password = password.takeIf { it.isNotBlank() },
                            authUserId = authUserId.trim().takeIf { it.isNotBlank() },
                        )
                    )
                }
            ) { Text(if (staff == null) "Ekle" else "Güncelle") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}
