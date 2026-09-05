package com.soroush.wirecat.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.soroush.wirecat.R
import com.soroush.wirecat.data.PacketDirection
import com.soroush.wirecat.data.PacketLogEntry

class LogAdapter : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    private var allItems: List<PacketLogEntry> = emptyList()
    private var items: List<PacketLogEntry> = emptyList()
    private var query: String = ""

    private val expandedIds = HashSet<Long>()

    fun submitList(newItems: List<PacketLogEntry>) {
        allItems = newItems
        applyFilter()
    }

    fun filter(newQuery: String) {
        query = newQuery
        applyFilter()
    }

    private fun applyFilter() {
        items = if (query.isBlank()) {
            allItems
        } else {
            val q = query.trim()
            allItems.filter { entry ->
                (entry.appLabel?.contains(q, ignoreCase = true) == true) ||
                    entry.type.name.contains(q, ignoreCase = true) ||
                    (entry.sourceAddress?.contains(q, ignoreCase = true) == true) ||
                    (entry.destAddress?.contains(q, ignoreCase = true) == true)
            }
        }
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerRow: View = view.findViewById(R.id.logHeaderRow)
        val type: TextView = view.findViewById(R.id.textLogType)
        val direction: TextView = view.findViewById(R.id.textLogDirection)
        val route: TextView = view.findViewById(R.id.textLogRoute)
        val time: TextView = view.findViewById(R.id.textLogTime)
        val chevron: ImageView = view.findViewById(R.id.imgLogChevron)
        val detailsSection: View = view.findViewById(R.id.logDetailsSection)
        val source: TextView = view.findViewById(R.id.textLogSource)
        val dest: TextView = view.findViewById(R.id.textLogDest)
        val protocol: TextView = view.findViewById(R.id.textLogProtocol)
        val ipVersion: TextView = view.findViewById(R.id.textLogIpVersion)
        val directionDetail: TextView = view.findViewById(R.id.textLogDirectionDetail)
        val size: TextView = view.findViewById(R.id.textLogSize)
        val app: TextView = view.findViewById(R.id.textLogApp)
        val fullTime: TextView = view.findViewById(R.id.textLogFullTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        holder.type.text = entry.type.name

        val directionLabel = when (entry.direction) {
            PacketDirection.SENT -> "Sent"
            PacketDirection.RECEIVED -> "Received"
            PacketDirection.BLOCKED -> "Blocked"
        }
        holder.direction.text = if (entry.appLabel != null) "$directionLabel \u2022 ${entry.appLabel}" else directionLabel
        holder.route.text = entry.formattedRoute()
        holder.time.text = entry.formattedTime()

        val expanded = expandedIds.contains(entry.id)
        holder.detailsSection.visibility = if (expanded) View.VISIBLE else View.GONE
        holder.chevron.rotation = if (expanded) 270f else 90f

        holder.source.text = "Source: ${entry.formattedSource()}"
        holder.dest.text = "Destination: ${entry.formattedDest()}"
        holder.protocol.text = "Protocol: ${entry.type.name}"
        holder.ipVersion.text = "IP version: ${entry.formattedIpVersion().ifBlank { "Unknown" }}"
        holder.directionDetail.text = "Direction: ${entry.formattedDirection()}"
        holder.size.text = "Size: ${entry.formattedSize()}"
        holder.app.text = "App: ${entry.appLabel ?: "Unknown"}"
        holder.fullTime.text = "Time: ${entry.formattedFullTime()}"

        holder.headerRow.setOnClickListener {
            if (expandedIds.contains(entry.id)) expandedIds.remove(entry.id) else expandedIds.add(entry.id)
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = items.size
}
