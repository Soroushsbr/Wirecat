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
        val context = holder.itemView.context
        holder.type.text = entry.type.name

        val directionLabel = when (entry.direction) {
            PacketDirection.SENT -> context.getString(R.string.log_direction_sent)
            PacketDirection.RECEIVED -> context.getString(R.string.log_direction_received)
            PacketDirection.BLOCKED -> context.getString(R.string.log_direction_blocked)
        }
        holder.direction.text = if (entry.appLabel != null) {
            context.getString(R.string.log_direction_with_app, directionLabel, entry.appLabel)
        } else {
            directionLabel
        }
        holder.route.text = formattedRoute(context, entry)
        holder.time.text = entry.formattedTime()

        val expanded = expandedIds.contains(entry.id)
        holder.detailsSection.visibility = if (expanded) View.VISIBLE else View.GONE
        holder.chevron.rotation = if (expanded) 270f else 90f

        val unknown = context.getString(R.string.unknown)
        holder.source.text = context.getString(R.string.log_field_source, formattedEndpoint(entry.sourceAddress, entry.sourcePort, unknown))
        holder.dest.text = context.getString(R.string.log_field_destination, formattedEndpoint(entry.destAddress, entry.destPort, unknown))
        holder.protocol.text = context.getString(R.string.log_field_protocol, entry.type.name)
        holder.ipVersion.text = context.getString(R.string.log_field_ip_version, formattedIpVersion(entry.ipVersion).ifBlank { unknown })
        holder.directionDetail.text = context.getString(R.string.log_field_direction, formattedDirectionDetail(context, entry.direction))
        holder.size.text = context.getString(R.string.log_field_size, if (entry.sizeBytes != null) context.getString(R.string.bytes_format, entry.sizeBytes) else unknown)
        holder.app.text = context.getString(R.string.log_field_app, entry.appLabel ?: unknown)
        holder.fullTime.text = context.getString(R.string.log_field_time, entry.formattedFullTime())

        holder.headerRow.setOnClickListener {
            if (expandedIds.contains(entry.id)) expandedIds.remove(entry.id) else expandedIds.add(entry.id)
            notifyItemChanged(position)
        }
    }

    private fun formattedEndpoint(address: String?, port: Int?, unknown: String): String {
        if (address == null) return unknown
        return if (port != null) "$address:$port" else address
    }

    private fun formattedRoute(context: android.content.Context, entry: PacketLogEntry): String {
        val src = entry.sourceAddress ?: return context.getString(R.string.unknown_source_destination)
        val dst = entry.destAddress ?: return src
        val srcLabel = if (entry.sourcePort != null) "$src:${entry.sourcePort}" else src
        val dstLabel = if (entry.destPort != null) "$dst:${entry.destPort}" else dst
        return "$srcLabel \u2192 $dstLabel"
    }

    private fun formattedIpVersion(ipVersion: Int?): String = when (ipVersion) {
        4 -> "IPv4"
        6 -> "IPv6"
        else -> ""
    }

    private fun formattedDirectionDetail(context: android.content.Context, direction: PacketDirection): String = when (direction) {
        PacketDirection.SENT -> context.getString(R.string.direction_sent_detail)
        PacketDirection.RECEIVED -> context.getString(R.string.direction_received_detail)
        PacketDirection.BLOCKED -> context.getString(R.string.direction_blocked_detail)
    }

    override fun getItemCount(): Int = items.size
}
