package com.soroush.wirecat.ui

import android.graphics.drawable.Drawable
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.soroush.wirecat.R
import com.soroush.wirecat.databinding.BottomNavBinding

enum class NavTab { HOME, LOGS, TUNNEL, SETTINGS }

object BottomNavController {

    private data class TabViews(
        val row: LinearLayout,
        val pill: FrameLayout,
        val icon: ImageView,
        val label: TextView
    )

    private fun tabViews(binding: BottomNavBinding): Map<NavTab, TabViews> = mapOf(
        NavTab.HOME to TabViews(binding.navHome, binding.navHomePill, binding.navHomeIcon, binding.navHomeLabel),
        NavTab.LOGS to TabViews(binding.navLogs, binding.navLogsPill, binding.navLogsIcon, binding.navLogsLabel),
        NavTab.TUNNEL to TabViews(binding.navTunnel, binding.navTunnelPill, binding.navTunnelIcon, binding.navTunnelLabel),
        NavTab.SETTINGS to TabViews(binding.navSettings, binding.navSettingsPill, binding.navSettingsIcon, binding.navSettingsLabel)
    )

    fun setup(binding: BottomNavBinding, current: NavTab, onTabSelected: (NavTab) -> Unit) {
        applyState(binding, current, animate = false)

        // always forward the tap and let the caller decide if it's a no-op - comparing
        // against `current` here would use a stale value from when setup() was called
        tabViews(binding).forEach { (tab, views) ->
            views.row.setOnClickListener { onTabSelected(tab) }
        }
    }

    fun updateSelection(binding: BottomNavBinding, selected: NavTab) {
        applyState(binding, selected, animate = true)
    }

    private fun applyState(binding: BottomNavBinding, selected: NavTab, animate: Boolean) {
        val root = binding.root
        if (animate) {
            TransitionManager.beginDelayedTransition(root, AutoTransition().setDuration(180))
        }
        val context = root.context
        val accent = ContextCompat.getColor(context, R.color.wc_accent)
        val muted = ContextCompat.getColor(context, R.color.wc_text_secondary)

        tabViews(binding).forEach { (tab, views) ->
            val isSelected = tab == selected

            // fade the pill's background instead of hiding the pill view itself - the icon is
            // a child of the pill, so hiding the pill would hide the icon too
            val pillBg: Drawable = views.pill.background.mutate()
            pillBg.alpha = if (isSelected) 255 else 0
            if (isSelected) {
                DrawableCompat.setTint(pillBg, accent)
            }
            views.icon.setColorFilter(if (isSelected) ContextCompat.getColor(context, R.color.wc_white) else muted)
            views.label.setTextColor(if (isSelected) accent else muted)
        }
    }
}
