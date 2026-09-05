package com.soroush.wirecat.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

object InsetUtils {

    // adds the system bar inset on top of whatever padding the view already had in XML,
    // so edge-to-edge display doesn't draw content under the status/nav bar

    fun applyTopInset(view: View) {
        val initialPaddingTop = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = initialPaddingTop + bars.top)
            insets
        }
        view.requestApplyInsets()
    }

    fun applyBottomInset(view: View) {
        val initialPaddingBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = initialPaddingBottom + bars.bottom)
            insets
        }
        view.requestApplyInsets()
    }

    fun applyTopAndBottomInset(view: View) {
        val initialPaddingTop = view.paddingTop
        val initialPaddingBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = initialPaddingTop + bars.top, bottom = initialPaddingBottom + bars.bottom)
            insets
        }
        view.requestApplyInsets()
    }
}
