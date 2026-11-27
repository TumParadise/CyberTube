package com.cybertube.app.account

import android.content.Context

data class CyberTubeAccount(
    val accountName: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

sealed interface AccountState {
    data object SignedOut : AccountState
    data object Connecting : AccountState

    data class SignedIn(
        val account: CyberTubeAccount,
    ) : AccountState

    data class Error(
        val message: String,
    ) : AccountState
}

interface AccountProvider {

    fun environment(context: Context): MicroGEnvironmentState

    fun currentState(): AccountState

    /** เรียกหลัง Google ให้สิทธิ์แล้ว: ดึงชื่อช่องมาแสดงและจำไว้ในเครื่อง */
    suspend fun signIn(accessToken: String): AccountState

    /** ดึงรายการช่องที่ติดตามจากบัญชี แล้วเพิ่มเข้า Store (ไม่ซ้ำของเดิม) คืนจำนวนช่องที่เพิ่มใหม่ */
    suspend fun importSubscriptions(accessToken: String): Result<Int>

    /** ออกจากระบบในแอป (ลบข้อมูลบัญชีที่จำไว้ ไม่ลบช่องที่ติดตามแล้ว) */
    fun disconnect(): AccountState
}
