package app.gibiflix.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gibiflix.R
import app.gibiflix.core.edit.PanelOps
import app.gibiflix.core.pkg.DocStats
import app.gibiflix.core.pkg.NRect
import app.gibiflix.core.pkg.PageInfo
import app.gibiflix.data.db.PrepStatus
import app.gibiflix.ui.components.StepperRow
import app.gibiflix.ui.theme.GibiColors
import app.gibiflix.ui.theme.LocalProfile
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun EditorScreen(comicId: String, onBack: () -> Unit, onSendToBoox: () -> Unit, vm: EditorViewModel = viewModel(key = "editor-$comicId")) {
    LaunchedEffect(comicId) { vm.open(comicId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val leave = { vm.flushAsync(); onBack() }
    BackHandler { leave() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EditorTopBar(ui, onBack = leave, onSend = { vm.flushAsync(); onSendToBoox() })
        val doc = ui.doc
        if (doc == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(if (ui.loading) R.string.reader_loading else R.string.reader_error))
            }
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    ThumbColumn(vm, ui, Modifier.width(150.dp).fillMaxHeight())
                    PageEditor(vm, ui, Modifier.weight(1f).fillMaxHeight())
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.width(330.dp).fillMaxHeight()) {
                        SidePanel(vm, ui, Modifier.verticalScroll(rememberScrollState()).padding(12.dp))
                    }
                }
            } else {
                var details by remember { mutableStateOf(false) }
                Column(Modifier.fillMaxSize()) {
                    ThumbRow(vm, ui)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        PageEditor(vm, ui, Modifier.fillMaxSize())
                        if (details) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxSize(),
                            ) { SidePanel(vm, ui, Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) }
                        }
                    }
                    ToolBar(vm, ui, details) { details = !details }
                }
            }
        }
    }
}

