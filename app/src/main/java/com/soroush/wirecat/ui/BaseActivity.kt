package com.soroush.wirecat.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.soroush.wirecat.R
import com.soroush.wirecat.util.PrefsManager

abstract class BaseActivity : AppCompatActivity() {

    private var appliedDarkTheme = true

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedDarkTheme = PrefsManager(this).isDarkTheme
        setTheme(if (appliedDarkTheme) R.style.Theme_Wirecat else R.style.Theme_Wirecat_Light)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (PrefsManager(this).isDarkTheme != appliedDarkTheme) {
            recreate() // theme was changed on another screen while this one was in the background
        }
    }
}
