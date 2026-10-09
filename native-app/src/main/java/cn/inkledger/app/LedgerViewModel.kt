package cn.inkledger.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = LedgerRepository(app)
    private val gate = Mutex()
    private val _ledger = MutableStateFlow<Ledger?>(null)
    val ledger = _ledger.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _welcome = MutableStateFlow(false)
    val welcome = _welcome.asStateFlow()
    var unreadable = false
        private set

    init {
        viewModelScope.launch {
            runCatching { repository.load() }
                .onSuccess { (d, new) ->
                    _ledger.value = d
                    _welcome.value = new
                }
                .onFailure {
                    unreadable = true
                    _error.value = "已有数据读取失败，原数据已保留：${it.message}"
                }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun report(s: String) {
        _error.value = s
    }

    fun change(transform: (Ledger) -> Ledger, done: () -> Unit = {}) {
        viewModelScope.launch {
            gate.withLock {
                runCatching {
                        val current = _ledger.value ?: error("请先恢复有效备份")
                        check(!unreadable)
                        val next = transform(current)
                        repository.save(next)
                        _ledger.value = next
                    }
                    .onSuccess { done() }
                    .onFailure { _error.value = it.message ?: "保存失败，请检查设备空间并导出备份" }
            }
        }
    }

    fun start(demo: Boolean) {
        change({ if (demo) demonstration() else Ledger() }) { _welcome.value = false }
    }

    fun saveEntry(
        id: String?,
        date: String,
        time: String,
        cat: String,
        cents: Long,
        note: String,
        done: () -> Unit,
    ) {
        change(
            { d ->
                require(cents > 0) { "金额必须大于 0" }
                LocalDate.parse(date)
                LocalTime.parse(time)
                require(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d").matches(time))
                require(note.length <= 200)
                val c = d.category(cat)
                val e = Entry(id ?: newId(), d.book, date, time, cat, cents, note, c.type)
                d.copy(entries = d.entries.filterNot { it.id == e.id } + e)
            },
            done,
        )
    }

    fun trash(ids: Set<String>, done: () -> Unit = {}) {
        change(
            { d ->
                val at = System.currentTimeMillis()
                d.copy(
                    entries = d.entries.map { if (it.id in ids) it.copy(deletedAt = at) else it }
                )
            },
            done,
        )
    }

    fun restore(id: String) {
        change({ d ->
            d.copy(entries = d.entries.map { if (it.id == id) it.copy(deletedAt = null) else it })
        })
    }

    fun purge(id: String, done: () -> Unit) {
        change({ d -> d.copy(entries = d.entries.filterNot { it.id == id }) }, done)
    }

    fun classify(ids: Set<String>, cat: String, done: () -> Unit) {
        change(
            { d ->
                val type = d.category(cat).type
                require(d.entries.filter { it.id in ids }.all { it.type == type }) {
                    "多选账单必须属于同一收支类型"
                }
                d.copy(entries = d.entries.map { if (it.id in ids) it.copy(cat = cat) else it })
            },
            done,
        )
    }

    fun plan(mode: String, range: String, cat: String?, value: Long, done: () -> Unit) {
        change(
            { d ->
                val key = "${d.book}:$mode:${range.take(if(mode=="year")4 else 7)}"
                val old = d.budgets[key] ?: Plan()
                val next =
                    if (cat == null) old.copy(total = value)
                    else old.copy(categories = old.categories + (cat to value))
                d.copy(budgets = d.budgets + (key to next))
            },
            done,
        )
    }

    fun importBackup(raw: String, done: () -> Unit) {
        viewModelScope.launch {
            gate.withLock {
                runCatching {
                        val d = withContext(Dispatchers.Default) { LedgerJson.decode(raw) }
                        repository.save(d)
                        unreadable = false
                        _ledger.value = d
                        _welcome.value = false
                        _error.value = null
                    }
                    .onSuccess { done() }
                    .onFailure { _error.value = "导入失败：${it.message}" }
            }
        }
    }
}
