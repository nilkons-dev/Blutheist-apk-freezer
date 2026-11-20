package com.ankan.netfreeze

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout

class SettingsScreen(ctx: Context, nav: Nav, private val isTab: Boolean) : Screen(ctx, nav) {
    private val page = Page(ctx, nav, "Settings", scroll = true, back = !isTab)
    override val view: View get() = page.root
    private val usageRow: UI.RowH
    private val filesRow: UI.RowH
    private val lockRow: UI.RowH

    init {
        page.add(
            UI.text(ctx, "Make Phone Manager look and behave the way you like.", 14.5f, UI.MUTED),
            UI.lp(UI.MATCH, UI.WRAP, 0f, UI.dp(ctx, 6), 0, UI.dp(ctx, 6), UI.dp(ctx, 6))
        )

        // appearance
        val a = section("Appearance")
        choiceRow(a, Ic.SUN, "Theme", "theme", UI.THEME_NAMES, 0, intArrayOf(Color.BLACK, Color.parseColor("#28282C"), Color.parseColor("#151E33"))) {
            UI.loadTheme(ctx)
            nav.restartUi()
        }
        divider(a)
        choiceRow(a, Ic.SPARKLE, "Accent colour", "accent", UI.ACCENT_NAMES, 0, UI.ACCENTS) {
            UI.loadTheme(ctx)
            nav.restartUi()
        }
        divider(a)
        toggleRow(a, Ic.BOLT, "Animations", "animations", true) { UI.anim = it }
        divider(a)
        toggleRow(a, Ic.PHONE, "Haptic feedback", "haptics", true) { UI.haptics = it }

        // scanning & cleaning
        val b = section("Scanning and cleaning")
        choiceRow(b, Ic.SHIELD, "Virus scan depth", "scan_depth", arrayOf("Quick (device and apps)", "Full (also every file)"), 1, null) { }
        divider(b)
        toggleRow(b, Ic.TRASH, "Confirm before deleting", "confirm_delete", true, null)
        divider(b)
        choiceRow(b, Ic.BATTERY, "Temperature unit", "temp_unit", arrayOf("Celsius (°C)", "Fahrenheit (°F)"), 0, null) { }

        // permissions
        val c = section("Permissions")
        usageRow = UI.row(ctx, Ic.CLOCK, "Usage access", "", true) { nav.openUsage() }
        filesRow = UI.row(ctx, Ic.FOLDER, "All files access", "", true) { nav.openAllFiles() }
        lockRow = UI.row(ctx, Ic.KEY, "App Lock service", "", true) { Tools.open(ctx, nav, Tools.APPLOCK) }
        c.addView(usageRow.root)
        divider(c)
        c.addView(filesRow.root)
        divider(c)
        c.addView(lockRow.root)

        // data
        val d = section("Data")
        d.addView(UI.row(ctx, Ic.TRASH, "Clear scan history", "", true) {
            Prefs.putLong(ctx, "last_scan", 0L)
            Prefs.putInt(ctx, "risk_count", 0)
            Prefs.remove(ctx, "records")
            Prefs.remove(ctx, "ignored_threats")
            Scanner.last = null
            nav.toast("Scan history cleared")
        }.root)
        divider(d)
        d.addView(UI.row(ctx, Ic.HOURGLASS, "Reset all settings", "", true) {
            Sheet.confirm(ctx, "Reset all settings?", "Theme, accent, App Lock PIN, locked apps and frozen-app choices go back to default.", "Reset") {
                Prefs.clearAll(ctx)
                UI.loadTheme(ctx)
                nav.restartUi()
            }
        }.root)

        // about
        val e = section("About")
        e.addView(UI.row(ctx, Ic.INFO, "Blutheist Phone Manager", "v3.2", true) {
            Sheet.info(ctx, "Blutheist Phone Manager", "Version 3.2\n\nStorage cleanup, system boost, virus and risk scan, App Lock, app management, Net Freeze, speed test, duplicate finder and more.\n\nEverything runs on your phone. Nothing is uploaded.")
        }.root)
    }

    private fun section(title: String): LinearLayout {
        page.add(UI.sectionLabel(ctx, title), UI.lp(UI.MATCH, UI.WRAP, 0f, 0, UI.dp(ctx, 22), 0, UI.dp(ctx, 10)))
        val c = UI.card(ctx)
        page.add(c)
        return c
    }

    private fun divider(card: LinearLayout) {
        card.addView(UI.divider(ctx), UI.lp(UI.MATCH, 1, 0f, UI.dp(ctx, 16), 0, UI.dp(ctx, 16), 0))
    }

    private fun toggleRow(card: LinearLayout, icon: Int, title: String, key: String, def: Boolean, onChange: ((Boolean) -> Unit)?) {
        val r = UI.row(ctx, icon, title, "", false, null)
        val sw = PillSwitch(ctx)
        sw.set(Prefs.getBool(ctx, key, def), false)
        r.root.addView(sw, UI.lp(UI.WRAP, UI.WRAP, 0f, UI.dp(ctx, 8), 0, 0, 0))
        r.root.isClickable = true
        r.root.setOnClickListener {
            UI.tap(it)
            val v = !sw.isOn()
            sw.set(v, true)
            Prefs.putBool(ctx, key, v)
            if (onChange != null) onChange(v)
        }
        UI.press(r.root, 0.985f)
        card.addView(r.root)
    }

    private fun choiceRow(
        card: LinearLayout, icon: Int, title: String, key: String, options: Array<String>, def: Int,
        swatches: IntArray?, onChange: () -> Unit
    ) {
        val cur = Prefs.getInt(ctx, key, def).coerceIn(0, options.size - 1)
        val r = UI.row(ctx, icon, title, options[cur], true) { }
        r.root.setOnClickListener {
            UI.tap(it)
            val now = Prefs.getInt(ctx, key, def).coerceIn(0, options.size - 1)
            Sheet.choose(ctx, title, options, now, swatches) { which ->
                Prefs.putInt(ctx, key, which)
                UI.swapText(r.value, options[which])
                onChange()
            }
        }
        card.addView(r.root)
    }

    override fun onShow() {
        usageRow.value.text = if (Sys.hasUsageAccess(ctx)) "Allowed" else "Not allowed"
        filesRow.value.text = if (Sys.hasStorageAccess(ctx)) "Allowed" else "Not allowed"
        lockRow.value.text = if (AppLockStore.serviceEnabled(ctx)) "On" else "Off"
    }
}
