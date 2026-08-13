package ru.runa.wfe.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private const val PREFERENCES_NAME = "app_preferences"
private const val PREFERENCES_SUFFIX = "_preferences"

val Context.dataStore by preferencesDataStore(
    name = PREFERENCES_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, context.packageName + PREFERENCES_SUFFIX))
    }
)

class PreferencesManager private constructor(private val context: Context) {
    private val gson: Gson = GsonBuilder().create()
    private val keystoreManager = KeyStoreManager(context)

    suspend fun <T> setKey(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit {
            it[key] = value
        }
    }

    suspend fun <T> hasKey(key: Preferences.Key<T>): Boolean =
        context.dataStore.data.first().contains(key)

    fun <T> getValueFlow(key: Preferences.Key<T>, defaultValue: T): Flow<T> {
        return context.dataStore.data
            .catch { exception ->
                emit(emptyPreferences())
                Log.e(this::class.simpleName, exception.message.toString())
            }
            .map {
                it[key] ?: defaultValue
            }
            .distinctUntilChanged()
    }

    fun <T> getValue(key: Preferences.Key<T>, defaultValue: T): T {
        var value: T
        runBlocking {
            value = context.dataStore.data.first()[key] ?: defaultValue
        }
        return value
    }

    suspend fun <T> getSecureValue(key: Preferences.Key<T>, type: Class<T>): T? {
        try {
            val encryptedValue = context.dataStore.data.first()[key]
            return gson.fromJson(keystoreManager.decrypt(encryptedValue.toString()), type)
        } catch (ex: Exception) {
            Log.e(this.javaClass.simpleName, ex.message.toString())
        }
        return null
    }

    suspend fun setSecureKey(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit {
            try {
                val encryptedValue = keystoreManager.encrypt(value)
                it[key] = encryptedValue
            } catch (ex: Exception) {
                Log.e(this.javaClass.simpleName, ex.message.toString())
            }
        }
    }

    suspend fun <T> deleteKeyValue(key: Preferences.Key<T>) {
        context.dataStore.edit {
            it.remove(key)
        }
    }

    suspend fun clearPreferences() {
        context.dataStore.edit {
            it.clear()
        }
    }

    companion object {
        val WEBVIEW_URL = stringPreferencesKey("urlQuery")
        val LAST_VERSION = stringPreferencesKey("last_version")
        val SHOW_URL = booleanPreferencesKey("showUrl")
        val POLLING_INTERVAL = longPreferencesKey("pollingInterval")
        val LAST_CHECK = stringPreferencesKey("lastCheck")
        val TOKEN = stringPreferencesKey("token")

        @Volatile
        private var instance: PreferencesManager? = null
        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}