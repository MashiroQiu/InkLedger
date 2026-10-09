package cn.inkledger.app

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import java.time.*
import kotlin.math.*

@Composable
fun SheetOverlay(
    s: Sheet,
    d: Ledger,
    model: LedgerViewModel,
    budgetMode: String,
    budgetRange: String,
    rangeMode: String,
    range: String,
    gutter: Float,
    width: Float,
    height: Float,
    p: Animatable<Float, *>,
    close: () -> Unit,
    replace: (Sheet) -> Unit,
    period: (String, String) -> Unit,
    budgetPeriod: (String) -> Unit,
) {
    val density = LocalDensity.current
    val contentWidth = width - gutter
    val panelWidth =
        if (s.kind == "detail") contentWidth else min(contentWidth, with(density) { 660.dp.toPx() })
    val panelHeight =
        if (s.kind == "detail") height * .5f
        else min(height * .82f, with(density) { 640.dp.toPx() })
    val left = gutter + (contentWidth - panelWidth) / 2
    val top = height - panelHeight
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.offset { IntOffset(gutter.roundToInt(), 0) }
                .size(with(density) { contentWidth.toDp() }, with(density) { height.toDp() })
                .graphicsLayer { alpha = p.value }
                .background(Color.Black.copy(.5f))
                .clickable(onClick = close)
        )
        Box(
            Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { panelWidth.toDp() }, with(density) { panelHeight.toDp() })
                .graphicsLayer {
                    val progress = p.value
                    val origin = s.origin
                    transformOrigin = TransformOrigin(0f, 0f)
                    if (origin != null) {
                        scaleX =
                            origin.width / panelWidth + (1 - origin.width / panelWidth) * progress
                        scaleY =
                            origin.height / panelHeight +
                                (1 - origin.height / panelHeight) * progress
                        translationX = (origin.left - left) * (1 - progress)
                        translationY = (origin.top - top) * (1 - progress)
                    } else translationY = panelHeight * (1 - progress)
                    val r = with(density) { 28.dp.toPx() }
                    val rx =
                        if (origin != null) panelWidth / 2 * (1 - progress) + r * progress else r
                    val ry =
                        if (origin != null) panelHeight / 2 * (1 - progress) + r * progress else r
                    shape = GenericShape { size, _ ->
                        addRoundRect(
                            RoundRect(
                                Rect(Offset.Zero, size),
                                CornerRadius(rx, ry),
                                CornerRadius(rx, ry),
                                CornerRadius(
                                    if (origin != null) panelWidth / 2 * (1 - progress) else 0f,
                                    if (origin != null) panelHeight / 2 * (1 - progress) else 0f,
                                ),
                                CornerRadius(
                                    if (origin != null) panelWidth / 2 * (1 - progress) else 0f,
                                    if (origin != null) panelHeight / 2 * (1 - progress) else 0f,
                                ),
                            )
                        )
                    }
                    clip = true
                }
                .drawBehind {
                    drawRect(
                        if (s.origin != null) lerp(Mid, Color(0xfff7f7f7), p.value)
                        else Color(0xfff7f7f7)
                    )
                }
        ) {
            if (s.origin != null)
                Box(
                    Modifier.align(Alignment.Center).graphicsLayer {
                        val t = p.value
                        alpha = (1 - t / .20f).coerceIn(0f, 1f)
                        val sx = s.origin.width / panelWidth + (1 - s.origin.width / panelWidth) * t
                        val sy =
                            s.origin.height / panelHeight + (1 - s.origin.height / panelHeight) * t
                        scaleX = 1 / sx
                        scaleY = 1 / sy
                    }
                ) {
                    InkIcon("plus", Modifier.size(28.dp), White, 4f)
                }
            Column(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        if (s.origin != null) alpha = ((p.value - .2f) / .8f).coerceIn(0f, 1f)
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(LightMid)
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Label(
                        when (s.kind) {
                            "editor" -> if (s.entry != null) "编辑账单" else "记一笔"
                            "detail" -> "账单详情"
                            "classify" -> "修改分类"
                            "books" -> "账本管理"
                            "amount" -> "设置预算"
                            "category" -> "编辑分类"
                            "period",
                            "budget-period" -> "选择时间范围"
                            "confirm" -> "请确认"
                            "welcome" -> "欢迎来到墨账"
                            else -> "选择预算分类"
                        },
                        Modifier.weight(1f),
                        size = 20,
                        bold = true,
                    )
                    IconButton("close", "关闭", close, Dark)
                }
                when (s.kind) {
                    "editor" -> EntryForm(s, d, model, close)
                    "detail" -> {
                        val e = s.entry!!
                        Row(
                            Modifier.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CatIcon(e.cat)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Label(d.category(e.cat).name, size = 20)
                                Label(
                                    (if (e.type == "income") "+" else "−") + money(e.cents),
                                    color = if (e.type == "income") Income else Expense,
                                    size = 32,
                                )
                            }
                        }
                        Label("${e.date}  ${e.time}", color = Mid)
                        Label(e.note, Modifier.padding(vertical = 16.dp), color = Mid)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            InkButton("编辑", Modifier.weight(1f)) {
                                replace(Sheet("editor", entry = e))
                            }
                            InkButton("移入回收站", Modifier.weight(1f)) {
                                model.trash(setOf(e.id), close)
                            }
                        }
                    }
                    "classify" ->
                        CategoryGrid(d.categories.filter { it.type == s.type }) { c ->
                            model.classify(s.ids, c.id, close)
                        }
                    "choose-budget-category" ->
                        CategoryGrid(d.categories.filter { it.type == "expense" }) { c ->
                            replace(Sheet("amount", cat = c.id))
                        }
                    "amount" -> {
                        val plan = d.plan(budgetMode, budgetRange)
                        var input by
                            remember(s.cat, budgetMode, budgetRange) {
                                mutableStateOf(
                                    amount(
                                        if (s.cat == null) plan.total
                                        else plan.categories[s.cat] ?: 0
                                    )
                                )
                            }
                        Input(
                            input,
                            { input = it },
                            if (s.cat == null) "合计预算" else d.category(s.cat).name,
                            KeyboardType.Decimal,
                        )
                        InkButton("保存", Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            runCatching { parseAmount(input) }
                                .onSuccess { model.plan(budgetMode, budgetRange, s.cat, it, close) }
                                .onFailure { model.report(it.message ?: "金额不正确") }
                        }
                    }
                    "books" -> {
                        d.books.forEach { b ->
                            Setting(b.name, if (b.id == d.book) "当前账本" else "切换到此账本") {
                                model.change({ it.copy(book = b.id) }, close)
                            }
                        }
                        var name by remember { mutableStateOf("") }
                        Input(name, { name = it.take(80) }, "新账本名称")
                        InkButton("创建账本", Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            if (name.isBlank()) model.report("请输入账本名称")
                            else
                                model.change(
                                    {
                                        val id = newId()
                                        it.copy(books = it.books + Book(id, name), book = id)
                                    },
                                    close,
                                )
                        }
                    }
                    "category" -> {
                        val old = d.categories.find { it.id == s.cat }
                        var name by remember(s.cat) { mutableStateOf(old?.name ?: "") }
                        var type by remember(s.cat) { mutableStateOf(old?.type ?: "expense") }
                        Input(name, { if (charWidth(it) <= 10) name = it }, "分类名称（最多 10 字符宽度）")
                        if (old == null)
                            Options("收支类型", listOf("expense" to "支出", "income" to "收入"), type) {
                                type = it
                            }
                        InkButton("保存", Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            if (name.isBlank()) model.report("请输入分类名称")
                            else
                                model.change(
                                    { db ->
                                        if (old == null)
                                            db.copy(
                                                categories =
                                                    db.categories + Category(newId(), name, type)
                                            )
                                        else
                                            db.copy(
                                                categories =
                                                    db.categories.map {
                                                        if (it.id == old.id) it.copy(name = name)
                                                        else it
                                                    }
                                            )
                                    },
                                    close,
                                )
                        }
                    }
                    "period",
                    "budget-period" -> {
                        var mode by
                            remember(s.kind) {
                                mutableStateOf(if (s.kind == "period") rangeMode else budgetMode)
                            }
                        var input by
                            remember(s.kind) {
                                mutableStateOf(if (s.kind == "period") range else budgetRange)
                            }
                        if (s.kind == "period")
                            Options("查看范围", listOf("month" to "月度", "year" to "年度"), mode) {
                                mode = it
                                input =
                                    if (it == "year") input.take(4)
                                    else
                                        input.take(4) +
                                            "-" +
                                            LocalDate.now().toString().substring(5, 7)
                            }
                        Input(input, { input = it }, if (mode == "year") "YYYY" else "YYYY-MM")
                        InkButton("查看", Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            runCatching {
                                    if (mode == "year")
                                        require(input.matches(Regex("(?:19\\d\\d|20\\d\\d|2100)")))
                                    else YearMonth.parse(input)
                                    if (s.kind == "period") period(mode, input)
                                    else budgetPeriod(input)
                                }
                                .onFailure { model.report("请选择有效时间范围") }
                        }
                    }
                    "confirm" -> {
                        Label(s.text, Modifier.padding(vertical = 16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            InkButton("取消", Modifier.weight(1f), false, close)
                            InkButton("确认", Modifier.weight(1f)) { s.confirm?.invoke() }
                        }
                    }
                    "welcome" -> {
                        Label("账单仅保存于这台设备。可使用空白账本，或体验演示数据。", Modifier.padding(vertical = 16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            InkButton("体验演示", Modifier.weight(1f), false) {
                                model.start(true)
                                close()
                            }
                            InkButton("开始记账", Modifier.weight(1f)) {
                                model.start(false)
                                close()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EntryForm(s: Sheet, d: Ledger, model: LedgerViewModel, close: () -> Unit) {
    val e = s.entry
    var type by remember(s) { mutableStateOf(e?.type ?: "expense") }
    var cat by
        remember(s) {
            mutableStateOf(e?.cat ?: d.categories.firstOrNull { it.type == type }?.id ?: "other")
        }
    var cents by remember(s) { mutableStateOf(e?.cents?.let(::amount) ?: "") }
    var note by remember(s) { mutableStateOf(e?.note ?: "") }
    var date by remember(s) { mutableStateOf(e?.date ?: s.date) }
    var time by remember(s) { mutableStateOf(e?.time ?: LocalTime.now().toString().take(5)) }
    Options("收支类型", listOf("expense" to "支出", "income" to "收入"), type) {
        type = it
        cat = d.categories.firstOrNull { c -> c.type == type }?.id ?: "other"
    }
    Input(cents, { cents = it }, "金额", KeyboardType.Decimal)
    CategoryGrid(d.categories.filter { it.type == type }, cat) { cat = it.id }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Input(date, { date = it }, "日期 YYYY-MM-DD", modifier = Modifier.weight(1f))
        Input(time, { time = it }, "时间 HH:mm", modifier = Modifier.weight(1f))
    }
    Input(note, { note = it.take(200) }, "备注")
    InkButton("保存账单", Modifier.fillMaxWidth().padding(top = 16.dp)) {
        runCatching { parseAmount(cents) }
            .onSuccess { model.saveEntry(e?.id, date, time, cat, it, note, close) }
            .onFailure { model.report(it.message ?: "金额不正确") }
    }
}

@Composable
fun CategoryGrid(
    categories: List<Category>,
    selected: String? = null,
    onClick: (Category) -> Unit,
) {
    categories.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { c ->
                Column(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onClick(c) }
                        .padding(vertical = 12.dp)
                        .background(
                            if (selected == c.id) Light else Color.Transparent,
                            RoundedCornerShape(12.dp),
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CatIcon(c.id)
                    Spacer(Modifier.height(4.dp))
                    Label(c.name, size = 12)
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun Input(
    value: String,
    onChange: (String) -> Unit,
    title: String,
    type: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value,
        onChange,
        modifier.fillMaxWidth().padding(vertical = 5.dp),
        label = { Text(title, fontSize = 12.sp) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = type),
    )
}

@Composable
fun InkButton(
    title: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick,
        modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (primary) Dark else Light,
                contentColor = if (primary) White else Dark,
            ),
    ) {
        Text(title)
    }
}
