package com.codefield.snapledger.ui.review

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codefield.snapledger.R
import com.codefield.snapledger.logic.MonthlyReview
import com.codefield.snapledger.logic.RateTable
import com.codefield.snapledger.model.CategoryTotal
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.model.MonthReview
import com.codefield.snapledger.model.ReviewCard
import com.codefield.snapledger.ui.common.categoryColor
import com.codefield.snapledger.ui.common.categoryEmoji
import com.codefield.snapledger.ui.common.categoryName
import com.codefield.snapledger.ui.common.formatMoney
import com.codefield.snapledger.ui.common.rememberMonthFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

/** 一张卡片停留多久，然后自动翻到下一张。 */
private const val CARD_MILLIS = 5_500

/** 封面的颜色，回顾页的预览也用这一组。 */
internal val COVER_COLORS = listOf(Color(0xFF5B2EE5), Color(0xFFC94BB0))

/** 分享图片四周的底色。 */
private val SHARE_BACKGROUND = Color(0xFF120A2E)

/**
 * 月度回顾的故事卡片（像 Spotify Wrapped）：上面一排进度条，每张停几秒自动翻页；
 * 点右边下一张、点左边上一张、按住暂停。最后一张总结卡可以存成图片分享出去。
 */
@Composable
fun StoryScreen(
    month: YearMonth,
    entries: List<Entry>?,
    rates: RateTable,
    homeCurrency: Currency,
    zone: ZoneId,
    onClose: () -> Unit,
    onShare: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val review = remember(entries, rates, homeCurrency, month) {
        entries?.let { MonthlyReview.build(it, month, homeCurrency, rates, zone) }
    }
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (review == null) return@Box
        val cards = review.cards
        var index by rememberSaveable(month.toString()) { mutableIntStateOf(0) }
        val current = index.coerceIn(0, cards.lastIndex)
        var paused by remember { mutableStateOf(false) }
        val progress = remember(current) { Animatable(0f) }
        // 暂停时这个效果被取消，进度停在原地；松手后从停下的地方接着走。
        LaunchedEffect(current, paused) {
            if (paused) return@LaunchedEffect
            val remaining = ((1f - progress.value) * CARD_MILLIS).roundToInt()
            progress.animateTo(1f, tween(durationMillis = remaining, easing = LinearEasing))
            if (current < cards.lastIndex) index = current + 1
        }

        CompositionLocalProvider(LocalContentColor provides Color.White) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.94f)) togetherWith fadeOut(tween(250))
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(cards.size) {
                        detectTapGestures(
                            onPress = {
                                paused = true
                                tryAwaitRelease()
                                paused = false
                            },
                            // 这段手势只设置一次，要在点的时候读最新的页码，不能用外面的 current（那是设置时的旧值）。
                            onTap = { offset ->
                                val now = index.coerceIn(0, cards.lastIndex)
                                index = if (offset.x < size.width * 0.3f) (now - 1).coerceAtLeast(0) else (now + 1).coerceAtMost(cards.lastIndex)
                            },
                        )
                    },
                label = "story",
            ) { page -> StoryCard(cards[page], review, zone, onShare) }

            StoryTopBar(cards.size, current, progress.value, review, onClose)
        }
    }
}

/** 一排进度条（看过的满、正在看的走着、后面的空），下面是标题和关闭按钮。 */
@Composable
private fun StoryTopBar(count: Int, current: Int, progress: Float, review: MonthReview, onClose: () -> Unit) {
    val title = stringResource(R.string.review_title, review.summary.month.format(rememberMonthFormat()))
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(count) { i ->
                val fill = when {
                    i < current -> 1f
                    i == current -> progress
                    else -> 0f
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.35f)),
                ) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fill).background(Color.White))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            val close = stringResource(R.string.close)
            TextButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = close }) {
                Text("✕", color = Color.White, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun StoryCard(card: ReviewCard, review: MonthReview, zone: ZoneId, onShare: (Bitmap) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientFor(card, review)))
            .padding(start = 28.dp, end = 28.dp, top = 76.dp, bottom = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 横屏或小屏放不下时可以上下滚；宽屏时不拉得太宽。
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (card) {
                ReviewCard.COVER -> CoverCard(review)
                ReviewCard.TOP_CATEGORY -> TopCategoryCard(review)
                ReviewCard.BIGGEST -> BiggestCard(review, zone)
                ReviewCard.FAVORITE_PLACE -> PlaceCard(review)
                ReviewCard.BUSIEST_DAY -> BusiestDayCard(review)
                ReviewCard.VERSUS_LAST_MONTH -> VersusCard(review)
                ReviewCard.FIRST_MONTH -> FirstMonthCard()
                ReviewCard.PERSONA -> PersonaCard(review)
                ReviewCard.SUMMARY -> SummaryCard(review, onShare)
            }
        }
    }
}

