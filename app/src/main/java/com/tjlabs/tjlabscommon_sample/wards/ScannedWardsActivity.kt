package com.tjlabs.tjlabscommon_sample.wards

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tjlabs.tjlabscommon_sample.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ScannedWardsActivity : AppCompatActivity() {

    private lateinit var tvCount: TextView
    private lateinit var tvChecked: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnReset: Button
    private lateinit var btnUncheck: Button
    private lateinit var rvWards: RecyclerView
    private val adapter = ScannedWardAdapter { name -> ScannedWardTracker.toggleChecked(name) }

    private val queryFlow = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanned_wards)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Scanned Wards"

        tvCount = findViewById(R.id.tvWardsCount)
        tvChecked = findViewById(R.id.tvWardsChecked)
        tvEmpty = findViewById(R.id.tvWardsEmpty)
        etSearch = findViewById(R.id.etWardSearch)
        btnReset = findViewById(R.id.btnWardsReset)
        btnUncheck = findViewById(R.id.btnWardsUncheck)
        rvWards = findViewById(R.id.rvScannedWards)

        rvWards.layoutManager = LinearLayoutManager(this)
        rvWards.adapter = adapter

        btnReset.setOnClickListener { ScannedWardTracker.reset() }
        btnUncheck.setOnClickListener { ScannedWardTracker.resetChecks() }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                queryFlow.value = s?.toString().orEmpty()
            }
        })

        lifecycleScope.launch {
            combine(
                ScannedWardTracker.entries,
                queryFlow,
                ScannedWardTracker.checkedNames,
            ) { list, query, checked ->
                RenderState(list, query, checked)
            }.collect { state -> render(state) }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private data class RenderState(
        val entries: List<ScannedWardTracker.WardEntry>,
        val query: String,
        val checked: Set<String>,
    )

    private fun render(state: RenderState) {
        val total = state.entries.size
        val filtered = if (state.query.isBlank()) {
            state.entries
        } else {
            val q = state.query.trim().lowercase()
            state.entries.filter { it.name.lowercase().contains(q) }
        }

        tvCount.text = "Total: $total (showing ${filtered.size})"

        val checkedIndices = state.entries
            .mapIndexedNotNull { index, entry ->
                if (entry.name in state.checked) index + 1 else null
            }
        tvChecked.text = if (checkedIndices.isEmpty()) {
            "Checked: 0"
        } else {
            "Checked: ${checkedIndices.size} → " +
                checkedIndices.joinToString(", ") { "#$it" }
        }

        val nameToFullIndex = HashMap<String, Int>(state.entries.size)
        state.entries.forEachIndexed { i, e -> nameToFullIndex[e.name] = i + 1 }
        adapter.submit(filtered, state.checked, nameToFullIndex)

        val showEmpty = total == 0 || filtered.isEmpty()
        tvEmpty.visibility = if (showEmpty) View.VISIBLE else View.GONE
        tvEmpty.text = when {
            total == 0 -> "No wards scanned yet"
            filtered.isEmpty() -> "No matches"
            else -> ""
        }
        rvWards.visibility = if (showEmpty) View.GONE else View.VISIBLE
    }
}

private class ScannedWardAdapter(
    private val onToggle: (String) -> Unit,
) : RecyclerView.Adapter<ScannedWardAdapter.WardViewHolder>() {

    private val items = mutableListOf<ScannedWardTracker.WardEntry>()
    private var checked: Set<String> = emptySet()
    private var indexLookup: Map<String, Int> = emptyMap()

    fun submit(
        newItems: List<ScannedWardTracker.WardEntry>,
        newChecked: Set<String>,
        newIndexLookup: Map<String, Int>,
    ) {
        items.clear()
        items.addAll(newItems)
        checked = newChecked
        indexLookup = newIndexLookup
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scanned_ward, parent, false)
        return WardViewHolder(view, onToggle)
    }

    override fun onBindViewHolder(holder: WardViewHolder, position: Int) {
        val entry = items[position]
        val fullIndex = indexLookup[entry.name] ?: (position + 1)
        holder.bind(fullIndex, entry, entry.name in checked)
    }

    override fun getItemCount(): Int = items.size

    class WardViewHolder(
        itemView: View,
        private val onToggle: (String) -> Unit,
    ) : RecyclerView.ViewHolder(itemView) {
        private val cbChecked: CheckBox = itemView.findViewById(R.id.cbWardChecked)
        private val tvIndex: TextView = itemView.findViewById(R.id.tvWardIndex)
        private val tvName: TextView = itemView.findViewById(R.id.tvWardName)
        private val tvRssi: TextView = itemView.findViewById(R.id.tvWardRssi)

        fun bind(index: Int, entry: ScannedWardTracker.WardEntry, isChecked: Boolean) {
            cbChecked.isChecked = isChecked
            tvIndex.text = "${index}."
            tvName.text = entry.name
            tvRssi.text = "max RSSI: ${entry.maxRssi}"
            itemView.setOnClickListener { onToggle(entry.name) }
        }
    }
}
