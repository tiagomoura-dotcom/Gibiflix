package app.gibiflix.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.gibiflix.R

/** Barra de topo simples (sem sombra), com voltar e ações. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    Column(Modifier.statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                maxLines = 1,
            )
            actions()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Ajuste numérico com botões − e + (mais fácil que slider numa tela e-ink). */
@Composable
fun StepperRow(title: String, value: Int, range: IntRange, step: Int = 1, format: (Int) -> String = { it.toString() }, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = { onChange((value - step).coerceIn(range)) },
            enabled = value > range.first,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(48.dp),
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            format(value),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 64.dp),
        )
        OutlinedButton(
            onClick = { onChange((value + step).coerceIn(range)) },
            enabled = value < range.last,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(48.dp),
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

/** Linha que abre um diálogo de escolha única. */
@Composable
fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title, options.firstOrNull { it.first == selected }?.second, onClick = { open = true })
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { (v, label) -> RadioRow(label, v == selected) { open = false; onSelect(v) } }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
