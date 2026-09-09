package com.soroush.wirecat.ui

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.soroush.wirecat.R
import com.soroush.wirecat.data.LiveAppUsage
import com.soroush.wirecat.util.FormatUtils

class AppUsageAdapter(
    private val packageManager: PackageManager,
    private val selectable: Boolean = false,
    private var selected: MutableSet<String> = mutableSetOf(),
    private val onSelectionChanged: ((Set<String>) -> Unit)? = null
) : RecyclerView.Adapter<AppUsageAdapter.ViewHolder>() {

    private var allItems: List<LiveAppUsage> = emptyList()
    private var items: List<LiveAppUsage> = emptyList()
    private var totalBytes: Long = 1L
    private var query: String = ""

    fun submitList(newItems: List<LiveAppUsage>) {
        allItems = newItems
        totalBytes = newItems.sumOf { it.bytes }.coerceAtLeast(1L)
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
            allItems.filter { it.appLabel.contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }

    fun setSelected(newSelected: Set<String>) {
        selected = newSelected.toMutableSet()
        notifyDataSetChanged()
    }

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val icon: android.widget.ImageView = view.findViewById(R.id.imgAppIcon)
        val name: android.widget.TextView = view.findViewById(R.id.textAppName)
        val usage: android.widget.TextView = view.findViewById(R.id.textAppUsage)
        val checkbox: android.widget.CheckBox = view.findViewById(R.id.checkboxSelect)
        val barFill: android.view.View = view.findViewById(R.id.usageBarFill)
        val barSpacer: android.view.View = view.findViewById(R.id.usageBarSpacer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_usage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.appLabel
        val context = holder.itemView.context
        val percent = ((item.bytes.toDouble() / totalBytes.toDouble()) * 100).let {
            if (it < 1.0 && item.bytes > 0) context.getString(R.string.usage_percent_lt_one)
            else context.getString(R.string.usage_percent_format, it.toInt())
        }
        holder.usage.text = context.getString(R.string.usage_summary_format, FormatUtils.formatBytes(item.bytes), percent)
        try {
            holder.icon.setImageDrawable(packageManager.getApplicationIcon(item.packageName))
        } catch (e: PackageManager.NameNotFoundException) {
            holder.icon.setImageDrawable(null)
        }

        val fraction = (item.bytes.toFloat() / totalBytes.toFloat()).coerceIn(0.01f, 1f)
        // usage bar is faked with two weighted views rather than an actual ProgressBar
        (holder.barFill.layoutParams as android.widget.LinearLayout.LayoutParams).weight = fraction
        (holder.barSpacer.layoutParams as android.widget.LinearLayout.LayoutParams).weight = 1f - fraction
        holder.barFill.requestLayout()

        if (selectable) {
            holder.checkbox.visibility = android.view.View.VISIBLE

            holder.usage.visibility = android.view.View.GONE // Tunnel's app picker doesn't track usage
            holder.itemView.findViewById<android.view.View>(R.id.usageBarContainer).visibility = android.view.View.GONE
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = selected.contains(item.packageName)
            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selected.add(item.packageName) else selected.remove(item.packageName)
                onSelectionChanged?.invoke(selected)
            }
            holder.itemView.setOnClickListener { holder.checkbox.toggle() }
        } else {
            holder.usage.visibility = android.view.View.VISIBLE
            holder.checkbox.visibility = android.view.View.GONE
            holder.itemView.setOnClickListener(null)
        }
    }

    override fun getItemCount(): Int = items.size
}
