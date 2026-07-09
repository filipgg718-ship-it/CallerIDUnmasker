package com.callerid.unmasker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.callerid.unmasker.models.CallEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UnmaskedCallAdapter(
    private val context: Context,
    private var entries: List<CallEntry>
) : RecyclerView.Adapter<UnmaskedCallAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val displayNumber: TextView = view.findViewById(R.id.tv_display_number)
        val realNumber: TextView = view.findViewById(R.id.tv_real_number)
        val callerName: TextView = view.findViewById(R.id.tv_caller_name)
        val timestamp: TextView = view.findViewById(R.id.tv_timestamp)
        val statusBadge: TextView = view.findViewById(R.id.tv_status_badge)
        val extrasInfo: TextView = view.findViewById(R.id.tv_extras_info)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_call_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]

        holder.displayNumber.text = if (entry.wasUnmasked) {
            "Displayed: ${entry.displayNumber}"
        } else {
            "Number: ${entry.displayNumber}"
        }

        if (entry.wasUnmasked && entry.realNumber != null) {
            holder.realNumber.text = "\uD83D\uDD13 Real caller: ${entry.realNumber}"
            holder.realNumber.visibility = View.VISIBLE
        } else {
            holder.realNumber.visibility = View.GONE
        }

        holder.callerName.text = if (!entry.callerName.isNullOrBlank()) {
            "Name: ${entry.callerName}"
        } else { "" }

        holder.timestamp.text = dateFormat.format(Date(entry.callTimestamp))

        if (entry.wasUnmasked) {
            holder.statusBadge.text = "UNMASKED"
            holder.statusBadge.setBackgroundColor(
                context.getColor(android.R.color.holo_red_dark)
            )
            holder.statusBadge.visibility = View.VISIBLE
        } else {
            holder.statusBadge.visibility = View.GONE
        }

        if (!entry.extrasBundleRaw.isNullOrBlank()) {
            holder.extrasInfo.text = "Extras: ${entry.extrasBundleRaw.take(150)}..."
            holder.extrasInfo.visibility = View.VISIBLE
        } else {
            holder.extrasInfo.visibility = View.GONE
        }
    }

    override fun getItemCount() = entries.size

    fun updateData(newEntries: List<CallEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }
}
