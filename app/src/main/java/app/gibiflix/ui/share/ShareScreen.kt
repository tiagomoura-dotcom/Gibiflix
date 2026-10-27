package app.gibiflix.ui.share

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.core.sync.RemoteState
import app.gibiflix.data.db.ComicEntity
import app.gibiflix.share.ShareHub
import app.gibiflix.share.ShareService
import app.gibiflix.ui.components.TopBar
import app.gibiflix.ui.library.LibraryViewModel

@Composable
fun ShareScreen(startInSendMode: Boolean = false, onConsumedStart: () -> Unit = {}, vm: ShareViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(startInSendMode) {
        if (startInSendMode) { vm.setMode(ShareMode.SEND); onConsumedStart() }
    }
    BackHandler(enabled = ui.mode != ShareMode.HOME) { vm.setMode(ShareMode.HOME) }
    when (ui.mode) {
        ShareMode.HOME -> ShareHome(vm, ui)
        ShareMode.SEND -> SendPanel(onBack = { vm.setMode(ShareMode.HOME) })
        ShareMode.RECEIVE -> ReceivePanel(vm, ui)
    }
}

@Composable
private fun BigAction(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ShareHome(vm: ShareViewModel, ui: ShareUi) {
    val context = LocalContext.current
    var exportPick by remember { mutableStateOf(false) }
    val chosen = remember { mutableStateListOf<String>() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            vm.exportToFolder(uri, chosen.toList())
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.importFromFolder(uri)
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.share_title), onBack = null)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BigAction(stringResource(R.string.share_send), stringResource(R.string.share_send_hint)) { vm.setMode(ShareMode.SEND) }
            BigAction(stringResource(R.string.share_receive), stringResource(R.string.share_receive_hint)) { vm.setMode(ShareMode.RECEIVE) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            BigAction(stringResource(R.string.share_export), stringResource(R.string.share_export_hint)) { exportPick = true }
            BigAction(stringResource(R.string.share_import_folder), stringResource(R.string.share_import_folder_hint)) { importLauncher.launch(null) }
            ui.exportProgress?.let { (d, t) ->
                LinearProgressIndicator(progress = { if (t == 0) 0f else d.toFloat() / t }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
            }
            ui.message?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
    if (exportPick) {
        val comics by context.container.library.comics.collectAsStateWithLifecycle(emptyList())
        ComicPicker(comics, chosen, onDismiss = { exportPick = false }, onConfirm = {
            exportPick = false
            if (chosen.isNotEmpty()) exportLauncher.launch(null)
        })
    }
}

@Composable
private fun ComicPicker(comics: List<ComicEntity>, chosen: MutableList<String>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_export)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    TextButton(onClick = { chosen.clear(); chosen.addAll(comics.map { it.id }) }) { Text(stringResource(R.string.select_all)) }
                }
                items(LibraryViewModel.sortBySeries(comics), key = { it.id }) { c ->
                    val on = c.id in chosen
                    Row(Modifier.fillMaxWidth().clickable { if (on) chosen.remove(c.id) else chosen.add(c.id) }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Text(LibraryViewModel.displayTitle(c), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.share_choose_folder)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SendPanel(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by ShareHub.send.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        ShareService.start(context)
        onDispose { ShareService.stop(context) }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.share_send), onBack = onBack)
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                state.error != null -> Text(stringResource(R.string.share_server_error, state.error!!), style = MaterialTheme.typography.bodyLarge)
                !state.running -> Text(stringResource(R.string.reader_loading))
                else -> {
                    Text(stringResource(R.string.share_visible_as, state.name), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.share_code), style = MaterialTheme.typography.bodyLarge)
                    Text(state.code.chunked(3).joinToString(" "), fontSize = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text(stringResource(R.string.share_address, state.address), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.share_keep_open), style = MaterialTheme.typography.bodyMedium)
                    if (state.events > 0) Text(stringResource(R.string.share_activity, state.events), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ReceivePanel(vm: ShareViewModel, ui: ShareUi) {
    var manual by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.share_receive), onBack = { vm.disconnect(); vm.setMode(ShareMode.HOME) })
        if (ui.items.isEmpty()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.share_searching), style = MaterialTheme.typography.bodyLarge)
                ui.devices.forEach { d ->
                    BigAction(d.name, "${d.host}:${d.port}") { vm.chooseDevice(d) }
                }
                TextButton(onClick = { manual = true }) { Text(stringResource(R.string.share_manual)) }
                if (ui.connecting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                ui.message?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        } else {
            RemoteList(vm, ui)
        }
    }
    if (ui.askCode) CodeDialog(ui.device?.name.orEmpty(), onDismiss = { vm.cancelCode() }, onOk = { vm.pair(it) })
    if (manual) ManualDialog(onDismiss = { manual = false }) { h, p -> manual = false; vm.manualDevice(h, p) }
}

@Composable
private fun RemoteList(vm: ShareViewModel, ui: ShareUi) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.downloadSelected() }, enabled = !ui.busy && ui.items.any { it.selected }, elevation = null, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.share_download_selected))
            }
            OutlinedButton(onClick = { vm.syncProgress() }, enabled = !ui.busy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.share_sync))
            }
        }
        ui.message?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium) }
        LazyColumn(Modifier.fillMaxSize()) {
            RemoteState.entries.forEach { st ->
                val group = ui.items.filter { it.state == st }
                if (group.isEmpty()) return@forEach
                item(key = "h-$st") {
                    Text(
                        stringResource(
                            when (st) {
                                RemoteState.NEW -> R.string.share_group_new
                                RemoteState.UPDATED -> R.string.share_group_updated
                                RemoteState.HAVE -> R.string.share_group_have
                            }
                        ) + " (${group.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(group, key = { it.summary.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !ui.busy) { vm.toggle(item.summary.id) }.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = item.selected, onCheckedChange = null)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            val s = item.summary
                            Text(if (s.series != null && s.number != null) "${s.series} #${s.number}" else s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.share_item_info, s.pages, s.preparedPages, s.reviewedPages, s.sizeBytes / (1024 * 1024)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            item.progress?.let { p -> if (!item.done) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), drawStopIndicator = {}) }
                            if (item.done) Text(stringResource(R.string.share_item_done), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            item.error?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun CodeDialog(name: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_enter_code, name)) },
        text = {
            OutlinedTextField(
                code, { v -> code = v.filter { it.isDigit() }.take(6) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = MaterialTheme.typography.headlineMedium,
            )
        },
        confirmButton = { TextButton(onClick = { onOk(code) }, enabled = code.length == 6) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ManualDialog(onDismiss: () -> Unit, onOk: (String, Int) -> Unit) {
    var addr by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_manual)) },
        text = {
            OutlinedTextField(addr, { addr = it.trim() }, singleLine = true, label = { Text("192.168.0.10:45678") })
        },
        confirmButton = {
            TextButton(onClick = {
                val host = addr.substringBefore(':'); val port = addr.substringAfter(':', "").toIntOrNull()
                if (host.isNotBlank() && port != null) onOk(host, port)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
