package com.gourav.investnest.di

import android.content.Context
import androidx.room.Room
import com.gourav.investnest.data.local.ExploreCacheDao
import com.gourav.investnest.data.local.InvestNestDatabase
import com.gourav.investnest.data.local.WatchlistDao
import com.gourav.investnest.data.remote.MfApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logger)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.mfapi.in/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideMfApiService(
        retrofit: Retrofit,
    ): MfApiService {
        return retrofit.create(MfApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): InvestNestDatabase {
        return Room.databaseBuilder(
            context,
            InvestNestDatabase::class.java,
            "invest_nest.db",
        ).build()
    }

    @Provides
    fun provideExploreCacheDao(
        database: InvestNestDatabase,
    ): ExploreCacheDao {
        return database.exploreCacheDao()
    }

    @Provides
    fun provideWatchlistDao(
        database: InvestNestDatabase,
    ): WatchlistDao {
        return database.watchlistDao()
    }
}
