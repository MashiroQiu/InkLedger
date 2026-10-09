package cn.inkledger.app

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

enum class Page(val label: String, val icon: String) {
    HOME("账单一览", "home"),
    BUDGET("预算管理", "wallet"),
    STATS("收支统计", "chart"),
    SEARCH("搜索账单", "search"),
    CATEGORIES("分类管理", "other"),
    SETTINGS("设置页", "settings"),
    TRASH("回收站", "trash"),
}

data class ScreenActions(
    val add: (String) -> Unit,
    val detail: (Entry) -> Unit,
    val category: (Set<String>, String) -> Unit,
    val context: (Entry, Offset, Rect) -> Unit,
    val rowBounds: (String, Rect) -> Unit,
    val delete: (Set<String>, Rect) -> Unit,
    val budget: (Rect) -> Unit,
    val amount: (String?) -> Unit,
    val categoryBudget: () -> Unit,
    val rename: (Category?) -> Unit,
    val books: () -> Unit,
    val trash: () -> Unit,
    val export: () -> Unit,
    val import: () -> Unit,
    val reset: () -> Unit,
    val settings: (Preferences) -> Unit,
    val restore: (Entry) -> Unit,
    val purge: (Entry) -> Unit,
)

@Composable
fun Summary(es: List<Entry>, stats: Boolean = false) {
    val out = remember(es) { sum(es, "expense") }
    val income = remember(es) { sum(es, "income") }
    Column(
        Modifier.fillMaxWidth().height(124.dp).padding(top = 12.dp, start = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Label(if (stats) "收支结余" else "合计支出", color = White.copy(.85f), size = 12)
        Row(verticalAlignment = Alignment.Bottom) {
            Label("¥", color = White, size = 24)
            Label(money(if (stats) income - out else out), color = White, size = 48)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Label("收入  ${money(income)}", color = White, size = 12)
            Label(
                if (stats) "支出  ${money(out)}" else "结余  ${money(income-out)}",
                color = White,
                size = 12,
            )
        }
    }
}

@Composable
fun BudgetCard(
    title: String,
    p: Plan,
    used: Long,
    modifier: Modifier = Modifier,
    onClick: (Rect) -> Unit,
) {
    var rect by remember { mutableStateOf(Rect.Zero) }
    val placeholder = remember(used) { "-".repeat((used / 100).toString().length) + ".--" }
    InkCard(
        modifier.onGloballyPositioned { rect = it.boundsInRoot() },
        onClick = { onClick(rect) },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(title, Modifier.weight(1f), bold = true, size = 18)
            InkIcon("arrow", Modifier.size(18.dp), Mid)
        }
        Spacer(Modifier.height(12.dp))
        Progress(used, p.total, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Label("用量", color = Mid, size = 10)
                Label(money(used), color = Mid, size = 12)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Label("总量", color = Mid, size = 10)
                Label(if (p.total > 0) money(p.total) else placeholder, color = Mid, size = 12)
            }
            Column(horizontalAlignment = Alignment.End) {
                Label("余量", color = Mid, size = 10)
                Label(
                    if (p.total > 0) money(p.total - used) else placeholder,
                    color = Mid,
                    size = 12,
                )
            }
        }
    }
}

@Composable
fun SwipeBox(
    delete: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    var drag by remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val width = with(density) { 72.dp.toPx() }
    val offset by
        androidx.compose.animation.core.animateFloatAsState(
            if (drag < -width / 2) -width else 0f,
            androidx.compose.animation.core.tween(220, easing = MotionEase),
            label = "reveal",
        )
    Box(modifier) {
        if (offset < -1)
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier.align(Alignment.CenterEnd)
                        .width(72.dp)
                        .fillMaxHeight()
                        .background(Light)
                        .clickable(onClickLabel = "移入回收站", onClick = delete),
                    contentAlignment = Alignment.Center,
                ) {
                    InkIcon("trash")
                }
            }
        content(
            Modifier.graphicsLayer { translationX = offset }
                .pointerInput(enabled) {
                    if (enabled)
                        detectHorizontalDragGestures(
                            onDragEnd = { drag = if (drag < -width / 2) -width else 0f },
                            onDragCancel = { drag = 0f },
                        ) { c, x ->
                            c.consume()
                            drag = (drag + x).coerceIn(-width, 0f)
                        }
                }
        )
    }
}

