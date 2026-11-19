package com.ankan.netfreeze

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class FileEntry(val file: File, val size: Long, val modified: Long = 0L) {
    var selected = false
    var cat = 0
}

class ScanResult {
    val junk = ArrayList<FileEntry>()
    val emptyDirs = ArrayList<File>()
    var junkBytes = 0L
    /** 0 = temp & log files, 1 = cache files, 2 = thumbnails, 3 = zero-byte files */
    val catBytes = LongArray(4)
    val catCount = IntArray(4)
    val apks = ArrayList<FileEntry>()
    var apkBytes = 0L
    val large = ArrayList<FileEntry>()
    val old = ArrayList<FileEntry>()
    val mid = ArrayList<FileEntry>()
    val images = ArrayList<FileEntry>()
    val videos = ArrayList<FileEntry>()
    val audio = ArrayList<FileEntry>()
    val docs = ArrayList<FileEntry>()
    val archives = ArrayList<FileEntry>()
    var imageBytes = 0L
    var videoBytes = 0L
    var audioBytes = 0L
    var docBytes = 0L
    var archiveBytes = 0L
    var files = 0
    var scannedBytes = 0L
    var whatsappBytes = 0L
    var telegramBytes = 0L
}

/** Finds junk, cache, zero-byte and big files on shared storage and deletes what the user selects. */
object Cleaner {
    @Volatile
    var last: ScanResult? = null

    val CAT_NAMES = arrayOf("Temp & log files", "Cache files", "Thumbnails", "Empty (0 KB) files")

