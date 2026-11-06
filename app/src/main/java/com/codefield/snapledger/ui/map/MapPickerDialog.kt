package com.codefield.snapledger.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codefield.snapledger.R
import com.codefield.snapledger.model.GeoPoint
import org.osmdroid.util.GeoPoint as OsmPoint
import org.osmdroid.views.MapView

/**
 * 在地图上选付款的地方：拖动、缩放地图，让中间的十字对准那个地方，点「确定」。
 * 没有原来的位置时从首尔市中心开始。
 */
@Composable
fun MapPickerDialog(initial: GeoPoint?, onPick: (GeoPoint) -> Unit, onDismiss: () -> Unit) {
    // 地图对象只在这里记一下，用来点「确定」时读中心点；不需要触发重组。
    val holder = remember { arrayOfNulls<MapView>(1) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.map_pick_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.map_pick_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    OsmMap(modifier = Modifier.fillMaxSize()) { map ->
                        if (holder[0] == null) {
                            holder[0] = map
                            map.controller.setZoom(if (initial == null) 12.0 else 17.0)
                            map.controller.setCenter(initial?.let { OsmPoint(it.latitude, it.longitude) } ?: SEOUL)
                        }
                    }
                    Crosshair(modifier = Modifier.align(Alignment.Center))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        holder[0]?.mapCenter?.let { onPick(GeoPoint(it.latitude, it.longitude)) }
                    }) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}

/** 地图中间的十字：白底描边的红线，深色、浅色地图上都看得清。 */
@Composable
private fun Crosshair(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(44.dp)) {
        val c = center
        val arm = size.minDimension / 2
        listOf(Color.White to 6.dp.toPx(), Color(0xFFD32F2F) to 2.5.dp.toPx()).forEach { (color, width) ->
            drawLine(color, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), strokeWidth = width)
            drawLine(color, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), strokeWidth = width)
            drawCircle(color, radius = arm * 0.45f, center = c, style = Stroke(width))
        }
    }
}
