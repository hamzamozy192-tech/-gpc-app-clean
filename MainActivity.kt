package com.focus.gpc_app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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

    // جسر بين صفحة الويب وأندرويد: بيسمح لأزرار "طباعة" و"إرسال" في التطبيق
    // إنها تستخدم نظام الطباعة الحقيقي وقائمة المشاركة الحقيقية بتاعة أندرويد
    inner class WebAppInterface(private val ctx: Context) {
        @JavascriptInterface
        fun shareText(text: String) {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(intent, "إرسال عبر"))
            }
        }

        @JavascriptInterface
        fun printText(text: String) {
            runOnUiThread { doPrint(text) }
        }
    }

    private fun doPrint(text: String) {
        val printWebView = WebView(this)
        printWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "GPC Invoice"
                val adapter = printWebView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, adapter, PrintAttributes.Builder().build())
            }
        }
        val htmlBody = text.replace("\n", "<br>")
        val html = "<html dir='rtl'><body style='font-family:sans-serif; font-size:16px; line-height:1.8; padding:16px;'>$htmlBody</body></html>"
        printWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

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

        webView.addJavascriptInterface(WebAppInterface(this), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }
        }

        webView.webChromeClient = WebChromeClient()

        webView.loadUrl(appUrl)
    }

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