    private val JUNK_EXT = setOf("tmp", "temp", "log", "bak", "old", "cache", "crdownload", "part", "dmp", "chk", "trace", "thumb")
    private val CACHE_DIRS = setOf(".cache", "cache", "caches", "code_cache", ".tmp", "tmp", "temp")
    private val THUMB_DIRS = setOf(".thumbnails", "thumbnails", ".thumbs", "thumbs")
    private val JUNK_DIRS = setOf("lost.dir", ".trash", ".trashed", ".recyclebin")
    private val IMG = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")
    private val VID = setOf("mp4", "mkv", "3gp", "webm", "avi", "mov", "m4v")
    private val AUD = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "opus", "amr")
    private val DOC = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "csv")
    private val ARC = setOf("zip", "rar", "7z", "tar", "gz")
    private const val LARGE = 100L * 1024L * 1024L
    private const val OLD_MIN = 50L * 1024L * 1024L
    private const val OLD_AGE = 90L * 24L * 3600L * 1000L

    fun root(): File = Environment.getExternalStorageDirectory()

    private fun keep(list: ArrayList<FileEntry>, e: FileEntry) {
        list.add(e)
        if (list.size > 400) {
            list.sortByDescending { it.size }
            while (list.size > 200) list.removeAt(list.size - 1)
        }
    }

    fun scan(root: File, cancel: AtomicBoolean, progress: (Int, Long) -> Unit): ScanResult {
        val r = ScanResult()
        val skipData = File(root, "Android/data").path
        val skipObb = File(root, "Android/obb").path
        val androidDir = File(root, "Android").path
        val now = System.currentTimeMillis()
        val stack = ArrayList<Pair<File, Boolean>>()
        stack.add(Pair(root, false))
        while (stack.isNotEmpty() && !cancel.get()) {
            val node = stack.removeAt(stack.size - 1)
            val dir = node.first
            val dname = dir.name.lowercase()
            val inCache = node.second || dname in CACHE_DIRS
            val list: Array<File>? = try {
                dir.listFiles()
            } catch (e: Exception) {
                null
            }
            if (list == null) continue
            if (list.isEmpty()) {
                if (dir.path != root.path && !dir.path.startsWith(androidDir)) r.emptyDirs.add(dir)
                continue
            }
            val inThumb = dname in THUMB_DIRS
            val inJunkDir = dname in JUNK_DIRS
            for (f in list) {
                if (f.isDirectory) {
                    if (f.path == skipData || f.path == skipObb) continue
                    stack.add(Pair(f, inCache))
                    continue
                }
                r.files++
                val size = f.length()
                r.scannedBytes += size
                if (r.files % 400 == 0) progress(r.files, r.scannedBytes)
                val name = f.name.lowercase()
                val ext = name.substringAfterLast('.', "")
                var cat = -1
                if (name != ".nomedia") {
                    cat = when {
                        inCache -> 1
                        inThumb -> 2
                        size == 0L -> 3
                        ext in JUNK_EXT || inJunkDir || name == "thumbs.db" -> 0
                        else -> -1
                    }
                }
                if (cat >= 0) {
                    r.junkBytes += size
                    r.catBytes[cat] += size
                    r.catCount[cat]++
                    if (r.junk.size < 30000) {
                        val e = FileEntry(f, size, f.lastModified())
                        e.cat = cat
                        r.junk.add(e)
                    }
                    continue
                }
                val mod = f.lastModified()
                if (size >= LARGE) keep(r.large, FileEntry(f, size, mod))
                if (size >= OLD_MIN && now - mod > OLD_AGE && mod > 0L) keep(r.old, FileEntry(f, size, mod))
                if (size >= 1024L * 1024L && r.mid.size < 60000) r.mid.add(FileEntry(f, size, mod))
                when {
                    ext == "apk" -> { r.apkBytes += size; keep(r.apks, FileEntry(f, size, mod)) }
                    ext in IMG -> { r.imageBytes += size; keep(r.images, FileEntry(f, size, mod)) }
                    ext in VID -> { r.videoBytes += size; keep(r.videos, FileEntry(f, size, mod)) }
                    ext in AUD -> { r.audioBytes += size; keep(r.audio, FileEntry(f, size, mod)) }
                    ext in DOC -> { r.docBytes += size; keep(r.docs, FileEntry(f, size, mod)) }
                    ext in ARC -> { r.archiveBytes += size; keep(r.archives, FileEntry(f, size, mod)) }
                    else -> { }
                }
            }
        }
        for (l in listOf(r.apks, r.large, r.old, r.images, r.videos, r.audio, r.docs, r.archives)) {
            l.sortByDescending { it.size }
        }
        r.junk.sortByDescending { it.size }
        r.whatsappBytes = mediaDirs(root, 0).sumOf { dirSize(it) }
        r.telegramBytes = mediaDirs(root, 1).sumOf { dirSize(it) }
        return r
    }

    /** Media folders of a chat app. 0 = WhatsApp, 1 = Telegram. */
    fun mediaDirs(root: File, app: Int): List<File> {
        val bases = if (app == 0) {
            listOf(
                File(root, "Android/media/com.whatsapp/WhatsApp/Media"),
                File(root, "WhatsApp/Media"),
                File(root, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media")
            )
        } else {
            listOf(File(root, "Telegram"), File(root, "Android/media/org.telegram.messenger/Telegram"))
        }
        val out = ArrayList<File>()
        for (b in bases) {
            val kids: Array<File>? = try {
                b.listFiles()
            } catch (e: Exception) {
                null
            }
            if (kids != null) {
                for (k in kids) if (k.isDirectory) out.add(k)
            }
        }
        return out
    }

    fun dirSize(f: File): Long {
        if (f.isFile) return f.length()
        var n = 0L
        val kids: Array<File>? = try {
            f.listFiles()
        } catch (e: Exception) {
            null
        }
        if (kids != null) {
            for (k in kids) n += dirSize(k)
        }
        return n
    }

    /** Deletes everything inside a folder but keeps the folder. Returns freed bytes. */
    fun clearDir(f: File): Long {
        var freed = 0L
        val kids: Array<File>? = try {
            f.listFiles()
        } catch (e: Exception) {
            null
        }
        if (kids != null) {
            for (k in kids) {
                if (k.isDirectory) {
                    freed += clearDir(k)
                    try {
                        k.delete()
                    } catch (e: Exception) {
                        // ignore
                    }
                } else {
                    try {
                        val s = k.length()
                        if (k.delete()) freed += s
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
        return freed
    }

    fun delete(entries: List<FileEntry>): Long {
        var freed = 0L
        for (e in entries) {
            try {
                if (e.file.delete()) freed += e.size
            } catch (ex: Exception) {
                // ignore
            }
        }
        return freed
    }

    fun deleteEmptyDirs(dirs: List<File>) {
        for (d in dirs) {
            try {
                d.delete()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun clearOwnCache(ctx: Context): Long {
        var n = clearDir(ctx.cacheDir)
        val ext = ctx.externalCacheDir
        if (ext != null) n += clearDir(ext)
        return n
    }

    /** How much cached data other apps hold that Android can clear for us (0 when unknown). */
    fun estimateSystemCache(ctx: Context): Long {
        if (Build.VERSION.SDK_INT < 26) return 0L
        return try {
            val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            (sm.getAllocatableBytes(StorageManager.UUID_DEFAULT) - Sys.storage().free).coerceAtLeast(0L)
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Clears the cache of every app. Android only lets other apps' cache be cleared by the system itself:
     * we ask it for almost all free space, it clears caches to make room, then we give the space back.
     * Returns the free space gained.
     */
    fun clearSystemCaches(ctx: Context): Long {
        if (Build.VERSION.SDK_INT < 26) return 0L
        val before = Sys.storage().free
        try {
            val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val allocatable = sm.getAllocatableBytes(StorageManager.UUID_DEFAULT)
            val margin = 6L * 1024L * 1024L
            if (allocatable - before <= margin) return 0L
            val f = File(ctx.filesDir, "nf_alloc.tmp")
            val fos = FileOutputStream(f)
            try {
                sm.allocateBytes(fos.fd, allocatable - margin)
            } finally {
                try {
                    fos.close()
                } catch (e: Exception) {
                    // ignore
                }
                f.delete()
            }
        } catch (e: Exception) {
            // the system refused; nothing was changed
        }
        return (Sys.storage().free - before).coerceAtLeast(0L)
    }

    /** Android 10 and older: cache folders of other apps are readable with storage permission. */
    fun clearLegacyAppCaches(root: File): Long {
        if (Build.VERSION.SDK_INT >= 30) return 0L
        var freed = 0L
        val apps: Array<File>? = try {
            File(root, "Android/data").listFiles()
        } catch (e: Exception) {
            null
        }
        if (apps != null) {
            for (a in apps) freed += clearDir(File(a, "cache"))
        }
        return freed
    }
}
