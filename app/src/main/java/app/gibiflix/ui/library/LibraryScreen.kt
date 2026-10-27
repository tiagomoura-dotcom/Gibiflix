@file:OptIn(ExperimentalFoundationApi::class)

package app.gibiflix.ui.library

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.core.settings.LibrarySort
import app.gibiflix.data.db.ComicEntity
import app.gibiflix.data.db.PrepStatus
import app.gibiflix.data.db.ProgressEntity
import app.gibiflix.ui.components.Badge
import app.gibiflix.ui.components.GibiLogo
import app.gibiflix.ui.components.LogoWithWordmark
import app.gibiflix.ui.components.PillTabs
import app.gibiflix.ui.theme.LocalProfile
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest

/** Ações que a biblioteca pede para o resto do app. */
class LibraryActions(
    val onRead: (String) -> Unit,
    val onPrepare: (String) -> Unit,
    val onOpenSettings: () -> Unit,
    val onImportDevice: () -> Unit,
    val onImportOpds: () -> Unit,
)

@Composable
fun LibraryScreen(actions: LibraryActions, vm: LibraryViewModel = viewModel()) {
    val context = LocalContext.current
    val container = context.container
    val state by vm.state.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val importProgress by container.importCenter.progress.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ComicEntity?>(null) }
    var openSeries by rememberSaveable { mutableStateOf<String?>(null) }
    var openCollection by rememberSaveable { mutableStateOf<Long?>(null) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { u -> runCatching { context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            app.gibiflix.work.ImportWorker.enqueueUris(context, uris)
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            app.gibiflix.work.ImportWorker.enqueueFolder(context, uri)
        }
    }
    val importChoices = ImportChoices(
        files = { pickFiles.launch(arrayOf("*/*")) },
        folder = { pickFolder.launch(null) },
        device = actions.onImportDevice,
        opds = actions.onImportOpds,
    )

    LaunchedEffect(Unit) {
        container.importCenter.messages.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (searching) {
            SearchBar(state.query, onChange = { vm.query.value = it }, onClose = { searching = false; vm.query.value = "" })
        } else {
            LibraryHeader(onSearch = { searching = true }, importChoices = importChoices, sort = state.sort, onSort = vm::setSort)
        }
        ProfileStrip(onClick = actions.onOpenSettings)
        importProgress?.let { p ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.import_progress, p.index + 1, p.total, p.name), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), drawStopIndicator = {})
            }
        }

        if (state.loaded && state.all.isEmpty()) {
            EmptyLibrary(onImport = importChoices.files)
            return@Column
        }

        PillTabs(
            items = listOf(
                LibTab.READING to stringResource(R.string.tab_reading),
                LibTab.ALL to stringResource(R.string.tab_all),
                LibTab.SERIES to stringResource(R.string.tab_series),
                LibTab.COLLECTIONS to stringResource(R.string.tab_collections),
            ),
            selected = tab,
            onSelect = { vm.tab.value = it; openSeries = null; openCollection = null },
        )

        val onLong: (ComicEntity) -> Unit = { menuFor = it }
        when {
            openSeries != null -> {
                val items = LibraryViewModel.sortBySeries(state.all.filter { it.series == openSeries })
                SubHeader(openSeries!!, onBack = { openSeries = null })
                ComicGrid(items, state, actions, onLong)
            }
            openCollection != null -> {
                val colId = openCollection!!
                val idsFlow = remember(colId) { vm.collectionComics(colId) }
                val ids by idsFlow.collectAsStateWithLifecycle(emptyList())
                val name = state.collections.firstOrNull { it.id == openCollection }?.name.orEmpty()
                SubHeader(name, onBack = { openCollection = null })
                ComicGrid(state.all.filter { it.id in ids }, state, actions, onLong)
            }
            tab == LibTab.READING -> ComicGrid(
                state.visible, state, actions, onLong,
                header = { state.continueReading?.let { ContinueCard(it, state.progress[it.id], actions.onRead) } },
                empty = stringResource(R.string.reading_empty),
            )
            tab == LibTab.ALL -> ComicGrid(state.visible, state, actions, onLong)
            tab == LibTab.SERIES -> SeriesList(state, onOpen = { openSeries = it })
            tab == LibTab.COLLECTIONS -> CollectionsList(state, vm, onOpen = { openCollection = it })
        }
    }

    menuFor?.let { comic ->
        ComicActionsDialog(
            comic = comic,
            vm = vm,
            actions = actions,
            onDismiss = { menuFor = null },
        )
    }
}

