package app.gibiflix.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.data.db.BookmarkEntity
import app.gibiflix.ui.components.TopBar
import app.gibiflix.ui.library.LibraryViewModel
import app.gibiflix.ui.library.TextInputDialog
import app.gibiflix.ui.openComic
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Lista geral de marcadores (ou de um quadrinho só). Tocar abre o leitor no ponto exato. */
@Composable
fun BookmarksScreen(onBack: () -> Unit, comicId: String? = null) {
    val context = LocalContext.current
    val c = context.container
    val flow = remember(comicId) { if (comicId != null) c.db.bookmarks().observeFor(comicId) else c.db.bookmarks().observeAll() }
    val marks by flow.collectAsStateWithLifecycle(emptyList())
    val comics by c.library.comics.collectAsStateWithLifecycle(emptyList())
    val byId = remember(comics) { comics.associateBy { it.id } }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<BookmarkEntity?>(null) }
    val fmt = remember { DateFormat.getDateInstance(DateFormat.SHORT) }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.bookmarks_title), onBack = onBack)
        if (marks.isEmpty()) {
            Text(stringResource(R.string.bookmarks_empty), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(marks.filter { it.comicId in byId }, key = { it.id }) { b ->
                val comic = byId[b.comicId]
                Row(
                    Modifier.fillMaxWidth().clickable { openComic(context, b.comicId, b.page, b.panel) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(comic?.let { LibraryViewModel.displayTitle(it) }.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.bookmark_position, b.page + 1, b.panel + 1) + " · " + fmt.format(Date(b.updatedAt)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (b.note.isNotBlank()) Text(b.note, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { editing = b }) { Icon(Icons.Outlined.Edit, stringResource(R.string.bookmark_note)) }
                    IconButton(onClick = {
                        scope.launch { c.db.bookmarks().upsert(b.copy(deleted = true, updatedAt = System.currentTimeMillis())) }
                    }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    editing?.let { b ->
        TextInputDialog(
            title = stringResource(R.string.bookmark_note),
            label = stringResource(R.string.bookmark_note),
            initial = b.note,
            confirm = stringResource(R.string.save),
            onDismiss = { editing = null },
            onConfirm = { text ->
                editing = null
                scope.launch { c.db.bookmarks().upsert(b.copy(note = text.trim(), updatedAt = System.currentTimeMillis())) }
            },
        )
    }
}
