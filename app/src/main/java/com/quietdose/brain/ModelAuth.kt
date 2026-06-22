package com.quietdose.brain

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.modelAuthStore: DataStore<Preferences> by preferencesDataStore("dose_model_auth")

/**
 * Stores an optional Hugging Face access token so the app can download
 * license-gated model files directly (it's sent as `Authorization: Bearer …`,
 * the same thing the Kaggle/HF `curl` command does). The token lives only in
 * app-private storage on this device and is never sent anywhere except to
 * huggingface.co when fetching a model file.
 */
object ModelAuth {

    private val HF_TOKEN = stringPreferencesKey("hf_token")

    fun tokenFlow(context: Context): Flow<String?> =
        context.applicationContext.modelAuthStore.data
            .map { prefs -> prefs[HF_TOKEN]?.takeIf { it.isNotBlank() } }

    suspend fun setToken(context: Context, token: String?) {
        context.applicationContext.modelAuthStore.edit { prefs ->
            val clean = token?.trim().orEmpty()
            if (clean.isEmpty()) prefs.remove(HF_TOKEN) else prefs[HF_TOKEN] = clean
        }
    }
}
