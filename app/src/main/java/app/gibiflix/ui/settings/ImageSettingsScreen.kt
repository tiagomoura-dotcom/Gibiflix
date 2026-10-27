package app.gibiflix.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.core.device.Profile
import app.gibiflix.core.settings.ImageSettings
import app.gibiflix.core.settings.UserSettings
import app.gibiflix.reader.PageCache
import app.gibiflix.reader.PageRenderer
import app.gibiflix.ui.components.ChoiceRow
import app.gibiflix.ui.components.StepperRow
import app.gibiflix.ui.components.SwitchRow
import app.gibiflix.ui.components.TopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ajustes de imagem do perfil em uso, com prévia ao vivo. */
@Composable
fun ImageSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val state by c.deviceProfile.state.collectAsStateWithLifecycle()
    val user by c.settings.user.collectAsStateWithLifecycle(UserSettings())
    val profile = state.profile
    val img = user.forProfile(profile).image
    val scope = rememberCoroutineScope()
    fun set(f: (ImageSettings) -> ImageSettings) = scope.launch { c.settings.updateProfile(profile) { it.copy(image = f(it.image)) } }

    var source by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    val renderer = remember { PageRenderer() }

    LaunchedEffect(Unit) {
        source = withContext(Dispatchers.IO) { sampleSource(context, renderer) }
    }
    LaunchedEffect(source, img, profile) {
        val src = source ?: return@LaunchedEffect
        delay(120)
        preview = withContext(Dispatchers.Default) {
            renderer.applyFilters(src.copy(Bitmap.Config.ARGB_8888, true), img, profile == Profile.EINK_MONO)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.settings_image), onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(
                Modifier.fillMaxWidth().height(320.dp).padding(12.dp).border(1.dp, MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center,
            ) {
                preview?.let { Image(remember(it) { it.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
            Text(
                stringResource(R.string.settings_image_hint, profileLabel(profile)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            StepperRow(stringResource(R.string.image_contrast), img.contrast, -50..50, step = 5, format = ::signed) { v -> set { it.copy(contrast = v) } }
            StepperRow(stringResource(R.string.image_brightness), img.brightness, -50..50, step = 5, format = ::signed) { v -> set { it.copy(brightness = v) } }
            if (profile != Profile.EINK_MONO) {
                StepperRow(stringResource(R.string.image_saturation), img.saturation, -50..100, step = 5, format = ::signed) { v -> set { it.copy(saturation = v) } }
            }
            StepperRow(stringResource(R.string.image_midtones), img.midtones, 0..60, step = 5) { v -> set { it.copy(midtones = v) } }
            ChoiceRow(
                stringResource(R.string.image_sharpness),
                listOf(
                    0 to stringResource(R.string.sharp_off), 1 to stringResource(R.string.sharp_light),
                    2 to stringResource(R.string.sharp_medium), 3 to stringResource(R.string.sharp_strong),
                ),
                img.sharpness,
            ) { v -> set { it.copy(sharpness = v) } }
            if (profile == Profile.EINK_MONO) {
                SwitchRow(stringResource(R.string.image_dither), img.dither, stringResource(R.string.image_dither_hint)) { v -> set { it.copy(dither = v) } }
            }
            OutlinedButton(
                onClick = { scope.launch { c.settings.resetProfile(profile) } },
                modifier = Modifier.padding(16.dp),
            ) { Text(stringResource(R.string.image_reset)) }
        }
    }
}

private fun signed(v: Int) = if (v > 0) "+$v" else v.toString()

/** Um quadro de exemplo da biblioteca; sem biblioteca, uma imagem gerada. */
private suspend fun sampleSource(context: android.content.Context, renderer: PageRenderer): Bitmap {
    val c = context.container
    val comic = c.library.comics.first().maxByOrNull { it.lastReadAt ?: it.addedAt }
    if (comic != null) {
        runCatching {
            val doc = c.packages.load(comic.id)?.doc ?: error("sem doc")
            val pageIndex = doc.pages.indexOfFirst { it.panels.isNotEmpty() }.takeIf { it >= 0 } ?: (doc.pages.size / 2)
            val page = doc.pages[pageIndex]
            val file = PageCache(c.files).page(comic.id, "${doc.id.take(8)}-${doc.sourceHash.takeLast(8)}", page.file)
            val panel = page.panels.firstOrNull()?.rect
            return if (panel != null) renderer.render(file, panel, 900.0 / (panel.w * page.width), ImageSettings(), false)
            else renderer.renderWhole(file, 900, 900)
        }
    }
    // Gerada: degradê, cores e traços finos.
    val w = 600; val h = 400
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    cv.drawColor(Color.WHITE)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    for (x in 0 until w) {
        val v = x * 255 / w
        p.color = Color.rgb(v, v, v)
        cv.drawLine(x.toFloat(), 0f, x.toFloat(), 80f, p)
    }
    val colors = intArrayOf(0xFFE1261C.toInt(), 0xFF1F5FA8.toInt(), 0xFFF2C14E.toInt(), 0xFF3C9D5D.toInt())
    colors.forEachIndexed { i, col -> p.color = col; cv.drawCircle(90f + i * 140f, 180f, 55f, p) }
    p.color = Color.BLACK; p.strokeWidth = 2f; p.textSize = 34f
    for (i in 0 until 12) cv.drawLine(20f + i * 48f, 260f, 44f + i * 48f, 390f, p)
    cv.drawText("Gibiflix · Aa Bb Cc 123", 40f, 330f, p)
    return bmp
}
