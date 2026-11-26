package com.cybertube.app.account

import android.content.Context

/**
 * AccountProvider implementation for a microG-compatible environment.
 *
 * This class deliberately does not collect a Google password and does not
 * contain credentials in source control. OAuth authorization will be started
 * only after CyberTube has a valid Android OAuth client configuration.
 */
class MicroGAccountProvider : AccountProvider {

    private var state: AccountState = AccountState.SignedOut

    override fun environment(context: Context): MicroGEnvironmentState {
        return MicroGEnvironment.inspect(context)
    }

    override fun currentState(): AccountState {
        return state
    }

    override suspend fun connect(context: Context): AccountState {
        val env = environment(context)

        state = when {
            !env.ready -> AccountState.Error(
                "ไม่พบ microG Services ที่พร้อมใช้งาน"
            )

            !OAuthConfig.isConfigured -> AccountState.Error(
                "ยังไม่ได้กำหนด OAuth client สำหรับ com.cybertube.app"
            )

            else -> AccountState.Error(
                "OAuth พร้อมตั้งค่าแล้ว แต่ authorization flow ยังไม่ได้เชื่อมต่อ"
            )
        }

        return state
    }

    override suspend fun disconnect(context: Context): AccountState {
        state = AccountState.SignedOut
        return state
    }
}
