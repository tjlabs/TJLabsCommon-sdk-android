package com.tjlabs.tjlabscommon_sample.upload

import android.app.Application
import android.util.Log
import com.tjlabs.tjlabscommon_sample.auth.AuthService
import com.tjlabs.tjlabscommon_sample.network.ApiServices
import com.tjlabs.tjlabscommon_sample.network.CollectionPresignRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class TelemetryUploader(
    private val application: Application,
    collectionsBaseUrl: String
) {
    private val tag = "TelemetryUploader"
    private val operatingSystem = "Android"
    private val collectionsApi = ApiServices.createCollectionsApi(collectionsBaseUrl)
    private val putClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class FileUploadResult(
        val fileName: String,
        val success: Boolean,
        val message: String
    )

    fun uploadAndCleanup(
        userId: String,
        serviceStartTime: String,
        sectorId: Int,
        progress: (FileUploadResult) -> Unit,
        done: (uploaded: Int, failed: Int) -> Unit
    ) {
        val fileNames = listOf(
            "${userId}_${serviceStartTime}_rfd.json",
            "${userId}_${serviceStartTime}_uvd.json",
            "${userId}_${serviceStartTime}_event.json"
        )

        scope.launch {
            val bearer = awaitBearer()
            if (bearer.isNullOrBlank()) {
                fileNames.forEach { fn ->
                    progress(FileUploadResult(fn, false, "no access token"))
                }
                done(0, fileNames.size)
                return@launch
            }

            var uploaded = 0
            var failed = 0
            fileNames.forEach { fileName ->
                val result = uploadFile(fileName, sectorId, bearer)
                progress(result)
                if (result.success) uploaded += 1 else failed += 1
            }
            done(uploaded, failed)
        }
    }

    private suspend fun uploadFile(
        fileName: String,
        sectorId: Int,
        bearer: String
    ): FileUploadResult {
        val file = File(application.filesDir, fileName)
        if (!file.exists()) {
            return FileUploadResult(fileName, false, "file not found")
        }
        val bytes = runCatching { file.readBytes() }
            .getOrElse { return FileUploadResult(fileName, false, "read failed: ${it.message}") }
        if (bytes.isEmpty()) {
            file.delete()
            return FileUploadResult(fileName, false, "file empty")
        }

        val presignResponse = runCatching {
            collectionsApi.postCollectionPresign(
                collectionVersion = ApiServices.COLLECTION_VERSION,
                authorization = bearer,
                request = CollectionPresignRequest(
                    sector_id = sectorId,
                    operating_system = operatingSystem,
                    file_name = fileName
                )
            )
        }.getOrElse {
            return FileUploadResult(fileName, false, "presign error: ${it.message}")
        }

        Log.d("CheckUpdateFile", "presignResponse : $presignResponse")

        if (!presignResponse.isSuccessful) {
            val errMsg = presignResponse.errorBody()?.string().orEmpty()
            return FileUploadResult(fileName, false, "presign HTTP ${presignResponse.code()} $errMsg")
        }

        val presign = presignResponse.body()
        val presignedUrl = presign?.presigned_url.orEmpty()
        if (presignedUrl.isBlank()) {
            return FileUploadResult(fileName, false, "empty presigned url")
        }

        val contentType = presign?.content_type.orEmpty().ifBlank { "application/json" }
        val mediaType = contentType.toMediaTypeOrNull()
        val body = bytes.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(presignedUrl.trim())
            .put(body)
            .build()

        val ok = runCatching {
            putClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrElse {
            return FileUploadResult(fileName, false, "put error: ${it.message}")
        }

        if (!ok) {
            return FileUploadResult(fileName, false, "put failed")
        }

        if (!file.delete()) {
            Log.w(tag, "uploaded but failed to delete $fileName")
        }
        return FileUploadResult(fileName, true, "ok bytes=${bytes.size}")
    }

    private suspend fun awaitBearer(): String? {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            AuthService.bearerToken { token ->
                if (cont.isActive) cont.resumeWith(Result.success(token))
            }
        }
    }
}
