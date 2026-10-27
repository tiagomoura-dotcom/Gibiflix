package app.gibiflix.ui.library

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.data.db.CollectionEntity
import app.gibiflix.data.db.ComicEntity
import app.gibiflix.data.db.PrepStatus
import app.gibiflix.ui.components.RadioRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Sub { MENU, EDIT, COLLECTION, DELETE }

@Composable
fun ComicActionsDialog(comic: ComicEntity, vm: LibraryViewModel, actions: LibraryActions, onDismiss: () -> Unit) {
    var sub by remember { mutableStateOf(Sub.MENU) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportedMsg = stringResource(R.string.exported)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.comicbook+zip")) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) {
                val src = context.container.files.packageFile(comic.id)
                context.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            }
            Toast.makeText(context, exportedMsg, Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
    when (sub) {
        Sub.MENU -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(LibraryViewModel.displayTitle(comic)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.pages_count, comic.pageCount), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                    MenuRow(stringResource(R.string.action_read)) { onDismiss(); actions.onRead(comic.id) }
                    MenuRow(
                        stringResource(if (comic.status == PrepStatus.NEW) R.string.action_prepare else R.string.action_review)
                    ) { onDismiss(); actions.onPrepare(comic.id) }
                    MenuRow(stringResource(R.string.action_edit)) { sub = Sub.EDIT }
                    MenuRow(stringResource(R.string.action_collection)) { sub = Sub.COLLECTION }
                    MenuRow(stringResource(if (comic.read) R.string.action_mark_unread else R.string.action_mark_read)) {
                        vm.setRead(comic.id, !comic.read); onDismiss()
                    }
                    MenuRow(stringResource(R.string.action_export)) {
                        export.launch(LibraryViewModel.displayTitle(comic).replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".cbz")
                    }
                    MenuRow(stringResource(R.string.action_delete)) { sub = Sub.DELETE }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        )
        Sub.EDIT -> EditInfoDialog(comic, onDismiss) { title, series, number, dir ->
            vm.editInfo(comic.id, title, series, number, dir); onDismiss()
        }
        Sub.COLLECTION -> CollectionPickerDialog(comic, vm, onDismiss)
        Sub.DELETE -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(stringResource(R.string.delete_confirm, LibraryViewModel.displayTitle(comic))) },
            confirmButton = { TextButton(onClick = { vm.delete(comic.id); onDismiss() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(vertical = 12.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun EditInfoDialog(comic: ComicEntity, onDismiss: () -> Unit, onSave: (String, String?, String?, String) -> Unit) {
    var title by remember { mutableStateOf(comic.title) }
    var series by remember { mutableStateOf(comic.series.orEmpty()) }
    var number by remember { mutableStateOf(comic.number.orEmpty()) }
    var dir by remember { mutableStateOf(comic.readingDirection) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.edit_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(series, { series = it }, label = { Text(stringResource(R.string.edit_series)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(number, { number = it }, label = { Text(stringResource(R.string.edit_number)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.direction), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                RadioRow(stringResource(R.string.direction_ltr), dir == "ltr") { dir = "ltr" }
                RadioRow(stringResource(R.string.direction_rtl), dir == "rtl") { dir = "rtl" }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, series, number, dir) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CollectionPickerDialog(comic: ComicEntity, vm: LibraryViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val all = remember { mutableStateListOf<CollectionEntity>() }
    val inside = remember { mutableStateListOf<Long>() }
    var newName by remember { mutableStateOf("") }
    LaunchedEffect(comic.id) {
        all.clear(); all.addAll(context.container.library.allCollections())
        inside.clear(); inside.addAll(context.container.library.collectionsOf(comic.id))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_collection)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                all.forEach { col ->
                    val checked = col.id in inside
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (checked) inside.remove(col.id) else inside.add(col.id)
                            vm.setInCollection(col.id, comic.id, !checked)
                        }.heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(col.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                OutlinedTextField(
                    newName, { newName = it },
                    label = { Text(stringResource(R.string.collection_new)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newName.isNotBlank()) vm.createCollection(newName, addComic = comic.id)
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    extra: Pair<String, () -> Unit>? = null,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (extra != null) {
                    TextButton(onClick = extra.second, modifier = Modifier.padding(top = 8.dp)) { Text(extra.first) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
