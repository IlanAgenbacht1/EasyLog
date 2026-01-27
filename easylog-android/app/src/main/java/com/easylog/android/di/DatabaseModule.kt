package com.easylog.android.di

import androidx.room.Room
import com.easylog.android.data.local.EasyLogDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            EasyLogDatabase::class.java,
            EasyLogDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    single { get<EasyLogDatabase>().engineerDao() }
    single { get<EasyLogDatabase>().customerDao() }
    single { get<EasyLogDatabase>().pendingUploadDao() }
}
