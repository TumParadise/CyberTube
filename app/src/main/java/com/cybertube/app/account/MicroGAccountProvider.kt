package com.cybertube.app.account

import android.content.Context
import com.cybertube.app.core.Store
import com.cybertube.app.core.YouTubeApi

/**
 * AccountProvider สำหรับเครื่องที่มี Google Services หรือ microG
 * Access token ไม่ถูกเก็บลงเครื่อง (หมดอายุเร็ว) ขอใหม่ผ่าน GoogleAuthorization ทุกครั้งที่ต้องใช้
 */
class MicroGAccountProvider : AccountProvider {

    override fun environment(context: Context): MicroGEnvironmentState =
        MicroGEnvironment.inspect(context)

    override fun currentState(): AccountState =
        Store.account.value?.let { AccountState.SignedIn(it) } ?: AccountState.SignedOut

    override suspend fun signIn(accessToken: String): AccountState =
        runCatching { YouTubeApi.me(accessToken) }
            .fold(
                onSuccess = { me ->
                    val acc = CyberTubeAccount(
                        accountName = me.id ?: "google-account",
                        displayName = me.name,
                        avatarUrl = me.avatar,
                    )
                    Store.setAccount(acc)
                    AccountState.SignedIn(acc)
                },
                onFailure = { AccountState.Error(YouTubeApi.describe(it)) },
            )

    override suspend fun importSubscriptions(accessToken: String): Result<Int> =
        runCatching {
            val list = YouTubeApi.subscriptions(accessToken)
            Store.addSubs(list)
        }

    override fun disconnect(): AccountState {
        Store.setAccount(null)
        return AccountState.SignedOut
    }
}
