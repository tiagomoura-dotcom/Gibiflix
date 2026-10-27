package app.gibiflix.reader

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.core.pkg.NRect
import app.gibiflix.core.reader.ViewKind
import app.gibiflix.core.settings.ReadMode
import app.gibiflix.ui.theme.LocalAnimationsEnabled
import app.gibiflix.ui.theme.LocalProfile
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ReaderScreen(vm: ReaderViewModel, onExit: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val profile = LocalProfile.current
    val eink = profile.isEink
    val bg = if (eink) Color.White else Color.Black
    val fg = if (eink) Color.Black else Color.White
    val flash by LocalContext.current.container.eink.flash.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    BackHandler(enabled = ui.menu) { vm.hideMenu() }

    BoxWithConstraints(Modifier.fillMaxSize().background(bg)) {
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        LaunchedEffect(wPx, hPx) { vm.setScreen(wPx, hPx) }
        val swipeThreshold = with(density) { 80.dp.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(ui.direction, vm.readerSettings.tapZones) {
                    detectTapGestures(
                        onDoubleTap = { vm.toggleMode() },
                        onTap = { o ->
                            val cur = vm.ui.value
                            val third = size.width / 3f
                            val zones = vm.readerSettings.tapZones
                            val rtl = cur.direction == "rtl"
                            when {
                                cur.menu -> vm.hideMenu()
                                !zones -> vm.toggleMenu()
                                o.x < third -> if (rtl) vm.next() else vm.prev()
                                o.x > 2 * third -> if (rtl) vm.prev() else vm.next()
                                else -> vm.toggleMenu()
                            }
                        },
                    )
                }
                .let { m ->
                    if (eink) m else m.pointerInput(Unit) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total < -swipeThreshold) vm.next() else if (total > swipeThreshold) vm.prev()
                            },
                            onHorizontalDrag = { _, d -> total += d },
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val camera = ui.camera
            if (camera != null && LocalAnimationsEnabled.current) {
                CameraTransition(camera, wPx, hPx, vm.readerSettings.panelMarginPct, onDone = { vm.onCameraFinished(camera.id) })
            } else {
                ui.frame?.let { FrameImage(it) }
            }
            if (ui.loading) Text(stringResource(R.string.reader_loading), color = fg)
            ui.error?.let { Text(it, color = fg, modifier = Modifier.padding(24.dp)) }
        }

        if (!ui.menu && ui.pageCount > 0) {
            Indicators(vm, ui, fg, bg, profile.isMono, Modifier.align(Alignment.BottomCenter))
        }
        if (ui.menu) {
            ReaderMenu(vm, ui, onExit)
        }
        if (ui.showText && !ui.text.isNullOrEmpty()) {
            TextOverlay(
                ui.text!!.joinToString("\n\n") { it.value },
                ui.translation?.joinToString("\n\n") { it.value },
                onClose = { vm.toggleText() },
            )
        }
        if (flash) Box(Modifier.fillMaxSize().background(Color.Black))
    }

    if (ui.endReached) {
        AlertDialog(
            onDismissRequest = { vm.dismissEnd() },
            title = { Text(stringResource(R.string.reader_end_title)) },
            text = { Text(stringResource(R.string.reader_end_text, ui.title)) },
            confirmButton = { TextButton(onClick = onExit) { Text(stringResource(R.string.reader_back_library)) } },
            dismissButton = { TextButton(onClick = { vm.dismissEnd() }) { Text(stringResource(R.string.reader_stay)) } },
        )
    }
}

@Composable
private fun FrameImage(bmp: Bitmap) {
    val image = remember(bmp) { bmp.asImageBitmap() }
    val d = LocalDensity.current
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.None,
        filterQuality = FilterQuality.None,
        modifier = with(d) { Modifier.size(bmp.width.toDp(), bmp.height.toDp()) },
    )
}

