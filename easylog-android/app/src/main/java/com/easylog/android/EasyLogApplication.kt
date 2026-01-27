package com.easylog.android

import android.app.Application
import com.easylog.android.di.appModule
import com.easylog.android.di.databaseModule
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class EasyLogApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Configure Firestore offline persistence FIRST
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@EasyLogApplication)
            modules(listOf(appModule, databaseModule))
        }
    }
}
