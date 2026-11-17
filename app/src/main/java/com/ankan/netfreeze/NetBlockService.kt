package com.ankan.netfreeze

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.util.concurrent.atomic.AtomicLong

/**
 * Local "black-hole" VPN. Every app except the exempt ones (and this app) is routed
 * into a TUN interface whose packets are read and thrown away, so those apps get no
 * internet. Nothing leaves the phone through this VPN. Hotspot traffic is not affected.
 */
class NetBlockService : VpnService() {

    companion object {
        const val ACTION_START = "com.ankan.netfreeze.START"
        const val ACTION_STOP = "com.ankan.netfreeze.STOP"

        @Volatile
        var running = false

        @Volatile
        var startedAt = 0L

        val packets = AtomicLong(0)
        val bytes = AtomicLong(0)
    }

    private var drainer: Drainer? = null
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
            .setSession("Blutheist NetFreeze")
            .setMtu(1500)
            .addAddress("10.255.255.1", 32)
            .addAddress("fd00:ffff::1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)

        // This app must keep its own internet (speed test / ping).
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            // ignore
        }
        // Apps the user chose to keep online.
        for (pkg in Prefs.exempt(this)) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (e: Exception) {
                // app uninstalled, ignore
            }
        }
        try {
            val pi = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setConfigureIntent(pi)
        } catch (e: Exception) {
            // ignore
        }

        val tun: ParcelFileDescriptor? = try {
            builder.establish()
        } catch (e: Exception) {
            null
        }
        if (tun == null) {
            stopBlocking()
            return
        }

        val old = drainer
        val d = Drainer(tun)
        d.isDaemon = true
        d.start()
        drainer = d
        old?.halt()

        if (!running) {
            packets.set(0)
            bytes.set(0)
            startedAt = SystemClock.elapsedRealtime()
        }
        running = true
        handler.removeCallbacks(killer)
        handler.post(killer)
    }

    private fun cleanup() {
        running = false
        startedAt = 0L
        handler.removeCallbacks(killer)
        drainer?.halt()
        drainer = null
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

    /** Stops background processes of every app that is not exempt (non-root limits apply). */
    private fun killBackgroundApps() {
        val ctx = applicationContext
        Thread {
            try {
                val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val pm = ctx.packageManager
                val skip = HashSet<String>(Prefs.exempt(ctx))
                skip.add(ctx.packageName)
                skip.add("android")
                skip.add("com.android.systemui")
                // keep the home screen and the keyboard alive
                val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                for (ri in pm.queryIntentActivities(home, 0)) {
                    skip.add(ri.activityInfo.packageName)
                }
                val ime = Settings.Secure.getString(
                    ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD
                )
                if (!ime.isNullOrEmpty()) skip.add(ime.substringBefore('/'))

                for (app in pm.getInstalledApplications(0)) {
                    val pkg = app.packageName
                    if (pkg in skip) continue
                    try {
                        am.killBackgroundProcesses(pkg)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }.start()
    }

    /**
     * Reads and discards every packet. Polls with a timeout so it can always be stopped,
     * and closes the TUN descriptor itself when it ends.
     */
    private class Drainer(private val pfd: ParcelFileDescriptor) : Thread("nf-drain") {
        @Volatile
        private var stopped = false

        fun halt() {
            stopped = true
        }

        override fun run() {
            val fd = pfd.fileDescriptor
            val p = StructPollfd()
            p.fd = fd
            p.events = OsConstants.POLLIN.toShort()
            val arr = arrayOf(p)
            val buf = ByteArray(32768)
            try {
                while (!stopped) {
                    try {
                        p.revents = 0
                        val n = Os.poll(arr, 400)
                        if (n <= 0) continue
                        val rev = p.revents.toInt()
                        if ((rev and OsConstants.POLLIN) != 0) {
                            val len = Os.read(fd, buf, 0, buf.size)
                            if (len > 0) {
                                NetBlockService.packets.incrementAndGet()
                                NetBlockService.bytes.addAndGet(len.toLong())
                            }
                        } else if ((rev and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL)) != 0) {
                            break
                        }
                    } catch (e: ErrnoException) {
                        if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) continue
                        break
                    }
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                try {
                    pfd.close()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }
}
