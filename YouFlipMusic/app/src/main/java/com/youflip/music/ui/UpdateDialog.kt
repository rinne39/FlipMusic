package com.youflip.music.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** "New version" dialog: release notes, then a progress bar while the APK downloads. */
@Composable
fun UpdateDialog(vm: MainViewModel) {
    val ui by vm.update.collectAsState()
    val info = ui.info ?: return
    if (!ui.show) return
    val progress = ui.progress
    AlertDialog(
        onDismissRequest = vm::dismissUpdate,
        title = { Text("新版本 ${info.versionName}") },
        text = {
            Column {
                if (info.notes.isNotBlank()) {
                    Text(
                        info.notes.trim(), style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    )
                }
                if (progress != null) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("正在下载 ${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::startUpdate, enabled = progress == null) { Text("更新") } },
        dismissButton = { if (progress == null) TextButton(onClick = vm::dismissUpdate) { Text("以后") } },
    )
}
