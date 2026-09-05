package com.soroush.wirecat.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.soroush.wirecat.BuildConfig
import com.soroush.wirecat.databinding.ActivitySplashBinding
import com.soroush.wirecat.util.InsetUtils

class SplashActivity : BaseActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())
    private val goToMain = Runnable {
        if (!isFinishing) {
            startActivity(Intent(this, HostActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetUtils.applyTopAndBottomInset(binding.root)

        binding.textAppVersion.text = getString(com.soroush.wirecat.R.string.version_format, BuildConfig.VERSION_NAME)
        handler.postDelayed(goToMain, SPLASH_DURATION_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(goToMain)
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_DURATION_MS = 900L
    }
}
