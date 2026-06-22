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
 * Stores the optional credentials the app uses to download license-gated model
 * files directly:
 *  - a **Hugging Face** access token (sent as `Authorization: Bearer …`), and
 *  - a **Kaggle** username + API key (sent as HTTP Basic auth).
 *
 * Both live only in app-private storage on this device and are sent nowhere
 * except to huggingface.co / kaggle.com when fetching a model file. Inference
 * always stays on-device.
 */
object ModelAuth {

    private val HF_TOKEN = stringPreferencesKey("hf_token")
    private val KAGGLE_USERNAME = stringPreferencesKey("kaggle_username")
    private val KAGGLE_KEY = stringPreferencesKey("kaggle_key")

    // ---- Hugging Face token ---------------------------------------------

    fun tokenFlow(context: Context): Flow<String?> =
        context.applicationContext.modelAuthStore.data
            .map { prefs -> prefs[HF_TOKEN]?.takeIf { it.isNotBlank() } }

    suspend fun setToken(context: Context, token: String?) {
        context.applicationContext.modelAuthStore.edit { prefs ->
            val clean = token?.trim().orEmpty()
            if (clean.isEmpty()) prefs.remove(HF_TOKEN) else prefs[HF_TOKEN] = clean
        }
    }

    // ---- Kaggle username + key ------------------------------------------

    /** Kaggle credentials; [isComplete] is true only when both halves are set. */
    data class KaggleCreds(val username: String, val key: String) {
        val isComplete: Boolean get() = username.isNotBlank() && key.isNotBlank()
    }

    fun kaggleFlow(context: Context): Flow<KaggleCreds> =
        context.applicationContext.modelAuthStore.data.map { prefs ->
            KaggleCreds(prefs[KAGGLE_USERNAME].orEmpty(), prefs[KAGGLE_KEY].orEmpty())
        }

    suspend fun setKaggle(context: Context, username: String?, key: String?) {
        context.applicationContext.modelAuthStore.edit { prefs ->
            val u = username?.trim().orEmpty()
            val k = key?.trim().orEmpty()
            if (u.isEmpty()) prefs.remove(KAGGLE_USERNAME) else prefs[KAGGLE_USERNAME] = u
            if (k.isEmpty()) prefs.remove(KAGGLE_KEY) else prefs[KAGGLE_KEY] = k
        }
    }
}
