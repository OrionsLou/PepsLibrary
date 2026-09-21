package app.pepslibrary.network

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Ao3Http {
    /**
     * OkHttp client for AO3. [userAgent] must be the WebView's own user-agent: bot-check cookies are tied to it,
     * so a different one can make AO3 reject a request the WebView itself would be allowed to make.
     */
    fun createClient(userAgent: String, cookieJar: CookieJar = WebViewCookieJar()): OkHttpClient =
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
}
