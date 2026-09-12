package com.focus.gpc_app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // غيّر الرابط ده لرابط الموقع النهائي بتاعك
    private val appUrl = "https://gpc-app-clean.vercel.app/"

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        progressBar = findViewById(R.id.progressBar)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }
        }

        webView.webChromeClient = WebChromeClient()

        webView.loadUrl(appUrl)
    }

    // بدل ما نقفل التطبيق فورًا، بنسأل صفحة الويب الأول: "فيه حاجة تقفلها إنت؟"
    // (مودال مفتوح، لوحة المستخدمين، أو تبويب غير الرئيسية). لو قالت "أيوة"،
    // معناه هي اتصرفت وخلاص. لو قالت "لأ"، وقتها بس نرجع للصفحة اللي قبلها
    // أو نقفل التطبيق فعليًا.
    override fun onBackPressed() {
        webView.evaluateJavascript(
            "(function(){ try { return window.handleAndroidBack ? window.handleAndroidBack() : false; } catch(e){ return false; } })();"
        ) { result ->
            val handledByWebApp = result == "true"
            if (!handledByWebApp) {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    super.onBackPressed()
                }
            }
        }
    }
}