/** Câmera do tablet: anima o recorte da página da vista anterior para a nova (~250 ms). */
@Composable
private fun CameraTransition(camera: Camera, wPx: Int, hPx: Int, marginPct: Int, onDone: () -> Unit) {
    val progress = remember(camera.id) { Animatable(0f) }
    LaunchedEffect(camera.id) {
        progress.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
        onDone()
    }
    val image = remember(camera.page) { camera.page.asImageBitmap() }
    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value
        fun lerp(a: Double, b: Double) = a + (b - a) * t
        val r = NRect(
            lerp(camera.from.x, camera.to.x), lerp(camera.from.y, camera.to.y),
            lerp(camera.from.w, camera.to.w), lerp(camera.from.h, camera.to.h),
        )
        val pw = image.width; val ph = image.height
        val src = IntOffset((r.x * pw).roundToInt().coerceIn(0, pw - 1), (r.y * ph).roundToInt().coerceIn(0, ph - 1))
        val srcSize = IntSize((r.w * pw).roundToInt().coerceIn(1, pw - src.x), (r.h * ph).roundToInt().coerceIn(1, ph - src.y))
        val m = 1f - 2f * marginPct / 100f
        val s = min(wPx * m / srcSize.width, hPx * m / srcSize.height)
        val dw = (srcSize.width * s).roundToInt(); val dh = (srcSize.height * s).roundToInt()
        drawImage(
            image, srcOffset = src, srcSize = srcSize,
            dstOffset = IntOffset((wPx - dw) / 2, (hPx - dh) / 2), dstSize = IntSize(dw, dh),
            filterQuality = FilterQuality.Medium,
        )
    }
}

