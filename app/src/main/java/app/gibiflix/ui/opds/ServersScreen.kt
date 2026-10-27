package app.gibiflix.ui.opds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.data.db.ServerEntity
import app.gibiflix.opds.SecretBox
import app.gibiflix.ui.components.TopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ServersScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val c = LocalContext.current.container
    val servers by c.db.servers().observeAll().collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<ServerEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.servers_title), onBack = onBack) {
            IconButton(onClick = { creating = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.server_add)) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (servers.isEmpty()) item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.servers_empty), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.servers_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { creating = true }) { Text(stringResource(R.string.server_add)) }
                }
            }
            items(servers, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(s.id) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                        Text(s.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = { editing = s }) { Icon(Icons.Outlined.Edit, stringResource(R.string.server_edit)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if (creating || editing != null) {
        ServerDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onDelete = editing?.let { e -> { scope.launch { c.db.servers().delete(e.id) }; editing = null } },
            onSave = { name, url, user, pass ->
                val base = editing
                creating = false; editing = null
                scope.launch {
                    val enc = withContext(Dispatchers.Default) {
                        when {
                            pass == null -> base?.passwordEnc
                            pass.isEmpty() -> null
                            else -> SecretBox.encrypt(pass)
                        }
                    }
                    c.db.servers().upsert(
                        ServerEntity(id = base?.id ?: 0, name = name.trim(), url = url.trim(), username = user.trim().ifEmpty { null }, passwordEnc = enc)
                    )
                }
            },
        )
    }
}

/** [onSave] recebe senha null quando o campo ficou intacto (mantém a anterior). */
@Composable
private fun ServerDialog(
    initial: ServerEntity?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, String, String, String?) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url ?: "http://") }
    var user by remember { mutableStateOf(initial?.username.orEmpty()) }
    var pass by remember { mutableStateOf("") }
    var passTouched by remember { mutableStateOf(initial == null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.server_add else R.string.server_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.server_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.server_url)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text(stringResource(R.string.server_user)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    pass, { pass = it; passTouched = true },
                    label = { Text(stringResource(if (initial?.passwordEnc != null) R.string.server_password_keep else R.string.server_password)) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.servers_help), style = MaterialTheme.typography.bodySmall)
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.ifBlank { url }, url, user, if (passTouched) pass else null) }, enabled = url.startsWith("http")) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
