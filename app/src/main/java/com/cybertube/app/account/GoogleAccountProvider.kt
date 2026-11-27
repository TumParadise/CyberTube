package com.cybertube.app.account

import com.cybertube.app.core.Store
import com.cybertube.app.core.YouTubeApi

/** บัญชี Google ผ่าน OAuth Device Code (ไม่พึ่ง Google Play Services / microG) */
class GoogleAccountProvider : AccountProvider {

    override fun currentState(): AccountState =
        if (GoogleAuth.hasRefreshToken()) {
            Store.account.value?.let { AccountState.SignedIn(it) } ?: AccountState.SignedOut
        } else AccountState.SignedOut

    override suspend fun signIn(): AccountState =
        runCatching { YouTubeApi.me(GoogleAuth.accessToken()) }
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

    override suspend fun importSubscriptions(): Result<Int> =
        runCatching { Store.addSubs(YouTubeApi.subscriptions(GoogleAuth.accessToken())) }

    override fun disconnect(): AccountState {
        GoogleAuth.signOut()
        Store.setAccount(null)
        return AccountState.SignedOut
    }
}
