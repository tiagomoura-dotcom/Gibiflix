package app.gibiflix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.ui.library.LibraryActions
import app.gibiflix.ui.library.LibraryScreen
import app.gibiflix.ui.settings.DiagnosticsScreen
import app.gibiflix.ui.settings.OnyxConfirmDialog
import app.gibiflix.ui.settings.SettingsScreen
import app.gibiflix.ui.share.ShareScreen

enum class Tab { LIBRARY, SHARE, SETTINGS }

/** Telas empilhadas por cima das abas. */
sealed interface Route {
    data object Diagnostics : Route
    data object Servers : Route
    data class Editor(val comicId: String) : Route
}

class Navigator(initial: List<Route> = emptyList()) {
    val stack = mutableStateListOf<Route>().apply { addAll(initial) }
    fun push(route: Route) { stack.add(route) }
    fun pop(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); true } else false
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val deviceProfile = context.container.deviceProfile
    val profileState by deviceProfile.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.LIBRARY) }
    val nav = remember { Navigator() }

    BackHandler(enabled = nav.stack.isNotEmpty()) { nav.pop() }
    BackHandler(enabled = nav.stack.isEmpty() && tab != Tab.LIBRARY) { tab = Tab.LIBRARY }

    val top = nav.stack.lastOrNull()
    if (top != null) {
        when (top) {
            Route.Diagnostics -> DiagnosticsScreen(onBack = { nav.pop() })
            Route.Servers -> PlaceholderScreen(onBack = { nav.pop() })
            is Route.Editor -> PlaceholderScreen(onBack = { nav.pop() })
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
                            onPrepare = { id -> openPrepare(context, nav, id) },
                            onOpenSettings = { tab = Tab.SETTINGS },
                            onImportDevice = { tab = Tab.SHARE },
                            onImportOpds = { nav.push(Route.Servers) },
                        )
                    )
                    Tab.SHARE -> ShareScreen()
                    Tab.SETTINGS -> SettingsScreen(onOpenDiagnostics = { nav.push(Route.Diagnostics) })
                }
            }
        }
    }

    if (profileState.loaded && profileState.needsOnyxConfirmation) {
        OnyxConfirmDialog(suggested = deviceProfile.detected)
    }
}

@Composable
private fun BottomBar(tab: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
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

fun openComic(context: android.content.Context, id: String, page: Int = -1, panel: Int = -1) {
    context.startActivity(app.gibiflix.reader.ReaderActivity.intent(context, id, page, panel))
}

fun openPrepare(context: android.content.Context, nav: Navigator, id: String) {
    nav.push(Route.Editor(id))
}

@Composable
private fun PlaceholderScreen(onBack: () -> Unit) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        app.gibiflix.ui.components.TopBar(stringResource(R.string.coming_soon), onBack = onBack)
    }
}
