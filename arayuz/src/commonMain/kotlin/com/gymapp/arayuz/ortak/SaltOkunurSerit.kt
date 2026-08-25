package com.gymapp.arayuz.ortak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * "Bu ekranı görebilirsiniz ama değiştiremezsiniz" açıklaması.
 *
 * Yetkisi olmayan kullanıcıda ekleme/silme düğmeleri gizleniyor. Tek başına
 * gizlemek yetmez: düğmenin **neden** olmadığı görünmezse kullanıcı uygulamayı
 * bozuk sanır ve destek isteyeceği yer burası olur. Bu şerit o boşluğu
 * dolduruyor.
 *
 * Ekran tamamen kapatılmıyor, yalnızca yazma kapatılıyor: eğitmenin fiyatı
 * görmesi gerekiyor (satış ekranında lazım), değiştirmesi gerekmiyor. Sunucu
 * kuralı da aynı ayrımı yapıyor — okuma salona bağlı herkese açık.
 */
@Composable
fun SaltOkunurSerit(
    text: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Lock,
                // Açıklama metni zaten aynı şeyi söylüyor; simgeyi ayrıca
                // seslendirmek ekran okuyucuda cümleyi tekrar ettirirdi.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
