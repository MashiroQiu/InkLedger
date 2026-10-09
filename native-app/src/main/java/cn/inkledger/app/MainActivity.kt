package cn.inkledger.app

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val model: LedgerViewModel by viewModels()
    private var pendingExport: String? = null
    private val exportFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri
            ->
            uri?.let {
                lifecycleScope.launch {
                    runCatching {
                            withContext(Dispatchers.IO) {
                                val data = pendingExport ?: error("备份尚未生成")
                                contentResolver.openOutputStream(it, "wt")?.use { stream ->
                                    stream.write(data.toByteArray(Charsets.UTF_8))
                                } ?: error("无法打开文件")
                            }
                        }
                        .onFailure { model.report("导出失败：${it.message}") }
                    pendingExport = null
                }
            }
        }
    private val importFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                lifecycleScope.launch {
                    runCatching {
                            withContext(Dispatchers.IO) {
                                contentResolver.openInputStream(it)?.use { stream ->
                                    val out = java.io.ByteArrayOutputStream()
                                    val chunk = ByteArray(8192)
                                    while (true) {
                                        val n = stream.read(chunk)
                                        if (n < 0) break
                                        require(out.size() + n <= 32 * 1024 * 1024) { "备份超过 32 MB" }
                                        out.write(chunk, 0, n)
                                    }
                                    out.toString("UTF-8")
                                } ?: error("无法打开文件")
                            }
                        }
                        .onSuccess { raw -> pendingImport.value = raw }
                        .onFailure { model.report("读取备份失败：${it.message}") }
                }
            }
        }
    private val pendingImport = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.rgb(85, 85, 85)
        window.navigationBarColor = Color.rgb(222, 222, 222)
        setContent {
            InkLedger(
                model,
                pendingImport.value,
                { pendingImport.value = null },
                {
                    pendingExport = it
                    exportFile.launch("墨账-${java.time.LocalDate.now()}.json")
                },
                {
                    importFile.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream")
                    )
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val current =
            if (android.os.Build.VERSION.SDK_INT >= 30) display ?: return
            else windowManager.defaultDisplay
        val mode =
            current.supportedModes
                .filter {
                    it.physicalWidth == current.mode.physicalWidth &&
                        it.physicalHeight == current.mode.physicalHeight
                }
                .maxByOrNull { it.refreshRate } ?: return
        window.attributes =
            window.attributes.apply {
                preferredDisplayModeId = mode.modeId
                preferredRefreshRate = mode.refreshRate
            }
        if (android.os.Build.VERSION.SDK_INT >= 35)
            window.decorView.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH)
    }
}
