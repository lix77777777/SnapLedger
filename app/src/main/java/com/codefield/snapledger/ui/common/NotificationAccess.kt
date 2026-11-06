package com.codefield.snapledger.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * 通知权限（安卓 13 起要用户同意）。granted 每次回到前台都会重新检查。
 * 用户拒绝过一次以后，request 改成打开系统里本应用的通知设置，因为系统不会再弹窗。
 */
class NotificationAccess(val granted: Boolean, val denied: Boolean, val request: () -> Unit)

@Composable
fun rememberNotificationAccess(): NotificationAccess {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(notificationsAllowed(context)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LifecycleResumeEffect(Unit) {
        granted = notificationsAllowed(context)
        onPauseOrDispose { }
    }
    return NotificationAccess(granted, denied) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !denied) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }
}

/** 安卓 13 以前不用申请权限，但用户也能在系统设置里关掉通知，所以两种情况都看这一个开关。 */
private fun notificationsAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()
