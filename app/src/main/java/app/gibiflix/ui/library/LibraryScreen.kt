package app.gibiflix.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.ui.components.GibiLogo
import app.gibiflix.ui.components.LogoWithWordmark
import app.gibiflix.ui.theme.LocalProfile

@Composable
fun LibraryScreen(onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LibraryHeader(onSearch = {}, onImport = {})
        ProfileStrip(onClick = onOpenSettings)
        EmptyLibrary(onImport = {})
    }
}

@Composable
fun LibraryHeader(onSearch: () -> Unit, onImport: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoWithWordmark()
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) {
            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.library_search))
        }
        IconButton(onClick = onImport) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.library_import))
        }
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
private fun EmptyLibrary(onImport: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            GibiLogo(112.dp)
            Text(
                stringResource(R.string.library_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.library_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onImport,
                colors = ButtonDefaults.buttonColors(),
                elevation = null,
                modifier = Modifier.height(56.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(stringResource(R.string.library_empty_button), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
