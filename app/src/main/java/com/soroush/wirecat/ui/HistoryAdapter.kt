package com.soroush.wirecat.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.soroush.wirecat.R
import com.soroush.wirecat.data.SessionType
import com.soroush.wirecat.data.SessionWithApps
import com.soroush.wirecat.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onClick: (SessionWithApps) -> Unit,
    private val onDelete: (SessionWithApps) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var items: List<SessionWithApps> = emptyList()
    private val dateFormat = SimpleDateFormat("MMM d, yyyy - HH:mm", Locale.US)

    fun submitList(newItems: List<SessionWithApps>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val type: TextView = view.findViewById(R.id.textSessionType)
        val date: TextView = view.findViewById(R.id.textSessionDate)
        val summary: TextView = view.findViewById(R.id.textSessionSummary)
        val delete: ImageButton = view.findViewById(R.id.btnDeleteSession)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val isTunnel = item.session.type == SessionType.TUNNEL
        holder.type.text = context.getString(if (isTunnel) R.string.history_type_tunnel else R.string.history_type_monitor)
        holder.date.text = dateFormat.format(Date(item.session.startTimeMillis))
        val duration = item.session.endTimeMillis - item.session.startTimeMillis
        val totalLabel = if (isTunnel) {
            context.getString(R.string.history_packets_blocked, item.session.totalBytes)
        } else {
            context.getString(R.string.history_total_bytes, FormatUtils.formatBytes(item.session.totalBytes))
        }
        holder.summary.text = context.getString(
            R.string.history_summary_full, totalLabel, FormatUtils.formatDuration(duration), item.packetCount
        )
        holder.itemView.setOnClickListener { onClick(item) }
        holder.delete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount(): Int = items.size
}
