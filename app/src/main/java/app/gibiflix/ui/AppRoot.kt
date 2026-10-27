package app.gibiflix.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.reader.ReaderActivity
import app.gibiflix.ui.components.TopBar
import app.gibiflix.ui.editor.EditorScreen
import app.gibiflix.ui.more.BackupScreen
import app.gibiflix.ui.more.BookmarksScreen
import app.gibiflix.ui.more.StatsScreen
import app.gibiflix.ui.opds.OpdsBrowserScreen
import app.gibiflix.ui.opds.ServersScreen
import app.gibiflix.ui.library.LibraryActions
import app.gibiflix.ui.library.LibraryScreen
import app.gibiflix.ui.settings.DiagnosticsScreen
import app.gibiflix.ui.settings.ImageSettingsScreen
import app.gibiflix.ui.settings.OnyxConfirmDialog
import app.gibiflix.ui.settings.SettingsRoutes
import app.gibiflix.ui.settings.SettingsScreen
import app.gibiflix.ui.share.ShareScreen
import kotlinx.coroutines.launch

enum class Tab { LIBRARY, SHARE, SETTINGS }

/** Telas empilhadas por cima das abas. */
sealed interface Route {
    data object Diagnostics : Route
    data object ImageSettings : Route
    data object Servers : Route
    data class Opds(val serverId: Long) : Route
    data object Stats : Route
    data object Backup : Route
    data object Bookmarks : Route
    data class Editor(val comicId: String) : Route
}

class Navigator {
    val stack = mutableStateListOf<Route>()
    fun push(route: Route) { stack.add(route) }
    fun pop(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); true } else false
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val c = context.container
    val profileState by c.deviceProfile.state.collectAsStateWithLifecycle()
    val asking by c.prepareScheduler.asking.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.LIBRARY) }
    var shareSend by rememberSaveable { mutableStateOf(false) }
    val nav = remember { Navigator() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = nav.stack.isNotEmpty()) { nav.pop() }
    BackHandler(enabled = nav.stack.isEmpty() && tab != Tab.LIBRARY) { tab = Tab.LIBRARY }

    val top = nav.stack.lastOrNull()
    if (top != null) {
        val back = { nav.pop(); Unit }
        when (top) {
            Route.Diagnostics -> DiagnosticsScreen(onBack = back)
            Route.ImageSettings -> ImageSettingsScreen(onBack = back)
            Route.Servers -> ServersScreen(onBack = back, onOpen = { nav.push(Route.Opds(it)) })
            is Route.Opds -> OpdsBrowserScreen(top.serverId, onBack = back)
            Route.Stats -> StatsScreen(onBack = back)
            Route.Backup -> BackupScreen(onBack = back)
            Route.Bookmarks -> BookmarksScreen(onBack = back)
            is Route.Editor -> EditorScreen(top.comicId, onBack = back, onSendToBoox = { nav.stack.clear(); shareSend = true; tab = Tab.SHARE })
        }
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = { BottomBar(tab) { tab = it } },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    Tab.LIBRARY -> LibraryScreen(
                        LibraryActions(
                            onRead = { id -> openComic(context, id) },
                            onReadAt = { id, page, panel -> openComic(context, id, page, panel) },
                            onPrepare = { id -> nav.push(Route.Editor(id)) },
                            onOpenSettings = { tab = Tab.SETTINGS },
                            onImportDevice = { tab = Tab.SHARE },
                            onImportOpds = { nav.push(Route.Servers) },
                        )
                    )
                    Tab.SHARE -> ShareScreen(startInSendMode = shareSend, onConsumedStart = { shareSend = false })
                    Tab.SETTINGS -> SettingsScreen(
                        SettingsRoutes(
                            diagnostics = { nav.push(Route.Diagnostics) },
                            image = { nav.push(Route.ImageSettings) },
                            servers = { nav.push(Route.Servers) },
                            stats = { nav.push(Route.Stats) },
                            backup = { nav.push(Route.Backup) },
                            bookmarks = { nav.push(Route.Bookmarks) },
                        )
                    )
                }
            }
        }
    }

    if (profileState.loaded && profileState.needsOnyxConfirmation) {
        OnyxConfirmDialog(suggested = c.deviceProfile.detected)
    } else if (asking.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { scope.launch { c.prepareScheduler.answer(false) } },
            title = { Text(stringResource(R.string.prepare_ask_title)) },
            text = { Text(stringResource(R.string.prepare_ask_text, asking.size)) },
            confirmButton = { TextButton(onClick = { scope.launch { c.prepareScheduler.answer(true) } }) { Text(stringResource(R.string.prepare_now)) } },
            dismissButton = { TextButton(onClick = { scope.launch { c.prepareScheduler.answer(false) } }) { Text(stringResource(R.string.prepare_later)) } },
        )
    }
}

fun openComic(context: Context, id: String, page: Int = -1, panel: Int = -1) {
    context.startActivity(ReaderActivity.intent(context, id, page, panel))
}

@Composable
private fun BottomBar(tab: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(
            Triple(Tab.LIBRARY, R.string.nav_library, Icons.AutoMirrored.Outlined.LibraryBooks),
            Triple(Tab.SHARE, R.string.nav_share, Icons.Outlined.SwapHoriz),
            Triple(Tab.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings),
        ).forEach { (t, label, icon) ->
            NavigationBarItem(
                selected = tab == t,
                onClick = { onSelect(t) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(stringResource(label)) },
                alwaysShowLabel = true,
                colors = colors,
            )
        }
    }
}
