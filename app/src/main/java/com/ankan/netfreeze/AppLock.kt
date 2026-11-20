package com.ankan.netfreeze

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.security.MessageDigest

object AppLockStore {
    fun hasPin(ctx: Context): Boolean = Prefs.getString(ctx, "lock_hash", "").isNotEmpty()

    private fun sha(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return d.joinToString("") { String.format("%02x", it) }
    }

    fun setPin(ctx: Context, pin: String) {
        val salt = java.util.UUID.randomUUID().toString()
        Prefs.putString(ctx, "lock_salt", salt)
        Prefs.putString(ctx, "lock_hash", sha(salt + pin))
        Prefs.putInt(ctx, "lock_len", pin.length)
    }

    fun check(ctx: Context, pin: String): Boolean {
        val salt = Prefs.getString(ctx, "lock_salt", "")
        return sha(salt + pin) == Prefs.getString(ctx, "lock_hash", "")
    }

    fun length(ctx: Context): Int = Prefs.getInt(ctx, "lock_len", 4).coerceIn(4, 6)
    fun locked(ctx: Context): Set<String> = Prefs.getSet(ctx, "locked_apps")

    fun serviceEnabled(ctx: Context): Boolean {
        val s = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } catch (e: Exception) {
            null
        } ?: return false
        return s.contains(ctx.packageName + "/") && s.contains("AppLockService")
    }
}

/** Watches which app comes to the front and shows the lock screen over locked apps. */
class AppLockService : AccessibilityService() {
    companion object {
        @Volatile
        var unlocked: String? = null
        @Volatile
        var lastPkg: String = ""
        @Volatile
        var lastLaunch: Long = 0L
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            unlocked = null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui" || pkg == "android") return
        val ime = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        } catch (ex: Exception) {
            null
        }
        if (ime != null && ime.startsWith("$pkg/")) return
        if (pkg in AppLockStore.locked(this)) {
            if (unlocked == pkg) return
            val now = SystemClock.elapsedRealtime()
            if (pkg == lastPkg && now - lastLaunch < 800L) return
            lastPkg = pkg
            lastLaunch = now
            val i = Intent(this, LockActivity::class.java)
            i.putExtra("pkg", pkg)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            try {
                startActivity(i)
            } catch (ex: Exception) {
                // ignore
            }
        } else {
            unlocked = null
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOff)
        } catch (e: Exception) {
            // ignore
        }
        super.onDestroy()
    }
}

/** Full-screen PIN pad shown over a locked app. */
class LockActivity : Activity() {
    private var pkg = ""
    private var label = ""
    private lateinit var msg: TextView
    private lateinit var pad: PinPad

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UI.loadTheme(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = UI.BG
        window.navigationBarColor = UI.BG
        pkg = intent.getStringExtra("pkg") ?: ""
        label = appLabel(this, pkg)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.background = UI.glow(this)
        root.setPadding(UI.dp(this, 24), UI.dp(this, 56), UI.dp(this, 24), UI.dp(this, 24))

        val icon = ImageView(this)
        try {
            icon.setImageDrawable(packageManager.getApplicationIcon(pkg))
        } catch (e: Exception) {
            icon.setImageResource(R.mipmap.ic_launcher)
        }
        root.addView(icon, UI.lp(UI.dp(this, 68), UI.dp(this, 68)))
        root.addView(UI.text(this, label, 22f, UI.TEXT, UI.bold()), UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(this, 16), 0, 0))
        msg = UI.text(this, "Enter your PIN to open this app", 15f, UI.MUTED)
        root.addView(msg, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(this, 8), 0, 0))

        val bio = Prefs.getBool(this, "lock_bio", false) && Build.VERSION.SDK_INT >= 28
        pad = PinPad(this, AppLockStore.length(this), bio, { entered -> verify(entered) }, { startBio() })
        root.addView(pad, UI.lp(UI.WRAP, UI.WRAP, 0f, 0, UI.dp(this, 34), 0, 0))
        setContentView(root)
        if (UI.anim) {
            pad.alpha = 0f
            pad.translationY = UI.dpf(this, 30f)
            pad.animate().alpha(1f).translationY(0f).setDuration(380).start()
        }
        if (bio) startBio()
    }

    private fun verify(entered: String) {
        if (AppLockStore.check(this, entered)) {
            success()
        } else {
            msg.text = "Wrong PIN. Try again"
            pad.shake()
        }
    }

    private fun success() {
        AppLockService.unlocked = pkg
        finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    private fun startBio() {
        if (Build.VERSION.SDK_INT < 28) return
        try {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle("Unlock $label")
                .setNegativeButton("Use PIN", mainExecutor, DialogInterface.OnClickListener { _, _ -> })
                .build()
            prompt.authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    success()
                }
            })
        } catch (e: Exception) {
            // fingerprint not available
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        val h = Intent(Intent.ACTION_MAIN)
        h.addCategory(Intent.CATEGORY_HOME)
        h.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(h)
        finish()
    }
}

