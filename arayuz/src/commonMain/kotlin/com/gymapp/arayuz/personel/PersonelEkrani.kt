package com.gymapp.arayuz.personel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymapp.arayuz.ortak.SaltOkunurSerit
import com.gymapp.data.access.yetkiOzetiTr
import com.gymapp.data.local.entity.StaffEntity
import com.gymapp.domain.Decimals
import com.gymapp.domain.Money
import com.gymapp.domain.PhoneNumber
import com.gymapp.domain.Rate
import com.gymapp.domain.StaffRole
import com.gymapp.domain.labelTr

/**
 * Personel yönetimi — `app`'teki `PersonnelScreen`'in ortak modüle taşınmış hâli.
 *
 * ### Diyalogların açık/kapalı hâli neden dışarıda
 * Kaydetme diyaloğu, kayıt BAŞARILI olduğunda kapanıyor; reddedildiğinde
 * (doğrulama, yetki) açık kalıyor ki kullanıcı yazdıklarını kaybetmesin. Bu
 * kararı ekran veremez — sonucu yalnızca olayları dinleyen taraf bilir.
 * Takvimdeki randevu sheet'iyle aynı gerekçe.
 *
 * Diyaloğun İÇİNDEKİ alanlar ekranda kalıyor: onlar yazma durumu.
 *
 * ### Yetki
 * [yazabilir] `false` iken ekleme düğmesi, satır tıklaması ve silme simgesi
 * hiç çizilmiyor; sebebi listenin başındaki şeritte yazıyor. Gizlenen bir
 * düğmenin sebebini yazmamak, kullanıcıya "uygulama bozuk" dedirtiyordu.
 *
 * @param formHedefi diyalog kapalıysa `null`; içindeki personel `null` ise
 *   yeni kayıt
 * @param silinecek silme onayı bekleyen personel, yoksa `null`
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonelEkrani(
    personeller: List<StaffEntity>,
    yazabilir: Boolean,
    formHedefi: PersonelFormHedefi?,
    silinecek: StaffEntity?,
    onGeri: () -> Unit,
    onYeniPersonel: () -> Unit,
    onPersonelSec: (StaffEntity) -> Unit,
    onFormKapat: () -> Unit,
    onKaydet: (personelId: String?, form: PersonelFormu) -> Unit,
    onSilIste: (StaffEntity) -> Unit,
    onSilOnayla: (String) -> Unit,
    onSilVazgec: () -> Unit,
    snackbarDurumu: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarDurumu) },
        topBar = {
            TopAppBar(
                title = { Text("Personel Yönetimi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onGeri) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
        floatingActionButton = {
            // Yetkisi olmayanda düğme hiç çizilmiyor; sebebi aşağıdaki şeritte.
            if (yazabilir) {
                FloatingActionButton(onClick = onYeniPersonel) {
                    Icon(Icons.Default.Add, contentDescription = "Personel Ekle")
                }
            }
        },
    ) { bosluk ->
        LazyColumn(
            modifier = Modifier.padding(bosluk).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!yazabilir) {
                item {
                    SaltOkunurSerit(
                        "Personel listesini görüntüleyebilirsiniz. Değiştirmek " +
                            "salon sahibi yetkisi gerektiriyor.",
                    )
                }
            }
            items(personeller, key = { it.id }) { personel ->
                PersonelSatiri(
                    personel = personel,
                    // Yetki yoksa satır düzenleme diyaloğunu açmıyor ve silme
                    // simgesi çizilmiyor.
                    onTikla = { if (yazabilir) onPersonelSec(personel) },
                    onSil = if (yazabilir) ({ onSilIste(personel) }) else null,
                )
            }
        }
    }

    formHedefi?.let { hedef ->
        PersonelDiyalogu(
            personel = hedef.personel,
            onKapat = onFormKapat,
            onKaydet = { form -> onKaydet(hedef.personel?.id, form) },
        )
    }

    silinecek?.let { personel ->
        AlertDialog(
            onDismissRequest = onSilVazgec,
            title = { Text("Personeli sil") },
            text = {
                Text(
                    "${personel.fullName} listeden kaldırılacak. " +
                        "Geçmiş randevu ve hakediş kayıtları korunur.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onSilOnayla(personel.id) }) {
                    Text("Sil", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = onSilVazgec) { Text("Vazgeç") }
            },
        )
    }
}

@Composable
private fun PersonelSatiri(
    personel: StaffEntity,
    onTikla: () -> Unit,
    onSil: (() -> Unit)?,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        onClick = onTikla
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
                Text(personel.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${personel.role.labelTr()} | ${personel.branch.ifBlank { "-" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        PhoneNumber.formatForDisplay(personel.phone),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "₺${Money(personel.monthlySalaryMinor)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Hakediş: %${Rate(personel.commissionBasisPoints).percentLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (onSil != null) IconButton(onClick = onSil) {
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
private fun PersonelDiyalogu(
    personel: StaffEntity?,
    onKapat: () -> Unit,
    onKaydet: (PersonelFormu) -> Unit,
) {
    var name by remember { mutableStateOf(personel?.fullName ?: "") }
    var title by remember { mutableStateOf(personel?.title ?: "") }
    var branch by remember { mutableStateOf(personel?.branch ?: "") }
    // Hazır değerler uygulamanın kendi biçimiyle yazılıyor: ayıraç virgül ve
    // gereksiz ondalık yok.
    //
    // Önceden `Double.toString()` kullanılıyordu ve kutuda "40.0" / "2500.0"
    // görünüyordu — klavyenin ürettiği ayıraç değil, uygulamanın geri kalanının
    // yazımı da değil. Günlük semptom buydu; ama biçim aynı zamanda **geri
    // okunamıyordu**: `Double.toString()` 10.000.000'dan itibaren bilimsel
    // gösterime geçiyor ve "1.0E7" değerini `Decimals` ayrıştırıcısı 1.0E8
    // olarak okuyor. Yani o büyüklükte bir maaşı olan personel kartı açılıp
    // maaş alanına hiç dokunulmadan kaydedildiğinde maaş **on katına**
    // çıkıyordu. Yeni biçim her değerde birebir geri okunuyor.
    var rate by remember {
        mutableStateOf(personel?.let { Rate(it.commissionBasisPoints).percentLabel } ?: "")
    }
    var salary by remember {
        mutableStateOf(personel?.let { Money(it.monthlySalaryMinor).toString() } ?: "")
    }
    var phone by remember { mutableStateOf(personel?.phone ?: "") }
    var nickname by remember { mutableStateOf(personel?.nickname ?: "") }
    var authUserId by remember { mutableStateOf(personel?.authUserId ?: "") }
    var role by remember { mutableStateOf(personel?.role ?: StaffRole.TRAINER) }
    var roleExpanded by remember { mutableStateOf(false) }

    val canSave = name.isNotBlank() && nickname.isNotBlank() && phone.isNotBlank()

    AlertDialog(
        onDismissRequest = onKapat,
        title = { Text(if (personel == null) "Yeni Personel" else "Personel Düzenle") },
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

                // Seçilen rolün somut karşılığı.
                //
                // Önceden yalnızca "Admin / Yönetici / Antrenör" yazıyordu ve bu
                // üç kelimenin ne verdiği hiçbir yerde görünmüyordu; salon
                // sahibi maaş ve hakediş görebilen bir yetkiyi ne verdiğini
                // bilmeden atıyordu. Satırlar elle yazılmıyor, uygulanan
                // kuralların kendisinden üretiliyor (`yetkiOzetiTr`) — yani
                // kural değişip açıklama unutulamaz.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "${role.labelTr()} yetkisi",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        role.yetkiOzetiTr().forEach { satir ->
                            Text(
                                "• $satir",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

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
                    onKaydet(
                        PersonelFormu(
                            name = name.trim(),
                            title = title.trim(),
                            branch = branch.trim(),
                            // Alan **yüzde** alır; baz puana çevrim tek noktada.
                            commissionPercent = Decimals.parseOrDefault(rate),
                            salary = Decimals.parseOrDefault(salary),
                            phone = phone.trim(),
                            nickname = nickname.trim(),
                            role = role,
                            authUserId = authUserId.trim().takeIf { it.isNotBlank() },
                        )
                    )
                }
            ) { Text(if (personel == null) "Ekle" else "Güncelle") }
        },
        dismissButton = {
            TextButton(onClick = onKapat) { Text("İptal") }
        }
    )
}