@Composable
private fun EditorTopBar(ui: EditorUi, onBack: () -> Unit, onSend: () -> Unit) {
    val doc = ui.doc
    val comic = ui.comic
    Column(Modifier.statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    stringResource(R.string.editor_title, comic?.title ?: doc?.title.orEmpty()),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val status = when {
                    comic?.status == PrepStatus.PREPARING && doc != null ->
                        stringResource(R.string.editor_detecting, (comic.prepareProgress * doc.pages.size / 100).coerceAtMost(doc.pages.size), doc.pages.size)
                    doc != null && doc.pages.none(DocStats::isPrepared) -> stringResource(R.string.editor_not_prepared)
                    else -> stringResource(R.string.editor_detected)
                }
                Text(
                    status + if (ui.saving) " · " + stringResource(R.string.editor_saving) else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
            Button(onClick = onSend, elevation = null, modifier = Modifier.padding(end = 8.dp)) { Text(stringResource(R.string.editor_send)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun stateLabel(p: PageInfo): String = when {
    p.reviewed -> stringResource(R.string.page_reviewed)
    DocStats.needsReview(p) -> stringResource(R.string.page_check)
    p.confidence == null -> stringResource(R.string.page_pending)
    else -> stringResource(R.string.page_ok)
}

private fun visiblePages(ui: EditorUi): List<Int> {
    val doc = ui.doc ?: return emptyList()
    return doc.pages.indices.filter { !ui.onlyReview || DocStats.needsReview(doc.pages[it]) || it == ui.page }
}

@Composable
private fun Thumb(vm: EditorViewModel, ui: EditorUi, index: Int, modifier: Modifier) {
    val page = ui.doc?.pages?.getOrNull(index) ?: return
    val bmp by produceState<Bitmap?>(null, index, ui.doc?.id) { value = vm.thumbnail(index) }
    val sel = index == ui.page
    Column(
        modifier
            .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            .clickable { vm.goTo(index) }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(110.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            bmp?.let { Image(remember(it) { it.asImageBitmap() }, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        }
        Text("${index + 1} · ${stateLabel(page)}", style = MaterialTheme.typography.labelSmall, maxLines = 1,
            fontWeight = if (DocStats.needsReview(page)) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ThumbColumn(vm: EditorViewModel, ui: EditorUi, modifier: Modifier) {
    Column(modifier.padding(6.dp)) {
        FilterToggle(ui.onlyReview) { vm.setOnlyReview(it) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(visiblePages(ui), key = { it }) { i -> Thumb(vm, ui, i, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun ThumbRow(vm: EditorViewModel, ui: EditorUi) {
    Column {
        FilterToggle(ui.onlyReview) { vm.setOnlyReview(it) }
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(visiblePages(ui), key = { it }) { i -> Thumb(vm, ui, i, Modifier.width(84.dp)) }
        }
    }
}

@Composable
private fun FilterToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Text(
        (if (on) "☑ " else "☐ ") + stringResource(R.string.editor_only_review),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clickable { onChange(!on) }.heightIn(min = 40.dp).padding(6.dp),
    )
}

/** Página com os quadros: toques, arrastos e pinça. */
@Composable
private fun PageEditor(vm: EditorViewModel, ui: EditorUi, modifier: Modifier) {
    val doc = ui.doc ?: return
    val page = doc.pages.getOrNull(ui.page) ?: return
    val image = ui.image
    val mono = LocalProfile.current.isMono
    val eink = LocalProfile.current.isEink
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var zoom by remember(ui.page) { mutableFloatStateOf(1f) }
    var pan by remember(ui.page) { mutableStateOf(Offset.Zero) }
    var draft by remember { mutableStateOf<NRect?>(null) }
    var line by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    val handleR = with(density) { 22.dp.toPx() }
    val blue = if (mono) Color.Black else GibiColors.DetectionBlue
    val orange = if (mono) Color.Black else GibiColors.SelectionOrange
    val bg = if (eink) Color.White else Color(0xFF0A0A0A)

    BoxWithConstraints(modifier.background(bg)) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val iw = (image?.width ?: page.width).toFloat()
        val ih = (image?.height ?: page.height).toFloat()
        val base = min(cw / iw, ch / ih) * 0.96f
        val dw = iw * base * zoom
        val dh = ih * base * zoom
        val left = (cw - dw) / 2 + pan.x
        val top = (ch - dh) / 2 + pan.y
        val geo = Geo(left, top, dw, dh)
        val latestGeo by rememberUpdatedState(geo)
        val latestUi by rememberUpdatedState(ui)
        val latestPage by rememberUpdatedState(page)
        fun toPx(r: NRect) = geo.toPx(r)

        val gestures = Modifier.pointerInput(ui.page) {
            awaitEachGesture {
                val g0 = latestGeo
                val ui = latestUi
                val page = latestPage
                fun toNorm(o: Offset) = latestGeo.toNorm(o)
                fun toPx(r: NRect) = latestGeo.toPx(r)
                val dw = g0.dw; val dh = g0.dh
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position
                var moved = false
                var multi = false
                var mode = 0 // 0 nada, 1 desenhar, 2 mover, 3 redimensionar, 4 dividir, 5 arrastar a página
                var corner = PanelOps.Corner.BR
                var target = -1
                var last = start
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2) {
                        multi = true
                        val z = event.calculateZoom()
                        val p = event.calculatePan()
                        zoom = (zoom * z).coerceIn(1f, 6f)
                        pan += p
                        if (zoom == 1f) pan = Offset.Zero
                        event.changes.forEach { it.consume() }
                        continue
                    }
                    val ch0 = pressed.first()
                    val pos = ch0.position
                    if (!moved && (pos - start).getDistance() > viewConfiguration.touchSlop) {
                        moved = true
                        if (!multi) {
                            val (sx, sy) = toNorm(start)
                            val sel = ui.selected.singleOrNull()
                            mode = when (ui.tool) {
                                Tool.DRAW -> 1
                                Tool.SPLIT -> 4
                                Tool.JOIN -> 0
                                Tool.SELECT -> {
                                    val selRect = sel?.let { page.panels.getOrNull(it)?.rect }
                                    val c = selRect?.let { r ->
                                        val (o, s) = toPx(r)
                                        listOf(
                                            PanelOps.Corner.TL to o, PanelOps.Corner.TR to Offset(o.x + s.width, o.y),
                                            PanelOps.Corner.BL to Offset(o.x, o.y + s.height), PanelOps.Corner.BR to Offset(o.x + s.width, o.y + s.height),
                                        ).firstOrNull { (_, p) -> (p - start).getDistance() <= handleR }
                                    }
                                    when {
                                        c != null -> { corner = c.first; target = sel!!; vm.beginDrag(); 3 }
                                        selRect != null && selRect.contains(sx, sy) -> { target = sel!!; vm.beginDrag(); 2 }
                                        else -> {
                                            val hit = PanelOps.hit(page.panels, sx, sy)
                                            if (hit >= 0) { vm.select(hit); target = hit; vm.beginDrag(); 2 } else if (zoom > 1f) 5 else 0
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (moved && !multi) {
                        val (nx, ny) = toNorm(pos)
                        when (mode) {
                            1 -> { val (sx, sy) = toNorm(start); draft = NRect(minOf(sx, nx), minOf(sy, ny), abs(nx - sx), abs(ny - sy)) }
                            2 -> { val d = pos - last; vm.movePanel(target, (d.x / dw).toDouble(), (d.y / dh).toDouble()) }
                            3 -> vm.resizePanel(target, corner, nx, ny)
                            4 -> line = start to pos
                            5 -> pan += pos - last
                        }
                        ch0.consume()
                    }
                    last = pos
                }
                if (!moved && !multi) {
                    val (x, y) = toNorm(start)
                    vm.tap(x, y)
                } else if (!multi) {
                    when (mode) {
                        1 -> draft?.let { vm.draw(it) }
                        4 -> line?.let { (a, b) ->
                            val (ax, ay) = toNorm(a); val (bx, by) = toNorm(b)
                            vm.split(ax, ay, bx, by)
                        }
                    }
                }
                draft = null
                line = null
            }
        }

        Canvas(Modifier.fillMaxSize().then(gestures)) {
            if (image != null) {
                drawImage(
                    image.asImageBitmapCached(),
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
                )
            }
            val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 8f))
            page.panels.forEachIndexed { i, p ->
                val (o, s) = toPx(p.rect)
                val selected = i in ui.selected
                drawRect(
                    color = if (selected) orange else blue, topLeft = o, size = s,
                    style = Stroke(width = if (selected) 6f else 4f, pathEffect = if (selected) null else dash),
                )
                if (selected && ui.selected.size == 1) {
                    listOf(o, Offset(o.x + s.width, o.y), Offset(o.x, o.y + s.height), Offset(o.x + s.width, o.y + s.height)).forEach { c ->
                        drawRect(orange, topLeft = c - Offset(14f, 14f), size = Size(28f, 28f))
                    }
                }
                val label = measurer.measure((i + 1).toString(), TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White))
                val bx = o.x + 6f; val by = o.y + 6f
                drawRect(if (selected) orange else blue, topLeft = Offset(bx, by), size = Size(label.size.width + 16f, label.size.height + 6f))
                drawText(label, topLeft = Offset(bx + 8f, by + 3f))
            }
            draft?.let { r -> val (o, s) = toPx(r); drawRect(orange, o, s, style = Stroke(5f)) }
            line?.let { (a, b) -> drawLine(orange, a, b, strokeWidth = 6f) }
        }
        if (zoom > 1f) {
            IconButton(onClick = { zoom = 1f; pan = Offset.Zero }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Outlined.ZoomOutMap, stringResource(R.string.editor_reset_zoom))
            }
        }
    }
}

/** Posição da página desenhada na tela (para converter toques em coordenadas 0..1). */
private data class Geo(val left: Float, val top: Float, val dw: Float, val dh: Float) {
    fun toNorm(o: Offset) = ((o.x - left) / dw).toDouble() to ((o.y - top) / dh).toDouble()
    fun toPx(r: NRect) = Offset(left + (r.x * dw).toFloat(), top + (r.y * dh).toFloat()) to Size((r.w * dw).toFloat(), (r.h * dh).toFloat())
}

private val bitmapCache = java.util.WeakHashMap<Bitmap, androidx.compose.ui.graphics.ImageBitmap>()
private fun Bitmap.asImageBitmapCached() = bitmapCache.getOrPut(this) { this.asImageBitmap() }

@Composable
private fun ToolButtons(vm: EditorViewModel, ui: EditorUi) {
    ToolChip(stringResource(R.string.tool_draw), ui.tool == Tool.DRAW) { vm.setTool(Tool.DRAW) }
    ToolChip(stringResource(R.string.tool_join) + if (ui.tool == Tool.JOIN && ui.selected.size >= 2) " (${ui.selected.size})" else "", ui.tool == Tool.JOIN) {
        if (ui.tool == Tool.JOIN && ui.selected.size >= 2) vm.join() else vm.setTool(Tool.JOIN)
    }
    ToolChip(stringResource(R.string.tool_split), ui.tool == Tool.SPLIT) { vm.setTool(Tool.SPLIT) }
    ToolChip(stringResource(R.string.tool_delete), false, enabled = ui.selected.isNotEmpty()) { vm.deleteSelected() }
    IconButton(onClick = { vm.undo() }, enabled = ui.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo)) }
    IconButton(onClick = { vm.redo() }, enabled = ui.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo)) }
}

@Composable
private fun ToolChip(text: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .padding(2.dp)
            .background(if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background, shape)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                active -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun ToolBar(vm: EditorViewModel, ui: EditorUi, details: Boolean, onDetails: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.navigationBarsPadding()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButtons(vm, ui)
                ToolChip(stringResource(if (details) R.string.editor_hide_details else R.string.editor_details), details, onClick = onDetails)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val doc = ui.doc
                Text(
                    doc?.let { stringResource(R.string.editor_page_of, ui.page + 1, it.pages.size) }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                )
                Button(onClick = { vm.approveAndNext() }, elevation = null) { Text(stringResource(R.string.editor_approve)) }
            }
        }
    }
}

@Composable
private fun SidePanel(vm: EditorViewModel, ui: EditorUi, modifier: Modifier) {
    val doc = ui.doc ?: return
    val page = doc.pages.getOrNull(ui.page) ?: return
    var prepMenu by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.editor_page_of, ui.page + 1, doc.pages.size), style = MaterialTheme.typography.titleLarge)
        val conf = page.confidence
        val level = when {
            conf == null -> stringResource(R.string.confidence_none)
            conf >= 0.85 -> stringResource(R.string.confidence_high)
            conf >= 0.7 -> stringResource(R.string.confidence_medium)
            else -> stringResource(R.string.confidence_low)
        }
        Text(stringResource(R.string.editor_panels_info, page.panels.size, level), style = MaterialTheme.typography.bodyMedium)

        Text(stringResource(R.string.direction), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolChip(stringResource(R.string.direction_ltr), doc.readingDirection == "ltr") { vm.setBookDirection("ltr") }
            ToolChip(stringResource(R.string.direction_rtl), doc.readingDirection == "rtl") { vm.setBookDirection("rtl") }
        }
        Text(stringResource(R.string.editor_page_direction), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolChip(stringResource(R.string.editor_inherit), page.readingDirection == null) { vm.setPageDirection(null) }
            ToolChip("→", page.readingDirection == "ltr") { vm.setPageDirection("ltr") }
            ToolChip("←", page.readingDirection == "rtl") { vm.setPageDirection("rtl") }
        }

        Text(stringResource(R.string.editor_tools), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) { ToolButtons(vm, ui) }
        Text(toolHint(ui.tool), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text(stringResource(R.string.editor_order), style = MaterialTheme.typography.titleSmall)
        OrderList(vm, ui, page)

        StepperRow(stringResource(R.string.settings_tolerance), ui.tolerance, 10..90, step = 5) { vm.setTolerance(it) }
        OutlinedButton(onClick = { vm.redetect() }, enabled = !ui.redetecting, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (ui.redetecting) R.string.editor_redetecting else R.string.editor_redetect))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.editor_reviewed_count, doc.reviewedPages, doc.pages.size), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { vm.approveAndNext() }, elevation = null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.editor_approve))
        }
        Box {
            TextButton(onClick = { prepMenu = true }) { Text(stringResource(R.string.editor_prepare_book)) }
            DropdownMenu(expanded = prepMenu, onDismissRequest = { prepMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.editor_prepare_keep)) }, onClick = { prepMenu = false; vm.prepareAll(false) })
                DropdownMenuItem(text = { Text(stringResource(R.string.editor_prepare_redo)) }, onClick = { prepMenu = false; vm.prepareAll(true) })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun toolHint(t: Tool): String = stringResource(
    when (t) {
        Tool.SELECT -> R.string.hint_select
        Tool.DRAW -> R.string.hint_draw
        Tool.JOIN -> R.string.hint_join
        Tool.SPLIT -> R.string.hint_split
    }
)

