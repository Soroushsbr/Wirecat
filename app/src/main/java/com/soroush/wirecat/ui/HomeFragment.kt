package com.soroush.wirecat.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.soroush.wirecat.R
import com.soroush.wirecat.data.MonitorService
import com.soroush.wirecat.databinding.FragmentHomeBinding
import com.soroush.wirecat.util.FormatUtils
import com.soroush.wirecat.util.PermissionUtils
import com.soroush.wirecat.util.PrefsManager
import com.soroush.wirecat.vpn.MonitorCaptureVpnService
import com.soroush.wirecat.vpn.TunnelVpnService
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AppUsageAdapter
    private lateinit var prefs: PrefsManager

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                ContextCompat.startForegroundService(
                    requireContext(), Intent(requireContext(), MonitorCaptureVpnService::class.java)
                )
            }

        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())

        adapter = AppUsageAdapter(requireContext().packageManager)
        binding.recyclerApps.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerApps.adapter = adapter

        binding.btnStartStop.setOnClickListener { onStartStopClicked() }
        binding.btnDonateShortcut.setOnClickListener { openDonateLink() }

        observeMonitorState()
        requestNotificationPermissionIfNeeded()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun onStartStopClicked() {
        if (MonitorService.isRunning.value) {

            ContextCompat.startForegroundService(
                requireContext(),
                Intent(requireContext(), MonitorService::class.java).setAction(MonitorService.ACTION_STOP)
            )
            if (MonitorCaptureVpnService.isCapturing.value) {
                ContextCompat.startForegroundService(
                    requireContext(),
                    Intent(requireContext(), MonitorCaptureVpnService::class.java).setAction(MonitorCaptureVpnService.ACTION_STOP)
                )
            }
            return
        }
        if (!hasWorkingNetwork()) {
            android.widget.Toast.makeText(
                requireContext(), R.string.no_network_message, android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        if (!PermissionUtils.hasUsageAccess(requireContext())) {
            showUsageAccessDialog()
            return
        }
        if (prefs.isMonitorCaptureEnabled) {

            if (TunnelVpnService.isTunnelRunning.value) {
                AlertDialog.Builder(requireContext())
                    .setMessage(R.string.capture_conflict_tunnel)
                    .setPositiveButton(R.string.not_now, null)
                    .show()
                return
            }
            val prepareIntent = VpnService.prepare(requireContext())
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                ContextCompat.startForegroundService(
                    requireContext(), Intent(requireContext(), MonitorCaptureVpnService::class.java)
                )
            }
        }
        ContextCompat.startForegroundService(requireContext(), Intent(requireContext(), MonitorService::class.java))
    }

    private fun hasWorkingNetwork(): Boolean {
        val cm = requireContext().getSystemService(android.net.ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun showUsageAccessDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.permission_usage_access_title)
            .setMessage(R.string.permission_usage_access_body)
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                startActivity(PermissionUtils.usageAccessSettingsIntent())
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionUtils.hasNotificationPermission(requireContext())
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun observeMonitorState() {
        viewLifecycleOwner.lifecycleScope.launch {
            MonitorService.isRunning.collect { running ->
                updateStatusLabel(running)
                binding.btnStartStop.text = getString(
                    if (running) R.string.stop_monitoring else R.string.start_monitoring
                )
                binding.btnStartStop.setIconResource(
                    if (running) R.drawable.ic_stop else R.drawable.ic_play
                )

            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            MonitorCaptureVpnService.isCapturing.collect { updateStatusLabel(MonitorService.isRunning.value) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            MonitorService.snapshot.collect { snapshot ->
                if (snapshot == null) {
                    binding.textTotalUsage.text = FormatUtils.formatBytes(0)
                    binding.emptyState.visibility = View.VISIBLE
                    adapter.submitList(emptyList())
                    return@collect
                }
                binding.textSessionTime.text = getString(R.string.session_time_format, FormatUtils.formatDuration(snapshot.elapsedMillis))
                binding.textTotalUsage.text = FormatUtils.formatBytes(snapshot.totalBytes)
                binding.textSpeed.text = FormatUtils.formatSpeed(snapshot.bytesPerSecond)
                binding.emptyState.visibility = if (snapshot.perApp.isEmpty()) View.VISIBLE else View.GONE
                adapter.submitList(snapshot.perApp)
            }
        }
    }

    private fun updateStatusLabel(running: Boolean) {
        binding.statusDot.setBackgroundResource(
            if (running) R.drawable.bg_status_dot else R.drawable.bg_status_dot_idle
        )
        val base = getString(if (running) R.string.status_monitoring else R.string.status_idle)
        binding.textStatusLabel.text = if (running && MonitorCaptureVpnService.isCapturing.value) {
            getString(R.string.status_with_capture, base)
        } else {
            base
        }
    }

    private fun openDonateLink() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.donate_url))))
    }
}
