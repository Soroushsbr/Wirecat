package com.soroush.wirecat.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.soroush.wirecat.R
import com.soroush.wirecat.databinding.FragmentSettingsBinding
import com.soroush.wirecat.util.PrefsManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PrefsManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PrefsManager(requireContext())

        binding.header.headerTitle.text = getString(R.string.settings_title)
        binding.header.headerIcon.setImageResource(R.drawable.ic_settings)

        binding.switchDarkTheme.isChecked = prefs.isDarkTheme
        binding.switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != prefs.isDarkTheme) {
                prefs.isDarkTheme = isChecked
                requireActivity().recreate()
            }
        }

        binding.switchCapturePackets.isChecked = prefs.isMonitorCaptureEnabled
        binding.switchCapturePackets.setOnCheckedChangeListener { _, isChecked ->
            prefs.isMonitorCaptureEnabled = isChecked
        }

        binding.rowHistory.setOnClickListener {
            startActivity(Intent(requireContext(), HistoryActivity::class.java))
        }
        binding.rowPrivacy.setOnClickListener {
            startActivity(Intent(requireContext(), PrivacyPolicyActivity::class.java))
        }
        binding.rowDonate.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.donate_url))))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
