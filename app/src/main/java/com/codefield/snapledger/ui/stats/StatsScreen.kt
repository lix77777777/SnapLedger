package com.codefield.snapledger.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codefield.snapledger.R
import com.codefield.snapledger.logic.CalendarHeat
import com.codefield.snapledger.logic.MonthlyStats
import com.codefield.snapledger.logic.RateTable
import com.codefield.snapledger.model.CategoryTotal
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.model.MonthSummary
import com.codefield.snapledger.ui.common.MonthSwitcher
import com.codefield.snapledger.ui.common.ScreenHeader
import com.codefield.snapledger.ui.common.categoryColor
import com.codefield.snapledger.ui.common.categoryEmoji
import com.codefield.snapledger.ui.common.categoryName
import com.codefield.snapledger.ui.common.formatMoney
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 统计：一个月的分类圆环、每天的柱状图、消费日历（颜色越深花得越多，点一天看详情）、常去的店。
 * 金额都按付款那天的汇率换算成主货币。左右箭头翻月份，最早翻到第一笔账那个月。
 */
@Composable
fun StatsScreen(
    entries: List<Entry>?,
    rates: RateTable,
    homeCurrency: Currency,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val thisMonth = remember(zone) { YearMonth.now(zone) }
    // YearMonth 存成「2026-10」这样的文字，旋转屏幕后还在原来那个月。
    var monthText by rememberSaveable { mutableStateOf(thisMonth.toString()) }
    val month = YearMonth.parse(monthText)
    val firstMonth = entries?.let { MonthlyStats.firstMonth(it, zone) }?.coerceAtMost(thisMonth) ?: thisMonth
    val summary = remember(entries, rates, homeCurrency, month) {
        entries?.let { MonthlyStats.summarize(it, month, homeCurrency, rates, zone) }
    }

    // 换月份时图表从 0 长出来。
    val progress = remember(month) { Animatable(0f) }
    LaunchedEffect(month) { progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.tab_stats))
        MonthSwitcher(
            month = month,
            canGoBack = month > firstMonth,
            canGoForward = month < thisMonth,
            onMonth = { monthText = it.toString() },
        )
        if (summary == null) return@Column
        TotalCard(summary)
        if (summary.entryCount == 0) {
            Section { Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.bodyLarge) }
            return@Column
        }
        if (summary.byCategory.isNotEmpty()) {
            Section(stringResource(R.string.stats_by_category)) { CategoryChart(summary, progress.value) }
        }
        Section(stringResource(R.string.stats_by_day)) {
            DailyBars(month, summary, LocalDate.now(zone), progress.value)
        }
        Section(stringResource(R.string.stats_calendar)) {
            SpendingCalendar(month, summary, entries.orEmpty(), zone)
        }
        if (summary.topMerchants.isNotEmpty() || summary.biggest != null) {
            Section(stringResource(R.string.stats_top_merchants)) { TopList(summary, zone) }
        }
    }
}

