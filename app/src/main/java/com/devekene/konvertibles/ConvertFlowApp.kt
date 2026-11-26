package com.devekene.konvertibles

import android.app.Application
import com.devekene.konvertibles.data.converter.ConversionEngine
import com.devekene.konvertibles.data.local.AppDatabase
import com.devekene.konvertibles.data.local.HistoryRepository
import com.devekene.konvertibles.data.local.PreferencesRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class AppContainer(application: Application) {
    val database: AppDatabase = AppDatabase.getInstance(application)
    val historyRepository: HistoryRepository = HistoryRepository(database.conversionDao())
    val preferencesRepository: PreferencesRepository = PreferencesRepository(application)
    val conversionEngine: ConversionEngine = ConversionEngine(application, historyRepository)
}

class ConvertFlowApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}
