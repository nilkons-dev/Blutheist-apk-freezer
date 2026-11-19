package com.ankan.netfreeze

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import java.util.Locale

/** Real device information and shared helpers. */
object Sys {

    class Mem(val total: Long, val avail: Long)
    class Stor(val total: Long, val free: Long)
    class Batt(
        val level: Int,
        val charging: Boolean,
        val statusText: String,
        val tempC: Double,
        val voltageV: Double,
        val health: String,
        val source: String,
        val tech: String
    )

    fun fmtBytes(b: Long): String {
        val v = if (b < 0) 0L else b
        return when {
            v < 1024L -> "$v B"
            v < 1024L * 1024L -> String.format(Locale.US, "%d KB", v / 1024L)
            v < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", v / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", v / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun fmtDuration(ms: Long): String {
        val totalMin = ms / 60000L
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> "$h h $m m"
            totalMin > 0 -> "$m m"
            else -> "< 1 m"
        }
    }

    fun ago(ms: Long): String {
        if (ms <= 0L) return "never"
        val d = System.currentTimeMillis() - ms
        val m = d / 60000L
        return when {
            m < 1 -> "just now"
            m < 60 -> "$m min ago"
            m < 60 * 24 -> "${m / 60} h ago"
            else -> "${m / (60 * 24)} d ago"
        }
    }

    fun mem(ctx: Context): Mem {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return Mem(mi.totalMem, mi.availMem)
    }

    fun storage(): Stor {
        return try {
            val sf = StatFs(Environment.getExternalStorageDirectory().path)
            Stor(sf.totalBytes, sf.availableBytes)
        } catch (e: Exception) {
            Stor(1L, 1L)
        }
    }

    fun hasUsageAccess(ctx: Context): Boolean {
        return try {
            val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) ==
                AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun hasStorageAccess(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Packages that must never be killed (launcher, keyboard, system UI, this app). */
    fun protectedPackages(ctx: Context): HashSet<String> {
        val skip = HashSet<String>(Prefs.exempt(ctx))
        skip.add(ctx.packageName)
        skip.add("android")
        skip.add("com.android.systemui")
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            for (ri in ctx.packageManager.queryIntentActivities(home, 0)) skip.add(ri.activityInfo.packageName)
            val ime = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            if (!ime.isNullOrEmpty()) skip.add(ime.substringBefore('/'))
        } catch (e: Exception) {
            // ignore
        }
        return skip
    }

    /** Asks Android to stop background processes. Returns how many packages were asked. */
    fun killBackground(ctx: Context, only: Collection<String>? = null, extraSkip: Set<String> = emptySet()): Int {
        var n = 0
        try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val skip = protectedPackages(ctx)
            skip.addAll(extraSkip)
            val targets: Collection<String> = only
                ?: ctx.packageManager.getInstalledApplications(0).map { it.packageName }
            for (pkg in targets) {
                if (pkg in skip) continue
                try {
                    am.killBackgroundProcesses(pkg)
                    n++
                } catch (e: Exception) {
                    // ignore
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return n
    }

    fun battery(ctx: Context): Batt {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra("level", -1) ?: -1
        val scale = i?.getIntExtra("scale", 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val status = i?.getIntExtra("status", 1) ?: 1
        val plugged = i?.getIntExtra("plugged", 0) ?: 0
        val health = i?.getIntExtra("health", 1) ?: 1
        val temp = (i?.getIntExtra("temperature", 0) ?: 0) / 10.0
        val volt = (i?.getIntExtra("voltage", 0) ?: 0) / 1000.0
        val tech = i?.getStringExtra("technology") ?: "—"
        val statusText = when (status) {
            2 -> "Charging"
            3 -> "Discharging"
            4 -> "Not charging"
            5 -> "Full"
            else -> "Unknown"
        }
        val healthText = when (health) {
            2 -> "Good"
            3 -> "Overheat"
            4 -> "Dead"
            5 -> "Over voltage"
            6 -> "Failure"
            7 -> "Cold"
            else -> "Unknown"
        }
        val source = when (plugged) {
            1 -> "AC charger"
            2 -> "USB"
            4 -> "Wireless"
            else -> "Battery"
        }
        return Batt(pct, status == 2 || status == 5, statusText, temp, volt, healthText, source, tech)
    }

    /** Device health score, 0-100, from storage, memory, risks, battery and how recently you optimized. */
    fun score(ctx: Context): Int {
        var s = 100
        val st = storage()
        val usedPct = 100 - (st.free * 100 / st.total.coerceAtLeast(1L))
        s -= when {
            usedPct > 92 -> 14
            usedPct > 85 -> 8
            usedPct > 75 -> 3
            else -> 0
        }
        val m = mem(ctx)
        val memPct = 100 - (m.avail * 100 / m.total.coerceAtLeast(1L))
        s -= when {
            memPct > 88 -> 8
            memPct > 78 -> 4
            else -> 0
        }
        s -= minOf(Prefs.getInt(ctx, "risk_count", 0) * 4, 12)
        val last = Prefs.getLong(ctx, "last_optimize", 0L)
        if (last == 0L || System.currentTimeMillis() - last > 24L * 3600L * 1000L) s -= 3
        if (battery(ctx).tempC > 42.0) s -= 4
        return s.coerceIn(0, 100)
    }

    fun fmtTemp(ctx: Context, c: Double): String {
        return if (Prefs.getInt(ctx, "temp_unit", 0) == 1) {
            String.format(Locale.US, "%.1f °F", c * 9.0 / 5.0 + 32.0)
        } else {
            String.format(Locale.US, "%.1f °C", c)
        }
    }

    /** Clears the clipboard (Android 9+). */
    fun clearClipboard(ctx: Context): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            if (Build.VERSION.SDK_INT >= 28) {
                cm.clearPrimaryClip()
            } else {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
