package com.focus.gpc_app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // غيّر الرابط ده لرابط الموقع النهائي بتاعك
    private val appUrl = "https://gpc-app-clean.vercel.app/"

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    // ===== اختيار/تصوير الصور (زرار "📷 تصوير الفاتورة" وصور المنتجات) =====
    // بدون الجزء ده، أي <input type="file"> جوه التطبيق مابيعملش حاجة خالص.
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback
            filePathCallback = null
            val cameraUri = cameraImageUri
            cameraImageUri = null

            val picked: Uri? = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
            val uris: Array<Uri>? = when {
                result.resultCode != Activity.RESULT_OK -> null
                picked != null -> arrayOf(picked)
                cameraUri != null -> arrayOf(cameraUri)
                else -> null
            }

            // لو الكاميرا اتلغت أو اختار من المعرض: امسح الملف الفاضي اللي اتجهّز للكاميرا
            if (cameraUri != null && (uris == null || uris[0] != cameraUri)) {
                try { contentResolver.delete(cameraUri, null, null) } catch (e: Exception) { }
            }
            callback?.onReceiveValue(uris)
        }

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

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: WebChromeClient.FileChooserParams?
            ): Boolean {
                if (callback == null) return false
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                cameraImageUri = null

                // تجهيز ملف للكاميرا في مجلد Pictures/GPC (بدون أي صلاحيات على أندرويد 10 فما فوق)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "GPC_" + System.currentTimeMillis() + ".jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GPC")
                    }
                    cameraImageUri = try {
                        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    } catch (e: Exception) { null }
                }

                val cameraIntent: Intent? = cameraImageUri?.let { uri ->
                    Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, uri)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }

                val launchIntent: Intent =
                    if (params?.isCaptureEnabled == true && cameraIntent != null) {
                        cameraIntent // زرار الكاميرا: يفتح الكاميرا مباشرة
                    } else {
                        val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }
                        Intent.createChooser(pick, "اختر صورة").apply {
                            if (cameraIntent != null) putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                        }
                    }

                return try {
                    fileChooserLauncher.launch(launchIntent)
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    cameraImageUri?.let { try { contentResolver.delete(it, null, null) } catch (ex: Exception) { } }
                    cameraImageUri = null
                    callback.onReceiveValue(null)
                    false
                }
            }
        }

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
