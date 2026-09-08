package com.soroush.wirecat.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.soroush.wirecat.R
import com.soroush.wirecat.databinding.FragmentSettingsBinding
import com.soroush.wirecat.util.LocaleUtils
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

        updateLanguageLabel()
        binding.rowLanguage.setOnClickListener { showLanguagePicker() }

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

    private fun updateLanguageLabel() {
        val isPersian = LocaleUtils.currentLanguageTag(requireContext()) == LocaleUtils.LANGUAGE_PERSIAN
        binding.textCurrentLanguage.text = getString(if (isPersian) R.string.language_persian else R.string.language_english)
    }

    private fun showLanguagePicker() {
        val options = arrayOf(getString(R.string.language_english), getString(R.string.language_persian))
        val currentIndex = if (LocaleUtils.currentLanguageTag(requireContext()) == LocaleUtils.LANGUAGE_PERSIAN) 1 else 0
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                dialog.dismiss()
                val tag = if (which == 1) LocaleUtils.LANGUAGE_PERSIAN else LocaleUtils.LANGUAGE_ENGLISH
                LocaleUtils.setLanguage(tag)
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }
}
