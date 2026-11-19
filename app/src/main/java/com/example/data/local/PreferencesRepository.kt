package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.PdfDpiPreset
import com.example.domain.model.PdfPageSize
import com.example.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "convert_flow_prefs")

data class UserPreferences(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val defaultQuality: Int = 85,
    val defaultPdfDpi: PdfDpiPreset = PdfDpiPreset.EBOOK_150,
    val defaultPdfPageSize: PdfPageSize = PdfPageSize.A4,
    val stripExifByDefault: Boolean = true
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DEFAULT_QUALITY = intPreferencesKey("default_quality")
        val DEFAULT_PDF_DPI = stringPreferencesKey("default_pdf_dpi")
        val DEFAULT_PDF_PAGE_SIZE = stringPreferencesKey("default_pdf_page_size")
        val STRIP_EXIF = booleanPreferencesKey("strip_exif")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            theme = prefs[Keys.THEME]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                ?: ThemePreference.SYSTEM,
            defaultQuality = (prefs[Keys.DEFAULT_QUALITY] ?: 85).coerceIn(10, 100),
            defaultPdfDpi = prefs[Keys.DEFAULT_PDF_DPI]?.let { runCatching { PdfDpiPreset.valueOf(it) }.getOrNull() }
                ?: PdfDpiPreset.EBOOK_150,
            defaultPdfPageSize = prefs[Keys.DEFAULT_PDF_PAGE_SIZE]?.let { runCatching { PdfPageSize.valueOf(it) }.getOrNull() }
                ?: PdfPageSize.A4,
            stripExifByDefault = prefs[Keys.STRIP_EXIF] ?: true
        )
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setDefaultQuality(quality: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_QUALITY] = quality.coerceIn(10, 100) }
    }

    suspend fun setDefaultPdfDpi(dpi: PdfDpiPreset) {
        context.dataStore.edit { it[Keys.DEFAULT_PDF_DPI] = dpi.name }
    }

    suspend fun setDefaultPdfPageSize(pageSize: PdfPageSize) {
        context.dataStore.edit { it[Keys.DEFAULT_PDF_PAGE_SIZE] = pageSize.name }
    }

    suspend fun setStripExif(strip: Boolean) {
        context.dataStore.edit { it[Keys.STRIP_EXIF] = strip }
    }
}