@Composable
private fun Indicators(vm: ReaderViewModel, ui: ReaderUi, fg: Color, bg: Color, mono: Boolean, modifier: Modifier) {
    val view = ui.view ?: return
    val page = vm.pageInfo(view.page) ?: return
    val thumb by produceState<Bitmap?>(null, view.page, page.file) { value = vm.thumbnail(view.page, 120, 180) }
    val panels = page.panels.size
    Row(
        modifier
            .fillMaxWidth()
            .background(bg.copy(alpha = if (LocalProfile.current.isEink) 1f else 0.6f))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        thumb?.let { t ->
            val img = remember(t) { t.asImageBitmap() }
            val d = LocalDensity.current
            val hDp = 44.dp
            val wDp = with(d) { (hDp.toPx() * t.width / t.height).toDp() }
            Box(Modifier.size(wDp, hDp).border(1.dp, fg)) {
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                if (view.kind == ViewKind.PANEL || view.kind == ViewKind.HALF) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(
                            color = if (mono) Color.Black else Color(0xFFE1261C),
                            topLeft = Offset((view.rect.x * size.width).toFloat(), (view.rect.y * size.height).toFloat()),
                            size = Size((view.rect.w * size.width).toFloat(), (view.rect.h * size.height).toFloat()),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            val pageText = stringResource(R.string.reader_page_of, view.page + 1, ui.pageCount)
            val panelText = if (view.panelIndex >= 0) " · " + stringResource(R.string.reader_panel_of, view.panelIndex + 1, panels) else ""
            Text(pageText + panelText, color = fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            if (ui.viewsOnPage > 1) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(ui.viewsOnPage.coerceAtMost(30)) { i ->
                        Box(
                            Modifier.size(width = 14.dp, height = if (i == ui.pos.view) 6.dp else 3.dp)
                                .background(if (i <= ui.pos.view) fg else fg.copy(alpha = 0.3f))
                        )
                    }
                }
            }
            if (ui.showHint) {
                Text(stringResource(R.string.reader_hint), color = fg, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ReaderMenu(vm: ReaderViewModel, ui: ReaderUi, onExit: () -> Unit) {
    val eink = LocalProfile.current.isEink
    val surface = MaterialTheme.colorScheme.surface
    var sliderPage by remember(ui.pos.page) { mutableFloatStateOf(ui.pos.page.toFloat()) }
    var noteDialog by remember { mutableStateOf(false) }
    var marksDialog by remember { mutableStateOf(false) }
    if (noteDialog) {
        app.gibiflix.ui.library.TextInputDialog(
            title = stringResource(R.string.bookmark_add_title),
            label = stringResource(R.string.bookmark_note),
            initial = "",
            confirm = stringResource(R.string.save),
            onDismiss = { noteDialog = false },
            onConfirm = { noteDialog = false; vm.toggleBookmark(it) },
        )
    }
    if (marksDialog) {
        val marks by vm.bookmarks.collectAsStateWithLifecycle(emptyList())
        AlertDialog(
            onDismissRequest = { marksDialog = false },
            title = { Text(stringResource(R.string.bookmarks_of_comic)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (marks.isEmpty()) Text(stringResource(R.string.bookmarks_empty))
                    marks.forEach { b ->
                        Column(Modifier.fillMaxWidth().clickable { marksDialog = false; vm.hideMenu(); vm.goTo(b.page, b.panel) }.padding(vertical = 10.dp)) {
                            Text(stringResource(R.string.bookmark_position, b.page + 1, b.panel + 1), style = MaterialTheme.typography.titleSmall)
                            if (b.note.isNotBlank()) Text(b.note, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { marksDialog = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    var dragging by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        // Topo: voltar e título.
        Surface(
            color = surface,
            border = if (eink) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
        ) {
            Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                Text(ui.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (ui.bookmarked) vm.removeBookmark() else noteDialog = true }) {
                    Icon(
                        if (ui.bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        stringResource(R.string.reader_bookmark),
                    )
                }
            }
        }
        // Base: barra de páginas e ações.
        Surface(
            color = surface,
            border = if (eink) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
        ) {
            Column(Modifier.navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dragging) {
                    val p = sliderPage.roundToInt()
                    val thumb by produceState<Bitmap?>(null, p) { value = vm.thumbnail(p, 200, 300) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        thumb?.let { Image(remember(it) { it.asImageBitmap() }, null, Modifier.height(120.dp)) }
                        Text(stringResource(R.string.reader_page_of, p + 1, ui.pageCount), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Text(stringResource(R.string.reader_page_of, ui.pos.page + 1, ui.pageCount), style = MaterialTheme.typography.bodyMedium)
                }
                if (ui.pageCount > 1) {
                    Slider(
                        value = sliderPage,
                        onValueChange = { sliderPage = it; dragging = true },
                        onValueChangeFinished = { dragging = false; vm.goToPage(sliderPage.roundToInt()) },
                        valueRange = 0f..(ui.pageCount - 1).toFloat(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MenuButton(
                        stringResource(if (ui.mode == ReadMode.PANEL) R.string.reader_mode_panel else R.string.reader_mode_page),
                        Modifier.weight(1f),
                    ) { vm.toggleMode() }
                    MenuButton(
                        stringResource(if (ui.direction == "rtl") R.string.direction_rtl else R.string.direction_ltr),
                        Modifier.weight(1f),
                    ) { vm.setDirection(if (ui.direction == "rtl") "ltr" else "rtl") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MenuButton(stringResource(R.string.reader_brightness_down), Modifier.weight(1f)) { vm.adjustBrightness(-10) }
                    MenuButton(stringResource(R.string.reader_brightness_up), Modifier.weight(1f)) { vm.adjustBrightness(10) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (!ui.text.isNullOrEmpty()) {
                        MenuButton(stringResource(R.string.reader_read_text), Modifier.weight(1f)) { vm.hideMenu(); vm.toggleText() }
                    }
                    MenuButton(stringResource(R.string.bookmarks_title), Modifier.weight(1f)) { marksDialog = true }
                    MenuButton(stringResource(R.string.reader_exit), Modifier.weight(1f)) { onExit() }
                }
            }
        }
    }
}

@Composable
private fun MenuButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
    }
}

/** "Ler texto": o texto do quadro em letra grande (ajuda com balões pequenos numa tela de 7"). */
@Composable
private fun TextOverlay(text: String, translation: String?, onClose: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize().clickable(onClick = onClose),
    ) {
        Column(
            Modifier.statusBarsPadding().navigationBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.reader_read_text), style = MaterialTheme.typography.titleMedium)
            Text(text, fontSize = 30.sp, lineHeight = 40.sp)
            if (translation != null) {
                Text(stringResource(R.string.reader_translation), style = MaterialTheme.typography.titleMedium)
                Text(translation, fontSize = 26.sp, lineHeight = 36.sp)
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
    }
}
