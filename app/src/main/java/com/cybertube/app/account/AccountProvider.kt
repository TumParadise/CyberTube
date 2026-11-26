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

    fun environment(
        context: Context,
    ): MicroGEnvironmentState

    fun currentState(): AccountState

    suspend fun connect(
        context: Context,
    ): AccountState

    suspend fun disconnect(
        context: Context,
    ): AccountState
}
