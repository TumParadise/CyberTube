package com.cybertube.app.account

import android.content.Context
import android.content.pm.PackageManager

data class MicroGEnvironmentState(
    val servicesInstalled: Boolean,
    val companionInstalled: Boolean,
) {
    // microG Services เป็นส่วนหลักที่เราต้องการตรวจในขั้นแรก
    val ready: Boolean
        get() = servicesInstalled
}

/**
 * ตรวจสอบ environment ที่รองรับ microG
 *
 * ขั้นตอนนี้:
 * - ไม่ขอ Google password
 * - ไม่ขอ OAuth token
 * - ไม่เข้าถึงข้อมูล YouTube
 * - ไม่กระทบ Player ของ CyberTube
 */
object MicroGEnvironment {

    private const val GMS_PACKAGE = "com.google.android.gms"
    private const val VENDING_PACKAGE = "com.android.vending"

    fun inspect(context: Context): MicroGEnvironmentState {
        return MicroGEnvironmentState(
            servicesInstalled =
                context.packageManager.isInstalled(GMS_PACKAGE),

            companionInstalled =
                context.packageManager.isInstalled(VENDING_PACKAGE),
        )
    }

    private fun PackageManager.isInstalled(
        packageName: String
    ): Boolean {
        return runCatching {
            @Suppress("DEPRECATION")
            getPackageInfo(packageName, 0)
        }.isSuccess
    }
}
