package com.airshare.app.util

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object Prefs {
    private val FOLDER_URI = stringPreferencesKey("folder_uri")
    private val PASSWORD = stringPreferencesKey("password")

    suspend fun getFolderUri(context: Context): Uri? {
        val uriString: String? = context.dataStore.data.map { it[FOLDER_URI] }.first()
        return uriString?.let { Uri.parse(it) }
    }

    suspend fun setFolderUri(context: Context, uri: Uri) {
        context.dataStore.edit { it[FOLDER_URI] = uri.toString() }
    }

    suspend fun getPassword(context: Context): String? {
        val pw: String? = context.dataStore.data.map { it[PASSWORD] }.first()
        return pw?.ifEmpty { null }
    }

    suspend fun setPassword(context: Context, password: String) {
        context.dataStore.edit { it[PASSWORD] = password }
    }
}