/** Lock settings and the list of apps to lock. */
class AppLockScreen(ctx: Context, nav: Nav) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "App Lock", scroll = false)
    override val view: View get() = page.root
    private val list = ListView(ctx)
    private val statusHost = LinearLayout(ctx)
    private val adapter: ItemAdapter
    private var shown: List<AppItem> = emptyList()
    private var locked = HashSet<String>()
    private var query = ""

    init {
        statusHost.orientation = LinearLayout.VERTICAL
        statusHost.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 4), UI.dp(ctx, 20), UI.dp(ctx, 8))
        list.divider = null
        list.overScrollMode = View.OVER_SCROLL_NEVER
        list.isVerticalScrollBarEnabled = false
        list.addHeaderView(statusHost, null, false)
        adapter = ItemAdapter(ctx, { shown.size }) { row, pos ->
            val a = shown[pos]
            bindAppIcon(ctx, row, a.pkg)
            row.title.text = a.label
            val on = a.pkg in locked
            row.sub.text = if (on) "Locked" else "Not locked"
            row.pill.visibility = View.VISIBLE
            row.pill.set(on, false)
            row.setPadding(UI.dp(ctx, 24), UI.dp(ctx, 8), UI.dp(ctx, 24), UI.dp(ctx, 8))
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val i = pos - list.headerViewsCount
            if (i in shown.indices) toggle(shown[i].pkg)
        }
        page.content.addView(list, UI.lp(UI.MATCH, 0, 1f))
    }

    override fun onShow() {
        locked = HashSet(AppLockStore.locked(ctx))
        buildStatus()
        AppRepo.load(ctx) { applyFilter() }
    }

    private fun toggle(pkg: String) {
        if (!AppLockStore.hasPin(ctx)) {
            pinDialog { toggle(pkg) }
            return
        }
        if (pkg in locked) locked.remove(pkg) else locked.add(pkg)
        Prefs.putSet(ctx, "locked_apps", locked)
        adapter.notifyDataSetChanged()
        buildStatus()
    }

    private fun pinDialog(after: (() -> Unit)?) {
        nav.push(PinSetupScreen(ctx, nav) {
            buildStatus()
            if (after != null) after()
        })
    }

    private fun buildStatus() {
        statusHost.removeAllViews()
        val on = AppLockStore.serviceEnabled(ctx)
        val ring = RingView(ctx)
        ring.iconKind = Ic.KEY
        ring.dimmed = !on
        ring.progress = if (on) 1f else 0f
        val rs = UI.dp(ctx, 120)
        statusHost.addView(ring, UI.lp(rs, rs, 0f, 0, UI.dp(ctx, 4), 0, 0).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        val title = UI.text(ctx, if (on) "App Lock is active" else "App Lock is off", 22f, UI.TEXT, UI.bold())
        title.gravity = Gravity.CENTER
        statusHost.addView(title, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 14), 0, 0))
        val sub = UI.text(
            ctx,
            if (on) locked.size.toString() + " app(s) are locked behind your PIN." else "Turn on the Blutheist App Lock service in Accessibility settings so locked apps ask for your PIN.",
            14f, UI.MUTED
        )
        sub.gravity = Gravity.CENTER
        statusHost.addView(sub, UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 8), UI.dp(ctx, 8), UI.dp(ctx, 8), UI.dp(ctx, 14)))

        val card = UI.card(ctx)
        card.addView(UI.row(ctx, Ic.SHIELD, "Protection service", if (on) "On" else "Turn on", true) {
            try {
                nav.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                nav.toast("Open Settings > Accessibility")
            }
        }.root)
        card.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        card.addView(UI.row(ctx, Ic.KEY, "PIN", if (AppLockStore.hasPin(ctx)) "Change" else "Set", true) { pinDialog(null) }.root)
        if (Build.VERSION.SDK_INT >= 28 && ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
            card.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
            val r = UI.row(ctx, Ic.LOCK, "Fingerprint unlock", "", false, null)
            val sw = PillSwitch(ctx)
            sw.set(Prefs.getBool(ctx, "lock_bio", false), false)
            r.root.addView(sw, UI.lp(UI.WRAP, UI.WRAP, 0f, UI.dp(ctx, 8), 0, 0, 0))
            r.root.isClickable = true
            r.root.setOnClickListener {
                val v = !sw.isOn()
                sw.set(v, true)
                Prefs.putBool(ctx, "lock_bio", v)
            }
            card.addView(r.root)
        }
        card.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
        card.addView(UI.row(ctx, Ic.SPARKLE, "Lock common apps", "", true) { lockCommon() }.root)
        statusHost.addView(card)
        if (Build.VERSION.SDK_INT >= 33 && !on) {
            statusHost.addView(
                UI.text(ctx, "Android 13+ may say the setting is restricted. Open Settings > Apps > Blutheist Phone Manager > menu (three dots) > Allow restricted settings, then turn the service on.", 12.5f, UI.MUTED),
                UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 4), UI.dp(ctx, 12), UI.dp(ctx, 4), 0)
            )
        }

        val search = EditText(ctx)
        search.hint = "Search apps"
        search.setHintTextColor(UI.MUTED)
        search.setTextColor(UI.TEXT)
        search.textSize = 15f
        search.isSingleLine = true
        search.imeOptions = EditorInfo.IME_ACTION_DONE
        search.setText(query)
        search.background = UI.rounded(ctx, UI.CARD, 26f, UI.STROKE, 1f)
        search.setPadding(UI.dp(ctx, 20), UI.dp(ctx, 13), UI.dp(ctx, 20), UI.dp(ctx, 13))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                applyFilter()
            }
        })
        statusHost.addView(search, UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 16), 0, 0))
    }

    private fun lockCommon() {
        if (!AppLockStore.hasPin(ctx)) {
            pinDialog { lockCommon() }
            return
        }
        val common = listOf(
            "com.whatsapp", "com.whatsapp.w4b", "com.facebook.orca", "com.facebook.katana", "com.instagram.android",
            "org.telegram.messenger", "com.google.android.apps.photos", "com.miui.gallery", "com.sec.android.gallery3d",
            "com.android.settings", "com.android.vending", "com.google.android.gm"
        )
        val installed = AppRepo.items.map { it.pkg }.toSet()
        var n = 0
        for (p in common) {
            if (p in installed && locked.add(p)) n++
        }
        Prefs.putSet(ctx, "locked_apps", locked)
        adapter.notifyDataSetChanged()
        buildStatus()
        nav.toast("$n apps locked")
    }

    private fun applyFilter() {
        val q = query.trim().lowercase()
        val out = ArrayList<AppItem>()
        for (a in AppRepo.items) {
            if (!a.hasLauncher) continue
            if (q.isNotEmpty() && !a.label.lowercase().contains(q)) continue
            out.add(a)
        }
        out.sortWith(compareByDescending<AppItem> { it.pkg in locked }.thenBy { it.label.lowercase() })
        shown = out
        adapter.notifyDataSetChanged()
    }
}
