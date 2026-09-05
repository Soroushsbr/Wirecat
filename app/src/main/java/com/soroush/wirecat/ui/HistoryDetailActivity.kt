package com.soroush.wirecat.ui

import android.app.AlertDialog
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.soroush.wirecat.R
import com.soroush.wirecat.data.LiveAppUsage
import com.soroush.wirecat.data.SessionType
import com.soroush.wirecat.data.WirecatDbHelper
import com.soroush.wirecat.databinding.ActivityHistoryDetailBinding
import com.soroush.wirecat.util.InsetUtils
import com.soroush.wirecat.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityHistoryDetailBinding
    private lateinit var appsAdapter: AppUsageAdapter
    private lateinit var logsAdapter: LogAdapter
    private var sessionId = -1L
    private var sessionType = SessionType.MONITOR
    private var hasPackets = false
    private var showingLogsTab = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetUtils.applyTopAndBottomInset(binding.root)

        binding.toolbarBack.textToolbarTitle.text = getString(R.string.history_title)
        binding.toolbarBack.btnToolbarBack.setOnClickListener { finish() }
        binding.toolbarBack.btnToolbarAction.visibility = android.view.View.VISIBLE
        binding.toolbarBack.btnToolbarAction.setImageResource(R.drawable.ic_delete)
        binding.toolbarBack.btnToolbarAction.contentDescription = getString(R.string.history_delete_one)
        binding.toolbarBack.btnToolbarAction.setOnClickListener { confirmDelete() }

        appsAdapter = AppUsageAdapter(packageManager)
        binding.recyclerDetailApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerDetailApps.adapter = appsAdapter

        logsAdapter = LogAdapter()
        binding.recyclerDetailLogs.layoutManager = LinearLayoutManager(this)
        binding.recyclerDetailLogs.adapter = logsAdapter

        binding.tabDataUsage.setOnClickListener { showTab(logs = false) }
        binding.tabLogs.setOnClickListener { showTab(logs = true) }

        binding.editDetailLogSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                logsAdapter.filter(s?.toString().orEmpty())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        if (sessionId == -1L) {
            finish()
            return
        }
        loadSession()
    }

    private fun loadSession() {
        lifecycleScope.launch {
            val db = WirecatDbHelper.get(this@HistoryDetailActivity)
            val sessionWithApps = withContext(Dispatchers.IO) { db.getSession(sessionId) }
            if (sessionWithApps == null) {
                finish()
                return@launch
            }
            sessionType = sessionWithApps.session.type
            hasPackets = sessionWithApps.packetCount > 0

            val duration = sessionWithApps.session.endTimeMillis - sessionWithApps.session.startTimeMillis
            val totalLabel = if (sessionType == SessionType.TUNNEL) {
                "${sessionWithApps.session.totalBytes} packets blocked"
            } else {
                "Total: ${FormatUtils.formatBytes(sessionWithApps.session.totalBytes)}"
            }
            binding.textDetailSummary.text = "$totalLabel \u2022 Duration: ${FormatUtils.formatDuration(duration)}"

            val perApp = sessionWithApps.apps
                .sortedByDescending { it.bytes }
                .map { LiveAppUsage(it.packageName, it.appLabel, it.bytes) }
            appsAdapter.submitList(perApp)

            if (hasPackets) {
                val packets = withContext(Dispatchers.IO) { db.getPacketsForSession(sessionId) }
                logsAdapter.submitList(packets.map { it.toLogEntry() })
            }

            binding.textDetailLogsEmpty.text = getString(
                if (sessionType == SessionType.TUNNEL) R.string.history_no_packets_tunnel
                else R.string.history_no_packets_monitor
            )

            showTab(logs = false)
        }
    }

    private fun showTab(logs: Boolean) {
        showingLogsTab = logs
        val accent = ContextCompat.getColor(this, R.color.wc_accent)
        val muted = ContextCompat.getColor(this, R.color.wc_text_secondary)

        binding.tabDataUsage.setTextColor(if (!logs) accent else muted)
        binding.tabDataUsage.setBackgroundResource(if (!logs) R.drawable.bg_pill_muted else 0)
        binding.tabLogs.setTextColor(if (logs) accent else muted)
        binding.tabLogs.setBackgroundResource(if (logs) R.drawable.bg_pill_muted else 0)

        binding.recyclerDetailApps.visibility = if (!logs) android.view.View.VISIBLE else android.view.View.GONE

        val showLogsList = logs && hasPackets
        binding.recyclerDetailLogs.visibility = if (showLogsList) android.view.View.VISIBLE else android.view.View.GONE
        binding.detailLogSearchBar.visibility = if (showLogsList) android.view.View.VISIBLE else android.view.View.GONE
        binding.textDetailLogsEmpty.visibility = if (logs && !hasPackets) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.history_delete_one)
            .setMessage(R.string.history_delete_one_confirm)
            .setPositiveButton(R.string.history_delete_confirm_yes) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        WirecatDbHelper.get(this@HistoryDetailActivity).deleteSession(sessionId)
                    }
                    finish()
                }
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}