/** Ordem de leitura; segure e arraste a alça para reordenar. */
@Composable
private fun OrderList(vm: EditorViewModel, ui: EditorUi, page: PageInfo) {
    val rowH = with(LocalDensity.current) { 48.dp.toPx() }
    Column {
        page.panels.forEachIndexed { i, p ->
            var drag by remember(i, page.panels.size) { mutableFloatStateOf(0f) }
            val sel = i in ui.selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { vm.select(i) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                Text(stringResource(R.string.editor_panel_n, i + 1), color = fg, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(if (p.source == "manual") "✎" else "", color = fg)
                Icon(
                    Icons.Outlined.DragIndicator, stringResource(R.string.editor_drag),
                    tint = fg,
                    modifier = Modifier.size(40.dp).padding(8.dp).pointerInput(i, page.panels.size) {
                        detectDragGesturesAfterLongPress(
                            onDragEnd = { drag = 0f },
                            onDragCancel = { drag = 0f },
                        ) { change, amount ->
                            change.consume()
                            drag += amount.y
                            val steps = (drag / rowH).toInt()
                            if (steps != 0) {
                                val to = (i + steps).coerceIn(0, page.panels.lastIndex)
                                if (to != i) vm.reorder(i, to)
                                drag = 0f
                            }
                        }
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
