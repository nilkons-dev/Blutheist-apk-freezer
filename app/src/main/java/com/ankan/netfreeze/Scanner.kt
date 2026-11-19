package com.ankan.netfreeze

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class Threat(
    val title: String,
    val detail: String,
    /** 1 = worth a look, 2 = risky, 3 = dangerous */
    val severity: Int,
    val pkg: String?,
    val path: String?
)

class SecCheck(val title: String, val detail: String, val ok: Boolean)

class ScanReport {
    val checks = ArrayList<SecCheck>()
    val threats = ArrayList<Threat>()
    var apps = 0
    var files = 0L
    var bytes = 0L
    var durationMs = 0L
    var filesSkipped = false
    var behaviorSkipped = false
    var cancelled = false
    fun risky(): Int = threats.count { it.severity >= 2 }
    fun failedChecks(): Int = checks.count { !it.ok }
}

/** Real checks of device settings, installed apps, app behaviour and every readable file. */
object Scanner {
    @Volatile
    var last: ScanReport? = null

    private val DOUBLE_EXT = Regex("\\.(jpg|jpeg|png|gif|bmp|webp|pdf|docx?|xlsx?|pptx?|txt|mp3|mp4|mkv|zip|rar)\\.(apk|exe|scr|bat|cmd|vbs|js|jar|sh|dex|com|pif|msi)$")
    private val EXEC_EXT = setOf("exe", "scr", "bat", "cmd", "vbs", "msi", "pif", "sh", "dex")
    private val MAGIC_EXT = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "mp3", "mp4", "mkv", "avi", "3gp", "pdf", "txt")
    private val EICAR_EXT = setOf("com", "txt", "exe", "bin", "zip", "eicar", "")
    private val SPY_WORDS = listOf(
        "mspy", "flexispy", "hoverwatch", "cocospy", "spyzie", "thetruthspy", "ikeymonitor", "xnspy", "spyic",
        "keylogger", "stealth spy", "spy phone", "phone spy", "hidden spy", "spyera", "highster", "mobile tracker free"
    )
    private val STORES = setOf(
        "com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia", "com.huawei.appmarket",
        "com.xiaomi.mipicks", "com.xiaomi.market", "com.heytap.market", "com.oppo.market", "com.vivo.appstore",
        "com.miui.packageinstaller.store", "com.google.android.packageinstaller.store"
    )
    private val IMITATED = mapOf(
        "whatsapp" to listOf("com.whatsapp"),
        "facebook" to listOf("com.facebook."),
        "messenger" to listOf("com.facebook."),
        "instagram" to listOf("com.instagram."),
        "telegram" to listOf("org.telegram."),
        "youtube" to listOf("com.google."),
        "gmail" to listOf("com.google."),
        "tiktok" to listOf("com.zhiliaoapp.", "com.ss.android."),
        "snapchat" to listOf("com.snapchat."),
        "binance" to listOf("com.binance."),
        "bkash" to listOf("com.bkash.")
    )
    private val DANGEROUS_SUFFIX = listOf(
        "READ_CONTACTS", "CAMERA", "RECORD_AUDIO", "ACCESS_FINE_LOCATION", "READ_SMS", "READ_CALL_LOG",
        "READ_PHONE_STATE", "READ_CALENDAR", "BODY_SENSORS", "ACCESS_BACKGROUND_LOCATION", "WRITE_CONTACTS", "SEND_SMS"
    )

    // ------------------------------------------------------------------------------ device checks

    private fun enabledPkgs(ctx: Context, key: String): List<String> {
        val s = try {
            Settings.Secure.getString(ctx.contentResolver, key)
        } catch (e: Exception) {
            null
        }
        if (s.isNullOrEmpty()) return emptyList()
        return s.split(":").map { it.substringBefore('/') }.filter { it.isNotEmpty() }
    }

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean {
        return try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun trusted(pkg: String): Boolean {
        return pkg.startsWith("com.google.") || pkg.startsWith("com.android.") || pkg.startsWith("com.samsung.") ||
            pkg.startsWith("com.miui.") || pkg.startsWith("com.xiaomi.") || pkg.startsWith("android")
    }

    fun deviceChecks(ctx: Context): List<SecCheck> {
        val out = ArrayList<SecCheck>()
        val cr = ctx.contentResolver
        val pm = ctx.packageManager
        try {
            val kg = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val secure = kg.isDeviceSecure
            out.add(SecCheck("Screen lock", if (secure) "A PIN, pattern or password protects this phone" else "No screen lock is set. Anyone can open the phone", secure))
        } catch (e: Exception) {
            // ignore
        }
        val adb = try { Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1 } catch (e: Exception) { false }
        out.add(SecCheck("USB debugging", if (adb) "On. Turn it off unless you develop apps" else "Off", !adb))
        val dev = try { Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 } catch (e: Exception) { false }
        out.add(SecCheck("Developer options", if (dev) "Enabled on this phone" else "Disabled", !dev))

        val suPaths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/app/Superuser.apk",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su"
        )
        val suFound = suPaths.any { try { File(it).exists() } catch (e: Exception) { false } }
        val rootApp = listOf("com.topjohnwu.magisk", "eu.chainfire.supersu", "com.koushikdutta.superuser", "com.noshufou.android.su")
            .any { isInstalled(pm, it) }
        val testKeys = Build.TAGS?.contains("test-keys") == true
        val rooted = suFound || rootApp || testKeys
        out.add(SecCheck("Root / modified system", if (rooted) "Signs of root or a custom system were found" else "No signs of root", !rooted))

        try {
            val patch = Build.VERSION.SECURITY_PATCH
            val d = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patch)
            val months = if (d != null) (System.currentTimeMillis() - d.time) / (30L * 24L * 3600L * 1000L) else 0L
            out.add(SecCheck("Security patch", "Patch level $patch ($months months old)", months <= 12))
        } catch (e: Exception) {
            // ignore
        }
        try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val enc = dpm.storageEncryptionStatus
            val ok = enc == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
                enc == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
            out.add(SecCheck("Storage encryption", if (ok) "Your data is encrypted" else "Storage is not encrypted", ok))
            val admins = (dpm.activeAdmins ?: emptyList<android.content.ComponentName>()).map { it.packageName }
            out.add(SecCheck("Device administrators", if (admins.isEmpty()) "No app has admin control" else admins.joinToString(", ") { appLabel(ctx, it) }, admins.isEmpty()))
        } catch (e: Exception) {
            // ignore
        }
        val verifier = try { Settings.Global.getInt(cr, "package_verifier_enable", 1) == 1 } catch (e: Exception) { true }
        out.add(SecCheck("App verification", if (verifier) "Apps are verified before install" else "App verification is turned off", verifier))
        val acc = enabledPkgs(ctx, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).filter { it != ctx.packageName && !trusted(it) }
        out.add(SecCheck("Accessibility apps", if (acc.isEmpty()) "No third-party app can read your screen" else acc.joinToString(", ") { appLabel(ctx, it) }, acc.isEmpty()))
        val nl = enabledPkgs(ctx, "enabled_notification_listeners").filter { it != ctx.packageName && !trusted(it) }
        out.add(SecCheck("Notification access", if (nl.isEmpty()) "No third-party app reads your notifications" else nl.joinToString(", ") { appLabel(ctx, it) }, nl.isEmpty()))
        return out
    }

    // ------------------------------------------------------------------------------ app analysis

    private fun isDebugCert(sig: android.content.pm.Signature): Boolean {
        return try {
            val cf = java.security.cert.CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(java.io.ByteArrayInputStream(sig.toByteArray())) as java.security.cert.X509Certificate
            cert.subjectX500Principal.name.contains("Android Debug", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    private class Eval(val points: Int, val reasons: List<String>)

    /** Rates what a package's manifest asks for. */
    private fun evaluate(pi: PackageInfo): Eval {
        val reasons = ArrayList<String>()
        var pts = 0
        val perms: Array<String> = pi.requestedPermissions ?: emptyArray<String>()
        val flags: IntArray? = pi.requestedPermissionsFlags
        fun granted(p: String): Boolean {
            val i = perms.indexOf(p)
            return i >= 0 && flags != null && i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        }
        if (granted("android.permission.READ_SMS") || granted("android.permission.RECEIVE_SMS") || granted("android.permission.SEND_SMS")) {
            pts += 2; reasons.add("Can read or send SMS")
        }
        if (granted("android.permission.READ_CALL_LOG")) {
            pts += 1; reasons.add("Can read call log")
        }
        if (perms.contains("android.permission.SYSTEM_ALERT_WINDOW")) {
            pts += 1; reasons.add("Can draw over other apps")
        }
        if (perms.contains("android.permission.REQUEST_INSTALL_PACKAGES")) {
            pts += 2; reasons.add("Can install other apps")
        }
        if (perms.contains("android.permission.RECEIVE_BOOT_COMPLETED") && granted("android.permission.RECORD_AUDIO") && granted("android.permission.CAMERA")) {
            pts += 1; reasons.add("Starts at boot with camera and microphone")
        }
        val services: Array<android.content.pm.ServiceInfo> = pi.services ?: emptyArray<android.content.pm.ServiceInfo>()
        if (services.any { it.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE" }) {
            pts += 3; reasons.add("Has an accessibility service")
        }
        if (services.any { it.permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" }) {
            pts += 2; reasons.add("Can read notifications")
        }
        val receivers: Array<android.content.pm.ActivityInfo> = pi.receivers ?: emptyArray<android.content.pm.ActivityInfo>()
        if (receivers.any { it.permission == "android.permission.BIND_DEVICE_ADMIN" }) {
            pts += 3; reasons.add("Can become device administrator")
        }
        var dangerous = 0
        for (p in perms) {
            if (granted(p) && DANGEROUS_SUFFIX.any { p.endsWith(it) }) dangerous++
        }
        if (dangerous >= 8) {
            pts += 1; reasons.add("$dangerous sensitive permissions granted")
        }
        return Eval(pts, reasons)
    }

    private fun severityFor(points: Int): Int = when {
        points >= 7 -> 3
        points >= 4 -> 2
        points >= 3 -> 1
        else -> 0
    }

    fun scanApps(ctx: Context, cancel: AtomicBoolean, progress: (String, Float) -> Unit, report: ScanReport) {
        val pm = ctx.packageManager
        val all = pm.getInstalledApplications(0)
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admins: Set<String> = try { (dpm.activeAdmins ?: emptyList<android.content.ComponentName>()).map { it.packageName }.toSet() } catch (e: Exception) { emptySet<String>() }
        val accEnabled = enabledPkgs(ctx, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).toSet()
        val nlEnabled = enabledPkgs(ctx, "enabled_notification_listeners").toSet()
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val launcher = HashSet<String>()
        for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            launcher.add(ri.activityInfo.packageName)
        }
        var n = 0
        for (ai in all) {
            if (cancel.get()) {
                report.cancelled = true
                return
            }
            n++
            val pkg = ai.packageName
            val label = try { ai.loadLabel(pm).toString() } catch (e: Exception) { pkg }
            progress(label, n.toFloat() / all.size.coerceAtLeast(1))
            report.apps++
            if (pkg == ctx.packageName) continue
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            var pts = 0
            val reasons = ArrayList<String>()

            val lower = (label + " " + pkg).lowercase()
            if (SPY_WORDS.any { lower.contains(it) }) {
                pts += 5; reasons.add("Name matches a known monitoring / spy app")
            }
            if (pkg in admins && !isSystem) {
                pts += 3; reasons.add("Active device administrator")
            }
            if (pkg in accEnabled && !trusted(pkg)) {
                pts += 3; reasons.add("Accessibility access is turned on")
            }
            if (pkg in nlEnabled && !trusted(pkg)) {
                pts += 2; reasons.add("Notification access is turned on")
            }
            if (!isSystem) {
                val pi: PackageInfo? = try {
                    val f = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
                        (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0)
                    pm.getPackageInfo(pkg, f)
                } catch (e: Exception) {
                    null
                }
                if (pi != null) {
                    val ev = evaluate(pi)
                    pts += ev.points
                    reasons.addAll(ev.reasons)
                    if (Build.VERSION.SDK_INT >= 28) {
                        val sigs = try { pi.signingInfo?.apkContentsSigners } catch (e: Exception) { null }
                        if (sigs != null && sigs.any { isDebugCert(it) }) {
                            pts += 2; reasons.add("Signed with a test/debug certificate")
                        }
                    }
                }
                if ((ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                    pts += 2; reasons.add("Debuggable build")
                }
                val installer = try { pm.getInstallerPackageName(pkg) } catch (e: Exception) { null }
                if (installer == null || installer !in STORES) {
                    pts += 1; reasons.add("Installed outside an app store")
                }
                try {
                    if (ops.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, ai.uid, pkg) == AppOpsManager.MODE_ALLOWED) {
                        pts += 2; reasons.add("Allowed to draw over other apps")
                    }
                } catch (e: Exception) {
                    // ignore
                }
                try {
                    if (ops.checkOpNoThrow("android:request_install_packages", ai.uid, pkg) == AppOpsManager.MODE_ALLOWED) {
                        pts += 2; reasons.add("Allowed to install unknown apps")
                    }
                } catch (e: Exception) {
                    // ignore
                }
                if (pkg !in launcher && pts >= 3) {
                    pts += 2; reasons.add("Hidden from the app drawer")
                }
                if (ai.targetSdkVersion < 23) {
                    pts += 1; reasons.add("Built for very old Android (skips permission prompts)")
                }
                val ll = label.lowercase()
                for ((name, prefixes) in IMITATED) {
                    if (ll.contains(name) && prefixes.none { pkg.lowercase().startsWith(it) }) {
                        pts += 3; reasons.add("Name imitates " + name.replaceFirstChar { it.uppercase() })
                        break
                    }
                }
            }
            val sev = severityFor(pts)
            if (sev > 0) report.threats.add(Threat(label, reasons.joinToString(" · "), sev, pkg, null))
        }
    }

    // ------------------------------------------------------------------------------ app behaviour

    fun scanBehavior(ctx: Context, cancel: AtomicBoolean, progress: (String, Float) -> Unit, report: ScanReport) {
        if (!Sys.hasUsageAccess(ctx)) {
            report.behaviorSkipped = true
            return
        }
        try {
            progress("Reading app activity", 0.2f)
            val end = System.currentTimeMillis()
            val begin = end - 7L * 24L * 3600L * 1000L
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val fg = usm.queryAndAggregateUsageStats(begin, end)
            val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val perUid = HashMap<Int, Long>()
            for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
                if (cancel.get()) return
                try {
                    val st = nsm.querySummary(type, null, begin, end)
                    val b = NetworkStats.Bucket()
                    while (st.hasNextBucket()) {
                        st.getNextBucket(b)
                        perUid[b.uid] = (perUid[b.uid] ?: 0L) + b.rxBytes + b.txBytes
                    }
                    st.close()
                } catch (e: Exception) {
                    // type not readable
                }
            }
            progress("Checking background data use", 0.7f)
            val pm = ctx.packageManager
            for ((uid, bytes) in perUid) {
                if (bytes < 150L * 1024L * 1024L) continue
                val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: continue
                if (pkg == ctx.packageName) continue
                val ai = try { pm.getApplicationInfo(pkg, 0) } catch (e: Exception) { continue }
                val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystem) continue
                val used = fg[pkg]?.totalTimeInForeground ?: 0L
                if (used < 60000L) {
                    report.threats.add(
                        Threat(
                            appLabel(ctx, pkg),
                            "Used " + Sys.fmtBytes(bytes) + " of data in 7 days but was barely opened. Check what it is sending.",
                            2, pkg, null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            report.behaviorSkipped = true
        }
    }

    // ------------------------------------------------------------------------------ file analysis

    private fun readHead(f: File, n: Int): ByteArray? {
        return try {
            FileInputStream(f).use { input ->
                val b = ByteArray(n)
                val got = input.read(b, 0, n)
                if (got <= 0) null else b.copyOf(got)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun mismatch(head: ByteArray): String? {
        if (head.size >= 4 && head[0] == 0x4D.toByte() && head[1] == 0x5A.toByte()) return "Windows program"
        if (head.size >= 4 && head[0] == 0x7F.toByte() && head[1] == 'E'.code.toByte() && head[2] == 'L'.code.toByte() && head[3] == 'F'.code.toByte()) return "native binary"
        if (head.size >= 4 && head[0] == 'd'.code.toByte() && head[1] == 'e'.code.toByte() && head[2] == 'x'.code.toByte() && head[3] == 0x0A.toByte()) return "Android code (DEX)"
        if (head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() && head[2] == 3.toByte() && head[3] == 4.toByte()) return "ZIP / APK package"
        if (head.size >= 2 && head[0] == '#'.code.toByte() && head[1] == '!'.code.toByte()) return "shell script"
        return null
    }

    fun scanFiles(
        ctx: Context,
        cancel: AtomicBoolean,
        progress: (String, Float) -> Unit,
        report: ScanReport
    ) {
        val root = Cleaner.root()
        val st = Sys.storage()
        val used = (st.total - st.free).coerceAtLeast(1L)
        val skipData = File(root, "Android/data").path
        val skipObb = File(root, "Android/obb").path
        val pm = ctx.packageManager
        val stack = ArrayList<File>()
        stack.add(root)
        var tick = 0
        while (stack.isNotEmpty()) {
            if (cancel.get()) {
                report.cancelled = true
                return
            }
            val dir = stack.removeAt(stack.size - 1)
            val list: Array<File>? = try { dir.listFiles() } catch (e: Exception) { null }
            if (list == null) continue
            for (f in list) {
                if (f.isDirectory) {
                    if (f.path != skipData && f.path != skipObb) stack.add(f)
                    continue
                }
                val size = f.length()
                report.files++
                report.bytes += size
                tick++
                if (tick % 250 == 0) {
                    progress(f.name, (report.bytes.toFloat() / used).coerceAtMost(0.97f))
                    if (cancel.get()) {
                        report.cancelled = true
                        return
                    }
                }
                val name = f.name.lowercase()
                val ext = name.substringAfterLast('.', "")
                if (DOUBLE_EXT.containsMatchIn(name)) {
                    report.threats.add(Threat(f.name, "Disguised program: the real type is hidden behind a fake extension", 3, null, f.path))
                    continue
                }
                if (ext in EXEC_EXT) {
                    report.threats.add(Threat(f.name, "Executable or script file on shared storage", 2, null, f.path))
                    continue
                }
                if (size in 60L..1024L && (ext in EICAR_EXT || name.contains("eicar"))) {
                    val head = readHead(f, 1024)
                    if (head != null && String(head, Charsets.ISO_8859_1).contains("EICAR-STANDARD-ANTIVIRUS-TEST-FILE")) {
                        report.threats.add(Threat(f.name, "EICAR antivirus test signature", 3, null, f.path))
                        continue
                    }
                }
                if (ext in MAGIC_EXT && size >= 8L) {
                    val head = readHead(f, 8)
                    val m = if (head != null) mismatch(head) else null
                    if (m != null) {
                        report.threats.add(Threat(f.name, "Content is a $m, not a ." + ext + " file", if (m == "ZIP / APK package" || m == "shell script") 2 else 3, null, f.path))
                        continue
                    }
                }
                if (ext == "apk" && size < 400L * 1024L * 1024L) {
                    val pi: PackageInfo? = try {
                        pm.getPackageArchiveInfo(f.path, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS)
                    } catch (e: Exception) {
                        null
                    }
                    if (pi != null) {
                        val ev = evaluate(pi)
                        val pts = ev.points + 1
                        val sev = severityFor(pts)
                        if (sev > 0) {
                            report.threats.add(Threat(f.name, "Not-installed APK. " + ev.reasons.joinToString(" · "), sev, null, f.path))
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------ orchestration

    /**
     * Runs the scan. onProgress(stageIndex, stageLabel, detail, overallFraction).
     * Stages: 0 device, 1 apps, 2 behaviour, 3 files.
     */
    fun run(
        ctx: Context,
        full: Boolean,
        cancel: AtomicBoolean,
        onProgress: (Int, String, String, Float) -> Unit
    ): ScanReport {
        val report = ScanReport()
        val t0 = System.currentTimeMillis()
        val filesOk = full && Sys.hasStorageAccess(ctx)
        // weights of the four stages
        val w = if (filesOk) floatArrayOf(0.04f, 0.26f, 0.08f, 0.62f) else floatArrayOf(0.08f, 0.62f, 0.30f, 0f)
        fun base(i: Int): Float {
            var b = 0f
            for (k in 0 until i) b += w[k]
            return b
        }

        onProgress(0, "Device security", "Checking phone settings", 0f)
        report.checks.addAll(deviceChecks(ctx))
        onProgress(0, "Device security", "Done", base(1))

        if (!cancel.get()) {
            scanApps(ctx, cancel, { label, f -> onProgress(1, "Installed apps", label, base(1) + w[1] * f) }, report)
        }
        if (!cancel.get()) {
            scanBehavior(ctx, cancel, { label, f -> onProgress(2, "App behaviour", label, base(2) + w[2] * f) }, report)
        }
        if (!cancel.get()) {
            if (filesOk) {
                scanFiles(ctx, cancel, { label, f -> onProgress(3, "Files", label, base(3) + w[3] * f) }, report)
            } else {
                report.filesSkipped = true
            }
        }
        report.cancelled = cancel.get()
        report.threats.sortByDescending { it.severity }
        report.durationMs = System.currentTimeMillis() - t0
        onProgress(3, "Finishing", "", 1f)
        return report
    }
}