class ImportChoices(val files: () -> Unit, val folder: () -> Unit, val device: () -> Unit, val opds: () -> Unit)

@Composable
fun LibraryHeader(onSearch: () -> Unit, importChoices: ImportChoices, sort: LibrarySort, onSort: (LibrarySort) -> Unit) {
    var importMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoWithWordmark()
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) {
            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.library_search))
        }
        Box {
            IconButton(onClick = { sortMenu = true }) {
                Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.sort))
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                listOf(
                    LibrarySort.RECENT_READ to R.string.sort_recent_read,
                    LibrarySort.RECENT_ADDED to R.string.sort_recent_added,
                    LibrarySort.TITLE to R.string.sort_title,
                    LibrarySort.SERIES to R.string.sort_series,
                ).forEach { (s, label) ->
                    DropdownMenuItem(
                        text = { Text((if (s == sort) "● " else "") + stringResource(label)) },
                        onClick = { sortMenu = false; onSort(s) },
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { importMenu = true }) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.library_import))
            }
            DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.import_files)) }, onClick = { importMenu = false; importChoices.files() })
                DropdownMenuItem(text = { Text(stringResource(R.string.import_folder)) }, onClick = { importMenu = false; importChoices.folder() })
                DropdownMenuItem(text = { Text(stringResource(R.string.import_device)) }, onClick = { importMenu = false; importChoices.device() })
                DropdownMenuItem(text = { Text(stringResource(R.string.import_opds)) }, onClick = { importMenu = false; importChoices.opds() })
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.search_close)) }
    }
}

