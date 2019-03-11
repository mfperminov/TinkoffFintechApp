package com.example.tinkofffintechapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import io.reactivex.Single
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface CurrencyApiService {

    @GET("convert")
    fun convert(@Query("q") query: String,
                @Query("compact") compact: String = "y"
               ): Single<Map<String, ConversationResult>>

    companion object Factory {
        fun create(okHttpClient: OkHttpClient): CurrencyApiService{
            val retrofit = Retrofit.Builder()
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl("https://free.currencyconverterapi.com/api/v6/")
                .client(okHttpClient)
                .build()

            return retrofit.create(CurrencyApiService::class.java)
        }
    }
}

class CacheControl(private val context: Context) {

    fun hasNetwork(context: Context): Boolean {
        var isConnected = false // Initial Value
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo
        if (activeNetwork != null && activeNetwork.isConnected)
            isConnected = true
        return isConnected
    }

    // размер кэша 1 мб
    private val cacheSize = (1 * 1024 * 1024).toLong()

    private val myCache = Cache(context.cacheDir, cacheSize)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cache(myCache)
        .addInterceptor { chain ->
            var request = chain.request()
            request = if (hasNetwork(context))
                // при наличии интернета делаем повторный запрос если кэш не позднее 10 секунд
                request.newBuilder().header("Cache-Control", "public, max-age=" + 10).build()
            else
                // при отсутствии кэш должен быть не позднее 1 часа
                request.newBuilder().header("Cache-Control", "public, only-if-cached, max-stale=" + 60 * 60 * 1).build()
            chain.proceed(request)
        }
        .build()
}