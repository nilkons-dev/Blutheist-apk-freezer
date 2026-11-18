package com.ankan.netfreeze

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class FileEntry(val file: File, val size: Long) {
    var selected = false
}

class ScanResult {
    val junk = ArrayList<FileEntry>()
    val emptyDirs = ArrayList<File>()
    var junkBytes = 0L
    val apks = ArrayList<FileEntry>()
    var apkBytes = 0L
    val large = ArrayList<FileEntry>()
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
    var whatsappBytes = 0L
    var telegramBytes = 0L
}

/** Finds junk and big files on shared storage and deletes what the user selects. */
object Cleaner {
    @Volatile
    var last: ScanResult? = null

    private val JUNK_EXT = setOf("tmp", "temp", "log", "bak", "old", "cache", "crdownload", "part", "thumb", "dmp", "chk", "trace")
    private val JUNK_DIRS = setOf(".thumbnails", "lost.dir", ".trash", ".trashed", "thumbnails", ".cache")
    private val IMG = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")
    private val VID = setOf("mp4", "mkv", "3gp", "webm", "avi", "mov", "m4v")
    private val AUD = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac", "opus", "amr")
    private val DOC = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "csv")
    private val ARC = setOf("zip", "rar", "7z", "tar", "gz")
    private const val LARGE = 100L * 1024L * 1024L

    fun root(): File = Environment.getExternalStorageDirectory()

    private fun keep(list: ArrayList<FileEntry>, e: FileEntry) {
        list.add(e)
        if (list.size > 400) {
            list.sortByDescending { it.size }
            while (list.size > 200) list.removeAt(list.size - 1)
        }
    }

    fun scan(root: File, cancel: AtomicBoolean, progress: (Int) -> Unit): ScanResult {
        val r = ScanResult()
        val skipData = File(root, "Android/data").path
        val skipObb = File(root, "Android/obb").path
        val androidDir = File(root, "Android").path
        val stack = ArrayList<File>()
        stack.add(root)
        while (stack.isNotEmpty() && !cancel.get()) {
            val dir = stack.removeAt(stack.size - 1)
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
            val inJunkDir = dir.name.lowercase() in JUNK_DIRS
            for (f in list) {
                if (f.isDirectory) {
                    if (f.path == skipData || f.path == skipObb) continue
                    stack.add(f)
                    continue
                }
                r.files++
                if (r.files % 400 == 0) progress(r.files)
                val size = f.length()
                val name = f.name.lowercase()
                val ext = name.substringAfterLast('.', "")
                val isJunk = name != ".nomedia" && (size == 0L || ext in JUNK_EXT || inJunkDir || name == "thumbs.db")
                if (isJunk) {
                    r.junkBytes += size
                    if (r.junk.size < 20000) r.junk.add(FileEntry(f, size))
                    continue
                }
                if (size >= LARGE) keep(r.large, FileEntry(f, size))
                when {
                    ext == "apk" -> { r.apkBytes += size; keep(r.apks, FileEntry(f, size)) }
                    ext in IMG -> { r.imageBytes += size; keep(r.images, FileEntry(f, size)) }
                    ext in VID -> { r.videoBytes += size; keep(r.videos, FileEntry(f, size)) }
                    ext in AUD -> { r.audioBytes += size; keep(r.audio, FileEntry(f, size)) }
                    ext in DOC -> { r.docBytes += size; keep(r.docs, FileEntry(f, size)) }
                    ext in ARC -> { r.archiveBytes += size; keep(r.archives, FileEntry(f, size)) }
                    else -> { }
                }
            }
        }
        for (l in listOf(r.apks, r.large, r.images, r.videos, r.audio, r.docs, r.archives)) {
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
}
