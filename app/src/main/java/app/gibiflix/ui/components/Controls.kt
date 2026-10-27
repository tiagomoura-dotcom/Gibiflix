package app.gibiflix.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gibiflix.ui.theme.LocalProfile

/**
 * Abas em forma de pílulas. A selecionada é invertida (fundo escuro/vermelho, texto claro),
 * o que funciona também no e-ink preto e branco.
 */
@Composable
fun <T> PillTabs(items: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (value, label) ->
            val sel = value == selected
            val shape = MaterialTheme.shapes.extraLarge
            Box(
                Modifier
                    .clip(shape)
                    .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
                    .border(BorderStroke(1.5.dp, if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outline), shape)
                    .clickable { onSelect(value) }
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    color = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Selo de estado (Novo, Pronto...). Em e-ink usa contorno/inversão, sem depender de cor. */
@Composable
fun Badge(text: String, strong: Boolean = false, modifier: Modifier = Modifier) {
    val eink = LocalProfile.current.isEink
    val bg = when {
        strong -> MaterialTheme.colorScheme.primary
        eink -> MaterialTheme.colorScheme.background
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = when {
        strong -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = bg,
        contentColor = fg,
        shape = MaterialTheme.shapes.extraSmall,
        border = if (eink && !strong) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        modifier = modifier,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
