package app.gibiflix.ui.more

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gibiflix.R
import app.gibiflix.backup.BackupReminderWorker
import app.gibiflix.container
import app.gibiflix.core.settings.UserSettings
import app.gibiflix.ui.components.SwitchRow
import app.gibiflix.ui.components.TopBar
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val user by c.settings.user.collectAsStateWithLifecycle(UserSettings())
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var full by remember { mutableStateOf(false) }
    var passwords by remember { mutableStateOf(false) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; message = null
        scope.launch {
            message = try {
                c.backup.create(uri, full, passwords) { d, t -> progress = d to t }
                context.getString(R.string.backup_done)
            } catch (e: Exception) { context.getString(R.string.backup_failed, e.message ?: "") }
            busy = false; progress = null
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; message = null
        scope.launch {
            message = try {
                val r = c.backup.restore(uri) { name -> message = name }
                context.getString(R.string.restore_done, r.progress, r.bookmarks, r.collections, r.sessions, r.comics)
            } catch (e: Exception) { context.getString(R.string.backup_failed, e.message ?: "") }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.backup_title), onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.backup_light_hint), style = MaterialTheme.typography.bodyMedium)
            Text(
                if (user.lastBackupAt > 0) stringResource(R.string.backup_last, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(user.lastBackupAt)))
                else stringResource(R.string.backup_never),
                style = MaterialTheme.typography.bodySmall,
            )
            SwitchRow(stringResource(R.string.backup_full), full, stringResource(R.string.backup_full_hint)) { full = it }
            SwitchRow(stringResource(R.string.backup_passwords), passwords, stringResource(R.string.backup_passwords_hint)) { passwords = it }
            Button(onClick = {
                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                create.launch("gibiflix-backup-${if (full) "completo" else "leve"}-$stamp.zip")
            }, enabled = !busy, elevation = null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_create)) }
            OutlinedButton(onClick = { restore.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_restore))
            }
            progress?.let { (d, t) -> LinearProgressIndicator(progress = { if (t == 0) 0f else d.toFloat() / t }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {}) }
            if (busy && progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            SwitchRow(stringResource(R.string.backup_reminder), user.backupReminder, stringResource(R.string.backup_reminder_hint)) { on ->
                scope.launch { c.settings.updateUser { it.copy(backupReminder = on) } }
                BackupReminderWorker.schedule(context, on)
            }
        }
    }
}
