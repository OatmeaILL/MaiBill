package com.maibill.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地备份：把数据库（WAL 先合并）存到系统「下载/小睦记账备份/」，
 * 卸载重装后可从备份恢复。恢复会覆盖当前数据并需要重启应用。
 */
object Backup {
    private const val PREF = "backup"
    private const val DB = "maibill.db"
    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun lastAuto(ctx: Context): Long = sp(ctx).getLong("last_auto", 0L)

    suspend fun maybeAutoBackup(ctx: Context) {
        if (System.currentTimeMillis() - lastAuto(ctx) > 24 * 3600_000L) {
            try {
                backupNow(ctx, auto = true)
            } catch (_: Exception) {
            }
        }
    }

    suspend fun backupNow(ctx: Context, auto: Boolean): String? {
        // WAL 合并进主文件，保证备份完整
        try {
            Graph.db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        } catch (_: Exception) {
        }
        val src = ctx.getDatabasePath(DB)
        if (!src.exists()) return null
        val bytes = src.readBytes()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(Date())
        val name = "小睦记账备份_$stamp.db"
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
            cv.clear()
            cv.put(MediaStore.Downloads.IS_PENDING, 0)
            ctx.contentResolver.update(uri, cv, null, null)
        } else {
            val dir = File(ctx.getExternalFilesDir(null), "backup").apply { mkdirs() }
            File(dir, name).writeBytes(bytes)
        }
        if (auto) sp(ctx).edit().putLong("last_auto", System.currentTimeMillis()).apply()
        return name
    }

    /** 恢复数据库。返回 null 表示成功（调用方需重启应用），否则为错误信息。 */
    suspend fun restoreFrom(ctx: Context, uri: Uri): String? {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return "无法读取所选文件"
        if (bytes.size < 16 || !String(bytes, 0, 15, Charsets.US_ASCII).startsWith("SQLite format 3")) {
            return "所选文件不是有效的备份数据库"
        }
        try {
            Graph.db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        } catch (_: Exception) {
        }
        return try {
            val dbFile = ctx.getDatabasePath(DB)
            val tmp = File(dbFile.parentFile, "restore_tmp.db")
            tmp.writeBytes(bytes)
            Graph.db.close()
            File(dbFile.parentFile, "$DB-wal").delete()
            File(dbFile.parentFile, "$DB-shm").delete()
            if (!tmp.renameTo(dbFile)) {
                tmp.copyTo(dbFile, overwrite = true)
                tmp.delete()
            }
            null
        } catch (e: Exception) {
            e.message ?: "恢复失败"
        }
    }
}
