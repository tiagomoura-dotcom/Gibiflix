package app.gibiflix.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gibiflix.BuildConfig
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.ui.components.TopBar

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val state by container.deviceProfile.state.collectAsStateWithLifecycle()
    val rows = remember(state) {
        container.deviceProfile.diagnostics(BuildConfig.VERSION_NAME, container.eink.name)
    }
    val copied = stringResource(R.string.diag_copied)
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.diag_title), onBack = onBack) {
            TextButton(onClick = {
                val text = "Gibiflix — Diagnóstico\n" + rows.joinToString("\n") { "${it.first}: ${it.second}" }
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("Gibiflix", text))
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.diag_copy)) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows) { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(labelFor(k), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.45f))
                    Text(v, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(0.55f))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun labelFor(key: String): String = when (key) {
    "resolution" -> stringResource(R.string.diag_resolution)
    "density" -> stringResource(R.string.diag_density)
    "detected" -> stringResource(R.string.diag_detected)
    "inUse" -> stringResource(R.string.diag_in_use)
    "choice" -> stringResource(R.string.settings_profile)
    "eink" -> stringResource(R.string.diag_eink_controller)
    "app" -> stringResource(R.string.diag_app_version)
    "SDK_INT", "RELEASE" -> stringResource(R.string.diag_android) + " ($key)"
    else -> key
}
