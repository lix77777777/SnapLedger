package com.codefield.snapledger.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.codefield.snapledger.LedgerApplication
import com.codefield.snapledger.MainActivity
import com.codefield.snapledger.R
import com.codefield.snapledger.logic.MoneyFormat
import com.codefield.snapledger.logic.MonthlyStats
import com.codefield.snapledger.logic.RateTable
import com.codefield.snapledger.logic.ReminderSchedule
import com.codefield.snapledger.model.Money
import java.time.Duration
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

/**
 * 每月 1 号上午 10 点提醒看上个月的回顾。WorkManager 排一次性任务：到点发通知，再排下个月的。
 * 关掉提醒时取消；上个月没记账就不发。点通知直接打开那个月的回顾。
 */
class ReviewReminder(private val context: Context) {

    /** 排下一次提醒。replace 为 false 时已经排好的不动：App 每次启动都会调用。 */
    fun schedule(replace: Boolean = false) {
        val now = ZonedDateTime.now()
        val request = OneTimeWorkRequestBuilder<ReviewReminderWorker>()
            .setInitialDelay(Duration.between(now, ReminderSchedule.next(now)))
            .build()
        val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_MONTHLY, policy, request)
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_MONTHLY)
    }

    /** 调试用：马上发一条某个月的提醒，不影响每月那一次。 */
    fun sendNow(month: YearMonth) {
        val request = OneTimeWorkRequestBuilder<ReviewReminderWorker>()
            .setInputData(workDataOf(KEY_MONTH to month.toString()))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_TEST, ExistingWorkPolicy.REPLACE, request)
    }

    /** 发通知：「10月的回顾做好了」「一共花了 ₩153,846，点开看看花在了哪里」。没有通知权限时什么也不做。 */
    internal fun notify(month: YearMonth, total: Money) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val locale = context.resources.configuration.locales[0]
        val monthText = month.format(DateTimeFormatter.ofPattern(context.getString(R.string.review_month_short_pattern), locale))
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_MONTH, month.toString())
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, month.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title, monthText))
            .setContentText(context.getString(R.string.reminder_text, MoneyFormat.format(total)))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        /** 通知点开 MainActivity 时带的月份（「2026-10」）。 */
        const val EXTRA_MONTH = "open_review_month"
        internal const val KEY_MONTH = "month"
        private const val CHANNEL = "monthly_review"
        private const val WORK_MONTHLY = "monthly_review_reminder"
        private const val WORK_TEST = "monthly_review_test"
        private const val NOTIFICATION_ID = 1001
    }
}

/**
 * 到点时 WorkManager 调用：算出上个月花了多少，发通知，再排下个月的。
 * WorkManager 用反射创建它，没法把对象传进来，所以从 [LedgerApplication] 取共用的那一套。
 *
 * 下个月的一定要最后排：用 REPLACE 排同名任务时，WorkManager 会把正在跑的这一个也取消掉，
 * 排在前面的话通知还没发出去就被停了（第 10 步在模拟器上强制运行任务时发现的）。
 */
class ReviewReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as LedgerApplication).container
        val testMonth = inputData.getString(ReviewReminder.KEY_MONTH)
        if (testMonth == null && !container.settings.reviewReminder) return Result.success()
        val zone = ZoneId.systemDefault()
        val month = testMonth?.let(YearMonth::parse) ?: YearMonth.now(zone).minusMonths(1)
        val entries = container.repository.observeEntries().first()
        val rates = RateTable(container.repository.observeRates().first())
        val summary = MonthlyStats.summarize(entries, month, container.settings.homeCurrency, rates, zone)
        if (summary.entryCount > 0) container.reviewReminder.notify(month, Money(summary.totalMinor, summary.currency))
        if (testMonth == null) container.reviewReminder.schedule(replace = true)
        return Result.success()
    }
}
