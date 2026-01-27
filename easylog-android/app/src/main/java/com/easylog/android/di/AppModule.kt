package com.easylog.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.easylog.android.R
import com.easylog.android.data.auth.GoogleAuthClient
import com.easylog.android.data.repository.AuthTokenProvider
import com.easylog.android.data.repository.CustomerRepository
import com.easylog.android.data.repository.FirebaseAuthRepository
import com.easylog.android.data.repository.TimeLogRepository
import com.easylog.android.ui.customer.AddCustomerViewModel
import com.easylog.android.ui.customer.CustomerSelectionViewModel
import com.easylog.android.ui.home.HomeViewModel
import com.easylog.android.ui.job.ActiveJobViewModel
import com.easylog.android.ui.job.CompleteJobViewModel
import com.easylog.android.ui.history.HistoryViewModel
import com.easylog.android.ui.job.SignatureViewModel
import com.easylog.android.ui.job.StartJobViewModel
import com.easylog.android.ui.login.LoginViewModel
import com.easylog.android.ui.settings.SettingsViewModel
import com.easylog.android.storage.StorageManager
import com.easylog.android.sync.SyncManager
import com.easylog.android.util.PhotoManager
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "easylog_preferences")

val appModule = module {
    single {
        androidContext().dataStore
    }

    single { AuthTokenProvider(get()) }

    // Google Auth Client for Firebase Authentication
    single {
        GoogleAuthClient(
            context = androidContext(),
            webClientId = androidContext().getString(R.string.default_web_client_id)
        )
    }

    // Firebase Auth Repository (Google Sign-in + Firestore)
    single { FirebaseAuthRepository(get(), get(), get()) }

    single { CustomerRepository(get()) }
    single { StorageManager(androidContext()) }
    single { TimeLogRepository(get(), get(), get(), androidContext()) }
    single { SyncManager(androidContext()) }
    single { PhotoManager(androidContext()) }

    // ViewModels using FirebaseAuthRepository
    viewModel { LoginViewModel(get<FirebaseAuthRepository>(), get()) }
    viewModel { HomeViewModel(get<FirebaseAuthRepository>(), get()) }
    viewModel { CustomerSelectionViewModel(get()) }
    viewModel { AddCustomerViewModel(get(), get()) }
    viewModel { StartJobViewModel(get(), get(), get()) }
    viewModel { ActiveJobViewModel(get()) }
    viewModel { CompleteJobViewModel(get(), get(), get()) }
    viewModel { SignatureViewModel(get(), get(), get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { SettingsViewModel(get<FirebaseAuthRepository>(), get()) }
}
