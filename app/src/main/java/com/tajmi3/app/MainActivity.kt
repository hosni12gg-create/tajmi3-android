package com.tajmi3.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.webkit.WebViewAssetLoader
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val bgDark = Color.parseColor("#0F1115")
    private val bgLight = Color.parseColor("#ECEEF2")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        live = this

        window.setDecorFitsSystemWindows(false)

        root = FrameLayout(this)
        root.setBackgroundColor(bgDark)

        web = WebView(this)
        web.setBackgroundColor(bgDark)
        root.addView(
            web,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        setContentView(root)

        // keep the page clear of the status bar, navigation bar and keyboard
        root.setOnApplyWindowInsetsListener { v, insets ->
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(i.left, i.top, i.right, i.bottom)
            WindowInsets.CONSUMED
        }

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "TajmiAndroid")

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return request?.let { loader.shouldInterceptRequest(it.url) }
            }

            // product links open in the phone's browser
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                if (uri.host == "appassets.androidplatform.net") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: Exception) {
                }
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("موافق") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("موافق") { _, _ -> result?.confirm() }
                    .setNegativeButton("إلغاء") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsPrompt(
                view: WebView?, url: String?, message: String?,
                defaultValue: String?, result: JsPromptResult?
            ): Boolean {
                val input = EditText(this@MainActivity)
                input.setText(defaultValue ?: "")
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton("موافق") { _, _ -> result?.confirm(input.text.toString()) }
                    .setNegativeButton("إلغاء") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            // choosing images from the phone
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    startActivityForResult(fileChooserParams!!.createIntent(), REQ_FILE)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }

        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        } else {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) LiveNotifier.showLast(applicationContext)
    }

    /** Called when the watch sent a new amount while the app is open. */
    fun kick() = js("window.pullWatch&&window.pullWatch()")

    companion object {
        @Volatile
        var live: MainActivity? = null
    }

    override fun onDestroy() {
        if (live === this) live = null
        super.onDestroy()
    }

    private fun js(code: String) = runOnUiThread { web.evaluateJavascript(code, null) }

    private fun googleSignIn(webClientId: String) {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        CredentialManager.create(this).getCredentialAsync(
            this, request, null, mainExecutor,
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    val c = result.credential
                    if (c is CustomCredential &&
                        c.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        val token = GoogleIdTokenCredential.createFrom(c.data).idToken
                        js("window.onGoogleToken(" + JSONObject.quote(token) + ")")
                    } else {
                        js("window.onGoogleError('unsupported')")
                    }
                }

                override fun onError(e: GetCredentialException) {
                    val why = if (e is GetCredentialCancellationException) "cancel" else (e.message ?: "error")
                    js("window.onGoogleError(" + JSONObject.quote(why) + ")")
                }
            }
        )
    }

    private fun applyTheme(dark: Boolean) {
        val c = if (dark) bgDark else bgLight
        root.setBackgroundColor(c)
        web.setBackgroundColor(c)
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (dark) 0 else mask, mask)
    }

    /** Called from index.html */
    inner class Bridge {
        @JavascriptInterface
        fun update(saved: Double, target: Double, cur: String, name: String) {
            LiveNotifier.update(applicationContext, saved, target, cur, name)
        }

        @JavascriptInterface
        fun watch(saved: Double, target: Double, cur: String, name: String, quick: String) {
            WatchSync.send(applicationContext, saved, target, cur, name, quick)
        }

        @JavascriptInterface
        fun pending(): String = PendingAdds.take(applicationContext)

        @JavascriptInterface
        fun signIn(webClientId: String) {
            runOnUiThread { googleSignIn(webClientId) }
        }

        @JavascriptInterface
        fun signOut() {
            runOnUiThread {
                CredentialManager.create(this@MainActivity).clearCredentialStateAsync(
                    ClearCredentialStateRequest(), null, mainExecutor,
                    object : CredentialManagerCallback<Void?, ClearCredentialException> {
                        override fun onResult(result: Void?) {}
                        override fun onError(e: ClearCredentialException) {}
                    }
                )
            }
        }

        @JavascriptInterface
        fun notif(on: Boolean) {
            LiveNotifier.setEnabled(applicationContext, on)
        }

        @JavascriptInterface
        fun openNotifSettings() {
            runOnUiThread {
                startActivity(
                    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
        }

        @JavascriptInterface
        fun openLiveSettings() {
            runOnUiThread {
                try {
                    startActivity(
                        Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS")
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                    )
                } catch (e: Exception) {
                    openNotifSettings()
                }
            }
        }

        @JavascriptInterface
        fun theme(dark: Boolean) {
            runOnUiThread { applyTheme(dark) }
        }
    }

    companion object {
        private const val REQ_FILE = 11
        private const val REQ_NOTIF = 12
    }
}
