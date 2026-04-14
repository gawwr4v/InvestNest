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
// this module tells hilt how to create and provide various dependencies like network and database throughout the app lifecycle
object AppModule {
    @Provides
    @Singleton
    // configures the network client with a logger to see api requests and responses in the logcat during development
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
    // sets up retrofit with the base url and gson converter to handle all network communication with the mutual fund api
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
    // creates the actual api service implementation that the repository uses to fetch fund data
    fun provideMfApiService(
        retrofit: Retrofit,
    ): MfApiService {
        return retrofit.create(MfApiService::class.java)
    }

    @Provides
    @Singleton
    // initializes the room database instance for local data storage and offline caching
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
    // provides the data access object for managing the explore screen cache
    fun provideExploreCacheDao(
        database: InvestNestDatabase,
    ): ExploreCacheDao {
        return database.exploreCacheDao()
    }

    @Provides
    // provides the data access object for managing watchlists and saved funds
    fun provideWatchlistDao(
        database: InvestNestDatabase,
    ): WatchlistDao {
        return database.watchlistDao()
    }
}