@Composable
private fun CoverCard(review: MonthReview) {
    val summary = review.summary
    Text(
        stringResource(R.string.review_title, summary.month.format(rememberMonthFormat())),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(24.dp))
    Eyebrow(stringResource(R.string.review_spent))
    CountUpMoney(summary.totalMinor, summary.currency)
    Text(stringResource(R.string.review_count_days, summary.entryCount, review.activeDays), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(32.dp))
    Text(
        stringResource(R.string.review_tap_hint),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.7f),
    )
}

@Composable
private fun TopCategoryCard(review: MonthReview) {
    val summary = review.summary
    val top = summary.byCategory.first()
    Eyebrow(stringResource(R.string.review_top_category))
    BigEmoji(categoryEmoji(top.category))
    Headline(stringResource(categoryName(top.category)))
    CountUpMoney(top.minor, summary.currency)
    Text(stringResource(R.string.review_share_of_total, percent(top.share)), style = MaterialTheme.typography.bodyLarge)
    CategoryBar(summary.byCategory, Modifier.padding(top = 8.dp))
}

@Composable
private fun BiggestCard(review: MonthReview, zone: ZoneId) {
    val summary = review.summary
    val biggest = summary.biggest ?: return
    Eyebrow(stringResource(R.string.review_biggest))
    BigEmoji(categoryEmoji(biggest.category))
    Headline(biggest.merchant.ifBlank { stringResource(categoryName(biggest.category)) })
    Text(dayText(Instant.ofEpochMilli(biggest.time).atZone(zone).toLocalDate()), style = MaterialTheme.typography.bodyLarge)
    CountUpMoney(summary.biggestMinor, summary.currency)
    // 外币那笔再写上原来的金额。
    if (biggest.amount.currency != summary.currency) {
        Text(formatMoney(biggest.amount), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun PlaceCard(review: MonthReview) {
    val place = review.favoritePlace ?: return
    val money = formatMoney(Money(place.minor, review.summary.currency))
    Eyebrow(stringResource(if (place.visits >= 2) R.string.review_place_visits else R.string.review_place_amount))
    BigEmoji("📍")
    Headline(place.merchant)
    Text(
        if (place.visits >= 2) stringResource(R.string.review_place_visits_detail, place.visits, money) else money,
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun BusiestDayCard(review: MonthReview) {
    val day = review.busiestDay ?: return
    Eyebrow(stringResource(R.string.review_busiest))
    BigEmoji("🔥")
    Headline(dayText(day.date))
    CountUpMoney(day.minor, review.summary.currency)
    Text(stringResource(R.string.review_busiest_detail, day.count), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun VersusCard(review: MonthReview) {
    val summary = review.summary
    val previous = summary.previousTotalMinor ?: return
    val difference = summary.totalMinor - previous
    BigEmoji(if (difference > 0) "📈" else if (difference < 0) "📉" else "🟰")
    Eyebrow(
        stringResource(
            when {
                difference > 0 -> R.string.review_more
                difference < 0 -> R.string.review_less
                else -> R.string.review_same
            }
        )
    )
    if (difference != 0L) {
        CountUpMoney(abs(difference), summary.currency)
        // 上个月是 0 时没有百分比可说。
        if (previous > 0) {
            val change = difference * 100.0 / previous
            Text((if (change > 0) "+" else "") + "%.0f%%".format(change), style = MaterialTheme.typography.titleLarge)
        }
    }
    Text(
        stringResource(R.string.review_last_month, formatMoney(Money(previous, summary.currency))),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun FirstMonthCard() {
    BigEmoji("🌱")
    Headline(stringResource(R.string.review_first_month))
    Text(stringResource(R.string.review_first_month_hint), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun PersonaCard(review: MonthReview) {
    Eyebrow(stringResource(R.string.review_persona))
    Text(personaEmoji(review.persona), fontSize = 88.sp)
    Text(stringResource(personaName(review.persona)), fontSize = 38.sp, fontWeight = FontWeight.Black, lineHeight = 44.sp)
    Text(stringResource(personaText(review.persona)), style = MaterialTheme.typography.bodyLarge)
}

/** 最后一张：一张能分享的总结图，下面是「分享图片」按钮（按钮不在图里）。 */
@Composable
private fun SummaryCard(review: MonthReview, onShare: (Bitmap) -> Unit) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    // 存成图片的那一份先铺一层深色底，四角不会透明；屏幕上只画卡片本身。
    Box(
        modifier = Modifier.drawWithContent {
            layer.record {
                drawRect(SHARE_BACKGROUND)
                this@drawWithContent.drawContent()
            }
            drawContent()
        },
    ) {
        SummaryImage(review)
    }
    Button(
        onClick = { scope.launch { onShare(layer.toImageBitmap().asAndroidBitmap()) } },
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF2B1660)),
        modifier = Modifier.padding(top = 8.dp),
    ) { Text(stringResource(R.string.review_share)) }
}

/** 分享出去的那张图。外面留一圈边，存成图片时这一圈是 [SHARE_BACKGROUND]。 */
@Composable
private fun SummaryImage(review: MonthReview) {
    val summary = review.summary
    val monthText = summary.month.format(rememberMonthFormat())
    Box(modifier = Modifier.padding(12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3B1C6E), Color(0xFF7B2A8C))))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.app_name) + " · " + stringResource(R.string.review_summary, monthText),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(stringResource(R.string.review_summary_spent), style = MaterialTheme.typography.titleMedium)
            Text(formatMoney(Money(summary.totalMinor, summary.currency)), fontSize = 38.sp, fontWeight = FontWeight.Black)
            if (summary.byCategory.isNotEmpty()) CategoryBar(summary.byCategory)
            summary.byCategory.firstOrNull()?.let { top ->
                SummaryRow(
                    stringResource(R.string.review_summary_top),
                    "${categoryEmoji(top.category)} ${stringResource(categoryName(top.category))} ${percent(top.share)}",
                )
            }
            summary.biggest?.let { biggest ->
                SummaryRow(
                    stringResource(R.string.review_summary_biggest),
                    biggest.merchant.ifBlank { stringResource(categoryName(biggest.category)) } + " " +
                        formatMoney(Money(summary.biggestMinor, summary.currency)),
                )
            }
            review.favoritePlace?.let { place -> SummaryRow(stringResource(R.string.review_summary_place), place.merchant) }
            SummaryRow(
                stringResource(R.string.review_summary_persona),
                personaEmoji(review.persona) + " " + stringResource(personaName(review.persona)),
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(0.38f))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.62f),
        )
    }
}

/** 各类别占比的横条，颜色和统计页一样。 */
@Composable
private fun CategoryBar(categories: List<CategoryTotal>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
        categories.forEach { total ->
            Box(
                modifier = Modifier
                    .weight(total.share.toFloat().coerceAtLeast(0.005f))
                    .fillMaxHeight()
                    .background(categoryColor(total.category)),
            )
        }
    }
}

/** 金额从 0 滚到最终的数。 */
@Composable
private fun CountUpMoney(minor: Long, currency: Currency) {
    val fraction = remember(minor) { Animatable(0f) }
    LaunchedEffect(minor) { fraction.animateTo(1f, tween(durationMillis = 1_100, easing = FastOutSlowInEasing)) }
    Text(
        formatMoney(Money((minor * fraction.value.toDouble()).roundToLong(), currency)),
        style = TextStyle(fontSize = 46.sp, fontWeight = FontWeight.Black, lineHeight = 52.sp),
    )
}

@Composable
private fun Eyebrow(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f))
}

