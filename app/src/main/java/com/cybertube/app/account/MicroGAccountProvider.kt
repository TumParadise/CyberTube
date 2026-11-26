package com.cybertube.app.account

import android.content.Context

/**
 * AccountProvider implementation for a microG-compatible environment.
 * OAuth authorization will be connected in a later step.
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

        state = if (!env.ready) {
            AccountState.Error("ไม่พบ microG Services ที่พร้อมใช้งาน")
        } else {
            AccountState.Error("ระบบ Google authorization ยังไม่ได้เชื่อมต่อ")
        }

        return state
    }

    override suspend fun disconnect(context: Context): AccountState {
        state = AccountState.SignedOut
        return state
    }
}
