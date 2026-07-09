package com.callerid.unmasker

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import com.callerid.unmasker.models.CallEntry

class CallLogReader(private val context: Context) {

    fun readCallLog(): List<CallEntry> {
        val entries = mutableListOf<CallEntry>()
        val resolver = context.contentResolver

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )

        val cursor: Cursor? = resolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC LIMIT 200"
        )

        cursor?.use { c ->
            val idIdx = c.getColumnIndex(CallLog.Calls._ID)
            val numIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
            val durIdx = c.getColumnIndex(CallLog.Calls.DURATION)
            val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)

            while (c.moveToNext()) {
                val displayNumber = if (numIdx >= 0) c.getString(numIdx) else ""
                val name = if (nameIdx >= 0) c.getString(nameIdx) else null
                val timestamp = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                val duration = if (durIdx >= 0) c.getLong(durIdx) else 0L
                val callType = if (typeIdx >= 0) c.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE

                val isMasked = displayNumber.isNullOrBlank() ||
                        displayNumber == "-1" ||
                        displayNumber.equals("unknown", ignoreCase = true) ||
                        displayNumber.equals("private", ignoreCase = true) ||
                        displayNumber.equals("restricted", ignoreCase = true) ||
                        displayNumber.equals("blocked", ignoreCase = true)

                var realNumber: String? = null

                val hiddenColumns = arrayOf(
                    "real_number", "original_number", "actual_number",
                    "clid_number", "incoming_number", "original_caller_id"
                )
                for (col in hiddenColumns) {
                    if (realNumber == null) {
                        try {
                            val val = c.getString(c.getColumnIndexOrThrow(col))
                            if (!val.isNullOrBlank()) realNumber = val
                        } catch (_: Exception) {}
                    }
                }

                val wasUnmasked = isMasked && realNumber != null && realNumber != displayNumber

                entries.add(CallEntry(
                    id = if (idIdx >= 0) c.getLong(idIdx) else 0L,
                    displayNumber = displayNumber,
                    realNumber = if (wasUnmasked) realNumber else null,
                    callerName = name,
                    callTimestamp = timestamp,
                    callType = callType,
                    durationSeconds = duration,
                    wasUnmasked = wasUnmasked
                ))
            }
        }
        return entries
    }
}
