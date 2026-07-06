package com.tjlabs.tjlabscommon_sample.preview

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tjlabs.tjlabscommon_sample.BuildConfig
import com.tjlabs.tjlabscommon_sample.R
import com.tjlabs.tjlabscommon_sample.upload.TelemetryUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PreviewFragment : Fragment() {

    companion object {
        private const val ARG_SECTOR_ID = "arg_sector_id"
        private const val TAG = "PreviewFragment"
        private const val DONE_DISPLAY_MILLIS = 1500L

        fun newInstance(sectorId: Int): PreviewFragment {
            val frag = PreviewFragment()
            frag.arguments = Bundle().apply { putInt(ARG_SECTOR_ID, sectorId) }
            return frag
        }
    }

    private var sectorId: Int = -1
    private lateinit var uploader: TelemetryUploader

    private lateinit var tvStatus: TextView
    private lateinit var tvSelected: TextView
    private lateinit var uvdView: UvdTrajectoryView
    private lateinit var rfdView: RfdPatternView
    private lateinit var rvSets: RecyclerView
    private lateinit var btnRefresh: Button
    private lateinit var btnUploadAll: Button
    private lateinit var btnDeleteAll: Button
    private lateinit var btnHidePreview: Button
    private lateinit var previewCard: View
    private lateinit var pbUploadAll: ProgressBar
    private lateinit var adapter: TestSetAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sectorId = arguments?.getInt(ARG_SECTOR_ID, -1) ?: -1
        uploader = TelemetryUploader(requireActivity().application, BuildConfig.USER_BASE_URL)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_preview, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvStatus = view.findViewById(R.id.tvPreviewStatus)
        tvSelected = view.findViewById(R.id.tvPreviewSelected)
        uvdView = view.findViewById(R.id.uvdTrajectoryView)
        rfdView = view.findViewById(R.id.rfdPatternView)
        rvSets = view.findViewById(R.id.rvTestSets)
        btnRefresh = view.findViewById(R.id.btnRefresh)
        btnUploadAll = view.findViewById(R.id.btnUploadAll)
        btnDeleteAll = view.findViewById(R.id.btnDeleteAll)
        btnHidePreview = view.findViewById(R.id.btnHidePreview)
        previewCard = view.findViewById(R.id.previewCard)
        pbUploadAll = view.findViewById(R.id.pbUploadAll)

        adapter = TestSetAdapter(
            onPreview = ::onPreview,
            onUpload = ::onUpload,
            onDelete = ::onDelete
        )
        rvSets.layoutManager = LinearLayoutManager(requireContext())
        rvSets.adapter = adapter

        btnRefresh.setOnClickListener { refresh() }
        btnUploadAll.setOnClickListener { onUploadAll() }
        btnDeleteAll.setOnClickListener { onDeleteAll() }
        btnHidePreview.setOnClickListener { previewCard.visibility = View.GONE }

        if (sectorId < 0) {
            updateStatus("Sector ID 없음 — 업로드는 메인 화면에서 Auth 후 진입하세요")
        }
        refresh()
    }

    private fun refresh() {
        val sets = TestSetRepository.list(requireActivity().application)
        adapter.submit(sets)
        val totalBytes = sets.sumOf { it.totalBytes() }
        updateStatus("총 ${sets.size}개 세트 · ${formatSize(totalBytes)}")
        if (sets.isEmpty()) {
            uvdView.submit(emptyList())
            rfdView.submit(emptyList())
            tvSelected.text = "선택된 세트 없음"
        }
    }

    private fun onPreview(set: TestSet) {
        adapter.setSelected(set)
        previewCard.visibility = View.VISIBLE
        tvSelected.text = "Preview: ${set.userId} · ${set.serviceStartTime}"
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val app = requireActivity().application
            val uvd = TelemetryFileReader.readUvd(app, set.userId, set.serviceStartTime)
            val rfd = TelemetryFileReader.readRfd(app, set.userId, set.serviceStartTime)
            withContext(Dispatchers.Main) {
                uvdView.submit(uvd)
                rfdView.submit(rfd)
                updateStatus("Preview: UVD=${uvd.size} RFD=${rfd.size}")
            }
        }
    }

    private fun onUpload(set: TestSet) {
        if (sectorId < 0) {
            updateStatus("Sector 가 선택되지 않아 업로드할 수 없습니다")
            return
        }
        val label = "${set.userId}_${set.serviceStartTime}"
        confirm(
            title = "업로드 확인",
            message = "$label\n\n이 세트를 업로드할까요?",
            positive = "업로드"
        ) {
            adapter.setUploadProgress(set, UploadProgress(UploadStage.UPLOADING, 0, 3, "starting"))
            updateStatus("Upload 시작: $label")
            uploadSet(set) { uploaded, failed ->
                val stage = if (failed == 0 && uploaded > 0) UploadStage.DONE else UploadStage.FAILED
                adapter.setUploadProgress(set, UploadProgress(stage, uploaded, 3, "success=$uploaded fail=$failed"))
                updateStatus("Upload 완료 ($label): 성공=$uploaded 실패=$failed")
                // Let the user see the ✓ for a moment before the row disappears on refresh.
                view?.postDelayed({ refresh() }, DONE_DISPLAY_MILLIS)
            }
        }
    }

    private fun onDelete(set: TestSet) {
        val label = "${set.userId}_${set.serviceStartTime}"
        confirm(
            title = "삭제 확인",
            message = "$label\n\n이 세트를 삭제할까요? 되돌릴 수 없습니다.",
            positive = "삭제"
        ) {
            val deleted = TestSetRepository.delete(requireActivity().application, set)
            updateStatus("삭제: $label ($deleted files)")
            refresh()
        }
    }

    private fun onUploadAll() {
        if (sectorId < 0) {
            updateStatus("Sector 가 선택되지 않아 업로드할 수 없습니다")
            return
        }
        val sets = adapter.current()
        if (sets.isEmpty()) {
            updateStatus("업로드할 세트가 없습니다")
            return
        }
        confirm(
            title = "전체 업로드 확인",
            message = "${sets.size}개 세트를 모두 업로드할까요?",
            positive = "업로드"
        ) {
            btnUploadAll.isEnabled = false
            pbUploadAll.visibility = View.VISIBLE
            pbUploadAll.max = sets.size
            pbUploadAll.progress = 0
            updateStatus("전체 업로드 시작: ${sets.size}개 세트")
            sets.forEach { set ->
                adapter.setUploadProgress(set, UploadProgress(UploadStage.UPLOADING, 0, 3, "queued"))
            }
            var completedSets = 0
            var totalUploaded = 0
            var totalFailed = 0
            sets.forEach { set ->
                uploadSet(set) { uploaded, failed ->
                    val stage = if (failed == 0 && uploaded > 0) UploadStage.DONE else UploadStage.FAILED
                    adapter.setUploadProgress(set, UploadProgress(stage, uploaded, 3, "success=$uploaded fail=$failed"))
                    completedSets += 1
                    totalUploaded += uploaded
                    totalFailed += failed
                    pbUploadAll.progress = completedSets
                    updateStatus("전체 업로드 $completedSets/${sets.size} · 파일 성공=$totalUploaded 실패=$totalFailed")
                    if (completedSets == sets.size) {
                        updateStatus("전체 업로드 완료: 파일 성공=$totalUploaded 실패=$totalFailed")
                        view?.postDelayed({
                            pbUploadAll.visibility = View.GONE
                            btnUploadAll.isEnabled = true
                            refresh()
                        }, DONE_DISPLAY_MILLIS)
                    }
                }
            }
        }
    }

    private fun uploadSet(set: TestSet, onDone: (uploaded: Int, failed: Int) -> Unit) {
        val total = 3
        var completed = 0
        var uploadedCount = 0
        uploader.uploadAndCleanup(
            userId = set.userId,
            serviceStartTime = set.serviceStartTime,
            sectorId = sectorId,
            progress = { result ->
                Log.d(TAG, "upload ${result.fileName} ok=${result.success} ${result.message}")
                runOnUi {
                    completed += 1
                    if (result.success) uploadedCount += 1
                    val shortName = result.fileName.substringAfterLast('_').removeSuffix(".json")
                    val statusText = if (result.success) "$shortName ok" else "$shortName failed"
                    adapter.setUploadProgress(
                        set,
                        UploadProgress(
                            stage = UploadStage.UPLOADING,
                            completed = completed,
                            total = total,
                            message = statusText
                        )
                    )
                }
            },
            done = { uploaded, failed ->
                runOnUi { onDone(uploaded, failed) }
            }
        )
    }

    private fun onDeleteAll() {
        val sets = adapter.current()
        if (sets.isEmpty()) {
            updateStatus("삭제할 세트가 없습니다")
            return
        }
        confirm(
            title = "전체 삭제 확인",
            message = "${sets.size}개 세트를 모두 삭제할까요? 되돌릴 수 없습니다.",
            positive = "삭제"
        ) {
            val totalDeleted = sets.sumOf { TestSetRepository.delete(requireActivity().application, it) }
            updateStatus("전체 삭제: ${sets.size}개 세트 · $totalDeleted files")
            refresh()
        }
    }

    private fun confirm(
        title: String,
        message: String,
        positive: String,
        onConfirm: () -> Unit
    ) {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positive) { _, _ -> onConfirm() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun updateStatus(message: String) {
        runOnUi { tvStatus.text = "Status: $message" }
    }

    private fun runOnUi(block: () -> Unit) {
        if (!isAdded) return
        requireActivity().runOnUiThread { if (isAdded) block() }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1fKB", kb)
        val mb = kb / 1024.0
        return String.format("%.1fMB", mb)
    }
}