/** "Boox Go 7 Color detectado · perfil e-ink ativo" (só no e-ink). */
@Composable
fun ProfileStrip(onClick: () -> Unit) {
    val profile = LocalProfile.current
    if (!profile.isEink) return
    val name = LocalContext.current.container.deviceProfile.displayName
    Surface(
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
    ) {
        Text(
            stringResource(R.string.profile_strip, name),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            GibiLogo(112.dp)
            Text(stringResource(R.string.library_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.library_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onImport, elevation = null, modifier = Modifier.height(56.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(stringResource(R.string.library_empty_button), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
fun gridColumns(configured: Int, maxWidthDp: Float): Int {
    if (configured > 0) return configured
    val eink = LocalProfile.current.isEink
    return if (eink) {
        if (maxWidthDp < 700) 4 else 6
    } else {
        (maxWidthDp / 150f).toInt().coerceIn(3, 8)
    }
}

@Composable
private fun ComicGrid(
    items: List<ComicEntity>,
    state: LibraryState,
    actions: LibraryActions,
    onLong: (ComicEntity) -> Unit,
    header: (@Composable () -> Unit)? = null,
    empty: String? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = gridColumns(state.columns, maxWidth.value)
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
            if (items.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        empty ?: stringResource(R.string.no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(items, key = { it.id }) { comic ->
                ComicCard(comic, state.progress[comic.id], onClick = { actions.onRead(comic.id) }, onLong = { onLong(comic) })
            }
        }
    }
}

@Composable
fun Cover(comic: ComicEntity, modifier: Modifier = Modifier) {
    val file = LocalContext.current.container.files.coverFile(comic.id)
    val eink = LocalProfile.current.isEink
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(MaterialTheme.shapes.small)
            .background(if (eink) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainer)
            .let { if (eink) it.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small) else it },
    ) {
        AsyncImage(
            model = coil.request.ImageRequest.Builder(LocalContext.current)
                .data(file)
                .memoryCacheKey("${comic.id}-${comic.revision}-${file.lastModified()}")
                .crossfade(!eink)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun statusText(comic: ComicEntity): String = when {
    comic.read -> stringResource(R.string.status_read)
    comic.status == PrepStatus.PREPARING -> stringResource(R.string.status_preparing, comic.prepareProgress)
    comic.status == PrepStatus.REVIEW -> stringResource(R.string.status_review)
    comic.status == PrepStatus.READY -> stringResource(R.string.status_ready)
    else -> stringResource(R.string.status_new)
}

@Composable
private fun ComicCard(comic: ComicEntity, progress: ProgressEntity?, onClick: () -> Unit, onLong: () -> Unit) {
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLong)) {
        Box {
            Cover(comic, Modifier.fillMaxWidth())
            Badge(
                statusText(comic),
                strong = comic.status == PrepStatus.REVIEW && !comic.read,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
            )
        }
        if (progress != null && !progress.finished && progress.pageCount > 0) {
            LinearProgressIndicator(
                progress = { (progress.page + 1f) / progress.pageCount },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp),
                drawStopIndicator = {},
            )
        }
        Text(
            LibraryViewModel.displayTitle(comic),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        LibraryViewModel.subtitle(comic)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ContinueCard(comic: ComicEntity, progress: ProgressEntity?, onRead: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = if (LocalProfile.current.isEink) BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(comic, Modifier.width(84.dp))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.continue_reading), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(LibraryViewModel.displayTitle(comic), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (progress != null) {
                    Text(
                        positionText(progress, comic.pageCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { (progress.page + 1f) / maxOf(1, comic.pageCount) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        drawStopIndicator = {},
                    )
                }
                Button(onClick = { onRead(comic.id) }, elevation = null, modifier = Modifier.height(48.dp)) {
                    Text(stringResource(R.string.continue_button))
                }
            }
        }
    }
}

@Composable
fun positionText(p: ProgressEntity, pageCount: Int): String {
    val panels = p.panelCount
    return if (p.mode == "panel" && panels > 0) {
        stringResource(R.string.continue_position, p.page + 1, pageCount, p.panel + 1, panels)
    } else {
        stringResource(R.string.continue_position_page, p.page + 1, pageCount)
    }
}

@Composable
private fun SeriesList(state: LibraryState, onOpen: (String) -> Unit) {
    if (state.series.isEmpty()) {
        Text(stringResource(R.string.no_results), modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
        return
    }
    val byId = remember(state.all) { state.all.associateBy { it.id } }
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.series, key = { it.series }) { s ->
            val volumes = LibraryViewModel.sortBySeries(state.all.filter { it.series == s.series })
            val next = volumes.firstOrNull { !it.read }
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(s.series) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                byId[s.coverId]?.let { Cover(it, Modifier.width(56.dp)) }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(s.series, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.series_count, s.count, s.readCount), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        next?.let { stringResource(R.string.series_next, LibraryViewModel.displayTitle(it)) } ?: stringResource(R.string.series_all_read),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun CollectionsList(state: LibraryState, vm: LibraryViewModel, onOpen: (Long) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val byId = remember(state.all) { state.all.associateBy { it.id } }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Button(onClick = { creating = true }, elevation = null, modifier = Modifier.padding(16.dp).height(48.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(stringResource(R.string.collection_new), modifier = Modifier.padding(start = 8.dp))
            }
            if (state.collections.isEmpty()) {
                Text(stringResource(R.string.collections_empty), modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        items(state.collections, key = { it.id }) { col ->
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(onClick = { onOpen(col.id) }, onLongClick = { editing = col.id to col.name })
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val cover = col.coverId?.let { byId[it] }
                if (cover != null) Cover(cover, Modifier.width(56.dp)) else Spacer(Modifier.size(56.dp, 84.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(col.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.collection_count, col.count), style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    if (creating) {
        TextInputDialog(
            title = stringResource(R.string.collection_new),
            label = stringResource(R.string.collection_name),
            initial = "",
            confirm = stringResource(R.string.create),
            onDismiss = { creating = false },
            onConfirm = { creating = false; if (it.isNotBlank()) vm.createCollection(it) },
        )
    }
    editing?.let { (id, name) ->
        TextInputDialog(
            title = stringResource(R.string.collection_rename),
            label = stringResource(R.string.collection_name),
            initial = name,
            confirm = stringResource(R.string.save),
            onDismiss = { editing = null },
            onConfirm = { editing = null; if (it.isNotBlank()) vm.renameCollection(id, it) },
            extra = stringResource(R.string.collection_delete) to { editing = null; vm.deleteCollection(id) },
        )
    }
}
