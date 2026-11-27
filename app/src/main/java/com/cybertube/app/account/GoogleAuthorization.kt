package com.cybertube.app.account

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

object GoogleAuthorization {
    private val youtubeReadOnly = Scope("https://<scrubbed-url-domain>/auth/youtube.readonly")

    fun authorize(
        activity: Activity,
        onResolutionRequired: (android.app.PendingIntent) -> Unit,
        onAuthorized: (AuthorizationResult) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(youtubeReadOnly))
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    result.pendingIntent?.let(onResolutionRequired)
                        ?: onError(IllegalStateException("ไม่พบหน้าต่างขออนุญาตจาก Google"))
                } else {
                    onAuthorized(result)
                }
            }
            .addOnFailureListener(onError)
    }

    fun resultFromIntent(activity: Activity, data: Intent?): Result<AuthorizationResult> =
        runCatching {
            Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
        }.recoverCatching { error ->
            if (error is ApiException) throw error
            throw error
        }
}
