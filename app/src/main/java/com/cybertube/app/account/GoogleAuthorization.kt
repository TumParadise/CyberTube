package com.cybertube.app.account

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

object GoogleAuthorization {
    // อ่านอย่างเดียว: ดูโปรไฟล์ช่องและรายการที่ติดตาม ไม่แก้ไขอะไรในบัญชี
    private val youtubeReadOnly = Scope("https://www.googleapis.com/auth/youtube.readonly")

    fun authorize(
        activity: Activity,
        onResolutionRequired: (PendingIntent) -> Unit,
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
        }

    fun describe(e: Throwable): String = when (e) {
        is ApiException -> when (e.statusCode) {
            10 -> "ตั้งค่า OAuth ไม่ตรง (รหัส 10): ตรวจ package name และ SHA-1 ใน Google Cloud Console ตามไฟล์ SETUP_GOOGLE.md"
            12501 -> "ยกเลิกการเชื่อมต่อบัญชี"
            else -> "Google แจ้งข้อผิดพลาด (รหัส ${e.statusCode})"
        }
        else -> e.message ?: "เกิดข้อผิดพลาดที่ไม่ทราบสาเหตุ"
    }
}
