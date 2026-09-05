package com.soroush.wirecat.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.soroush.wirecat.R
import com.soroush.wirecat.data.SessionWithApps
import com.soroush.wirecat.data.WirecatDbHelper
import com.soroush.wirecat.databinding.ActivityHistoryBinding
import com.soroush.wirecat.util.InsetUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : BaseActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetUtils.applyTopAndBottomInset(binding.root)

        adapter = HistoryAdapter(
            onClick = { session ->
                val intent = Intent(this, HistoryDetailActivity::class.java)
                intent.putExtra(HistoryDetailActivity.EXTRA_SESSION_ID, session.session.id)
                startActivity(intent)
            },
            onDelete = { session -> confirmDeleteOne(session) }
        )

        binding.toolbarBack.textToolbarTitle.text = getString(R.string.history_title)
        binding.toolbarBack.btnToolbarBack.setOnClickListener { finish() }
        binding.toolbarBack.btnToolbarAction.setImageResource(R.drawable.ic_delete)
        binding.toolbarBack.btnToolbarAction.contentDescription = getString(R.string.history_delete_all)
        binding.toolbarBack.btnToolbarAction.setOnClickListener { confirmDeleteAll() }

        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadSessions()
    }

    private fun loadSessions() {
        lifecycleScope.launch {
            val sessions = withContext(Dispatchers.IO) {
                WirecatDbHelper.get(this@HistoryActivity).getAllSessions()
            }
            adapter.submitList(sessions)
            binding.textHistoryEmpty.visibility =
                if (sessions.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.toolbarBack.btnToolbarAction.visibility =
                if (sessions.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    private fun confirmDeleteOne(session: SessionWithApps) {
        AlertDialog.Builder(this)
            .setTitle(R.string.history_delete_one)
            .setMessage(R.string.history_delete_one_confirm)
            .setPositiveButton(R.string.history_delete_confirm_yes) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        WirecatDbHelper.get(this@HistoryActivity).deleteSession(session.session.id)
                    }
                    loadSessions()
                }
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.history_delete_all)
            .setMessage(R.string.history_delete_all_confirm)
            .setPositiveButton(R.string.history_delete_confirm_yes) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        WirecatDbHelper.get(this@HistoryActivity).deleteAllSessions()
                    }
                    loadSessions()
                }
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }
}
