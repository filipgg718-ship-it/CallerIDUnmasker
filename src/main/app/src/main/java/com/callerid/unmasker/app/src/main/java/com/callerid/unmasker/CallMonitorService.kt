package com.callerid.unmasker

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import android.content.Intent
import com.callerid.unmasker.models.CallEntry

class CallMonitorService : InCallService() {

    companion object {
        const val TAG = "CallMonitorSvc"
        const val ACTION_CALL_UNMASKED = "com.callerid.unmasker.CALL_UNMASKED"

        private val KNOWN_NUMBER_KEYS = arrayOf(
            "com.android.phone.extra.ACTUAL_NUMBER",
            "android.telecom.extra.CALL_TARGET_NUMBER",
            "incoming_calling_number",
            "original_number",
            "original_caller_id",
            "real_caller_number",
            "clid_number",
            "actual_number",
            "com.android.phone.extra.CONNECTED_NUMBER",
            "com.sec.phone.extra.ORIGINAL_NUMBER",
            "com.xiaomi.phone.extra.CALLER_NUMBER",
            "com.oneplus.phone.extra.REAL_NUMBER",
            "oppo.phone.extra.REAL_NUMBER",
            "com.vivo.phone.extra.CALLER_NUMBER",
            "com.huawei.phone.extra.REAL_NUMBER",
            "P-Asserted-Identity",
            "P-Preferred-Identity"
        )
    }

    private val dbHelper by lazy { CallDatabaseHelper(this) }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added")

        val details = call.details
        val extras = details.extras
        val intentExtras = details.intentExtras

        val displayNumber = details.handle?.schemeSpecificPart ?: "Unknown"
        val isMasked = displayNumber.isNullOrBlank() ||
                displayNumber == "-1" ||
                displayNumber.equals("unknown", ignoreCase = true) ||
                displayNumber.equals("private", ignoreCase = true) ||
                displayNumber.equals("restricted", ignoreCase = true)

        var realNumber: String? = null
        val allExtras = mutableMapOf<String, String>()

        if (extras != null) {
            for (key in extras.keySet()) {
                val value = extras.get(key)
                allExtras[key] = value?.toString() ?: "null"

                if (realNumber == null) {
                    for (knownKey in KNOWN_NUMBER_KEYS) {
                        if (key.equals(knownKey, ignoreCase = true) ||
                            key.lowercase().contains(knownKey.lowercase())
                        ) {
                            val candidate = value?.toString()?.trim()
                            if (!candidate.isNullOrBlank() && candidate.length >= 4) {
                                val digits = candidate.filter { it.isDigit() || it == '+' }
                                if (digits.length >= 4) {
                                    realNumber = digits
                                    Log.i(TAG, "Found real number [$key]: $realNumber")
                                    break
                                }
                            }
                        }
                    }
                }
            }
        }

        if (realNumber == null && intentExtras != null) {
            for (key in intentExtras.keySet()) {
                val value = intentExtras.get(key)
                allExtras["intent:$key"] = value?.toString() ?: "null"

                for (knownKey in KNOWN_NUMBER_KEYS) {
                    if (key.equals(knownKey, ignoreCase = true) ||
                        key.lowercase().contains(knownKey.lowercase())
                    ) {
                        val candidate = value?.toString()?.trim()
                        if (!candidate.isNullOrBlank() && candidate.length >= 4) {
                            val digits = candidate.filter { it.isDigit() || it == '+' }
                            if (digits.length >= 4) {
                                realNumber = digits
                                Log.i(TAG, "Found real number [intent:$key]: $realNumber")
                                break
                            }
                        }
                    }
                }
            }
        }

        val wasUnmasked = isMasked && realNumber != null

        if (wasUnmasked) {
            Log.w(TAG, "*** UNMASKED: $realNumber (displayed as: $displayNumber) ***")
        }

        val entry = CallEntry(
            displayNumber = displayNumber,
            realNumber = realNumber,
            callerName = details.callerDisplayName,
            callTimestamp = System.currentTimeMillis(),
            callType = convertStateToCallLogType(call.state),
            durationSeconds = 0L,
            wasUnmasked = wasUnmasked,
            extrasBundleRaw = allExtras.entries.joinToString("\n") { "${it.key}=${it.value}" }
        )
        dbHelper.insertCall(entry)

        val intent = Intent(ACTION_CALL_UNMASKED).apply {
            putExtra("display_number", entry.displayNumber)
            putExtra("real_number", entry.realNumber)
            putExtra("caller_name", entry.callerName)
            putExtra("was_unmasked", entry.wasUnmasked)
            putExtra("timestamp", entry.callTimestamp)
        }
        sendBroadcast(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {}
    override fun onCanAddCallChanged(canAddCall: Boolean) {}

    private fun convertStateToCallLogType(state: Int): Int {
        return when (state) {
            Call.STATE_RINGING, Call.STATE_CONNECTING, Call.STATE_ACTIVE -> 
                android.provider.CallLog.Calls.INCOMING_TYPE
            Call.STATE_DIALING -> android.provider.CallLog.Calls.OUTGOING_TYPE
            else -> android.provider.CallLog.Calls.INCOMING_TYPE
        }
    }
}
