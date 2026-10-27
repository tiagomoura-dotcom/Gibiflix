package app.gibiflix.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.core.stats.Session
import app.gibiflix.core.stats.StatsCalculator
import app.gibiflix.ui.components.SectionTitle
import app.gibiflix.ui.components.TopBar
import app.gibiflix.ui.library.LibraryViewModel
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val sessions by c.db.sessions().observeAll().collectAsStateWithLifecycle(emptyList())
    val comics by c.library.comics.collectAsStateWithLifecycle(emptyList())
    val summary = remember(sessions) {
        StatsCalculator.compute(
            sessions.map { Session(it.comicId, it.startedAt, it.endedAt, it.pages, it.panels, it.finishedComic) },
            System.currentTimeMillis(), ZoneId.systemDefault(),
        )
    }
    val byId = remember(comics) { comics.associateBy { it.id } }
    val locale = Locale.forLanguageTag("pt-BR")
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.stats_title), onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.stats_week), duration(summary.weekMs), Modifier.weight(1f))
                StatTile(stringResource(R.string.stats_month), duration(summary.monthMs), Modifier.weight(1f))
                StatTile(stringResource(R.string.stats_streak), stringResource(R.string.stats_days, summary.streakDays), Modifier.weight(1f))
            }
            SectionTitle(stringResource(R.string.stats_finished_per_month))
            val max = (summary.finishedPerMonth.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
            Row(
                Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                summary.finishedPerMonth.forEach { m ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text(m.count.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(0.75f * m.count / max + 0.01f)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(1.dp, MaterialTheme.colorScheme.outline)
                        )
                        Text(m.month.month.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            SectionTitle(stringResource(R.string.stats_total_per_comic))
            Text(
                stringResource(R.string.stats_total, duration(summary.totalMs), summary.sessions),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp),
            )
            summary.perComicMs.entries.sortedByDescending { it.value }.take(50).forEach { (id, ms) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(byId[id]?.let { LibraryViewModel.displayTitle(it) } ?: "—", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(duration(ms), fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun duration(ms: Long): String {
    val min = ms / 60_000
    return if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
}
