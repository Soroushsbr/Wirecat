package com.soroush.wirecat.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.soroush.wirecat.R
import com.soroush.wirecat.data.PacketLogEntry
import com.soroush.wirecat.data.PacketLogStore
import com.soroush.wirecat.databinding.FragmentLogBinding
import kotlinx.coroutines.launch

class LogFragment : Fragment() {

    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!

    private val adapter = LogAdapter()
    private var latestEntries: List<PacketLogEntry> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.header.headerTitle.text = getString(R.string.log_title)
        binding.header.headerIcon.setImageResource(R.drawable.ic_logs)

        binding.recyclerLog.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLog.adapter = adapter

        binding.editLogSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                adapter.filter(s?.toString().orEmpty())
                updateEmptyState()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        viewLifecycleOwner.lifecycleScope.launch {
            PacketLogStore.logFlow.collect { entries ->
                latestEntries = entries
                adapter.submitList(entries)
                updateEmptyState()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateEmptyState() {
        val query = binding.editLogSearch.text?.toString().orEmpty()
        val isEmpty = if (query.isBlank()) latestEntries.isEmpty() else adapter.itemCount == 0
        binding.textLogEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.textLogEmpty.text = if (query.isBlank()) getString(R.string.log_empty) else getString(R.string.log_no_matches)
    }
}
