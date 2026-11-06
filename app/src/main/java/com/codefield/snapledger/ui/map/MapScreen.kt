package com.codefield.snapledger.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codefield.snapledger.R
import com.codefield.snapledger.logic.CurrencyConverter
import com.codefield.snapledger.logic.MonthlyStats
import com.codefield.snapledger.logic.RateTable
import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.ui.common.CategoryBadge
import com.codefield.snapledger.ui.common.MonthSwitcher
import com.codefield.snapledger.ui.common.ScreenHeader
import com.codefield.snapledger.ui.common.categoryColor
import com.codefield.snapledger.ui.common.categoryEmoji
import com.codefield.snapledger.ui.common.categoryName
import com.codefield.snapledger.ui.common.formatMoney
import com.codefield.snapledger.ui.ledger.rememberDateTimeFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint as OsmPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * 消费地图：这个月带位置的每一笔是地图上的一个圆点（颜色和表情跟着类别），点一个看详情。
 * 翻到别的月份时，地图自动缩放到能看见所有的点。
 */
@Composable
fun MapScreen(entries: List<Entry>?, rates: RateTable, homeCurrency: Currency, zone: ZoneId, modifier: Modifier = Modifier) {
    val thisMonth = remember(zone) { YearMonth.now(zone) }
    var monthText by rememberSaveable { mutableStateOf(thisMonth.toString()) }
    val month = YearMonth.parse(monthText)
    val firstMonth = entries?.let { MonthlyStats.firstMonth(it, zone) }?.coerceAtMost(thisMonth) ?: thisMonth
    val located = remember(entries, month) {
        entries.orEmpty().filter { it.location != null && YearMonth.from(Instant.ofEpochMilli(it.time).atZone(zone)) == month }
    }
    var selectedId by rememberSaveable(monthText) { mutableStateOf<Long?>(null) }
    val selected = located.firstOrNull { it.id == selectedId }
    val context = LocalContext.current
    val icons = remember { mutableMapOf<Category, Drawable>() }
    // 最近一次缩放是为了哪个月、几个点：只在换月份或多了点的时候重新缩放，用户自己拖过的不打乱。
    val fittedFor = remember { arrayOfNulls<String>(1) }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader(title = stringResource(R.string.tab_map))
        MonthSwitcher(month, canGoBack = month > firstMonth, canGoForward = month < thisMonth, onMonth = { monthText = it.toString() })
        OsmMap(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp)),
        ) { map ->
            map.overlays.removeAll { it is Marker }
            located.forEach { entry ->
                val place = entry.location ?: return@forEach
                map.overlays.add(
                    Marker(map).apply {
                        position = OsmPoint(place.latitude, place.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = icons.getOrPut(entry.category) { markerIcon(context, entry.category) }
                        title = entry.merchant
                        setOnMarkerClickListener { _, _ ->
                            selectedId = entry.id
                            true
                        }
                    }
                )
            }
            val key = "$monthText:${located.size}"
            if (fittedFor[0] != key) {
                fittedFor[0] = key
                fit(map, located)
            }
            map.invalidate()
        }
        when {
            located.isEmpty() -> Text(
                stringResource(R.string.map_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            selected != null -> SelectedEntry(selected, homeCurrency, rates, zone)
            else -> {
                val total = remember(located, homeCurrency, rates) {
                    located.sumOf { entry ->
                        val day = Instant.ofEpochMilli(entry.time).atZone(zone).toLocalDate()
                        CurrencyConverter.convert(entry.amount, homeCurrency, rates.ratesFor(day))?.minor ?: 0L
                    }
                }
                Text(
                    stringResource(R.string.map_count, located.size, formatMoney(Money(total, homeCurrency))),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** 点了地图上的一个点：这一笔的商家、时间、金额（外币下面写换算后的）。 */
@Composable
private fun SelectedEntry(entry: Entry, homeCurrency: Currency, rates: RateTable, zone: ZoneId) {
    val time = Instant.ofEpochMilli(entry.time).atZone(zone)
    val converted = if (entry.amount.currency == homeCurrency) null
    else CurrencyConverter.convert(entry.amount, homeCurrency, rates.ratesFor(time.toLocalDate()))
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(entry.category)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.merchant.ifBlank { stringResource(categoryName(entry.category)) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    time.format(rememberDateTimeFormat()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(entry.amount), style = MaterialTheme.typography.titleMedium)
                if (converted != null) {
                    Text("≈ " + formatMoney(converted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 缩放到能看见所有的点：一个点就放大到街道，没有点就看首尔。要等地图量好大小以后才能算。 */
private fun fit(map: MapView, entries: List<Entry>) {
    val points = entries.mapNotNull { entry -> entry.location?.let { OsmPoint(it.latitude, it.longitude) } }
    val apply = {
        when (points.size) {
            0 -> {
                map.controller.setZoom(12.0)
                map.controller.setCenter(SEOUL)
            }
            1 -> {
                map.controller.setZoom(16.0)
                map.controller.setCenter(points.first())
            }
            else -> map.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points).increaseByScale(1.4f), false)
        }
    }
    if (map.isLayoutOccurred) apply() else map.addOnFirstLayoutListener { _, _, _, _, _ -> apply() }
}

/** 地图上的点：类别颜色的圆，白边，中间是类别的表情。 */
private fun markerIcon(context: Context, category: Category): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (34 * density).toInt()
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = size / 2f
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(radius, radius, radius, paint)
    paint.color = categoryColor(category).toArgb()
    canvas.drawCircle(radius, radius, radius - 2.5f * density, paint)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * 0.48f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(categoryEmoji(category), radius, radius - (text.descent() + text.ascent()) / 2, text)
    return bitmap.toDrawable(context.resources)
}
