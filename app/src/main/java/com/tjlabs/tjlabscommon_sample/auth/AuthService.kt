package com.tjlabs.tjlabscommon_sample.auth

import android.app.Application
import android.util.Log
import com.tjlabs.tjlabsauth_sdk_android.TJLabsAuthManager
import com.tjlabs.tjlabsauth_sdk_android.TokenResult

object AuthService {

    private const val TAG = "Auth"

    data class AuthResult(
        val success: Boolean,
        val code: Int,
        val tenantName: String,
        val tenantUserName: String
    )

    fun signIn(
        application: Application,
        clientSecret: String,
        accessKey: String,
        secretAccessKey: String,
        completion: (AuthResult) -> Unit
    ) {
        val serverProvider = "gcp"
        val serverRegion = "KOREA"
        val serverService = "jupiter"

        Log.d(TAG, "signIn() inputs:")
        Log.d(TAG, "  clientSecret    = ${mask(clientSecret)} (len=${clientSecret.length})")
        Log.d(TAG, "  accessKey       = ${mask(accessKey)} (len=${accessKey.length})")
        Log.d(TAG, "  secretAccessKey = ${mask(secretAccessKey)} (len=${secretAccessKey.length})")
        Log.d(TAG, "  server          = provider=$serverProvider region=$serverRegion service=$serverService")

        if (clientSecret.isBlank() || accessKey.isBlank() || secretAccessKey.isBlank()) {
            Log.w(TAG, "signIn() aborted: one or more inputs are blank")
        }

        TJLabsAuthManager.setClientSecret(application, clientSecret)
        TJLabsAuthManager.setServerURL(serverProvider, serverRegion, serverService)

        Log.d(TAG, "signIn() requesting auth...")
        val startedAt = System.currentTimeMillis()
        TJLabsAuthManager.auth(accessKey, secretAccessKey) { code, success ->
            val elapsed = System.currentTimeMillis() - startedAt
            val tenantName = TJLabsAuthManager.getTenantName().orEmpty()
            val tenantUserName = TJLabsAuthManager.getTenantUserName().orEmpty()
            val resolvedSuccess = success && tenantUserName.isNotBlank()

            Log.d(TAG, "signIn() response (${elapsed}ms):")
            Log.d(TAG, "  code            = $code")
            Log.d(TAG, "  sdk success     = $success")
            Log.d(TAG, "  tenantName      = '$tenantName'")
            Log.d(TAG, "  tenantUserName  = '$tenantUserName'")
            Log.d(TAG, "  resolved success= $resolvedSuccess")

            completion(
                AuthResult(
                    success = resolvedSuccess,
                    code = code,
                    tenantName = tenantName,
                    tenantUserName = tenantUserName
                )
            )
        }
    }

    fun bearerToken(callback: (String?) -> Unit) {
        Log.d(TAG, "bearerToken() requesting access token...")
        TJLabsAuthManager.getAccessToken { result ->
            when (result) {
                is TokenResult.Success -> {
                    Log.d(TAG, "bearerToken() success token=${mask(result.token)} (len=${result.token.length})")
                    callback("Bearer ${result.token}")
                }
                is TokenResult.Failure -> {
                    Log.w(TAG, "bearerToken() failure result=$result")
                    callback(null)
                }
            }
        }
    }

    fun currentTenantUserName(): String =
        TJLabsAuthManager.getTenantUserName().orEmpty()

    private fun mask(value: String): String {
        if (value.isEmpty()) return "<empty>"
        if (value.length <= 8) return "*".repeat(value.length)
        val head = value.take(4)
        val tail = value.takeLast(4)
        val maskedMiddle = "*".repeat(value.length - 8)
        return "$head$maskedMiddle$tail"
    }
}
