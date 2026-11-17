package com.ankan.netfreeze

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val reqVpn = 100
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var toggle: Button

    private val refresher = object : Runnable {
        override fun run() {
            refreshUi()
            handler.postDelayed(this, 1000L)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#0B0F14"))
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }

        val title = TextView(this).apply {
            text = "NetFreeze"
            textSize = 30f
            setTextColor(Color.parseColor("#00E5FF"))
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        root.addView(title)

        status = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(24))
        }
        root.addView(status)

        toggle = Button(this).apply {
            textSize = 20f
            setTextColor(Color.BLACK)
            setOnClickListener { onToggle() }
        }
        root.addView(
            toggle,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(90))
        )

        val allowBtn = Button(this).apply {
            text = "Allowed apps (whitelist)"
            setOnClickListener { showWhitelist() }
        }
        root.addView(
            allowBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(24) }
        )

        val batteryBtn = Button(this).apply {
            text = "Data Saver / Data usage settings"
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_DATA_USAGE_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }
        root.addView(batteryBtn)

        val note = TextView(this).apply {
            text = "ON: every app (except whitelisted ones) loses internet and " +
                "its background processes are stopped every minute.\n" +
                "Your hotspot keeps working and gets the full bandwidth.\n" +
                "OFF: everything returns to normal."
            textSize = 13f
            setTextColor(Color.parseColor("#8899AA"))
            setPadding(0, dp(24), 0, 0)
        }
        root.addView(note)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresher)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    private fun refreshUi() {
        if (NetBlockService.running) {
            status.text = "STATUS: BLOCKING ALL BACKGROUND INTERNET"
            toggle.text = "TURN OFF"
            toggle.setBackgroundColor(Color.parseColor("#FF5252"))
        } else {
            status.text = "STATUS: NORMAL (not blocking)"
            toggle.text = "BLOCK ALL"
            toggle.setBackgroundColor(Color.parseColor("#00E5FF"))
        }
    }

    private fun onToggle() {
        if (NetBlockService.running) {
            startService(
                Intent(this, NetBlockService::class.java).setAction(NetBlockService.ACTION_STOP)
            )
        } else {
            val prepare = VpnService.prepare(this)
            if (prepare != null) {
                startActivityForResult(prepare, reqVpn)
            } else {
                startBlocking()
            }
        }
    }

    private fun startBlocking() {
        startService(
            Intent(this, NetBlockService::class.java).setAction(NetBlockService.ACTION_START)
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqVpn) {
            if (resultCode == RESULT_OK) {
                startBlocking()
            } else {
                Toast.makeText(this, "VPN permission is required", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showWhitelist() {
        val pm = packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(query, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != packageName }
            .sortedBy { it.second.lowercase() }

        if (apps.isEmpty()) {
            Toast.makeText(this, "No apps found", Toast.LENGTH_SHORT).show()
            return
        }

        val saved = Prefs.allowed(this)
        val names = apps.map { it.second }.toTypedArray()
        val checked = BooleanArray(apps.size) { apps[it].first in saved }

        AlertDialog.Builder(this)
            .setTitle("Apps that keep internet")
            .setMultiChoiceItems(names, checked) { _, index, isChecked ->
                checked[index] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                val result = HashSet<String>()
                for (i in apps.indices) {
                    if (checked[i]) result.add(apps[i].first)
                }
                Prefs.saveAllowed(this, result)
                if (NetBlockService.running) startBlocking() // re-apply instantly
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
