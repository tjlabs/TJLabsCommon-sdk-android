package com.tjlabs.tjlabscommon_sample.preview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tjlabs.tjlabscommon_sample.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class UploadStage { IDLE, UPLOADING, DONE, FAILED }

data class UploadProgress(
    val stage: UploadStage = UploadStage.IDLE,
    val completed: Int = 0,
    val total: Int = 3,
    val message: String = ""
)

class TestSetAdapter(
    private val onPreview: (TestSet) -> Unit,
    private val onUpload: (TestSet) -> Unit,
    private val onDelete: (TestSet) -> Unit
) : RecyclerView.Adapter<TestSetAdapter.VH>() {

    private val items = mutableListOf<TestSet>()
    private val uploadStates = mutableMapOf<String, UploadProgress>()
    private var selectedKey: String? = null
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun submit(newItems: List<TestSet>) {
        items.clear()
        items.addAll(newItems)
        // Drop upload state for sets that no longer exist (e.g. deleted after upload).
        val keys = newItems.map { it.key() }.toSet()
        uploadStates.keys.retainAll(keys)
        notifyDataSetChanged()
    }

    fun current(): List<TestSet> = items.toList()

    fun setSelected(set: TestSet) {
        val newKey = set.key()
        if (selectedKey == newKey) return
        selectedKey = newKey
        notifyDataSetChanged()
    }

    fun setUploadProgress(set: TestSet, progress: UploadProgress) {
        uploadStates[set.key()] = progress
        val pos = items.indexOfFirst { it.key() == set.key() }
        if (pos >= 0) notifyItemChanged(pos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_test_set, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.bind(
            item = item,
            isSelected = item.key() == selectedKey,
            progress = uploadStates[item.key()] ?: UploadProgress()
        )
    }

    override fun getItemCount(): Int = items.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: View = itemView.findViewById(R.id.containerTestSet)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTestSetTime)
        private val tvUser: TextView = itemView.findViewById(R.id.tvTestSetUser)
        private val tvMeta: TextView = itemView.findViewById(R.id.tvTestSetMeta)
        private val tvFiles: TextView = itemView.findViewById(R.id.tvTestSetFiles)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvTestSetBadge)
        private val tvUploadStatus: TextView = itemView.findViewById(R.id.tvTestSetUploadStatus)
        private val pbUpload: ProgressBar = itemView.findViewById(R.id.pbTestSetUpload)
        private val btnPreview: Button = itemView.findViewById(R.id.btnTestSetPreview)
        private val btnUpload: Button = itemView.findViewById(R.id.btnTestSetUpload)
        private val btnDelete: Button = itemView.findViewById(R.id.btnTestSetDelete)

        fun bind(item: TestSet, isSelected: Boolean, progress: UploadProgress) {
            val tsMillis = item.timestampMillis()
            tvTime.text = if (tsMillis > 0) {
                dateFormatter.format(Date(tsMillis))
            } else {
                item.serviceStartTime
            }
            tvUser.text = "user: ${item.userId}"

            val metaLine = item.meta?.infoLine().orEmpty()
            if (metaLine.isBlank()) {
                tvMeta.visibility = View.GONE
            } else {
                tvMeta.visibility = View.VISIBLE
                tvMeta.text = metaLine
            }

            val parts = listOf("rfd", "uvd", "event").map { type ->
                val file = item.files.firstOrNull { it.type == type }
                if (file != null) "$type ${formatSize(file.sizeBytes)}" else "$type —"
            }
            tvFiles.text = parts.joinToString("  ·  ")

            container.isSelected = isSelected
            container.alpha = if (isSelected) 1f else 0.96f

            renderProgress(progress)

            btnPreview.setOnClickListener { onPreview(item) }
            btnUpload.setOnClickListener { onUpload(item) }
            btnDelete.setOnClickListener { onDelete(item) }
            btnUpload.isEnabled = progress.stage != UploadStage.UPLOADING
            btnDelete.isEnabled = progress.stage != UploadStage.UPLOADING
        }

        private fun renderProgress(progress: UploadProgress) {
            when (progress.stage) {
                UploadStage.IDLE -> {
                    pbUpload.visibility = View.GONE
                    tvUploadStatus.visibility = View.GONE
                    tvBadge.visibility = View.GONE
                }
                UploadStage.UPLOADING -> {
                    pbUpload.visibility = View.VISIBLE
                    pbUpload.max = progress.total
                    pbUpload.progress = progress.completed
                    tvUploadStatus.visibility = View.VISIBLE
                    tvUploadStatus.text = "Uploading ${progress.completed}/${progress.total}" +
                        if (progress.message.isNotBlank()) " · ${progress.message}" else ""
                    tvBadge.visibility = View.GONE
                }
                UploadStage.DONE -> {
                    pbUpload.visibility = View.VISIBLE
                    pbUpload.max = progress.total
                    pbUpload.progress = progress.total
                    tvUploadStatus.visibility = View.VISIBLE
                    tvUploadStatus.text = "✓ Uploaded ${progress.completed}/${progress.total}"
                    tvBadge.visibility = View.VISIBLE
                }
                UploadStage.FAILED -> {
                    pbUpload.visibility = View.VISIBLE
                    pbUpload.max = progress.total
                    pbUpload.progress = progress.completed
                    tvUploadStatus.visibility = View.VISIBLE
                    tvUploadStatus.text = "Upload failed (${progress.completed}/${progress.total})" +
                        if (progress.message.isNotBlank()) " · ${progress.message}" else ""
                    tvBadge.visibility = View.GONE
                }
            }
        }

        private fun formatSize(bytes: Long): String {
            if (bytes < 1024) return "${bytes}B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1fKB", kb)
            val mb = kb / 1024.0
            return String.format(Locale.US, "%.1fMB", mb)
        }
    }
}
