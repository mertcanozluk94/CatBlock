package com.catblock.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.catblock.app.permissions.PermissionUtils

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (PermissionUtils.hasOverlayPermission(context) &&
                PermissionUtils.hasUsageStatsPermission(context)) {
                UsageMonitorService.start(context)
            }
        }
    }
}
