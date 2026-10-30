package com.devtraining.ticketsapp

import android.app.Application
import android.content.Context
import com.devtraining.ticketsapp.data.local.TokenStore
import com.devtraining.ticketsapp.data.remote.AuthInterceptor
import com.devtraining.ticketsapp.data.remote.TicketsApi
import com.devtraining.ticketsapp.data.repository.AuthRepository
import com.devtraining.ticketsapp.data.repository.BookingRepository
import com.devtraining.ticketsapp.data.repository.EventRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

class TicketsApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency injection: one instance of each dependency for the whole app. */
class AppContainer(context: Context) {

    val tokenStore = TokenStore(context)

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenStore))
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
                redactHeader("Authorization")
            },
        )
        .build()

    private val api: TicketsApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create()

    val authRepository = AuthRepository(api, tokenStore, json)
    val eventRepository = EventRepository(api, json)
    val bookingRepository = BookingRepository(api, json)
}
