package com.example.privatemusiclibrary.di

import com.example.privatemusiclibrary.data.auth.AuthTokenStore
import com.example.privatemusiclibrary.data.network.MixtapeApi
import com.example.privatemusiclibrary.data.network.RefreshRequest
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Using BuildConfig for environment-specific URLs
    private val BASE_URL = com.example.privatemusiclibrary.BuildConfig.API_BASE_URL

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenStore: AuthTokenStore): Interceptor {
        return Interceptor { chain ->
            val original = chain.request()
            // Don't add token to auth routes
            if (original.url.encodedPath.contains("/auth/")) {
                return@Interceptor chain.proceed(original)
            }
            
            val token = tokenStore.getAccessToken()
            if (token != null) {
                val request = original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            } else {
                chain.proceed(original)
            }
        }
    }

    @Provides
    @Singleton
    fun provideAuthenticator(
        tokenStore: AuthTokenStore,
        // Lazy injection of Retrofit to avoid circular dependency
        retrofitProvider: Provider<Retrofit> 
    ): Authenticator {
        return object : Authenticator {
            override fun authenticate(route: Route?, response: Response): Request? {
                // If it's a 401 on the refresh endpoint itself, give up to avoid loops
                if (response.request.url.encodedPath.contains("/auth/refresh")) {
                    tokenStore.clear()
                    return null
                }
                
                val refreshToken = tokenStore.getRefreshToken() ?: return null

                return synchronized(this) {
                    val currentToken = tokenStore.getAccessToken()
                    val requestHeaderToken = response.request.header("Authorization")?.removePrefix("Bearer ")
                    
                    // If token changed while waiting for lock, just retry with new token
                    if (requestHeaderToken != currentToken && currentToken != null) {
                        return@synchronized response.request.newBuilder()
                            .header("Authorization", "Bearer $currentToken")
                            .build()
                    }

                    // Otherwise, we need to refresh
                    val api = retrofitProvider.get().create(MixtapeApi::class.java)
                    val result = runBlocking {
                        try {
                            api.refresh(RefreshRequest(refreshToken))
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (result != null && result.isSuccessful) {
                        result.body()?.let {
                            tokenStore.save(it.accessToken, it.refreshToken)
                            return@synchronized response.request.newBuilder()
                                .header("Authorization", "Bearer ${it.accessToken}")
                                .build()
                        }
                    }

                    // Refresh failed, clear session
                    tokenStore.clear()
                    null
                }
            }
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: Interceptor,
        authenticator: Authenticator
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideMixtapeApi(retrofit: Retrofit): MixtapeApi {
        return retrofit.create(MixtapeApi::class.java)
    }
}
