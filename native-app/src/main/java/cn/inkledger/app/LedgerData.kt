package cn.inkledger.app

import android.content.Context
import androidx.room.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "books") data class Book(@PrimaryKey val id: String, val name: String)

@Entity(tableName = "categories")
data class Category(@PrimaryKey val id: String, val name: String, val type: String)

@Entity(tableName = "entries", indices = [Index(value = ["book", "date"])])
data class Entry(
    @PrimaryKey val id: String,
    val book: String,
    val date: String,
    val time: String,
    val cat: String,
    val cents: Long,
    val note: String,
    val type: String,
    val deletedAt: Long? = null,
)

@Entity(tableName = "budgets") data class BudgetRow(@PrimaryKey val key: String, val total: Long)

@Entity(tableName = "category_budgets", primaryKeys = ["budgetKey", "cat"])
data class CategoryBudget(val budgetKey: String, val cat: String, val cents: Long)

@Entity(tableName = "metadata") data class Metadata(@PrimaryKey val key: String, val value: String)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM books") suspend fun books(): List<Book>

    @Query("SELECT * FROM categories") suspend fun categories(): List<Category>

    @Query("SELECT * FROM entries ORDER BY date DESC,time DESC") suspend fun entries(): List<Entry>

    @Query("SELECT * FROM budgets") suspend fun budgets(): List<BudgetRow>

    @Query("SELECT * FROM category_budgets") suspend fun categoryBudgets(): List<CategoryBudget>

    @Query("SELECT * FROM metadata WHERE `key`='state'") suspend fun metadata(): Metadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBooks(v: List<Book>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCategories(v: List<Category>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putEntries(v: List<Entry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBudgets(v: List<BudgetRow>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCategoryBudgets(v: List<CategoryBudget>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMetadata(v: Metadata)

    @Query("DELETE FROM books") suspend fun clearBooks()

    @Query("DELETE FROM categories") suspend fun clearCategories()

    @Query("DELETE FROM entries") suspend fun clearEntries()

    @Delete suspend fun removeEntries(v: List<Entry>)

    @Query("DELETE FROM budgets") suspend fun clearBudgets()

    @Query("DELETE FROM category_budgets") suspend fun clearCategoryBudgets()
}

@Database(
    entities =
        [
            Book::class,
            Category::class,
            Entry::class,
            BudgetRow::class,
            CategoryBudget::class,
            Metadata::class,
        ],
    version = 1,
    exportSchema = true,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledger(): LedgerDao
}

data class Plan(val total: Long = 0, val categories: Map<String, Long> = emptyMap())

data class Preferences(
    val effect: String = "balanced",
    val budgetMode: String = "month",
    val tabletDockPosition: String = "center",
)

data class Ledger(
    val books: List<Book> = listOf(Book("default", "日常账本")),
    val book: String = "default",
    val categories: List<Category> = defaults(),
    val entries: List<Entry> = emptyList(),
    val budgets: Map<String, Plan> = emptyMap(),
    val settings: Preferences = Preferences(),
    val demo: Boolean = false,
) {
    fun category(id: String) =
        categories.firstOrNull { it.id == id } ?: Category("other", "其他", "expense")

    fun selected(mode: String, range: String) =
        entries
            .filter {
                it.deletedAt == null &&
                    it.book == book &&
                    it.date.startsWith(range.take(if (mode == "year") 4 else 7))
            }
            .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.time })

    fun plan(mode: String, range: String) =
        budgets["$book:$mode:${range.take(if(mode=="year")4 else 7)}"] ?: Plan()
}

fun defaults() =
    listOf(
            "food" to "餐饮",
            "transport" to "交通",
            "shop" to "购物",
            "daily" to "生活",
            "health" to "医疗",
            "fun" to "娱乐",
            "other" to "其他",
        )
        .map { Category(it.first, it.second, "expense") } +
        listOf(Category("salary", "工资", "income"), Category("bonus", "奖金", "income"))

fun newId() = UUID.randomUUID().toString()

private val formatMoney =
    ThreadLocal.withInitial {
        java.text.NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

fun money(n: Long) = formatMoney.get()!!.format(BigDecimal.valueOf(n, 2))

fun amount(n: Long) = BigDecimal.valueOf(n, 2).toPlainString()

fun parseAmount(s: String): Long {
    require(Regex("\\d{1,10}(\\.\\d{1,2})?").matches(s)) { "请输入最多两位小数的非负金额" }
    return BigDecimal(s).movePointRight(2).longValueExact()
}

fun sum(entries: List<Entry>, type: String) =
    entries.filter { it.type == type }.fold(0L) { a, e -> Math.addExact(a, e.cents) }

fun charWidth(s: String) =
    s.codePoints().toArray().fold(0) { n, c ->
        n + if (c in 48..57 || c in 65..90 || c in 97..122) 1 else 2
    }

object LedgerJson {
    fun encode(d: Ledger): String {
        val o = JSONObject().put("version", 1).put("book", d.book).put("demo", d.demo)
        o.put(
            "books",
            JSONArray(d.books.map { JSONObject().put("id", it.id).put("name", it.name) }),
        )
        o.put(
            "categories",
            JSONArray(
                d.categories.map {
                    JSONObject().put("id", it.id).put("name", it.name).put("type", it.type)
                }
            ),
        )
        fun row(e: Entry) =
            JSONObject()
                .put("id", e.id)
                .put("book", e.book)
                .put("date", e.date)
                .put("time", e.time)
                .put("cat", e.cat)
                .put("cents", e.cents)
                .put("note", e.note)
                .put("type", e.type)
                .apply { e.deletedAt?.let { put("deletedAt", it) } }
        o.put("entries", JSONArray(d.entries.filter { it.deletedAt == null }.map(::row)))
        o.put("trash", JSONArray(d.entries.filter { it.deletedAt != null }.map(::row)))
        o.put(
            "settings",
            JSONObject()
                .put("effect", d.settings.effect)
                .put("budgetMode", d.settings.budgetMode)
                .put("tabletDockPosition", d.settings.tabletDockPosition),
        )
        val b = JSONObject()
        d.budgets.forEach { (k, p) ->
            b.put(k, JSONObject().put("total", p.total).put("categories", JSONObject(p.categories)))
        }
        o.put("budgets", b)
        return o.toString()
    }

    fun decode(raw: String): Ledger {
        require(raw.toByteArray().size <= 32 * 1024 * 1024) { "备份过大" }
        val o = JSONObject(raw)
        require(o.getInt("version") == 1) { "备份版本不支持" }
        fun objects(key: String) =
            o.getJSONArray(key).let { a -> List(a.length()) { a.getJSONObject(it) } }
        fun id(v: String): String {
            require(Regex("[A-Za-z0-9_-]{1,100}").matches(v)) { "标识符不正确" }
            return v
        }
        val books =
            objects("books").map {
                Book(
                    id(it.getString("id")),
                    it.getString("name").also { n -> require(n.isNotBlank() && n.length <= 80) },
                )
            }
        require(books.isNotEmpty() && books.map { it.id }.distinct().size == books.size)
        val cats =
            objects("categories").map {
                Category(
                    id(it.getString("id")),
                    it.getString("name").also { n ->
                        require(n.isNotEmpty() && charWidth(n) <= 10)
                    },
                    it.getString("type").also { t -> require(t in listOf("expense", "income")) },
                )
            }
        require(cats.map { it.id }.distinct().size == cats.size)
        fun read(e: JSONObject, trash: Boolean): Entry {
            val date = e.getString("date")
            require(
                Regex("\\d{4}-\\d{2}-\\d{2}").matches(date) &&
                    LocalDate.parse(date).toString() == date
            )
            val time = e.getString("time")
            require(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(time))
            LocalTime.parse(time)
            val cents = e.getLong("cents")
            require(
                cents > 0 &&
                    cents <= 9_007_199_254_740_991L &&
                    e.getDouble("cents") == cents.toDouble()
            )
            val book = id(e.getString("book"))
            val cat = id(e.getString("cat"))
            val type = e.getString("type")
            require(books.any { it.id == book } && cats.any { it.id == cat && it.type == type })
            return Entry(
                id(e.getString("id")),
                book,
                date,
                time,
                cat,
                cents,
                e.getString("note").also { require(it.length <= 200) },
                type,
                if (trash) e.optLong("deletedAt", System.currentTimeMillis()) else null,
            )
        }
        val entries =
            objects("entries").map { read(it, false) } + objects("trash").map { read(it, true) }
        require(entries.map { it.id }.distinct().size == entries.size)
        require(entries.fold(0L) { a, e -> Math.addExact(a, e.cents) } <= 9_007_199_254_740_991L)
        val settings = o.getJSONObject("settings")
        val prefs =
            Preferences(
                settings.getString("effect"),
                settings.getString("budgetMode"),
                settings.optString("tabletDockPosition", "center"),
            )
        require(
            prefs.effect in listOf("strong", "balanced", "weak") &&
                prefs.budgetMode in listOf("month", "year") &&
                prefs.tabletDockPosition in listOf("left", "center", "right")
        )
        val b = o.getJSONObject("budgets")
        val plans =
            b.keys().asSequence().associateWith { k ->
                val parts = k.split(':')
                require(
                    parts.size == 3 &&
                        books.any { it.id == parts[0] } &&
                        parts[1] in listOf("month", "year")
                )
                if (parts[1] == "month") java.time.YearMonth.parse(parts[2])
                else require(parts[2].matches(Regex("\\d{4}")))
                val p = b.getJSONObject(k)
                val total = p.getLong("total")
                require(
                    total in 0..9_007_199_254_740_991L && p.getDouble("total") == total.toDouble()
                )
                val cs = p.getJSONObject("categories")
                Plan(
                    total,
                    cs.keys()
                        .asSequence()
                        .associateWith { c ->
                            require(cats.any { it.id == c && it.type == "expense" })
                            cs.getLong(c).also {
                                require(
                                    it in 0..9_007_199_254_740_991L &&
                                        cs.getDouble(c) == it.toDouble()
                                )
                            }
                        }
                        .also { values ->
                            require(
                                values.values.fold(0L) { a, v -> Math.addExact(a, v) } <=
                                    9_007_199_254_740_991L
                            )
                        },
                )
            }
        val current = o.getString("book")
        require(books.any { it.id == current })
        return Ledger(books, current, cats, entries, plans, prefs, o.optBoolean("demo", false))
    }
}

class LedgerRepository(context: Context) {
    private val db =
        Room.databaseBuilder(
                context.applicationContext,
                LedgerDatabase::class.java,
                "inkledger-native.db",
            )
            .build()
    private val dir = context.filesDir
    private var cached: Ledger? = null

    suspend fun load(): Pair<Ledger, Boolean> =
        withContext(Dispatchers.IO) {
            val dao = db.ledger()
            val meta = dao.metadata()
            if (meta != null) {
                val m = JSONObject(meta.value)
                val categoryRows = dao.categoryBudgets()
                val plans =
                    dao.budgets().associate { b ->
                        b.key to
                            Plan(
                                b.total,
                                categoryRows
                                    .filter { it.budgetKey == b.key }
                                    .associate { it.cat to it.cents },
                            )
                    }
                val s = m.getJSONObject("settings")
                return@withContext Ledger(
                        dao.books(),
                        m.getString("book"),
                        dao.categories(),
                        dao.entries(),
                        plans,
                        Preferences(
                            s.getString("effect"),
                            s.getString("budgetMode"),
                            s.getString("tabletDockPosition"),
                        ),
                        m.getBoolean("demo"),
                    )
                    .also { cached = it } to false
            }
            val old = java.io.File(dir, "ledger.json")
            val backup = java.io.File(dir, "ledger.webview-backup.json")
            if (old.exists()) {
                val data = LedgerJson.decode(old.readText())
                if (!backup.exists()) old.copyTo(backup)
                save(data)
                data to false
            } else Ledger() to true
        }

    suspend fun save(d: Ledger) =
        withContext(Dispatchers.IO) {
            val previous = cached
            db.withTransaction {
                val dao = db.ledger()
                if (previous?.books != d.books) {
                    dao.clearBooks()
                    dao.putBooks(d.books)
                }
                if (previous?.categories != d.categories) {
                    dao.clearCategories()
                    dao.putCategories(d.categories)
                }
                val before = previous?.entries?.associateBy { it.id }.orEmpty()
                val after = d.entries.associateBy { it.id }
                val removed = before.values.filter { it.id !in after }
                if (removed.isNotEmpty()) dao.removeEntries(removed)
                val changed = d.entries.filter { before[it.id] != it }
                if (changed.isNotEmpty()) dao.putEntries(changed)
                if (previous?.budgets != d.budgets) {
                    dao.clearBudgets()
                    dao.clearCategoryBudgets()
                    dao.putBudgets(d.budgets.map { BudgetRow(it.key, it.value.total) })
                    dao.putCategoryBudgets(
                        d.budgets.flatMap { (k, p) ->
                            p.categories.map { CategoryBudget(k, it.key, it.value) }
                        }
                    )
                }
                val settings =
                    JSONObject()
                        .put("effect", d.settings.effect)
                        .put("budgetMode", d.settings.budgetMode)
                        .put("tabletDockPosition", d.settings.tabletDockPosition)
                dao.putMetadata(
                    Metadata(
                        "state",
                        JSONObject()
                            .put("book", d.book)
                            .put("demo", d.demo)
                            .put("settings", settings)
                            .toString(),
                    )
                )
            }
            cached = d
        }
}

fun demonstration(): Ledger {
    val now = LocalDate.now()
    val base = Ledger(demo = true, books = listOf(Book("default", "日常 · 演示账本")))
    val e = mutableListOf<Entry>()
    fun add(day: Int, cat: String, cents: Long, note: String, type: String = "expense") {
        if (day > 0)
            e +=
                Entry(
                    newId(),
                    "default",
                    now.withDayOfMonth(day).toString(),
                    "12:30",
                    cat,
                    cents,
                    note,
                    type,
                )
    }
    add(now.dayOfMonth, "food", 3850, "午餐")
    add(now.dayOfMonth, "transport", 600, "地铁通勤")
    add(now.dayOfMonth, "shop", 12900, "生活用品")
    add(now.dayOfMonth - 1, "food", 2800, "咖啡与早餐")
    add(now.dayOfMonth - 1, "daily", 8600, "日常生活用品")
    add(now.dayOfMonth - 2, "salary", 1250000, "月度工资", "income")
    add(now.dayOfMonth - 2, "fun", 6800, "周末电影")
    for (day in 1 until now.dayOfMonth - 2) {
        add(day, "food", if (day % 2 == 1) 4800 else 3250, "日常餐饮")
        if (day % 3 == 0) add(day, "shop", 15600, "衣物与日用品")
    }
    val cats =
        mapOf("food" to 180000L, "transport" to 80000L, "shop" to 160000L, "daily" to 100000L)
    return base.copy(
        entries = e,
        budgets =
            mapOf(
                "default:month:${now.toString().take(7)}" to Plan(600000, cats),
                "default:year:${now.year}" to Plan(7200000, cats.mapValues { it.value * 12 }),
            ),
    )
}
