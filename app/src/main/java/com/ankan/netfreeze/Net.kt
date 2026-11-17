package com.ankan.netfreeze

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.min

/** TCP "ping": time of a TCP handshake to a public server (ICMP needs root on Android). */
object Pinger {
    val HOSTS = arrayOf("1.1.1.1", "8.8.8.8", "9.9.9.9")

    fun tcpPing(host: String, port: Int, timeoutMs: Int): Double? {
        val s = Socket()
        return try {
            val t0 = System.nanoTime()
            s.connect(InetSocketAddress(host, port), timeoutMs)
            (System.nanoTime() - t0) / 1_000_000.0
        } catch (e: Exception) {
            null
        } finally {
            try {
                s.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}

/** Pings once a second until stopped. Samples are delivered on the main thread. */
class LiveLatency(private val onSample: (Double?) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var gen = 0

    fun start() {
        gen += 1
        val my = gen
        val t = Thread {
            var idx = 0
            var fails = 0
            while (gen == my) {
                val ms = Pinger.tcpPing(Pinger.HOSTS[idx], 443, 1500)
                if (ms == null) {
                    fails++
                    if (fails >= 3) {
                        idx = (idx + 1) % Pinger.HOSTS.size
                        fails = 0
                    }
                } else {
                    fails = 0
                }
                if (gen != my) break
                main.post {
                    if (gen == my) onSample(ms)
                }
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
        t.isDaemon = true
        t.start()
    }

    fun stop() {
        gen += 1
    }
}

/**
 * Ping + download + upload test. Callbacks arrive on a worker thread.
 * The app itself is excluded from the freeze VPN, so results show the real connection.
 */
class SpeedTest(private val cb: Callback) {

    interface Callback {
        fun onStage(stage: Int) // 0 = ping, 1 = download, 2 = upload
        fun onPing(sample: Double?, index: Int, total: Int)
        fun onSpeed(stage: Int, mbps: Double, progress: Float)
        fun onDone(result: Outcome)
        fun onError(message: String)
    }

    class Outcome(
        val ping: Double?,
        val jitter: Double?,
        val lossPercent: Int,
        val down: Double?,
        val up: Double?
    )

    companion object {
        private const val PING_COUNT = 12
        private const val DOWN_MS = 8000L
        private const val UP_MS = 6000L
        private const val WARMUP_MS = 1500L
        private const val UP_CHUNK = 1_000_000

        private val DOWN_URLS = arrayOf(
            "https://speed.cloudflare.com/__down?bytes=50000000",
            "https://proof.ovh.net/files/100Mb.dat"
        )
        private const val UP_URL = "https://speed.cloudflare.com/__up"
    }

    @Volatile
    private var cancelled = false
    private val conns = CopyOnWriteArrayList<HttpURLConnection>()

    fun start() {
        val t = Thread { runTest() }
        t.isDaemon = true
        t.start()
    }

    fun cancel() {
        cancelled = true
        closeConns()
    }

    private fun closeConns() {
        for (c in conns) {
            try {
                c.disconnect()
            } catch (e: Exception) {
                // ignore
            }
        }
        conns.clear()
    }

    private fun runTest() {
        try {
            // ---- ping ----
            cb.onStage(0)
            val samples = ArrayList<Double>()
            var lost = 0
            var hostIdx = 0
            var fails = 0
            for (i in 0 until PING_COUNT) {
                if (cancelled) return
                var ms = Pinger.tcpPing(Pinger.HOSTS[hostIdx], 443, 2000)
                if (ms == null) {
                    fails++
                    if (fails >= 2 && hostIdx < Pinger.HOSTS.size - 1) {
                        hostIdx++
                        fails = 0
                        ms = Pinger.tcpPing(Pinger.HOSTS[hostIdx], 443, 2000)
                    }
                } else {
                    fails = 0
                }
                if (ms == null) lost++ else samples.add(ms)
                cb.onPing(ms, i + 1, PING_COUNT)
                Thread.sleep(120)
            }
            if (samples.isEmpty()) {
                cb.onError("No internet connection")
                return
            }
            val avg = samples.average()
            var jitter: Double? = null
            if (samples.size >= 2) {
                var sum = 0.0
                for (k in 1 until samples.size) sum += abs(samples[k] - samples[k - 1])
                jitter = sum / (samples.size - 1)
            }
            val lossPct = (lost * 100) / PING_COUNT

            // ---- download ----
            if (cancelled) return
            cb.onStage(1)
            val down = measure(1, DOWN_MS, 4)
            if (cancelled) return

            // ---- upload ----
            cb.onStage(2)
            val up = measure(2, UP_MS, 3)
            if (cancelled) return

            if (down == null && up == null) {
                cb.onError("Could not reach the speed test servers")
            } else {
                cb.onDone(SpeedTest.Outcome(avg, jitter, lossPct, down, up))
            }
        } catch (e: InterruptedException) {
            // cancelled
        } catch (e: Exception) {
            if (!cancelled) cb.onError("Test failed: " + (e.message ?: "unknown error"))
        } finally {
            closeConns()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 5000
        c.readTimeout = 8000
        c.useCaches = false
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) BlutheistNetFreeze/2.0")
        c.setRequestProperty("Accept-Encoding", "identity")
        c.setRequestProperty("Cache-Control", "no-cache")
        if (url.contains("speed.cloudflare.com")) {
            c.setRequestProperty("Referer", "https://speed.cloudflare.com/")
            c.setRequestProperty("Origin", "https://speed.cloudflare.com")
        }
        conns.add(c)
        return c
    }

    /** stage 1 = download, stage 2 = upload. Returns Mbps or null when nothing could be measured. */
    private fun measure(stage: Int, durationMs: Long, threads: Int): Double? {
        val counter = AtomicLong(0)
        val alive = AtomicInteger(0)
        val stop = AtomicBoolean(false)

        for (w in 0 until threads) {
            alive.incrementAndGet()
            val t = Thread {
                try {
                    if (stage == 1) downloadWorker(w, counter, stop) else uploadWorker(counter, stop)
                } finally {
                    alive.decrementAndGet()
                }
            }
            t.isDaemon = true
            t.start()
        }

        val t0 = SystemClock.elapsedRealtime()
        var lastBytes = 0L
        var lastT = t0
        var warmBytes = -1L
        var warmT = 0L
        var smooth = 0.0
        while (true) {
            Thread.sleep(250)
            if (cancelled) break
            val now = SystemClock.elapsedRealtime()
            val total = counter.get()
            val dt = (now - lastT) / 1000.0
            if (dt > 0.0) {
                val inst = (total - lastBytes) * 8.0 / dt / 1_000_000.0
                smooth = if (smooth == 0.0) inst else smooth * 0.6 + inst * 0.4
                val prog = ((now - t0).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                cb.onSpeed(stage, smooth, prog)
            }
            lastBytes = total
            lastT = now
            if (warmBytes < 0 && now - t0 >= WARMUP_MS) {
                warmBytes = total
                warmT = now
            }
            if (now - t0 >= durationMs) break
            if (alive.get() == 0) break
        }
        stop.set(true)
        closeConns()

        val now = SystemClock.elapsedRealtime()
        val total = counter.get()
        if (total <= 0L) return null
        return if (warmBytes >= 0 && now - warmT > 500) {
            (total - warmBytes) * 8.0 / ((now - warmT) / 1000.0) / 1_000_000.0
        } else {
            total * 8.0 / (maxOf(now - t0, 1L) / 1000.0) / 1_000_000.0
        }
    }

    private fun downloadWorker(index: Int, counter: AtomicLong, stop: AtomicBoolean) {
        var urlIdx = index % DOWN_URLS.size
        var failures = 0
        val buf = ByteArray(65536)
        while (!stop.get() && !cancelled && failures < 4) {
            var conn: HttpURLConnection? = null
            try {
                val c = open(DOWN_URLS[urlIdx], "GET")
                conn = c
                val code = c.responseCode
                if (code != 200) throw IOException("HTTP $code")
                val input = c.inputStream
                while (!stop.get() && !cancelled) {
                    val n = input.read(buf)
                    if (n < 0) break
                    counter.addAndGet(n.toLong())
                }
                input.close()
            } catch (e: Exception) {
                if (!stop.get() && !cancelled) {
                    failures++
                    urlIdx = (urlIdx + 1) % DOWN_URLS.size
                }
            } finally {
                if (conn != null) {
                    conns.remove(conn)
                    try {
                        conn.disconnect()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
    }

    private fun uploadWorker(counter: AtomicLong, stop: AtomicBoolean) {
        var failures = 0
        val buf = ByteArray(16384)
        Random().nextBytes(buf)
        while (!stop.get() && !cancelled && failures < 3) {
            var conn: HttpURLConnection? = null
            try {
                val c = open(UP_URL, "POST")
                conn = c
                c.doOutput = true
                c.setFixedLengthStreamingMode(UP_CHUNK)
                c.setRequestProperty("Content-Type", "application/octet-stream")
                val out = c.outputStream
                var sent = 0
                while (sent < UP_CHUNK && !stop.get() && !cancelled) {
                    val n = min(buf.size, UP_CHUNK - sent)
                    out.write(buf, 0, n)
                    sent += n
                    counter.addAndGet(n.toLong())
                }
                if (sent >= UP_CHUNK) {
                    out.flush()
                    val code = c.responseCode
                    if (code !in 200..299) throw IOException("HTTP $code")
                    c.inputStream.close()
                }
            } catch (e: Exception) {
                if (!stop.get() && !cancelled) failures++
            } finally {
                if (conn != null) {
                    conns.remove(conn)
                    try {
                        conn.disconnect()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
    }
}
