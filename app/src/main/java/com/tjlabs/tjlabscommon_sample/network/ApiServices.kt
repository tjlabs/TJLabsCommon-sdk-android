package com.tjlabs.tjlabscommon_sample.network

import android.util.Log
import com.google.gson.JsonElement
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface TenantApi {
    @retrofit2.http.GET("{tenant_version}/tenants/me/sectors")
    suspend fun getMySectors(
        @retrofit2.http.Path("tenant_version") tenantVersion: String,
        @Header("Authorization") authorization: String
    ): Response<JsonElement>
}

data class CollectionPresignRequest(
    val sector_id: Int,
    val operating_system: String,
    val file_name: String
)

data class CollectionPresignResponse(
    val presigned_url: String = "",
    val object_key: String = "",
    val content_type: String = "",
    val expires_in: Int = 0
)

interface CollectionsApi {
    @POST("{collection_version}/collections")
    suspend fun postCollectionPresign(
        @retrofit2.http.Path("collection_version") collectionVersion: String,
        @Header("Authorization") authorization: String,
        @Body request: CollectionPresignRequest
    ): Response<CollectionPresignResponse>
}

object ApiServices {
    const val TENANT_VERSION = "2025-12-16"
    const val COLLECTION_VERSION = "2026-06-09"
    private const val TAG = "ApiServices"

    private fun client(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    private fun normalizeBaseUrl(raw: String, label: String): String {
        var v = raw.trim()
        // Strip a wrapping or stray leading/trailing quote that can sneak in from local.properties
        if (v.length >= 2 && (v.first() == '"' || v.first() == '\'') && v.first() == v.last()) {
            v = v.substring(1, v.length - 1)
        }
        while (v.isNotEmpty() && (v.first() == '"' || v.first() == '\'')) v = v.substring(1)
        while (v.isNotEmpty() && (v.last() == '"' || v.last() == '\'')) v = v.substring(0, v.length - 1)
        v = v.trim()
        if (!v.endsWith("/")) v = "$v/"
        require(v.startsWith("http://") || v.startsWith("https://")) {
            "$label must start with http:// or https:// but was '$v'"
        }
        Log.d(TAG, "$label baseUrl='$v' (len=${v.length})")
        return v
    }

    fun createTenantApi(baseUrl: String): TenantApi =
        Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl, "tenant"))
            .addConverterFactory(GsonConverterFactory.create())
            .client(client())
            .build()
            .create(TenantApi::class.java)

    fun createCollectionsApi(baseUrl: String): CollectionsApi =
        Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl, "collections"))
            .addConverterFactory(GsonConverterFactory.create())
            .client(client())
            .build()
            .create(CollectionsApi::class.java)
}
