package com.callerid.unmasker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.callerid.unmasker.models.CallEntry

class CallDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "unmasked_calls.db"
        const val DATABASE_VERSION = 2
        const val TABLE_CALLS = "calls"
        const val COL_ID = "id"
        const val COL_DISPLAY_NUMBER = "display_number"
        const val COL_REAL_NUMBER = "real_number"
        const val COL_CALLER_NAME = "caller_name"
        const val COL_TIMESTAMP = "call_timestamp"
        const val COL_CALL_TYPE = "call_type"
        const val COL_DURATION = "duration"
        const val COL_WAS_UNMASKED = "was_unmasked"
        const val COL_EXTRAS_RAW = "extras_raw"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_CALLS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DISPLAY_NUMBER TEXT NOT NULL,
                $COL_REAL_NUMBER TEXT,
                $COL_CALLER_NAME TEXT,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_CALL_TYPE INTEGER NOT NULL DEFAULT 1,
                $COL_DURATION INTEGER NOT NULL DEFAULT 0,
                $COL_WAS_UNMASKED INTEGER NOT NULL DEFAULT 0,
                $COL_EXTRAS_RAW TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CALLS")
        onCreate(db)
    }

    fun insertCall(entry: CallEntry): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_DISPLAY_NUMBER, entry.displayNumber)
            put(COL_REAL_NUMBER, entry.realNumber)
            put(COL_CALLER_NAME, entry.callerName)
            put(COL_TIMESTAMP, entry.callTimestamp)
            put(COL_CALL_TYPE, entry.callType)
            put(COL_DURATION, entry.durationSeconds)
            put(COL_WAS_UNMASKED, if (entry.wasUnmasked) 1 else 0)
            put(COL_EXTRAS_RAW, entry.extrasBundleRaw)
        }
        return db.insert(TABLE_CALLS, null, values)
    }

    fun getAllCalls(): List<CallEntry> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CALLS, null, null, null, null, null,
            "$COL_TIMESTAMP DESC", "200"
        )
        val calls = mutableListOf<CallEntry>()
        cursor.use {
            while (it.moveToNext()) {
                calls.add(CallEntry(
                    id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                    displayNumber = it.getString(it.getColumnIndexOrThrow(COL_DISPLAY_NUMBER)),
                    realNumber = it.getString(it.getColumnIndexOrThrow(COL_REAL_NUMBER)),
                    callerName = it.getString(it.getColumnIndexOrThrow(COL_CALLER_NAME)),
                    callTimestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                    callType = it.getInt(it.getColumnIndexOrThrow(COL_CALL_TYPE)),
                    durationSeconds = it.getLong(it.getColumnIndexOrThrow(COL_DURATION)),
                    wasUnmasked = it.getInt(it.getColumnIndexOrThrow(COL_WAS_UNMASKED)) == 1,
                    extrasRaw = it.getString(it.getColumnIndexOrThrow(COL_EXTRAS_RAW))
                ))
            }
        }
        return calls
    }

    fun deleteAll() {
        val db = writableDatabase
        db.delete(TABLE_CALLS, null, null)
    }
}
