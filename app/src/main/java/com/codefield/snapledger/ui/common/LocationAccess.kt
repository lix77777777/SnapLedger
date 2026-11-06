package com.codefield.snapledger.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * 定位权限：精确或大概位置给了一个就算有。request 问完以后调用 onResult（同意了没有）。
 * 用户拒绝过以后，openSettings 打开系统里本应用的设置页，因为系统不会再弹窗。
 */
class LocationAccess(
    val granted: Boolean,
    val denied: Boolean,
    val request: (onResult: (Boolean) -> Unit) -> Unit,
    val openSettings: () -> Unit,
)

@Composable
fun rememberLocationAccess(): LocationAccess {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(locationAllowed(context)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    // 问完权限要接着做的事（比如保存这一笔）。
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.any { it }
        denied = !granted
        pending[0]?.invoke(granted)
        pending[0] = null
    }
    LifecycleResumeEffect(Unit) {
        granted = locationAllowed(context)
        onPauseOrDispose { }
    }
    return LocationAccess(
        granted = granted,
        denied = denied,
        request = { onResult ->
            pending[0] = onResult
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        },
        openSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        },
    )
}

private fun locationAllowed(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
