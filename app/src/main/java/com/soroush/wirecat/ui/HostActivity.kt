package com.soroush.wirecat.ui

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.soroush.wirecat.R
import com.soroush.wirecat.databinding.ActivityHostBinding
import com.soroush.wirecat.util.InsetUtils

class HostActivity : BaseActivity() {

    private lateinit var binding: ActivityHostBinding
    private var currentTab: NavTab = NavTab.HOME

    private lateinit var homeFragment: HomeFragment
    private lateinit var logFragment: LogFragment
    private lateinit var tunnelFragment: TunnelFragment
    private lateinit var settingsFragment: SettingsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHostBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetUtils.applyTopAndBottomInset(binding.root)

        // don't trust auto-restored fragments after a recreate - rebuild from scratch instead
        if (supportFragmentManager.fragments.isNotEmpty()) {
            val cleanup = supportFragmentManager.beginTransaction()
            supportFragmentManager.fragments.forEach { cleanup.remove(it) }
            cleanup.commitNow()
        }

        homeFragment = HomeFragment()
        logFragment = LogFragment()
        tunnelFragment = TunnelFragment()
        settingsFragment = SettingsFragment()

        // all four tabs are added up front and just shown/hidden, so switching never recreates a fragment's view
        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, settingsFragment, TAB_SETTINGS).hide(settingsFragment)
            .add(R.id.fragmentContainer, tunnelFragment, TAB_TUNNEL).hide(tunnelFragment)
            .add(R.id.fragmentContainer, logFragment, TAB_LOGS).hide(logFragment)
            .add(R.id.fragmentContainer, homeFragment, TAB_HOME)
            .commitNow()

        // only the selected tab is restored across recreation, not the fragment instances themselves
        val restoredTab = savedInstanceState?.getString(KEY_CURRENT_TAB)
            ?.let { runCatching { NavTab.valueOf(it) }.getOrNull() }
            ?: NavTab.HOME
        currentTab = NavTab.HOME
        if (restoredTab != NavTab.HOME) {
            applyTab(restoredTab, animateIn = false)
        }

        BottomNavController.setup(binding.bottomNavInclude, currentTab) { tab -> selectTab(tab) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTab != NavTab.HOME) {
                    selectTab(NavTab.HOME)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CURRENT_TAB, currentTab.name)
    }

    private fun selectTab(tab: NavTab) {
        if (tab == currentTab) return
        applyTab(tab, animateIn = true)
        BottomNavController.updateSelection(binding.bottomNavInclude, tab)
    }

    private fun applyTab(tab: NavTab, animateIn: Boolean) {
        val (showFragment, hideFragments) = when (tab) {
            NavTab.HOME -> homeFragment to listOf(logFragment, tunnelFragment, settingsFragment)
            NavTab.LOGS -> logFragment to listOf(homeFragment, tunnelFragment, settingsFragment)
            NavTab.TUNNEL -> tunnelFragment to listOf(homeFragment, logFragment, settingsFragment)
            NavTab.SETTINGS -> settingsFragment to listOf(homeFragment, logFragment, tunnelFragment)
        }

        val transaction: FragmentTransaction = supportFragmentManager.beginTransaction()
        hideFragments.forEach { transaction.hide(it) }
        transaction.show(showFragment)
        transaction.commitNow()

        if (animateIn) animateContentIn(showFragment)
        currentTab = tab
    }

    private fun animateContentIn(fragment: Fragment) {
        val view = fragment.view ?: return
        view.alpha = 0f
        view.translationY = 14f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180)
            .start()
    }

    companion object {
        private const val TAB_HOME = "tab_home"
        private const val TAB_LOGS = "tab_logs"
        private const val TAB_TUNNEL = "tab_tunnel"
        private const val TAB_SETTINGS = "tab_settings"
        private const val KEY_CURRENT_TAB = "current_tab"
    }
}
