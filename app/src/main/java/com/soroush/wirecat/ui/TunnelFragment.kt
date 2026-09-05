package com.soroush.wirecat.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.soroush.wirecat.R
import com.soroush.wirecat.data.LiveAppUsage
import com.soroush.wirecat.data.NetworkStatsHelper
import com.soroush.wirecat.databinding.FragmentTunnelBinding
import com.soroush.wirecat.util.PrefsManager
import com.soroush.wirecat.vpn.MonitorCaptureVpnService
import com.soroush.wirecat.vpn.TunnelVpnService
import kotlinx.coroutines.launch

class TunnelFragment : Fragment() {

    private var _binding: FragmentTunnelBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: PrefsManager
    private lateinit var adapter: AppUsageAdapter

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) startTunnelService()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTunnelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())

        binding.header.headerTitle.text = getString(R.string.tunnel_title)
        binding.header.headerIcon.setImageResource(R.drawable.ic_shield)

        adapter = AppUsageAdapter(
            packageManager = requireContext().packageManager,
            selectable = true,
            selected = prefs.tunnelBlockedPackages.toMutableSet(),
            onSelectionChanged = { selected -> prefs.tunnelBlockedPackages = selected }
        )
        binding.recyclerTunnelApps.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerTunnelApps.adapter = adapter

        loadInstalledApps()

        binding.editTunnelSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnTunnelToggle.setOnClickListener { onToggleClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            TunnelVpnService.isTunnelRunning.collect { running ->
                binding.btnTunnelToggle.text =
                    getString(if (running) R.string.tunnel_stop else R.string.tunnel_start)
                binding.recyclerTunnelApps.alpha = if (running) 0.5f else 1f
                binding.recyclerTunnelApps.isEnabled = !running
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadInstalledApps() {
        val helper = NetworkStatsHelper(requireContext())
        val apps = helper.installedApps()
            .map { LiveAppUsage(it.packageName, it.label, 0L) }
            .sortedBy { it.appLabel.lowercase() }
        adapter.submitList(apps)
    }

    private fun onToggleClicked() {
        if (TunnelVpnService.isTunnelRunning.value) {

            ContextCompat.startForegroundService(
                requireContext(), Intent(requireContext(), TunnelVpnService::class.java).apply {
                    action = TunnelVpnService.ACTION_STOP
                }
            )
            return
        }
        if (prefs.tunnelBlockedPackages.isEmpty()) {
            return
        }
        if (MonitorCaptureVpnService.isCapturing.value) {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.tunnel_conflict_capture)
                .setPositiveButton(R.string.not_now, null)
                .show()
            return
        }
        val prepareIntent = VpnService.prepare(requireContext())
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            startTunnelService()
        }
    }

    private fun startTunnelService() {
        ContextCompat.startForegroundService(requireContext(), Intent(requireContext(), TunnelVpnService::class.java))
    }
}