/** 总额、笔数、和上个月比、没能换算的外币。 */
@Composable
private fun TotalCard(summary: MonthSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.stats_total), style = MaterialTheme.typography.labelLarge)
            Text(formatMoney(Money(summary.totalMinor, summary.currency)), style = MaterialTheme.typography.headlineMedium)
            Text(pluralStringResource(R.plurals.ledger_month_count, summary.entryCount, summary.entryCount), style = MaterialTheme.typography.bodyMedium)
            val previous = summary.previousTotalMinor
            if (previous != null) {
                val difference = formatMoney(Money(abs(summary.totalMinor - previous), summary.currency))
                val text = when {
                    summary.totalMinor > previous -> stringResource(R.string.stats_vs_last_more, difference)
                    summary.totalMinor < previous -> stringResource(R.string.stats_vs_last_less, difference)
                    else -> stringResource(R.string.stats_vs_last_same)
                }
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            if (summary.unconvertedCount > 0) {
                Text(pluralStringResource(R.plurals.ledger_unconverted, summary.unconvertedCount, summary.unconvertedCount), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** 分类圆环：从 12 点钟方向顺时针，按金额从大到小；旁边是图例。 */
@Composable
private fun CategoryChart(summary: MonthSummary, progress: Float) {
    val names = summary.byCategory.map { stringResource(categoryName(it.category)) }
    val description = summary.byCategory.mapIndexed { i, it -> "${names[i]} ${percent(it)}" }.joinToString(", ")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
            Donut(summary.byCategory, progress, Modifier.fillMaxSize().semantics { contentDescription = description })
            Text(
                summary.byCategory.first().let { categoryEmoji(it.category) + " " + percent(it) },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            summary.byCategory.forEachIndexed { i, total ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(categoryColor(total.category)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(names[i], style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(percent(total), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    formatMoney(Money(total.minor, summary.currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Donut(categories: List<CategoryTotal>, progress: Float, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val colors = categories.map { categoryColor(it.category) }
    Canvas(modifier) {
        val stroke = 22.dp.toPx()
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
        var start = -90f
        categories.forEachIndexed { i, total ->
            val sweep = (total.share * 360 * progress).toFloat()
            // 相邻两块之间留一点缝；只有一块或块太小时不留。
            val gap = if (categories.size > 1 && sweep > 4f) 1.5f else 0f
            drawArc(colors[i], start, sweep - gap, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            start += sweep
        }
    }
}

/** 每天花了多少：一天一根柱子，今天的换个颜色；下面标 1、5、10…号。 */
@Composable
private fun DailyBars(month: YearMonth, summary: MonthSummary, today: LocalDate, progress: Float) {
    val barColor = MaterialTheme.colorScheme.primary
    val todayColor = MaterialTheme.colorScheme.secondary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val days = month.lengthOfMonth()
    val top = summary.byDay.maxByOrNull { it.value }
    val maxMinor = top?.value ?: 0L
    val topText = top?.let {
        stringResource(R.string.stats_top_day, it.key.dayOfMonth, formatMoney(Money(it.value, summary.currency)))
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .semantics { contentDescription = topText.orEmpty() },
    ) {
        val labelHeight = 18.dp.toPx()
        val chartHeight = size.height - labelHeight
        val slot = size.width / days
        val barWidth = slot * 0.62f
        drawLine(axisColor, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 1.dp.toPx())
        for (day in 1..days) {
            val date = month.atDay(day)
            val minor = summary.byDay[date] ?: 0L
            if (minor > 0 && maxMinor > 0) {
                val height = (chartHeight - 4.dp.toPx()) * (minor.toFloat() / maxMinor) * progress
                drawRoundRect(
                    color = if (date == today) todayColor else barColor,
                    topLeft = Offset((day - 1) * slot + (slot - barWidth) / 2, chartHeight - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 3),
                )
            }
            if (day == 1 || day % 5 == 0) {
                val label = measurer.measure("$day", labelStyle)
                drawText(label, topLeft = Offset((day - 0.5f) * slot - label.size.width / 2, chartHeight + 2.dp.toPx()))
            }
        }
    }
    if (topText != null) Hint(topText)
}

/**
 * 消费日历：一格一天，颜色越深花得越多（按占最多那天的比例分 4 级）。点一天在下面显示那天的金额和笔数。
 * 一周从周几开始跟着界面语言。
 */
@Composable
private fun SpendingCalendar(month: YearMonth, summary: MonthSummary, entries: List<Entry>, zone: ZoneId) {
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    var selected by rememberSaveable(month.toString()) { mutableStateOf<Int?>(null) }
    val rows = CalendarHeat.rows(month, firstDay)
    val maxMinor = summary.byDay.values.maxOrNull() ?: 0L

    // 没花钱的格子用页面底色，在卡片上看得出一格一格。
    val empty = MaterialTheme.colorScheme.surface
    val full = MaterialTheme.colorScheme.primary
    val levelColors = (0..CalendarHeat.LEVELS).map { level ->
        if (level == 0) empty else full.copy(alpha = 0.2f + 0.8f * level / CalendarHeat.LEVELS)
    }
    val selectedColor = MaterialTheme.colorScheme.secondary
    val numberStyle = MaterialTheme.typography.labelMedium
    val darkNumber = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()

    Hint(stringResource(R.string.stats_calendar_hint))
    Row(modifier = Modifier.fillMaxWidth()) {
        (0..6).map { firstDay.plus(it.toLong()) }.forEach { day: DayOfWeek ->
            Text(
                day.getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(7f / rows)
            .pointerInput(month, firstDay) {
                detectTapGestures { offset ->
                    val cell = size.width / 7f
                    val index = (offset.y / cell).toInt() * 7 + (offset.x / cell).toInt()
                    CalendarHeat.dayAt(month, firstDay, index)?.let { selected = it }
                }
            },
    ) {
        val cell = size.width / 7
        val gap = 2.dp.toPx()
        val corner = CornerRadius(6.dp.toPx())
        for (index in 0 until rows * 7) {
            val day = CalendarHeat.dayAt(month, firstDay, index) ?: continue
            val level = CalendarHeat.level(summary.byDay[month.atDay(day)] ?: 0L, maxMinor)
            val topLeft = Offset((index % 7) * cell + gap, (index / 7) * cell + gap)
            val box = Size(cell - 2 * gap, cell - 2 * gap)
            drawRoundRect(levelColors[level], topLeft, box, corner)
            if (day == selected) drawRoundRect(selectedColor, topLeft, box, corner, style = Stroke(2.5.dp.toPx()))
            // 深色格子上用白字。
            val number = measurer.measure("$day", numberStyle.copy(color = if (level >= 3) Color.White else darkNumber))
            drawText(
                number,
                topLeft = Offset(topLeft.x + (box.width - number.size.width) / 2, topLeft.y + (box.height - number.size.height) / 2),
            )
        }
    }

    val day = selected
    if (day != null) {
        val date = month.atDay(day)
        val pattern = stringResource(R.string.date_header_pattern)
        val dateText = remember(pattern, locale, date) { date.format(DateTimeFormatter.ofPattern(pattern, locale)) }
        val count = remember(entries, date) { entries.count { Instant.ofEpochMilli(it.time).atZone(zone).toLocalDate() == date } }
        val minor = summary.byDay[date]
        Text(
            if (count == 0) stringResource(R.string.stats_day_empty, dateText)
            else pluralStringResource(R.plurals.stats_day_detail, count, dateText, formatMoney(Money(minor ?: 0L, summary.currency)), count),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 花钱最多的几家店，和这个月最大的一笔。 */
@Composable
private fun TopList(summary: MonthSummary, zone: ZoneId) {
    summary.topMerchants.forEach { merchant ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(merchant.merchant, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.stats_merchant_count, merchant.count, merchant.count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(formatMoney(Money(merchant.minor, summary.currency)), style = MaterialTheme.typography.bodyLarge)
        }
    }
    val biggest = summary.biggest ?: return
    val locale = LocalConfiguration.current.locales[0]
    val pattern = stringResource(R.string.date_header_pattern)
    val date = Instant.ofEpochMilli(biggest.time).atZone(zone).toLocalDate()
    val name = biggest.merchant.ifBlank { stringResource(categoryName(biggest.category)) }
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.stats_biggest), style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${categoryEmoji(biggest.category)} $name · ${date.format(DateTimeFormatter.ofPattern(pattern, locale))}",
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(formatMoney(Money(summary.biggestMinor, summary.currency)), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Section(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 「45%」：不到 1% 的写「<1%」。 */
private fun percent(total: CategoryTotal): String {
    val value = (total.share * 100).roundToInt()
    return if (value == 0 && total.minor > 0) "<1%" else "$value%"
}
