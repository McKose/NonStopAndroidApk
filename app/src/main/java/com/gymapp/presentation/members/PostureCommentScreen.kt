package com.gymapp.presentation.members

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gymapp.data.local.entity.PostureCommentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Postür / duruş gözlem notları — tarihlendirilmiş liste.
 * Yeni not ekleme, var olan notları düzenleme ve silme burada yapılır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostureCommentScreen(
    memberId: Long,
    onNavigateBack: () -> Unit,
    viewModel: MemberViewModel = hiltViewModel()
) {
    val comments by viewModel.getPostureComments(memberId).collectAsState(initial = emptyList())

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PostureCommentEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<PostureCommentEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Postür Notları") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editing = null
                    showEditor = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Yeni Not") }
            )
        }
    ) { padding ->
        if (comments.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Henüz postür notu yok",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "\"Yeni Not\" butonu ile tarihli gözlem ekleyebilirsin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(comments, key = { it.id }) { entity ->
                    PostureCommentCard(
                        entity = entity,
                        onEdit = {
                            editing = entity
                            showEditor = true
                        },
                        onDelete = { pendingDelete = entity }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showEditor) {
        PostureCommentEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false; editing = null },
            onSave = { dateMs, text ->
                val current = editing
                if (current != null) {
                    viewModel.updatePostureComment(
                        current.copy(dateMs = dateMs, comment = text.trim())
                    )
                } else {
                    viewModel.addPostureComment(memberId, text, dateMs)
                }
                showEditor = false
                editing = null
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Notu sil") },
            text = { Text("Bu postür notu silinecek. Devam edilsin mi?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePostureComment(target)
                    pendingDelete = null
                }) { Text("Sil") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun PostureCommentCard(
    entity: PostureCommentEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = formatDate(entity.dateMs),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Düzenle")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(
                text = entity.comment,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostureCommentEditorDialog(
    initial: PostureCommentEntity?,
    onDismiss: () -> Unit,
    onSave: (dateMs: Long, comment: String) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial?.comment ?: "") }
    var dateMs by remember(initial) { mutableStateOf(initial?.dateMs ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dateMs = it }
                    showDatePicker = false
                }) { Text("Tamam") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Vazgeç") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Yeni Postür Notu" else "Postür Notunu Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tarih: ${formatDate(dateMs)}")
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Gözlem / Not") },
                    placeholder = { Text("Örn: Kamburluk azalmış, sağ omuz hâlâ düşük.") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(dateMs, text) },
                enabled = text.isNotBlank()
            ) { Text("Kaydet") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Vazgeç")
            }
        }
    )
}

private val dateFormatter by lazy {
    SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
}

private fun formatDate(ms: Long): String = dateFormatter.format(Date(ms))
