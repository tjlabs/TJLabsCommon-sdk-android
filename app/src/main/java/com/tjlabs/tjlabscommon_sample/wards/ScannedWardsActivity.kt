package com.tjlabs.tjlabscommon_sample.wards

import android.app.AlertDialog
import android.app.ProgressDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tjlabs.tjlabscommon_sample.R
import com.tjlabs.tjlabscommon_sample.preview.SessionMeta
import com.tjlabs.tjlabscommon_sample.preview.TelemetryFileReader
import com.tjlabs.tjlabscommon_sample.preview.TestSet
import com.tjlabs.tjlabscommon_sample.preview.TestSetRepository
import com.tjlabs.tjlabscommon_sdk_android.rfd.ReceivedForce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScannedWardsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SECTOR_ID = "extra_sector_id"
        const val EXTRA_SECTOR_DISPLAY = "extra_sector_display"
    }

    private lateinit var tvEmpty: TextView
    private lateinit var chipMatched: TextView
    private lateinit var chipScanOnly: TextView
    private lateinit var chipBundleOnly: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnReset: Button
    private lateinit var btnCompare: Button
    private lateinit var btnLoadFile: Button
    private lateinit var rvWards: RecyclerView
    private lateinit var cardLevelSummary: View
    private lateinit var tvLevelSummary: TextView
    private val adapter = ScannedWardAdapter()

    private enum class GroupFilter { MATCHED, SCAN_ONLY, BUNDLE_ONLY }
    private val queryFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow<GroupFilter?>(null)
    private var sectorId: Int = -1
    private var sectorDisplay: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanned_wards)

        sectorId = intent.getIntExtra(EXTRA_SECTOR_ID, -1)
        sectorDisplay = intent.getStringExtra(EXTRA_SECTOR_DISPLAY).orEmpty()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Scanned Wards"

        tvEmpty = findViewById(R.id.tvWardsEmpty)
        chipMatched = findViewById(R.id.chipMatched)
        chipScanOnly = findViewById(R.id.chipScanOnly)
        chipBundleOnly = findViewById(R.id.chipBundleOnly)
        etSearch = findViewById(R.id.etWardSearch)
        btnReset = findViewById(R.id.btnWardsReset)
        btnCompare = findViewById(R.id.btnWardsCompare)
        btnLoadFile = findViewById(R.id.btnWardsLoadFile)
        rvWards = findViewById(R.id.rvScannedWards)
        cardLevelSummary = findViewById(R.id.cardLevelSummary)
        tvLevelSummary = findViewById(R.id.tvLevelSummary)

        rvWards.layoutManager = LinearLayoutManager(this)
        rvWards.adapter = adapter

        btnReset.setOnClickListener { ScannedWardTracker.reset() }
        btnCompare.setOnClickListener { compareWithBundle() }
        btnLoadFile.setOnClickListener { showLoadFromFileDialog() }

        chipMatched.setOnClickListener { toggleFilter(GroupFilter.MATCHED) }
        chipScanOnly.setOnClickListener { toggleFilter(GroupFilter.SCAN_ONLY) }
        chipBundleOnly.setOnClickListener { toggleFilter(GroupFilter.BUNDLE_ONLY) }

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
                ScannedWardTracker.bundleSectorId,
                filterFlow,
            ) { list, query, bundleSector, filter ->
                RenderState(list, query, bundleSector, filter)
            }.collect { state -> render(state) }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun toggleFilter(target: GroupFilter) {
        filterFlow.value = if (filterFlow.value == target) null else target
    }

    private fun buildRows(
        entries: List<ScannedWardTracker.WardEntry>,
        showSections: Boolean,
    ): List<Row> {
        if (!showSections) {
            return entries.mapIndexed { i, e -> Row.Entry(i + 1, e) }
        }
        val byLevel = LinkedHashMap<String, MutableList<ScannedWardTracker.WardEntry>>()
        val unmatched = mutableListOf<ScannedWardTracker.WardEntry>()
        for (e in entries) {
            val level = e.matchedLevel
            if (level != null) byLevel.getOrPut(level) { mutableListOf() } += e
            else unmatched += e
        }
        val rows = mutableListOf<Row>()
        for ((level, items) in byLevel) {
            rows += Row.Header(level, items.size, SectionKind.LEVEL)
            items.forEachIndexed { i, e -> rows += Row.Entry(i + 1, e) }
        }
        if (unmatched.isNotEmpty()) {
            rows += Row.Header("Unmatched (scan)", unmatched.size, SectionKind.UNMATCHED)
            unmatched.forEachIndexed { i, e -> rows += Row.Entry(i + 1, e) }
        }
        return rows
    }

    enum class SectionKind { LEVEL, UNMATCHED }

    sealed class Row {
        data class Header(val title: String, val count: Int, val kind: SectionKind) : Row()
        data class Entry(val indexInSection: Int, val entry: ScannedWardTracker.WardEntry) : Row()
    }

    private data class RenderState(
        val entries: List<ScannedWardTracker.WardEntry>,
        val query: String,
        val bundleSectorId: Int?,
        val filter: GroupFilter?,
    )

    private fun render(state: RenderState) {
        val matchedCount = state.entries.count { it.isScanned && it.isMatched }
        val scanOnlyCount = state.entries.count { it.isScanned && !it.isMatched }
        val bundleOnlyCount = state.entries.count { it.source == ScannedWardTracker.Source.BUNDLE_ONLY }

        chipMatched.text = "Match $matchedCount"
        chipScanOnly.text = "Scan $scanOnlyCount"
        chipBundleOnly.text = "Bundle $bundleOnlyCount"
        chipMatched.isSelected = state.filter == GroupFilter.MATCHED
        chipScanOnly.isSelected = state.filter == GroupFilter.SCAN_ONLY
        chipBundleOnly.isSelected = state.filter == GroupFilter.BUNDLE_ONLY

        renderLevelSummary(state.entries, state.bundleSectorId)

        val byGroup = state.entries.filter { entry ->
            when (state.filter) {
                GroupFilter.MATCHED -> entry.isScanned && entry.isMatched
                GroupFilter.SCAN_ONLY -> entry.isScanned && !entry.isMatched
                GroupFilter.BUNDLE_ONLY -> entry.source == ScannedWardTracker.Source.BUNDLE_ONLY
                null -> true
            }
        }
        val filtered = if (state.query.isBlank()) {
            byGroup
        } else {
            val q = state.query.trim().lowercase()
            byGroup.filter { it.name.lowercase().contains(q) }
        }

        val rows = buildRows(filtered, showSections = state.bundleSectorId != null)
        adapter.submit(rows)

        val total = state.entries.size
        val showEmpty = total == 0 || filtered.isEmpty()
        tvEmpty.visibility = if (showEmpty) View.VISIBLE else View.GONE
        tvEmpty.text = when {
            total == 0 -> "No wards scanned yet"
            filtered.isEmpty() -> "No matches"
            else -> ""
        }
        rvWards.visibility = if (showEmpty) View.GONE else View.VISIBLE
    }

    private fun renderLevelSummary(
        entries: List<ScannedWardTracker.WardEntry>,
        bundleSectorId: Int?,
    ) {
        if (bundleSectorId == null) {
            cardLevelSummary.visibility = View.GONE
            return
        }
        val byLevel = LinkedHashMap<String, MutableList<ScannedWardTracker.WardEntry>>()
        for (e in entries) {
            val level = e.matchedLevel ?: continue
            byLevel.getOrPut(level) { mutableListOf() } += e
        }
        if (byLevel.isEmpty()) {
            cardLevelSummary.visibility = View.GONE
            return
        }
        cardLevelSummary.visibility = View.VISIBLE
        tvLevelSummary.text = byLevel.entries.joinToString(separator = "  ") { (level, items) ->
            val matched = items.count { it.isScanned }
            "$level(${matched}/${items.size})"
        }
    }

    private fun compareWithBundle() {
        if (sectorId < 0) {
            Toast.makeText(this, "Sector 미설정 — MainActivity에서 선택 후 다시 열어주세요.", Toast.LENGTH_LONG).show()
            return
        }
        val progress = ProgressDialog(this).apply {
            setMessage("Bundle 로드 중… (sector $sectorId)")
            setCancelable(false)
            show()
        }
        BundleService.load(application, sectorId) { result ->
            runOnUiThread {
                progress.dismiss()
                result.onSuccess { bundle ->
                    ScannedWardTracker.applyBundle(bundle)
                    Toast.makeText(
                        this,
                        "Bundle: ${bundle.nameToLevelLabel.size} wards / ${bundle.levelCount} levels",
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure { err ->
                    Toast.makeText(this, "Bundle 로드 실패: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showLoadFromFileDialog() {
        lifecycleScope.launch {
            val sets = withContext(Dispatchers.IO) { TestSetRepository.list(application) }
                .filter { it.hasType("rfd") }
            if (sets.isEmpty()) {
                Toast.makeText(this@ScannedWardsActivity, "저장된 RFD 파일이 없습니다.", Toast.LENGTH_LONG).show()
                return@launch
            }
            showMultiSelectDialog(groupDialogEntries(sets))
        }
    }

    private data class FileRow(val primary: String, val secondary: String, val set: TestSet)
    private data class FileGroup(val title: String, val rows: List<FileRow>)

    private fun groupDialogEntries(sets: List<TestSet>): List<FileGroup> {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentSector = mutableListOf<FileRow>()
        val otherSector = mutableListOf<FileRow>()
        val noMeta = mutableListOf<FileRow>()

        for (set in sets) {
            val when_ = set.timestampMillis()
                .takeIf { it > 0 }?.let { ts.format(Date(it)) } ?: set.serviceStartTime
            val size = "${set.totalBytes() / 1024} KB"
            val meta = set.meta
            when {
                meta == null -> noMeta += FileRow(
                    primary = "${set.userId} · $when_",
                    secondary = "no metadata · $size",
                    set = set
                )
                sectorId >= 0 && meta.sectorId == sectorId -> currentSector += FileRow(
                    primary = "${meta.userId} · $when_",
                    secondary = "${describeSector(meta)} · $size",
                    set = set
                )
                else -> otherSector += FileRow(
                    primary = "${meta.userId} · $when_",
                    secondary = "${describeSector(meta)} · $size",
                    set = set
                )
            }
        }
        val result = mutableListOf<FileGroup>()
        if (currentSector.isNotEmpty()) result += FileGroup("Current sector", currentSector)
        if (otherSector.isNotEmpty()) result += FileGroup("Other sector", otherSector)
        if (noMeta.isNotEmpty()) result += FileGroup("No metadata", noMeta)
        return result
    }

    private fun describeSector(meta: SessionMeta): String = when {
        meta.sectorDisplay.isNotBlank() -> "sector ${meta.sectorDisplay}"
        meta.sectorId >= 0 -> "sector ${meta.sectorId}"
        else -> "sector ?"
    }

    private fun showMultiSelectDialog(groups: List<FileGroup>) {
        val inflater = LayoutInflater.from(this)
        val root = inflater.inflate(R.layout.dialog_load_wards, null) as LinearLayout
        val summary = root.findViewById<TextView>(R.id.tvDialogSummary)
        val btnSelectAll = root.findViewById<Button>(R.id.btnDialogSelectAll)
        val btnClear = root.findViewById<Button>(R.id.btnDialogClear)
        val listContainer = root.findViewById<LinearLayout>(R.id.dialogListContainer)

        val selected = LinkedHashSet<TestSet>()
        val rowViews = mutableListOf<Pair<TestSet, CheckBox>>()

        fun refreshSummary() {
            val totalKB = selected.sumOf { it.totalBytes() } / 1024
            summary.text = if (selected.isEmpty()) "0 selected" else "${selected.size} selected · $totalKB KB"
        }

        fun addRow(row: FileRow) {
            val view = inflater.inflate(R.layout.item_load_wards_entry, listContainer, false)
            val cb = view.findViewById<CheckBox>(R.id.cbSelect)
            view.findViewById<TextView>(R.id.tvPrimary).text = row.primary
            view.findViewById<TextView>(R.id.tvSecondary).text = row.secondary
            view.setOnClickListener {
                cb.isChecked = !cb.isChecked
            }
            cb.setOnCheckedChangeListener { _, checked ->
                if (checked) selected += row.set else selected -= row.set
                refreshSummary()
            }
            listContainer.addView(view)
            rowViews += row.set to cb
        }

        for (group in groups) {
            val header = inflater.inflate(R.layout.item_load_wards_header, listContainer, false) as TextView
            header.text = group.title
            listContainer.addView(header)
            for (row in group.rows) addRow(row)
        }

        btnSelectAll.setOnClickListener {
            rowViews.forEach { it.second.isChecked = true }
        }
        btnClear.setOnClickListener {
            rowViews.forEach { it.second.isChecked = false }
        }

        refreshSummary()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Load RFD sessions")
            .setView(root)
            .setPositiveButton("Load", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (selected.isEmpty()) {
                    Toast.makeText(this, "선택된 파일이 없습니다.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                replayIntoTracker(selected.toList())
            }
        }
        dialog.show()
    }

    private fun replayIntoTracker(sets: List<TestSet>) {
        val progress = ProgressDialog(this).apply {
            setMessage("파일 로드 중…")
            setCancelable(false)
            show()
        }
        lifecycleScope.launch {
            var totalSamples = 0
            for (set in sets) {
                val samples = withContext(Dispatchers.IO) {
                    TelemetryFileReader.readRfd(application, set.userId, set.serviceStartTime)
                }
                if (samples.isEmpty()) continue
                withContext(Dispatchers.Default) {
                    for (s in samples) {
                        ScannedWardTracker.record(
                            ReceivedForce(mobile_time = s.mobileTime, rfs = s.rfs)
                        )
                    }
                }
                totalSamples += samples.size
            }
            progress.dismiss()
            Toast.makeText(
                this@ScannedWardsActivity,
                "Loaded ${sets.size} session(s) · $totalSamples RFD samples",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

private class ScannedWardAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
    }

    private val rows = mutableListOf<ScannedWardsActivity.Row>()

    fun submit(newRows: List<ScannedWardsActivity.Row>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is ScannedWardsActivity.Row.Header -> TYPE_HEADER
        is ScannedWardsActivity.Row.Entry -> TYPE_ENTRY
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_ward_section, parent, false)
            )
            else -> WardViewHolder(
                inflater.inflate(R.layout.item_scanned_ward, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is ScannedWardsActivity.Row.Header -> (holder as HeaderViewHolder).bind(row)
            is ScannedWardsActivity.Row.Entry -> (holder as WardViewHolder)
                .bind(row.indexInSection, row.entry)
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dot: View = itemView.findViewById(R.id.vSectionDot)
        private val title: TextView = itemView.findViewById(R.id.tvSectionTitle)
        private val count: TextView = itemView.findViewById(R.id.tvSectionCount)

        fun bind(row: ScannedWardsActivity.Row.Header) {
            title.text = row.title
            count.text = "${row.count}"
            val ctx = itemView.context
            val bg = when (row.kind) {
                ScannedWardsActivity.SectionKind.UNMATCHED ->
                    ContextCompat.getColor(ctx, R.color.ward_scan_only_text)
                else -> ContextCompat.getColor(ctx, R.color.brand_primary)
            }
            val shape = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(bg)
            }
            dot.background = shape
        }
    }

    class WardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val root: View = itemView.findViewById(R.id.wardItemRoot)
        private val tvIndex: TextView = itemView.findViewById(R.id.tvWardIndex)
        private val tvName: TextView = itemView.findViewById(R.id.tvWardName)
        private val tvRssi: TextView = itemView.findViewById(R.id.tvWardRssi)

        fun bind(index: Int, entry: ScannedWardTracker.WardEntry) {
            val ctx = itemView.context
            tvIndex.text = "${index}."
            tvName.text = entry.name

            when {
                entry.source == ScannedWardTracker.Source.BUNDLE_ONLY -> {
                    root.setBackgroundColor(ContextCompat.getColor(ctx, R.color.ward_bundle_only_bg))
                    val txt = ContextCompat.getColor(ctx, R.color.ward_bundle_only_text)
                    tvName.setTextColor(txt)
                    tvRssi.setTextColor(txt)
                    tvRssi.text = "(not scanned)"
                }
                entry.isMatched -> {
                    root.setBackgroundColor(ContextCompat.getColor(ctx, R.color.ward_matched_bg))
                    val txt = ContextCompat.getColor(ctx, R.color.ward_matched_text)
                    tvName.setTextColor(txt)
                    tvRssi.setTextColor(txt)
                    tvRssi.text = "max RSSI: ${entry.maxRssi ?: '-'}"
                }
                else -> {
                    root.setBackgroundColor(ContextCompat.getColor(ctx, R.color.ward_scan_only_bg))
                    val txt = ContextCompat.getColor(ctx, R.color.ward_scan_only_text)
                    tvName.setTextColor(txt)
                    tvRssi.setTextColor(txt)
                    tvRssi.text = "max RSSI: ${entry.maxRssi ?: '-'}"
                }
            }
        }
    }
}
