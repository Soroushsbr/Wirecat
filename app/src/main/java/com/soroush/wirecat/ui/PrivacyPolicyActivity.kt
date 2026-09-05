package com.soroush.wirecat.ui

import android.os.Bundle
import com.soroush.wirecat.R
import com.soroush.wirecat.databinding.ActivityPrivacyPolicyBinding
import com.soroush.wirecat.util.InsetUtils

class PrivacyPolicyActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityPrivacyPolicyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetUtils.applyTopAndBottomInset(binding.root)

        binding.toolbarBack.textToolbarTitle.text = getString(R.string.privacy_policy_title)
        binding.toolbarBack.btnToolbarBack.setOnClickListener { finish() }
    }
}
