package cn.inkledger.app

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.*
import kotlin.math.*
import kotlinx.coroutines.launch

val MotionEase = CubicBezierEasing(.16f, 1f, .3f, 1f)

data class Navigation(
    val from: Page,
    val to: Page,
    val horizontal: Boolean,
    val origin: Rect? = null,
    val token: Long = System.nanoTime(),
)

data class Sheet(
    val kind: String,
    val entry: Entry? = null,
    val date: String = LocalDate.now().toString(),
    val ids: Set<String> = emptySet(),
    val type: String = "expense",
    val cat: String? = null,
    val origin: Rect? = null,
    val text: String = "",
    val confirm: (() -> Unit)? = null,
)

data class Deletion(
    val ids: Set<String>,
    val rect: Rect,
    val seed: Long = System.nanoTime(),
    val rects: List<Rect> = listOf(rect),
)

@Composable
fun InkLedger(
    model: LedgerViewModel,
    pendingImport: String?,
    clearImport: () -> Unit,
    export: (String) -> Unit,
    import: () -> Unit,
) {
    val data by model.ledger.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val welcome by model.welcome.collectAsStateWithLifecycle()
    val colors =
        lightColorScheme(
            primary = Dark,
            secondary = Mid,
            surface = White,
            background = LightMid,
            onPrimary = White,
        )
    MaterialTheme(colorScheme = colors) {
        if (data == null) {
            Box(Modifier.fillMaxSize().background(LightMid), contentAlignment = Alignment.Center) {
                if (error == null) CircularProgressIndicator(color = Mid)
                else
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Label(error!!)
                        TextButton(onClick = import) { Text("导入备份恢复", color = Dark) }
                    }
            }
            if (pendingImport != null)
                AlertDialog(
                    onDismissRequest = clearImport,
                    title = { Text("导入备份") },
                    text = { Text("已保留无法读取的原数据。确认使用所选备份恢复账本？") },
                    confirmButton = {
                        TextButton(
                            onClick = { model.importBackup(pendingImport) { clearImport() } }
                        ) {
                            Text("确认恢复")
                        }
                    },
                    dismissButton = { TextButton(onClick = clearImport) { Text("取消") } },
                )
            return@MaterialTheme
        }
        val d = data!!
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        var page by rememberSaveable { mutableStateOf(Page.HOME) }
        var lastFunction by rememberSaveable { mutableStateOf(Page.HOME) }
        var hidden by rememberSaveable { mutableStateOf(false) }
        var drawer by rememberSaveable { mutableStateOf(false) }
        var rangeMode by rememberSaveable { mutableStateOf("month") }
        var range by rememberSaveable { mutableStateOf(LocalDate.now().toString().take(7)) }
        var budgetMode by rememberSaveable { mutableStateOf("month") }
        var budgetRange by rememberSaveable { mutableStateOf(LocalDate.now().toString().take(7)) }
        var nav by remember { mutableStateOf<Navigation?>(null) }
        val progress = remember { Animatable(1f) }
        var sheet by remember { mutableStateOf<Sheet?>(null) }
        var sheetClosing by remember { mutableStateOf(false) }
        val sheetProgress = remember { Animatable(0f) }
        var context by remember { mutableStateOf<Triple<Entry, Offset, Rect>?>(null) }
        var selection by rememberSaveable { mutableStateOf(false) }
        var selected by remember { mutableStateOf(emptySet<String>()) }
        var deletion by remember { mutableStateOf<Deletion?>(null) }
        val deletionPhase = remember { Animatable(0f) }
        val rowBounds = remember { mutableMapOf<String, Rect>() }
        val lists = remember { Page.entries.associateWith { LazyListState() } }
        var addRect by remember { mutableStateOf(Rect.Zero) }
        fun show(s: Sheet) {
            context = null
            sheetClosing = false
            sheet = s
        }
        fun close() {
            if (sheetClosing) return
            sheetClosing = true
            scope.launch {
                sheetProgress.animateTo(
                    0f,
                    tween(if (sheet?.origin != null) 320 else 280, easing = MotionEase),
                )
                sheet = null
                sheetClosing = false
            }
        }
        fun go(target: Page, origin: Rect? = null) {
            if (target == page) {
                drawer = false
                return
            }
            sheet = null
            sheetClosing = false
            scope.launch { sheetProgress.snapTo(0f) }
            context = null
            drawer = false
            selected = emptySet()
            selection = false
            val isSettings = target == Page.SETTINGS || target == Page.TRASH
            val wasSettings = page == Page.SETTINGS || page == Page.TRASH
            if (!wasSettings) lastFunction = page
            if (!isSettings) {
                lastFunction = target
                if (wasSettings) hidden = false
            }
            nav = Navigation(page, target, isSettings != wasSettings, origin)
            page = target
        }
        LaunchedEffect(nav?.token) {
            val n = nav ?: return@LaunchedEffect
            progress.snapTo(0f)
            val steps = abs(n.to.ordinal - n.from.ordinal).coerceAtLeast(1)
            progress.animateTo(
                1f,
                tween(
                    if (n.horizontal || n.origin != null) 480 else max(260, 430 - 30 * (steps - 1)),
                    easing = MotionEase,
                ),
            )
            nav = null
        }
        LaunchedEffect(sheet) {
            if (sheet != null) {
                sheetProgress.snapTo(0f)
                sheetProgress.animateTo(
                    1f,
                    tween(if (sheet?.origin != null) 460 else 360, easing = MotionEase),
                )
            }
        }
        LaunchedEffect(welcome) { if (welcome) show(Sheet("welcome")) }
        LaunchedEffect(pendingImport) {
            pendingImport?.let { raw ->
                runCatching {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            LedgerJson.decode(raw)
                        }
                    }
                    .onSuccess {
                        show(
                            Sheet(
                                "confirm",
                                text = "导入备份将替换当前全部账本、账单、预算和回收站。",
                                confirm = {
                                    model.importBackup(raw) {
                                        clearImport()
                                        close()
                                    }
                                },
                            )
                        )
                    }
                    .onFailure {
                        model.report("备份格式不正确：${it.message}")
                        clearImport()
                    }
            }
        }
        BackHandler(sheet != null || context != null || selection || drawer || page != Page.HOME) {
            when {
                context != null -> context = null
                sheet != null -> close()
                selection -> {
                    selection = false
                    selected = emptySet()
                }
                drawer -> drawer = false
                page == Page.TRASH -> go(Page.SETTINGS)
                page == Page.SETTINGS -> go(lastFunction)
                else -> go(Page.HOME)
            }
        }
        val a =
            ScreenActions(
                add = { show(Sheet("editor", date = it)) },
                detail = { show(Sheet("detail", entry = it)) },
                category = { ids, type -> show(Sheet("classify", ids = ids, type = type)) },
                context = { e, p, r -> context = Triple(e, p, r) },
                rowBounds = { id, rect ->
                    if (rect == Rect.Zero) rowBounds.remove(id) else rowBounds[id] = rect
                },
                delete = { ids, r -> if (deletion == null) deletion = Deletion(ids, r) },
                budget = { r ->
                    budgetMode = d.settings.budgetMode
                    budgetRange =
                        if (budgetMode == "year") range.take(4)
                        else if (range.length >= 7) range
                        else range + "-" + LocalDate.now().toString().substring(5, 7)
                    go(Page.BUDGET, r)
                },
                amount = { show(Sheet("amount", cat = it)) },
                categoryBudget = { show(Sheet("choose-budget-category")) },
                rename = { show(Sheet("category", cat = it?.id)) },
                books = { show(Sheet("books")) },
                trash = { go(Page.TRASH) },
                export = { export(LedgerJson.encode(d)) },
                import = import,
                reset = {
                    show(
                        Sheet(
                            "confirm",
                            text = "清空演示期间的全部数据并创建空白账本？",
                            confirm = {
                                model.change({ Ledger() }) {
                                    close()
                                    go(Page.HOME)
                                }
                            },
                        )
                    )
                },
                settings = { p -> model.change({ it.copy(settings = p) }) },
                restore = { model.restore(it.id) },
                purge = { e ->
                    show(
                        Sheet(
                            "confirm",
                            text = "这笔账单将无法恢复。确认永久删除？",
                            confirm = { model.purge(e.id) { close() } },
                        )
                    )
                },
            )
        val layer = rememberGraphicsLayer()
        CompositionLocalProvider(
            LocalBackdrop provides layer,
            LocalEffect provides d.settings.effect,
            LocalDeletingIds provides (deletion?.ids ?: emptySet()),
            LocalDeleteProgress provides { deletionPhase.value },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().background(LightMid).clipToBounds()) {
                val tablet = maxWidth >= 840.dp
                val sideWidth = if (tablet) 256.dp else 280.dp
                val w = with(density) { maxWidth.toPx() }
                val h = with(density) { maxHeight.toPx() }
                val sidePx = with(density) { sideWidth.toPx() }
                val manualSide by
                    animateFloatAsState(
                        if (hidden) 0f else 1f,
                        tween(480, easing = MotionEase),
                        label = "manual-sidebar",
                    )
                val settingsPage = page in listOf(Page.SETTINGS, Page.TRASH)
                fun gutter(p: Page) =
                    if (tablet && p !in listOf(Page.SETTINGS, Page.TRASH)) sidePx * manualSide
                    else 0f
                val targetGutter = gutter(page)
                fun visibleSide(): Float {
                    val n = nav
                    return if (n?.horizontal == true) {
                        val a = if (n.from in listOf(Page.SETTINGS, Page.TRASH)) 0f else manualSide
                        val b = if (n.to in listOf(Page.SETTINGS, Page.TRASH)) 0f else manualSide
                        a + (b - a) * progress.value
                    } else if (tablet) {
                        if (settingsPage) 0f else manualSide
                    } else 0f
                }
                val drawerProgress by
                    animateFloatAsState(
                        if (drawer) 1f else 0f,
                        tween(480, easing = MotionEase),
                        label = "drawer",
                    )
                Box(
                    Modifier.fillMaxSize().drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                ) {
                    val outgoing = nav
                    fun pageModifier(p: Page, old: Boolean): Modifier {
                        val base =
                            Modifier.fillMaxSize()
                                .padding(start = with(density) { gutter(p).toDp() })
                        return base.graphicsLayer {
                            val n = nav
                            val t = if (n == null) 1f else progress.value
                            val effect = if (old) t else 1 - t
                            val distance =
                                if (n?.horizontal == true) max(180f, w * .38f)
                                else
                                    with(density) {
                                        (104 * abs((n?.to?.ordinal ?: 0) - (n?.from?.ordinal ?: 0)))
                                            .dp
                                            .toPx()
                                    }
                            val sign =
                                if (n?.horizontal == true) {
                                    if (n.to in listOf(Page.SETTINGS, Page.TRASH)) 1 else -1
                                } else if ((n?.to?.ordinal ?: 0) > (n?.from?.ordinal ?: 0)) 1
                                else -1
                            if (n?.origin == null) {
                                if (n?.horizontal == true)
                                    translationX = (if (old) -1 else 1) * sign * distance * effect
                                else translationY = (if (old) -1 else 1) * sign * distance * effect
                            }
                            alpha = if (old) 1 - t else if (n?.origin != null) .2f + .8f * t else t
                            val blur =
                                if (n == null) 0f else effect * (if (n.horizontal) 12f else 10f)
                            val modalBlur =
                                if (!old)
                                    max(
                                        sheetProgress.value * 8f,
                                        if (!tablet) drawerProgress * 4f else 0f,
                                    )
                                else 0f
                            if (Build.VERSION.SDK_INT >= 31 && max(blur, modalBlur) > .1f)
                                renderEffect =
                                    BlurEffect(
                                        max(blur, modalBlur).dp.toPx(),
                                        max(blur, modalBlur).dp.toPx(),
                                        TileMode.Clamp,
                                    )
                            if (!old && n?.origin != null) {
                                val r = n.origin
                                val left = (r.left - gutter(p)) * (1 - t)
                                val top = r.top * (1 - t)
                                val right =
                                    (r.right - gutter(p)) + (size.width - (r.right - gutter(p))) * t
                                val bottom = r.bottom + (size.height - r.bottom) * t
                                shape = GenericShape { _, _ ->
                                    addRoundRect(
                                        RoundRect(
                                            Rect(left, top, right, bottom),
                                            CornerRadius(with(density) { 18.dp.toPx() } * (1 - t)),
                                        )
                                    )
                                }
                                clip = true
                            }
                        }
                    }
                    @Composable
                    fun Surface(p: Page, old: Boolean) {
                        Box(
                            pageModifier(p, old).drawBehind {
                                val list = lists.getValue(p)
                                val shift =
                                    if (p == Page.HOME || p == Page.STATS) {
                                        if (list.firstVisibleItemIndex == 0)
                                            list.firstVisibleItemScrollOffset
                                                .toFloat()
                                                .coerceAtMost(136.dp.toPx())
                                        else 136.dp.toPx()
                                    } else 136.dp.toPx()
                                val end = (360.dp.toPx() - shift).coerceAtLeast(180.dp.toPx())
                                val middle = (160.dp.toPx() - shift).coerceAtLeast(64.dp.toPx())
                                drawRect(
                                    Brush.verticalGradient(
                                        0f to MidDark,
                                        (middle / end).coerceIn(.01f, .99f) to Mid,
                                        1f to LightMid,
                                        endY = end,
                                    )
                                )
                            }
                        ) {
                            Screen(
                                p,
                                d,
                                rangeMode,
                                range,
                                budgetMode,
                                budgetRange,
                                tablet,
                                lists.getValue(p),
                                a,
                                selected,
                                selection,
                                { id ->
                                    selected = if (id in selected) selected - id else selected + id
                                },
                                { delta ->
                                    val current =
                                        if (budgetMode == "month")
                                            YearMonth.parse(budgetRange).atDay(1)
                                        else LocalDate.of(budgetRange.toInt(), 1, 1)
                                    val next =
                                        if (budgetMode == "month")
                                            current.plusMonths(delta.toLong())
                                        else current.plusYears(delta.toLong())
                                    budgetRange =
                                        next.toString().take(if (budgetMode == "month") 7 else 4)
                                },
                                { show(Sheet("budget-period")) },
                            )
                            Toolbar(
                                p,
                                if (old) ({})
                                else
                                    ({
                                        if (p == Page.TRASH) go(Page.SETTINGS)
                                        else if (p == Page.BUDGET) go(Page.HOME)
                                        else if (tablet) hidden = !hidden else drawer = !drawer
                                    }),
                                rangeMode,
                                range,
                                budgetMode,
                                { m ->
                                    budgetMode = m
                                    budgetRange =
                                        if (m == "year") budgetRange.take(4)
                                        else
                                            budgetRange.take(4) +
                                                "-" +
                                                LocalDate.now().toString().substring(5, 7)
                                },
                                { show(Sheet("period")) },
                                { go(Page.STATS) },
                            )
                        }
                    }
                    if (outgoing != null) Surface(outgoing.from, true)
                    Surface(page, false)
                }
                if (!tablet && drawerProgress > 0f)
                    Box(
                        Modifier.fillMaxSize()
                            .background(Color.Black.copy(.4f * drawerProgress))
                            .clickable { if (tablet) hidden = true else drawer = false }
                    )
                if (tablet || drawerProgress > 0f)
                    Sidebar(
                        d,
                        if (settingsPage) lastFunction else page,
                        nav,
                        { progress.value },
                        Modifier.width(sideWidth).fillMaxHeight().graphicsLayer {
                            translationX =
                                -sidePx * (1 - if (tablet) visibleSide() else drawerProgress)
                        },
                        a.books,
                        { go(it) },
                        { if (tablet) hidden = true else drawer = false },
                    )
                val dockWidth = with(density) { 252.dp.toPx() }
                val dockPos = if (tablet) d.settings.tabletDockPosition else "center"
                fun dockX(g: Float) =
                    when (dockPos) {
                        "left" -> g + with(density) { 20.dp.toPx() }
                        "right" -> w - dockWidth - with(density) { 20.dp.toPx() }
                        else -> (w + g - dockWidth) / 2
                    }
                val baseX = dockX(targetGutter)
                if (sheet?.origin == null || sheetProgress.value < .01f)
                    Dock(
                        settingsPage,
                        dockPos,
                        Modifier.align(Alignment.BottomStart)
                            .offset { IntOffset(baseX.roundToInt(), 0) }
                            .padding(bottom = 18.dp)
                            .graphicsLayer {
                                val n = nav
                                if (n?.horizontal == true) {
                                    translationX =
                                        (dockX(gutter(n.from)) - dockX(gutter(n.to))) *
                                            (1 - progress.value)
                                }
                            },
                        { if (settingsPage) go(lastFunction) },
                        { go(Page.SETTINGS) },
                        { show(Sheet("editor", origin = addRect)) },
                    ) {
                        addRect = it
                    }
                if (selection)
                    Glass(
                        Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 92.dp)
                            .widthIn(max = 400.dp)
                            .height(50.dp)
                    ) {
                        Row(
                            Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = {
                                    selected = d.selected(rangeMode, range).map { it.id }.toSet()
                                }
                            ) {
                                Text("全选", color = Dark)
                            }
                            Label("${selected.size} 笔", Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    val es = d.entries.filter { it.id in selected }
                                    if (es.isNotEmpty()) {
                                        if (es.map { it.type }.distinct().size > 1)
                                            model.report("多选账单必须属于同一收支类型")
                                        else
                                            show(
                                                Sheet(
                                                    "classify",
                                                    ids = selected,
                                                    type = es.first().type,
                                                )
                                            )
                                    }
                                }
                            ) {
                                Text("分类", color = Dark)
                            }
                            TextButton(
                                onClick = {
                                    if (selected.isNotEmpty())
                                        deletion =
                                            Deletion(
                                                selected,
                                                Rect(targetGutter, 70f, w, h - 160f),
                                                rects =
                                                    selected
                                                        .mapNotNull { rowBounds[it] }
                                                        .filter { it.bottom > 0 && it.top < h },
                                            )
                                }
                            ) {
                                Text("删除", color = Dark)
                            }
                            TextButton(
                                onClick = {
                                    selection = false
                                    selected = emptySet()
                                }
                            ) {
                                Text("退出", color = Dark)
                            }
                        }
                    }
                sheet?.let { s ->
                    SheetOverlay(
                        s,
                        d,
                        model,
                        budgetMode,
                        budgetRange,
                        rangeMode,
                        range,
                        targetGutter,
                        w,
                        h,
                        sheetProgress,
                        { close() },
                        { show(it) },
                        { m, r ->
                            rangeMode = m
                            range = r
                            close()
                        },
                        { r ->
                            budgetRange = r
                            close()
                        },
                    )
                }
                context?.let { (e, p, r) ->
                    Box(Modifier.fillMaxSize().clickable { context = null }) {
                        InkCard(
                            Modifier.offset {
                                    IntOffset(
                                        p.x
                                            .coerceIn(
                                                targetGutter,
                                                w - with(density) { 120.dp.toPx() },
                                            )
                                            .roundToInt(),
                                        p.y
                                            .coerceIn(0f, h - with(density) { 160.dp.toPx() })
                                            .roundToInt(),
                                    )
                                }
                                .width(120.dp)
                        ) {
                            listOf("编辑", "删除", "多选").forEach { label ->
                                Label(
                                    label,
                                    Modifier.fillMaxWidth()
                                        .clickable {
                                            context = null
                                            when (label) {
                                                "编辑" -> show(Sheet("editor", entry = e))
                                                "删除" -> deletion = Deletion(setOf(e.id), r)
                                                else -> {
                                                    selection = true
                                                    selected = setOf(e.id)
                                                }
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
                deletion?.let { del ->
                    DeletionParticles(del, deletionPhase, Rect(0f, 0f, w, h)) {
                        model.trash(del.ids) {
                            deletion = null
                            selected = selected - del.ids
                        }
                    }
                }
                error?.let {
                    AlertDialog(
                        onDismissRequest = model::clearError,
                        title = { Text("操作未完成") },
                        text = { Text(it) },
                        confirmButton = { TextButton(onClick = model::clearError) { Text("确定") } },
                    )
                }
            }
        }
    }
}

@Composable
fun Toolbar(
    page: Page,
    menu: () -> Unit,
    mode: String,
    range: String,
    budgetMode: String,
    onMode: (String) -> Unit,
    onPeriod: () -> Unit,
    onStats: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .height(64.dp)
            .background(
                Brush.verticalGradient(listOf(MidDark, MidDark.copy(.95f), Color.Transparent))
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (page != Page.SETTINGS)
            IconButton(
                if (page == Page.BUDGET || page == Page.TRASH) "back" else "menu",
                if (page == Page.BUDGET || page == Page.TRASH) "返回" else "菜单",
                menu,
            )
        else Spacer(Modifier.width(44.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            when (page) {
                Page.HOME,
                Page.STATS,
                Page.SEARCH ->
                    Label(
                        range.replace('-', '.'),
                        Modifier.clickable(onClick = onPeriod),
                        White,
                        19,
                    )
                Page.BUDGET -> PillTabs(listOf("month" to "月度", "year" to "年度"), budgetMode, onMode)
                else -> Label(page.label, color = White, size = 19)
            }
        }
        if (page == Page.HOME) IconButton("chart", "收支统计", onStats)
        else Spacer(Modifier.width(44.dp))
    }
}

@Composable
fun PillTabs(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val x by animateFloatAsState(index * 76f, tween(480, easing = MotionEase), label = "budget-tab")
    Glass(Modifier.width((options.size * 76 + 8).dp).height(38.dp), sample = false) {
        Box(
            Modifier.padding(4.dp)
                .size(76.dp, 30.dp)
                .graphicsLayer { translationX = x.dp.toPx() }
                .background(White.copy(.7f), CircleShape)
        )
        Row(Modifier.padding(4.dp)) {
            options.forEach { (id, title) ->
                Box(
                    Modifier.width(76.dp).height(30.dp).clickable { onSelect(id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Label(title, size = 13)
                }
            }
        }
    }
}

@Composable
fun Sidebar(
    d: Ledger,
    page: Page,
    nav: Navigation?,
    progress: () -> Float,
    modifier: Modifier,
    books: () -> Unit,
    go: (Page) -> Unit,
    close: () -> Unit,
) {
    Glass(modifier, RoundedCornerShape(0.dp), sample = true) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("墨账", Modifier.weight(1f), size = 25, bold = true)
                IconButton("close", "收起菜单", close, Dark)
            }
            Setting(d.books.first { it.id == d.book }.name, "切换账本", books)
            Spacer(Modifier.height(20.dp))
            Box {
                val pages = Page.entries.take(5)
                val current = pages.indexOf(page).coerceAtLeast(0)
                Box(
                    Modifier.fillMaxWidth()
                        .height(48.dp)
                        .graphicsLayer {
                            val start = pages.indexOf(nav?.from).takeIf { it >= 0 } ?: current
                            val end = pages.indexOf(nav?.to).takeIf { it >= 0 } ?: current
                            translationY = (start + (end - start) * progress()) * 52.dp.toPx()
                        }
                        .background(White.copy(.72f), RoundedCornerShape(14.dp))
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    pages.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { go(p) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            InkIcon(p.icon)
                            Spacer(Modifier.width(12.dp))
                            Label(p.label, size = 14, bold = p == page)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Dock(
    settings: Boolean,
    position: String,
    modifier: Modifier,
    functions: () -> Unit,
    settingsClick: () -> Unit,
    add: () -> Unit,
    addBounds: (Rect) -> Unit,
) {
    val order =
        when (position) {
            "left" -> listOf("add", "functions", "settings")
            "right" -> listOf("functions", "settings", "add")
            else -> listOf("functions", "add", "settings")
        }
    val selected = order.indexOf(if (settings) "settings" else "functions")
    val x by
        animateFloatAsState(selected * 80f, tween(480, easing = MotionEase), label = "dock-marker")
    Box(modifier.width(252.dp).height(64.dp)) {
        Glass(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(48.dp)) {
            Box(
                Modifier.padding(start = 6.dp, top = 4.dp)
                    .size(80.dp, 40.dp)
                    .graphicsLayer { translationX = x.dp.toPx() }
                    .background(White.copy(.66f), CircleShape)
            )
            Row(Modifier.padding(horizontal = 6.dp)) {
                order.forEach { id ->
                    if (id == "add") Spacer(Modifier.width(80.dp).height(48.dp))
                    else
                        Column(
                            Modifier.width(80.dp)
                                .height(48.dp)
                                .clip(CircleShape)
                                .clickable(
                                    onClick = if (id == "functions") functions else settingsClick
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            InkIcon(
                                if (id == "functions") "home" else "settings",
                                Modifier.size(18.dp),
                            )
                            Label(if (id == "functions") "功能页" else "设置页", size = 10)
                        }
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomStart)
                .offset(x = (6 + order.indexOf("add") * 80 + 8).dp)
                .size(64.dp)
                .onGloballyPositioned { addBounds(it.boundsInRoot()) }
                .clip(CircleShape)
                .background(Mid)
                .clickable(onClickLabel = "记一笔", onClick = add),
            contentAlignment = Alignment.Center,
        ) {
            InkIcon("plus", Modifier.size(28.dp), White, 4f)
        }
    }
}

fun particleEase(value: Float): Float = 1 - (1 - value.coerceIn(0f, 1f)).let { it * it * it }

data class Particle(val point: Offset, val velocity: Offset, val radius: Float, val color: Color)

@Composable
fun DeletionParticles(
    deletion: Deletion,
    p: Animatable<Float, *>,
    viewport: Rect,
    done: () -> Unit,
) {
    val density = LocalDensity.current
    val effect = LocalEffect.current
    val points =
        remember(deletion.seed, effect, density, viewport) {
            val random = kotlin.random.Random(deletion.seed)
            val result = mutableListOf<Particle>()
            val step =
                with(density) {
                    (when (effect) {
                            "strong" -> 7f
                            "weak" -> 13f
                            else -> 9f
                        } * max(1f, sqrt(deletion.rects.size / 8f)))
                        .dp
                        .toPx()
                }
            for (source in deletion.rects) {
                val r = source.intersect(viewport)
                if (r.width <= 0f || r.height <= 0f) continue
                var y = r.top
                while (y < r.bottom) {
                    var x = r.left
                    while (x < r.right) {
                        val ox = x - source.center.x
                        val oy = y - source.center.y
                        val length = hypot(ox, oy).coerceAtLeast(1f)
                        val vx =
                            with(density) {
                                (ox / length * (12 + random.nextFloat() * 42) +
                                        (random.nextFloat() - .5f) * 28)
                                    .dp
                                    .toPx()
                            }
                        val vy =
                            with(density) {
                                (oy / length * (12 + random.nextFloat() * 42) +
                                        (random.nextFloat() - .5f) * 28)
                                    .dp
                                    .toPx()
                            }
                        result +=
                            Particle(
                                Offset(x, y),
                                Offset(vx, vy),
                                with(density) { (1 + random.nextFloat() * 2.2f).dp.toPx() },
                                if (random.nextFloat() > .7f) Color(0xff777777)
                                else Color(0xffeeeeee),
                            )
                        x += step
                    }
                    y += step
                }
            }
            result
        }
    LaunchedEffect(deletion.seed) {
        p.snapTo(0f)
        p.animateTo(1f, tween(1050, easing = LinearEasing))
        done()
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = p.value
        val spread = particleEase((t - .35f) / .65f)
        val alpha = particleEase(t / .32f) * (1 - spread)
        points.forEach {
            drawCircle(
                it.color.copy(alpha = alpha),
                it.radius,
                it.point + it.velocity * spread * 3f,
            )
        }
    }
}
