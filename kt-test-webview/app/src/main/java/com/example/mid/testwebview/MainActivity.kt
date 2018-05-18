package com.example.mid.testwebview

import android.content.pm.ApplicationInfo
import android.net.http.SslError
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.mid.testwebview.jsonplaceholder.JsonPlaceHolderService
import com.example.mid.testwebview.php.PhpServerService
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.function.Consumer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.*

class MainActivity : AppCompatActivity() {

    companion object {
        @JvmStatic lateinit var TAG:String
    }

    init {
        TAG = MainActivity.javaClass.name

    }

    lateinit var webView:WebView;
    val serverUrl = BuildConfig.serverUrl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (0 != (ApplicationInfo.FLAG_DEBUGGABLE and (applicationInfo?.flags ?: 0))
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }


        webView = findViewById<WebView>(R.id.WebView1)
        webView.setInitialScale(1)
        with (webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        webView.webViewClient =  object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }
        }
        webView.loadUrl(serverUrl + "phpinfo.php")

        val certPinner = CertificatePinner.Builder()
                .add(BuildConfig.pinningAlias, BuildConfig.pinningSha256)
                .build()

        val builder = getCustomTrustOkHttpClient(certPinner)

        val retrofit = Retrofit.Builder()
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl(serverUrl)
                .client(builder.build())
                .build()
        val phpServerService = retrofit.create(PhpServerService::class.java)
        phpServerService.callApi()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe({
                    hmacData -> Log.d(TAG, "Server HMAC " + hmacData.hmacValue)
                }, { error ->
                    error.printStackTrace()
                })

        Log.d(TAG, "Calculated HMAC " + hmacSha256("Hello, World", "0011223344"))

//        var r2 = Retrofit.Builder()
//                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
//                .addConverterFactory(GsonConverterFactory.create())
//                .baseUrl("https://jsonplaceholder.typicode.com/")
//                .client(getTrustAllOkHttpClient(certPinner).build())
//                .build()
//        val jsonPlaceHolderService = r2.create(JsonPlaceHolderService::class.java)
//        jsonPlaceHolderService.posts()
//                .observeOn(AndroidSchedulers.mainThread())
//                .subscribeOn(Schedulers.io())
//                .subscribe({
//                    posts -> posts.forEach(Consumer { post -> Log.d(TAG, post.title ) })
//                })
    }

    @Throws(Exception::class)
    fun hmacSha256(str:String, secret:String): String {
        val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")

        val hmacSha256 = Mac.getInstance("HmacSHA256")
        hmacSha256.init(secretKey)

        return Base64.encodeToString(
                hmacSha256.doFinal(str.toByteArray()),
                Base64.DEFAULT
        )
    }

    fun getDefaultOkHttpClient(certPinner: CertificatePinner):OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
                .certificatePinner(certPinner)
        return builder
    }

    fun getTrustAllOkHttpClient(certPinner:CertificatePinner):OkHttpClient.Builder {
        val trustAllCerts = arrayOf<TrustManager>(object: X509TrustManager {
            @Throws(CertificateException::class)
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {

            }

            @Throws(CertificateException::class)
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {

            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                return arrayOf()
            }
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        val sslSocketFactory = sslContext.socketFactory
        val builder = OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .certificatePinner(certPinner)
                .hostnameVerifier { _, _ -> true }

        return builder
    }

    fun getCustomTrustOkHttpClient(certPinner: CertificatePinner):OkHttpClient.Builder {
        val keyStorePassword = "password"

        val certStream = applicationContext.resources.openRawResource(R.raw.ssl_cert_snakeoil)
        val certificates = CertificateFactory.getInstance("X.509")
                .generateCertificates(certStream)

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, keyStorePassword.toCharArray())
        keyStore.setCertificateEntry("1", certificates.first())

        val keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm()
        )
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray())

        val trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(keyStore)

        val trustManagers = trustManagerFactory.trustManagers

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustManagers, java.security.SecureRandom())
        val sslSocketFactory = sslContext.socketFactory
        val builder = OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustManagers[0] as X509TrustManager)
                .certificatePinner(certPinner)
                .hostnameVerifier { _, _ -> true }

        return builder
    }
}
