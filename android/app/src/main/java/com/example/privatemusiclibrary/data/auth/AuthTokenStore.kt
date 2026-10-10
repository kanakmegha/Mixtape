package com.example.privatemusiclibrary.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AuthTokenStore @Inject constructor(
    @param:ApplicationContext private val context: Context?
) {
    private val masterKey by lazy {
        context?.let {
            MasterKey.Builder(it)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }
    }

    private val sharedPreferences by lazy {
        if (context != null && masterKey != null) {
            EncryptedSharedPreferences.create(
                context,
                "auth_prefs",
                masterKey!!,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } else null
    }

    open fun save(accessToken: String, refreshToken: String) {
        sharedPreferences?.edit()
            ?.putString("access_token", accessToken)
            ?.putString("refresh_token", refreshToken)
            ?.apply()
    }

    open fun getAccessToken(): String? = sharedPreferences?.getString("access_token", null)

    open fun getRefreshToken(): String? = sharedPreferences?.getString("refresh_token", null)

    open fun clear() {
        sharedPreferences?.edit()?.clear()?.apply()
    }
}

