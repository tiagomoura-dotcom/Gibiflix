package app.gibiflix.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.ui.theme.GibiflixTheme
import app.gibiflix.work.ImportWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deviceProfile = container.deviceProfile
        setContent {
            val state by deviceProfile.state.collectAsStateWithLifecycle()
            GibiflixTheme(state.profile) {
                AppRoot()
            }
        }
        if (savedInstanceState == null) {
            container.files.cleanTemp()
            handleIncoming(intent)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    /** "Abrir com" e "Compartilhar": copia para o cache (a permissão é temporária) e importa. */
    private fun handleIncoming(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        setIntent(Intent(this, MainActivity::class.java))
        val c = container
        c.importCenter.message(getString(R.string.import_receiving))
        lifecycleScope.launch {
            val copies = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val name = c.importer.displayName(uri).replace('/', '_')
                        val dir = File(cacheDir, "incoming/${UUID.randomUUID()}").apply { mkdirs() }
                        val out = File(dir, name)
                        contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                        Uri.fromFile(out)
                    }.getOrNull()
                }
            }
            if (copies.isNotEmpty()) ImportWorker.enqueueUris(this@MainActivity, copies, deleteAfter = true)
        }
    }
}
