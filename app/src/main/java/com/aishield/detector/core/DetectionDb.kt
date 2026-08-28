package com.aishield.detector.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local record of every analyzed piece of content.
 *
 * Per client spec, results between logThreshold and alertThreshold are stored
 * silently (LOG_SILENT) and never surface in the UI while scrolling; they are
 * visible only in History for transparency.
 */
class DetectionDb(context: Context) :
    SQLiteOpenHelper(context, "detections.db", null, 1) {

    data class DetectionRow(
        val id: Long,
        val packageName: String,
        val timeMs: Long,
        val visual: Double,
        val deepfake: Double?,
        val audio: Double?,
        val c2pa: String,
        val overall: Double,
        val action: String,
        val thumbPath: String?
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE detections(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name TEXT NOT NULL,
                time_ms INTEGER NOT NULL,
                visual REAL NOT NULL,
                deepfake REAL,
                audio REAL,
                c2pa TEXT,
                overall REAL NOT NULL,
                action TEXT NOT NULL,
                thumb_path TEXT
            )"""
        )
        db.execSQL("CREATE INDEX idx_time ON detections(time_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS detections")
        onCreate(db)
    }

    fun insert(row: DetectionRow): Long {
        val cv = ContentValues().apply {
            put("package_name", row.packageName)
            put("time_ms", row.timeMs)
            put("visual", row.visual)
            if (row.deepfake != null) put("deepfake", row.deepfake) else putNull("deepfake")
            if (row.audio != null) put("audio", row.audio) else putNull("audio")
            put("c2pa", row.c2pa)
            put("overall", row.overall)
            put("action", row.action)
            if (row.thumbPath != null) put("thumb_path", row.thumbPath) else putNull("thumb_path")
        }
        return writableDatabase.insert("detections", null, cv)
    }

    fun byId(id: Long): DetectionRow? =
        readableDatabase.query(
            "detections", null, "id=?", arrayOf(id.toString()),
            null, null, null
        ).use { it.moveToFirstRow() }

    fun recentAlerts(limit: Int = 10): List<DetectionRow> = queryList("action='ALERT'", limit)

    fun all(limit: Int = 500): List<DetectionRow> = queryList(null, limit)

    private fun queryList(where: String?, limit: Int): List<DetectionRow> =
        readableDatabase.query(
            "detections", null, where, null, null, null, "time_ms DESC", "$limit"
        ).use { cur ->
            val out = ArrayList<DetectionRow>()
            while (cur.moveToNext()) cur.toRow()?.let { out.add(it) }
            out
        }

    /** @return Pair(scannedToday, alertedToday) */
    fun todayCounts(): Pair<Int, Int> {
        val startOfDay = System.currentTimeMillis() - (System.currentTimeMillis() % 86_400_000)
        var scanned = 0
        var alerted = 0
        readableDatabase.query(
            "detections",
            arrayOf("action"),
            "time_ms >= ?", arrayOf(startOfDay.toString()),
            null, null, null
        ).use { cur ->
            while (cur.moveToNext()) {
                scanned++
                if (cur.getString(0) == "ALERT") alerted++
            }
        }
        return scanned to alerted
    }

    private fun android.database.Cursor.moveToFirstRow(): DetectionRow? =
        if (moveToFirst()) toRow() else null

    private fun android.database.Cursor.toRow(): DetectionRow? {
        val df = getColumnIndexOrThrow("deepfake")
        val af = getColumnIndexOrThrow("audio")
        val tf = getColumnIndexOrThrow("thumb_path")
        return DetectionRow(
            id = getLong(getColumnIndexOrThrow("id")),
            packageName = getString(getColumnIndexOrThrow("package_name")),
            timeMs = getLong(getColumnIndexOrThrow("time_ms")),
            visual = getDouble(getColumnIndexOrThrow("visual")),
            deepfake = if (isNull(df)) null else getDouble(df),
            audio = if (isNull(af)) null else getDouble(af),
            c2pa = getString(getColumnIndexOrThrow("c2pa")) ?: "NOT_DETECTED",
            overall = getDouble(getColumnIndexOrThrow("overall")),
            action = getString(getColumnIndexOrThrow("action")),
            thumbPath = if (isNull(tf)) null else getString(tf)
        )
    }
}
