package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.Money
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 表头和类别名。由界面按当前语言给出，规则里不碰资源文件。inHome 是「折合主货币」那一列。 */
data class CsvLabels(
    val date: String,
    val time: String,
    val merchant: String,
    val category: String,
    val amount: String,
    val currency: String,
    val inHome: String,
    val note: String,
    val latitude: String,
    val longitude: String,
    val categories: Map<Category, String>,
)

/**
 * 把账目导出成 CSV 表格（RFC 4180）：一行一笔，按付款时间从早到晚。
 * - 金额不带货币符号和千位分隔符（「6500」「23.50」），表格软件能直接当数字算；另有一列按付款那天的汇率折合成主货币，缺汇率时空着。
 * - 带逗号、双引号、换行的格子用双引号包起来；以「= + - @」开头的文字前面加一个单引号，免得表格软件把它当公式执行。
 * - 最前面加 UTF-8 的 BOM，Excel 打开中文、韩文才不会乱码。
 */
object CsvExport {

    private const val BOM = "\uFEFF"
    private const val LINE_END = "\r\n"
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val FORMULA_START = setOf('=', '+', '-', '@', '\t', '\r')

    fun csv(entries: List<Entry>, home: Currency, rates: RateTable, zone: ZoneId, labels: CsvLabels): String {
        val header = listOf(
            labels.date, labels.time, labels.merchant, labels.category, labels.amount,
            labels.currency, labels.inHome, labels.note, labels.latitude, labels.longitude,
        )
        val rows = entries.sortedWith(compareBy<Entry> { it.time }.thenBy { it.id }).map { entry ->
            val at = Instant.ofEpochMilli(entry.time).atZone(zone)
            val converted = CurrencyConverter.convert(entry.amount, home, rates.ratesFor(at.toLocalDate()))
            listOf(
                DATE.format(at),
                TIME.format(at),
                text(entry.merchant),
                labels.categories[entry.category] ?: entry.category.name,
                plain(entry.amount),
                entry.amount.currency.name,
                converted?.let(::plain).orEmpty(),
                text(entry.note),
                entry.location?.let { coordinate(it.latitude) }.orEmpty(),
                entry.location?.let { coordinate(it.longitude) }.orEmpty(),
            )
        }
        return BOM + (listOf(header) + rows).joinToString(separator = "") { row -> row.joinToString(",") { quote(it) } + LINE_END }
    }

    /** 金额写成普通小数：「6500」「23.50」。 */
    internal fun plain(money: Money): String = BigDecimal(money.minor).movePointLeft(money.currency.fractionDigits).toPlainString()

    /** 用户或截图里来的文字：开头像公式时加单引号。 */
    internal fun text(value: String): String = if (value.firstOrNull() in FORMULA_START) "'$value" else value

    /** 有逗号、双引号、换行时整格加双引号，里面的双引号写两遍。 */
    internal fun quote(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\"" else field

    private fun coordinate(value: Double): String = String.format(Locale.US, "%.6f", value)
}