@Composable
fun DayCard(
    date: String,
    entries: List<Entry>,
    d: Ledger,
    a: ScreenActions,
    selected: Set<String>,
    multi: Boolean,
    onSelect: (String) -> Unit,
) {
    var rect by remember { mutableStateOf(Rect.Zero) }
    val deleting = LocalDeletingIds.current
    val deleteProgress = LocalDeleteProgress.current
    val wholeDay = entries.all { it.id in deleting }
    val day = remember(date) { LocalDate.parse(date) }
    val relative =
        when (java.time.temporal.ChronoUnit.DAYS.between(day, LocalDate.now())) {
            0L -> "今天"
            1L -> "昨天"
            2L -> "前天"
            else -> ""
        }
    val week = listOf("一", "二", "三", "四", "五", "六", "日")[day.dayOfWeek.value - 1]
    SwipeBox(
        { a.delete(entries.map { it.id }.toSet(), rect) },
        !multi,
        Modifier.fillMaxWidth()
            .onGloballyPositioned { rect = it.boundsInRoot() }
            .graphicsLayer { if (wholeDay) alpha = 1 - particleEase(deleteProgress() / .38f) },
    ) { swipe ->
        Column(swipe.clip(RoundedCornerShape(18.dp)).background(White)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Light, Color(0xfff8f8f8))))
                    .pointerInput(date) {
                        detectTapGestures(onTap = { a.add(date) }, onLongPress = { a.add(date) })
                    }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Label(day.format(DateTimeFormatter.ofPattern("MM.dd")), bold = true, size = 17)
                    Label("星期$week", bold = true, size = 15)
                    if (relative.isNotEmpty()) Label(relative, bold = true, size = 13)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    val out = sum(entries, "expense")
                    val inc = sum(entries, "income")
                    Label("支 ${money(out)}", color = Expense, bold = true, size = 11)
                    Label("收 ${money(inc)}", color = Income, bold = true, size = 11)
                }
            }
            HorizontalDivider(color = Mid.copy(.45f), thickness = .5.dp)
            entries.forEachIndexed { index, e ->
                if (index > 0)
                    HorizontalDivider(
                        Modifier.padding(horizontal = 12.dp),
                        color = Light,
                        thickness = .5.dp,
                    )
                var rowRect by remember { mutableStateOf(Rect.Zero) }
                DisposableEffect(e.id) { onDispose { a.rowBounds(e.id, Rect.Zero) } }
                SwipeBox(
                    { a.delete(setOf(e.id), rowRect) },
                    !multi,
                    Modifier.fillMaxWidth()
                        .onGloballyPositioned {
                            rowRect = it.boundsInRoot()
                            a.rowBounds(e.id, rowRect)
                        }
                        .graphicsLayer {
                            if (!wholeDay && e.id in deleting)
                                alpha = 1 - particleEase(deleteProgress() / .38f)
                        },
                ) { rowSwipe ->
                    Row(
                        rowSwipe
                            .fillMaxWidth()
                            .background(White)
                            .pointerInput(e.id, multi) {
                                detectTapGestures(
                                    onTap = { if (multi) onSelect(e.id) else a.detail(e) },
                                    onLongPress = { p ->
                                        a.context(e, rowRect.topLeft + p, rowRect)
                                    },
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CatIcon(e.cat, e.id in selected) {
                            if (multi) onSelect(e.id) else a.category(setOf(e.id), e.type)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                            Text(
                                d.category(e.cat).name,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                e.note,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = Mid,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Label(
                            (if (e.type == "income") "+" else "−") + money(e.cents),
                            color = if (e.type == "income") Income else Expense,
                            size = 18,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Screen(
    page: Page,
    d: Ledger,
    mode: String,
    range: String,
    budgetMode: String,
    budgetRange: String,
    tablet: Boolean,
    listState: LazyListState,
    a: ScreenActions,
    selected: Set<String>,
    multi: Boolean,
    onSelect: (String) -> Unit,
    onShift: (Int) -> Unit,
    onRange: () -> Unit,
) {
    val es = remember(d.entries, d.book, mode, range) { d.selected(mode, range) }
    var query by remember { mutableStateOf("") }
    val groups =
        remember(es, query) {
            es.filter {
                    ("${d.category(it.cat).name} ${it.note} ${amount(it.cents)}").contains(
                        query,
                        true,
                    )
                }
                .groupBy { it.date }
                .toList()
        }
    val padding = if (tablet) 20.dp else 12.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val contentWidth = minOf(maxWidth, 1120.dp)
        val leftWidth =
            if (tablet && page == Page.HOME) (contentWidth - padding * 2 - 12.dp) * .375f else 0.dp
        LazyColumn(
            Modifier.width(contentWidth).fillMaxHeight().align(Alignment.TopCenter),
            state = listState,
            contentPadding =
                PaddingValues(start = padding, end = padding, top = 64.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (page) {
                Page.HOME -> {
                    item("summary") { Summary(es) }
                    if (!tablet)
                        item("home-budget") {
                            val bm = d.settings.budgetMode
                            val br =
                                if (bm == "year") range.take(4)
                                else if (range.length >= 7) range
                                else range + "-" + LocalDate.now().toString().substring(5, 7)
                            BudgetCard(
                                if (bm == "year") "年度预算" else "月度预算",
                                d.plan(bm, br),
                                sum(d.selected(bm, br), "expense"),
                                onClick = a.budget,
                            )
                        }
                    items(groups, key = { it.first }) { (date, entries) ->
                        Box(Modifier.padding(start = if (tablet) leftWidth + 12.dp else 0.dp)) {
                            DayCard(date, entries, d, a, selected, multi, onSelect)
                        }
                    }
                    if (groups.isEmpty()) item { Empty("暂无账单") }
                }
                Page.SEARCH -> {
                    item {
                        OutlinedTextField(
                            query,
                            { query = it },
                            Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索分类、备注或金额") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                    items(groups, key = { it.first }) { (date, entries) ->
                        DayCard(date, entries, d, a, selected, multi, onSelect)
                    }
                    if (groups.isEmpty()) item { Empty("暂无匹配的账单") }
                }
                Page.BUDGET -> {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton("back", "上一期", { onShift(-1) }, Dark)
                            Label(
                                budgetRange.replace('-', '.'),
                                Modifier.clickable(onClick = onRange).padding(12.dp),
                                size = 22,
                            )
                            IconButton("arrow", "下一期", { onShift(1) }, Dark)
                        }
                    }
                    val period = d.selected(budgetMode, budgetRange)
                    val plan = d.plan(budgetMode, budgetRange)
                    val used = sum(period, "expense")
                    item { BudgetCard("合计预算", plan, used, onClick = { a.amount(null) }) }
                    item {
                        if (tablet)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                CategoryBudgets(d, plan, period, a, Modifier.weight(1f))
                                Trend(plan, period, budgetMode, budgetRange, Modifier.weight(1f))
                            }
                        else CategoryBudgets(d, plan, period, a)
                    }
                    if (!tablet) item { Trend(plan, period, budgetMode, budgetRange) }
                }
                Page.STATS -> {
                    item { Summary(es, true) }
                    item {
                        InkCard(Modifier.fillMaxWidth()) {
                            Label("支出构成", bold = true, size = 16)
                            val totals =
                                es.filter { it.type == "expense" }
                                    .groupBy { it.cat }
                                    .mapValues { sum(it.value, "expense") }
                                    .toList()
                                    .sortedByDescending { it.second }
                            if (totals.isEmpty()) Empty("暂无支出记录")
                            totals.forEach { (id, v) ->
                                Row(
                                    Modifier.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CatIcon(id)
                                    Spacer(Modifier.width(8.dp))
                                    Label(d.category(id).name, size = 12)
                                    Spacer(Modifier.width(10.dp))
                                    Progress(v, sum(es, "expense"), Modifier.weight(1f))
                                    Spacer(Modifier.width(10.dp))
                                    Label(money(v), size = 12)
                                }
                            }
                        }
                    }
                }
                Page.CATEGORIES -> {
                    item {
                        InkCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Label("记账分类", Modifier.weight(1f), bold = true, size = 16)
                                IconButton("plus", "新增分类", { a.rename(null) }, Dark)
                            }
                            d.categories.chunked(if (tablet) 5 else 3).forEach { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    row.forEach { c ->
                                        Column(
                                            Modifier.weight(1f)
                                                .clickable { a.rename(c) }
                                                .padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            CatIcon(c.id)
                                            Spacer(Modifier.height(5.dp))
                                            Label(c.name)
                                            Label(
                                                if (c.type == "income") "收入" else "支出",
                                                color = Mid,
                                                size = 10,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Page.SETTINGS -> {
                    item {
                        InkCard(Modifier.fillMaxWidth()) {
                            Setting("当前账本", d.books.first { it.id == d.book }.name, a.books)
                        }
                    }
                    item {
                        InkCard(Modifier.fillMaxWidth()) {
                            Options(
                                "光感强度",
                                listOf("strong" to "强", "balanced" to "均衡", "weak" to "弱"),
                                d.settings.effect,
                            ) {
                                a.settings(d.settings.copy(effect = it))
                            }
                            Options(
                                "主页预算",
                                listOf("month" to "月度", "year" to "年度"),
                                d.settings.budgetMode,
                            ) {
                                a.settings(d.settings.copy(budgetMode = it))
                            }
                            Options(
                                "平板底栏位置",
                                listOf("left" to "左下", "center" to "居中", "right" to "右下"),
                                d.settings.tabletDockPosition,
                            ) {
                                a.settings(d.settings.copy(tabletDockPosition = it))
                            }
                            Setting("回收站", "手动恢复或永久删除，不自动清理", a.trash)
                        }
                    }
                    item {
                        InkCard(Modifier.fillMaxWidth()) {
                            Setting("导出完整备份", "账本、账单、分类、预算和回收站", a.export)
                            Setting("导入备份", "检查格式后确认替换当前数据", a.import)
                            if (d.demo) Setting("结束演示，创建空白账本", "建议先导出当前记录", a.reset)
                        }
                    }
                    item {
                        Label(
                            "墨账 0.1.14-native · Kotlin / Jetpack Compose",
                            Modifier.fillMaxWidth(),
                            color = Mid,
                            size = 11,
                        )
                    }
                }
                Page.TRASH -> {
                    val trash =
                        d.entries
                            .filter { it.deletedAt != null && it.book == d.book }
                            .sortedByDescending { it.deletedAt }
                    item { Label("已删除 · ${trash.size} 笔", color = White, bold = true, size = 16) }
                    items(trash, key = { it.id }) { e ->
                        InkCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CatIcon(e.cat)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Label(
                                        "${d.category(e.cat).name} · ${money(e.cents)}",
                                        size = 13,
                                    )
                                    Label("${e.date} ${e.note}", color = Mid, size = 11)
                                }
                                TextButton(onClick = { a.restore(e) }) { Text("恢复", color = Dark) }
                                IconButton("trash", "永久删除", { a.purge(e) }, Dark)
                            }
                        }
                    }
                    if (trash.isEmpty()) item { Empty("回收站是空的") }
                }
            }
        }
        if (tablet && page == Page.HOME) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val bm = d.settings.budgetMode
            val br =
                if (bm == "year") range.take(4)
                else if (range.length >= 7) range
                else range + "-" + LocalDate.now().toString().substring(5, 7)
            BudgetCard(
                if (bm == "year") "年度预算" else "月度预算",
                d.plan(bm, br),
                sum(d.selected(bm, br), "expense"),
                Modifier.width(leftWidth).offset {
                    androidx.compose.ui.unit.IntOffset(
                        with(density) { (padding + (maxWidth - contentWidth) / 2).toPx() }.toInt(),
                        with(density) {
                            val top =
                                if (listState.firstVisibleItemIndex == 0)
                                    200.dp.toPx() - listState.firstVisibleItemScrollOffset
                                else 76.dp.toPx()
                            top.coerceAtLeast(76.dp.toPx()).toInt()
                        },
                    )
                },
                a.budget,
            )
        }
    }
}

@Composable
fun Empty(s: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
        Label(s, color = Mid, size = 13)
    }
}

@Composable
fun Setting(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Label(title)
            Label(subtitle, color = Mid, size = 10)
        }
        InkIcon("arrow")
    }
}

@Composable
fun Options(
    title: String,
    options: List<Pair<String, String>>,
    current: String,
    onChange: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(title, Modifier.weight(1f), size = 13)
        options.forEach { (key, name) ->
            TextButton(
                onClick = { onChange(key) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier =
                    Modifier.defaultMinSize(minWidth = 1.dp)
                        .then(
                            if (key == current) Modifier.background(Light, CircleShape)
                            else Modifier
                        ),
            ) {
                Text(name, color = Dark, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun CategoryBudgets(
    d: Ledger,
    p: Plan,
    es: List<Entry>,
    a: ScreenActions,
    modifier: Modifier = Modifier,
) {
    InkCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("分类预算", Modifier.weight(1f), bold = true, size = 16)
            IconButton("plus", "新增分类预算", a.categoryBudget, Dark)
        }
        Label("合计：${money(p.categories.values.sum())}", color = Mid, size = 11)
        HorizontalDivider(Modifier.padding(top = 8.dp), color = Mid.copy(.45f), thickness = .5.dp)
        if (p.categories.isEmpty()) Empty("暂未设置分类预算")
        p.categories.entries.forEachIndexed { index, (id, total) ->
            if (index > 0) HorizontalDivider(color = Light, thickness = .5.dp)
            val entries = es.filter { it.cat == id && it.type == "expense" }
            val used = sum(entries, "expense")
            Row(
                Modifier.fillMaxWidth().clickable { a.amount(id) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(.40f), verticalAlignment = Alignment.CenterVertically) {
                    CatIcon(id)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Label(d.category(id).name, size = 11)
                        Label("额度：${money(total)}", color = Mid, size = 8)
                    }
                }
                Column(Modifier.weight(.60f)) {
                    Progress(used, total, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Label("用量：${money(used)}（共${entries.size}笔）", color = Mid, size = 8)
                        Label("余量：${money(total-used)}", color = Mid, size = 8)
                    }
                }
            }
        }
    }
}

@Composable
fun Trend(
    plan: Plan,
    entries: List<Entry>,
    mode: String,
    range: String,
    modifier: Modifier = Modifier,
) {
    InkCard(modifier.fillMaxWidth()) {
        Label("预算趋势", bold = true, size = 16)
        if (plan.total <= 0) Empty("暂未设置合计预算")
        else {
            Row(
                Modifier.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Label("● 余量", color = Income, size = 10)
                Label("● 用量", color = Expense, size = 10)
            }
            val start =
                if (mode == "year") LocalDate.of(range.take(4).toInt(), 1, 1)
                else YearMonth.parse(range.take(7)).atDay(1)
            val end =
                if (mode == "year") start.plusYears(1).minusDays(1)
                else start.plusMonths(1).minusDays(1)
            val daily =
                remember(entries) {
                    entries
                        .filter { it.type == "expense" }
                        .groupBy { it.date }
                        .mapValues { sum(it.value, "expense") }
                }
            val count = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
            val plotted =
                min(
                    count,
                    (java.time.temporal.ChronoUnit.DAYS.between(start, LocalDate.now()).toInt() + 1)
                        .coerceAtLeast(0),
                )
            val values =
                remember(daily, start, plotted) {
                    var used = 0L
                    List(plotted) { i ->
                        used += daily[start.plusDays(i.toLong()).toString()] ?: 0
                        used
                    }
                }
            val high = max(plan.total, values.maxOrNull() ?: 0L).toDouble().coerceAtLeast(1.0)
            val low = min(0L, plan.total - (values.maxOrNull() ?: 0L)).toDouble()
            Canvas(Modifier.fillMaxWidth().height(220.dp).padding(top = 8.dp, bottom = 24.dp)) {
                val h = size.height
                val w = size.width
                fun y(v: Double): Float {
                    return ((high - v) / (high - low) * h).toFloat()
                }
                val zero = y(0.0)
                drawLine(Light, Offset(0f, zero), Offset(w, zero), 1.dp.toPx())
                for ((color, remaining) in listOf(Income to true, Expense to false)) {
                    val points =
                        values.mapIndexed { i, v ->
                            Offset(
                                if (count > 1) i.toFloat() / (count - 1) * w else 0f,
                                y((if (remaining) plan.total - v else v).toDouble()),
                            )
                        }
                    if (points.isNotEmpty()) {
                        val line =
                            Path().apply {
                                moveTo(points[0].x, points[0].y)
                                points.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                        val area =
                            Path().apply {
                                addPath(line)
                                lineTo(points.last().x, zero)
                                lineTo(points.first().x, zero)
                                close()
                            }
                        drawPath(area, color.copy(alpha = .5f))
                        drawPath(line, color, style = Stroke(2.dp.toPx()))
                        if (mode == "month") points.forEach { drawCircle(color, 2.dp.toPx(), it) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Label(if (mode == "year") "01.01" else "01", color = Mid, size = 9)
                Label(
                    if (mode == "year") "12.31" else end.dayOfMonth.toString(),
                    color = Mid,
                    size = 9,
                )
            }
        }
    }
}
