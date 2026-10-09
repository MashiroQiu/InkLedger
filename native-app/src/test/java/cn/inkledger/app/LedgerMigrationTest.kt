package cn.inkledger.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LedgerMigrationTest {
    private fun fixture() = javaClass.getResource("/webview-0.1.13.json")!!.readText()

    @Test
    fun legacyBackupRoundTrip() {
        val source = LedgerJson.decode(fixture())
        val restored = LedgerJson.decode(LedgerJson.encode(source))
        assertEquals(source, restored)
        assertEquals(15, source.entries.size)
        assertEquals(90900L, sum(source.entries.filter { it.deletedAt == null }, "expense"))
        assertEquals(1250000L, sum(source.entries, "income"))
    }

    @Test
    fun defaultsForOldDockSetting() {
        val o = JSONObject(fixture())
        o.getJSONObject("settings").remove("tabletDockPosition")
        assertEquals("center", LedgerJson.decode(o.toString()).settings.tabletDockPosition)
    }

    @Test
    fun trashSurvivesRoundTrip() {
        val d = LedgerJson.decode(fixture())
        val e = d.entries.first()
        val changed =
            d.copy(
                entries = d.entries.map { if (it.id == e.id) it.copy(deletedAt = 12345L) else it }
            )
        val restored = LedgerJson.decode(LedgerJson.encode(changed))
        assertEquals(
            changed.copy(entries = changed.entries.sortedBy { it.id }),
            restored.copy(entries = restored.entries.sortedBy { it.id }),
        )
    }

    @Test
    fun moneyIsExact() {
        assertEquals("9,007,199,254,740.91", money(900719925474091L))
        assertEquals(235L, parseAmount("2.35"))
        assertEquals(1L, parseAmount("0.01"))
        assertEquals("0.00", money(0))
        assertEquals("-0.01", money(-1))
    }

    @Test
    fun invalidAmountRejected() {
        for (s in listOf("-1", "NaN", "1.234", "1e3", "99999999999")) assertThrows(
            IllegalArgumentException::class.java
        ) {
            parseAmount(s)
        }
    }

    @Test
    fun invalidDateRejected() {
        val o = JSONObject(fixture())
        o.getJSONArray("entries").getJSONObject(0).put("date", "2026-02-30")
        assertThrows(Exception::class.java) { LedgerJson.decode(o.toString()) }
    }

    @Test
    fun duplicateIdRejected() {
        val o = JSONObject(fixture())
        val a = o.getJSONArray("entries")
        a.getJSONObject(1).put("id", a.getJSONObject(0).getString("id"))
        assertThrows(Exception::class.java) { LedgerJson.decode(o.toString()) }
    }

    @Test
    fun typeCategoryMismatchRejected() {
        val o = JSONObject(fixture())
        o.getJSONArray("entries").getJSONObject(0).put("type", "income")
        assertThrows(Exception::class.java) { LedgerJson.decode(o.toString()) }
    }

    @Test
    fun characterWidthMatchesOriginal() {
        assertEquals(4, charWidth("餐饮"))
        assertEquals(10, charWidth("abcdefghij"))
        assertEquals(2, charWidth("😀"))
    }

    @Test
    fun independentBudgetsPreserved() {
        val d = LedgerJson.decode(fixture())
        val p = d.budgets.values.first { it.total == 600000L }
        assertEquals(520000L, p.categories.values.sum())
        assertEquals(600000L, p.total)
    }
}
