package com.ankan.netfreeze

import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import java.io.FileInputStream

/**
 * A local "black-hole" VPN. All traffic of every app (except the whitelist)
 * is routed into the TUN interface and silently discarded, so those apps
 * get no internet. Nothing ever leaves the phone through this VPN.
 * Hotspot (tethering) traffic is not routed through this VPN.
 */
class NetBlockService : VpnService() {

    companion object {
        const val ACTION_START = "com.ankan.netfreeze.START"
        const val ACTION_STOP = "com.ankan.netfreeze.STOP"

        @Volatile
        var running = false
    }

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val handler = Handler(Looper.getMainLooper())

    private val killer = object : Runnable {
        override fun run() {
            killBackgroundApps()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBlocking()
            return START_NOT_STICKY
        }
        startBlocking()
        return START_STICKY
    }

    private fun startBlocking() {
        val builder = Builder()
            .setSession("NetFreeze")
            .setMtu(1500)
            .addAddress("10.255.255.1", 32)
            .addAddress("fd00:ffff::1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)

        // Whitelisted apps bypass the VPN, so they keep normal internet.
        for (pkg in Prefs.allowed(this)) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (_: PackageManager.NameNotFoundException) {
                // app was uninstalled, ignore
            }
        }

        val newTun: ParcelFileDescriptor? = try {
            builder.establish()
        } catch (_: Exception) {
            null
        }

        if (newTun == null) {
            stopBlocking()
            return
        }

        val oldTun = tun
        val oldWorker = worker
        tun = newTun
        worker = startDrain(newTun)
        oldWorker?.interrupt()
        try {
            oldTun?.close()
        } catch (_: Exception) {
        }

        running = true
        handler.removeCallbacks(killer)
        handler.post(killer)
    }

    /** Reads and discards every packet so the TUN buffer never fills up. */
    private fun startDrain(pfd: ParcelFileDescriptor): Thread {
        val t = Thread {
            try {
                val input = FileInputStream(pfd.fileDescriptor)
                val buffer = ByteArray(32767)
                while (!Thread.currentThread().isInterrupted) {
                    if (input.read(buffer) < 0) break
                }
            } catch (_: Exception) {
                // fd closed -> stop
            }
        }
        t.isDaemon = true
        t.start()
        return t
    }

    /** Kills cached/background processes of all other apps (non-root limits apply). */
    private fun killBackgroundApps() {
        Thread {
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                val skip = Prefs.allowed(this)
                for (app in packageManager.getInstalledApplications(0)) {
                    val pkg = app.packageName
                    if (pkg == packageName || pkg in skip) continue
                    try {
                        am.killBackgroundProcesses(pkg)
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun cleanup() {
        running = false
        handler.removeCallbacks(killer)
        worker?.interrupt()
        worker = null
        try {
            tun?.close()
        } catch (_: Exception) {
        }
        tun = null
    }

    private fun stopBlocking() {
        cleanup()
        stopSelf()
    }

    override fun onRevoke() {
        stopBlocking()
        super.onRevoke()
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }
}
