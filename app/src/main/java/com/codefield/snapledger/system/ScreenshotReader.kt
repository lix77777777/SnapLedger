package com.codefield.snapledger.system

import android.util.Log
import com.codefield.snapledger.logic.OcrLayout
import com.codefield.snapledger.logic.ReadingChooser
import com.codefield.snapledger.logic.ReceiptParser
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.ParsedReceipt
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

/**
 * 读一张存好的截图：两个模型各识别一遍 → 按位置拼成行 → 挑读得懂的那份 → 解析出金额、时间、商家。
 * 识别出错时当作什么都没读到，用户在确认对话框里自己填。
 * @param debug 调试版把识别出的文字写进日志，方便用真实截图调规则（正式版不写，截图里有个人信息）。
 */
class ScreenshotReader(
    private val store: ScreenshotStore,
    private val scanner: TextScanner,
    private val debug: Boolean,
) {

    suspend fun read(name: String, fallbackCurrency: Currency, now: Long, zone: ZoneId): ParsedReceipt {
        val lines = try {
            val bitmap = store.load(name)
            if (bitmap == null) {
                emptyList()
            } else {
                val readings = try { scanner.read(bitmap) } finally { bitmap.recycle() }
                ReadingChooser.choose(readings.map(OcrLayout::lines))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "text recognition failed", e)
            emptyList()
        }
        if (debug) lines.forEach { Log.d(TAG, "top=${it.top} h=${it.height} | ${it.text}") }
        return ReceiptParser.parse(lines, now, zone, fallbackCurrency)
    }

    private companion object {
        const val TAG = "ScreenshotReader"
    }
}
