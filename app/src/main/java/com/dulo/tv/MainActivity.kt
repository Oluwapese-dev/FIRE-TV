package com.dulo.tv

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this)
        setContentView(webView)

        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        
        // This makes sure the mouse toggle and d-pad interactions work smoothly
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        
        // Set focusable so D-Pad can interact with elements
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        webView.webViewClient = WebViewClient()
        
        // Load the specified URL
        webView.loadUrl("https://dulo.cx")
    }

    // Handle back button to go back in web history if possible, otherwise exit
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
