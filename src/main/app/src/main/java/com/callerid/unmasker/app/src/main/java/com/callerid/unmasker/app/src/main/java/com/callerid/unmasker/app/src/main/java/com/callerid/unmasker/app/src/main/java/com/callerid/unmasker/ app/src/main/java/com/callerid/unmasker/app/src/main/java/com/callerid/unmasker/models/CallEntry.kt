package com.callerid.unmasker.models

data class CallEntry(
    val id: Long = 0,
    val displayNumber: String,
    val realNumber: String?,
    val callerName: String?,
    val callTimestamp: Long,
    val callType: Int,
    val durationSeconds: Long,
    val wasUnmasked: Boolean,
    val extrasBundleRaw: String? = null
)