@Composable
private fun Headline(text: String) {
    Text(text, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun BigEmoji(emoji: String) {
    Text(emoji, fontSize = 64.sp)
}

/** 「10月28日 周三」，和账本的日期标题一样。 */
@Composable
private fun dayText(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = stringResource(R.string.date_header_pattern)
    return remember(pattern, locale, date) { date.format(DateTimeFormatter.ofPattern(pattern, locale)) }
}

private fun percent(share: Double): String = "${(share * 100).roundToInt()}%"

/** 每张卡片的底色（从上到下的渐变），都够深，白字看得清。 */
private fun gradientFor(card: ReviewCard, review: MonthReview): List<Color> = when (card) {
    ReviewCard.COVER -> COVER_COLORS
    ReviewCard.TOP_CATEGORY -> {
        val color = categoryColor(review.summary.byCategory.first().category)
        listOf(lerp(color, Color.Black, 0.6f), lerp(color, Color.Black, 0.3f))
    }
    ReviewCard.BIGGEST -> listOf(Color(0xFFB0182F), Color(0xFFE2522C))
    ReviewCard.FAVORITE_PLACE -> listOf(Color(0xFF0B6B66), Color(0xFF178F69))
    ReviewCard.BUSIEST_DAY -> listOf(Color(0xFF173F7E), Color(0xFF2479B8))
    ReviewCard.VERSUS_LAST_MONTH ->
        if (review.summary.totalMinor > (review.summary.previousTotalMinor ?: 0)) listOf(Color(0xFF8E0E12), Color(0xFFC92A24))
        else listOf(Color(0xFF0B6149), Color(0xFF168A5E))
    ReviewCard.FIRST_MONTH -> listOf(Color(0xFF3E3462), Color(0xFF6A4FA3))
    ReviewCard.PERSONA -> listOf(Color(0xFFD45500), Color(0xFFB01E85))
    ReviewCard.SUMMARY -> listOf(Color(0xFF120A2E), Color(0xFF2B1660))
}
