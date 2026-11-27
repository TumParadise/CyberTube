package com.cybertube.app.account

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

    fun currentState(): AccountState

    /** เรียกหลังผู้ใช้ยืนยันรหัสที่ google.com/device แล้ว: ดึงชื่อช่องมาแสดงและจำไว้ในเครื่อง */
    suspend fun signIn(): AccountState

    /** ดึงรายการช่องที่ติดตามจากบัญชี แล้วเพิ่มเข้า Store (ไม่ซ้ำของเดิม) คืนจำนวนช่องที่เพิ่มใหม่ */
    suspend fun importSubscriptions(): Result<Int>

    /** ออกจากระบบในแอป (ลบ token และข้อมูลบัญชีที่จำไว้ ไม่ลบช่องที่ติดตามแล้ว) */
    fun disconnect(): AccountState
}
