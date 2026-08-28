package com.aishield.detector.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

/** Resolves which app is currently in the foreground (optional nicety). */
object AppNames {

    private val KNOWN = mapOf(
        "com.instagram.android" to "Instagram",
        "com.instagram.lite" to "Instagram Lite",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.facebook.katana" to "Facebook",
        "com.facebook.lite" to "Facebook Lite",
        "com.twitter.android" to "X (Twitter)",
        "com.reddit.frontpage" to "Reddit",
        "com.google.android.youtube" to "YouTube",
        "com.snapchat.android" to "Snapchat",
        "com.pinterest" to "Pinterest",
        "com.linkedin.android" to "LinkedIn"
    )

    fun pretty(packageName: String): String {
        KNOWN[packageName]?.let { return it }
        val last = packageName.substringAfterLast('.')
        return last.replaceFirstChar { it.uppercase() }.ifEmpty { packageName }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Package name of the most recently resumed app (within ~10s), or null. */
    fun latestForeground(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val begin = now - 10_000
        val events = usm.queryEvents(begin, now)
        var latest: String? = null
        var latestTime = 0L
        val ev = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND &&
                ev.timeStamp >= latestTime
            ) {
                latestTime = ev.timeStamp
                latest = ev.packageName
            }
        }
        return latest
    }

    fun startOfToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
