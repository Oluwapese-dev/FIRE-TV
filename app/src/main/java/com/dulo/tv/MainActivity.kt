package com.dulo.tv

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @Volatile
    private var isNativeMode = false
    
    inner class TVInterface {
        @JavascriptInterface
        fun setNativeMode(native: Boolean) {
            isNativeMode = native
            Log.d("TVWebView", "Native mode set to: $native")
        }
    }

    private val cursorScript = """
        javascript:(function() {
            if (window.tvCursorInjected) return;
            window.tvCursorInjected = true;
            
            var cursor = document.createElement('div');
            cursor.id = 'tv-cursor';
            cursor.style.position = 'fixed';
            cursor.style.width = '20px';
            cursor.style.height = '20px';
            cursor.style.backgroundColor = 'rgba(255, 50, 50, 0.8)';
            cursor.style.border = '2px solid white';
            cursor.style.borderRadius = '50%';
            cursor.style.zIndex = '2147483647';
            cursor.style.pointerEvents = 'none';
            cursor.style.boxShadow = '0 0 8px rgba(0,0,0,0.6)';
            
            var cx = window.innerWidth / 2;
            var cy = window.innerHeight / 2;
            cursor.style.left = cx + 'px';
            cursor.style.top = cy + 'px';
            
            document.body.appendChild(cursor);
            
            window.lastHoveredElem = null;
            
            var isNative = false;
            
            function enterNativeMode() {
                isNative = true;
                cursor.style.display = 'none';
                if (window.AndroidTV) window.AndroidTV.setNativeMode(true);
            }
            
            function exitNativeMode() {
                isNative = false;
                cursor.style.display = 'block';
                if (window.AndroidTV) window.AndroidTV.setNativeMode(false);
            }
            
            document.addEventListener('keydown', function(e) {
                if (isNative && e.key === 'ArrowDown') {
                    setTimeout(function() {
                        var ae = document.activeElement;
                        // Exit native mode if focus is lost or moves below the header area
                        if (!ae || ae === document.body || ae.getBoundingClientRect().bottom > 150) {
                            exitNativeMode();
                            cx = window.innerWidth / 2;
                            cy = Math.max(150, (ae && ae !== document.body) ? ae.getBoundingClientRect().bottom + 20 : 150);
                            cursor.style.left = cx + 'px';
                            cursor.style.top = cy + 'px';
                        }
                    }, 50);
                }
            });
            
            function performScroll(sdx, sdy) {
                // Try standard scrolling targets without expensive DOM loops
                var targets = [
                    window,
                    document.getElementById('__next'),
                    document.getElementById('root'),
                    document.getElementById('app'),
                    document.body,
                    document.documentElement
                ];
                
                for (var i = 0; i < targets.length; i++) {
                    var t = targets[i];
                    if (t) {
                        try {
                            t.scrollBy({top: sdy, left: sdx, behavior: 'smooth'});
                        } catch(e) {}
                    }
                }
            }
            
            window.moveTvCursor = function(dx, dy) {
                cx += dx;
                cy += dy;
                if (cx < 0) cx = 0;
                if (cx > window.innerWidth - 1) cx = window.innerWidth - 1;
                if (cy < 0) cy = 0;
                if (cy > window.innerHeight - 1) cy = window.innerHeight - 1;
                
                cursor.style.left = cx + 'px';
                cursor.style.top = cy + 'px';
                
                var elem = document.elementFromPoint(cx, cy);
                if (elem && elem !== window.lastHoveredElem) {
                    if (window.lastHoveredElem) {
                        window.lastHoveredElem.dispatchEvent(new MouseEvent('mouseout', { bubbles: true, view: window, clientX: cx, clientY: cy }));
                        window.lastHoveredElem.dispatchEvent(new MouseEvent('mouseleave', { bubbles: true, view: window, clientX: cx, clientY: cy }));
                    }
                    elem.dispatchEvent(new MouseEvent('mouseover', { bubbles: true, view: window, clientX: cx, clientY: cy }));
                    elem.dispatchEvent(new MouseEvent('mouseenter', { bubbles: true, view: window, clientX: cx, clientY: cy }));
                    elem.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, view: window, clientX: cx, clientY: cy }));
                    window.lastHoveredElem = elem;
                }
                
                var sdx = 0;
                var sdy = 0;
                if (cy > window.innerHeight - 60) sdy = 60;
                if (cy < 60) sdy = -60;
                if (cx > window.innerWidth - 60) sdx = 60;
                if (cx < 60) sdx = -60;
                
                if (sdx !== 0 || sdy !== 0) {
                    performScroll(sdx, sdy);
                    
                    // If we tried to scroll up but we are already at the top, switch to native mode for the menu
                    if (sdy < 0 && window.scrollY <= 10 && cy < 30) {
                        enterNativeMode();
                    }
                }
            };
            
            window.clickTvCursor = function() {
                var elem = document.elementFromPoint(cx, cy);
                if (elem) {
                    elem.click();
                    var events = ['pointerdown', 'mousedown', 'pointerup', 'mouseup', 'click'];
                    events.forEach(function(evt) {
                        elem.dispatchEvent(new MouseEvent(evt, { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy }));
                    });
                }
            };
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this)
        setContentView(webView)

        webView.addJavascriptInterface(TVInterface(), "AndroidTV")

        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        
        // Support multiple windows to allow WebChromeClient to intercept popups safely
        webSettings.setSupportMultipleWindows(true)
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(cursorScript, null)
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                // Block intents that try to open external apps (Play Store, etc.) which crash Fire TV
                if (url.startsWith("intent://") || url.startsWith("market://") || url.startsWith("whatsapp://")) {
                    Log.d("TVWebView", "Blocked external intent: ${url}")
                    return true // Consume the event, don't load
                }
                return false
            }
            
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                // Silently ignore errors from ad networks to prevent breaking the main UI
                Log.d("TVWebView", "Resource error: ${error?.description}")
            }
            
            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                // If the WebView renderer crashes (e.g. out of memory due to ads)
                Log.e("TVWebView", "Renderer crashed")
                return true
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                // Intercept popups (ads) and SILENTLY block them.
                Log.d("TVWebView", "Blocked popup window attempt")
                // By returning false and not doing anything with resultMsg, the popup is discarded
                return false
            }
        }
        
        webView.loadUrl("https://dulo.cx")
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.dispatchKeyEvent(event)
        
        val moveAmount = 40
        
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (isNativeMode) {
                // In native mode, let the WebView handle the spatial navigation natively
                return super.dispatchKeyEvent(event)
            }
        
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    webView.evaluateJavascript("window.moveTvCursor(0, -$moveAmount);", null)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    webView.evaluateJavascript("window.moveTvCursor(0, $moveAmount);", null)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    webView.evaluateJavascript("window.moveTvCursor(-$moveAmount, 0);", null)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    webView.evaluateJavascript("window.moveTvCursor($moveAmount, 0);", null)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    webView.evaluateJavascript("window.clickTvCursor();", null)
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    if (webView.canGoBack()) {
                        webView.goBack()
                        return true
                    }
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            if (isNativeMode) {
                return super.dispatchKeyEvent(event)
            }
            // Consume ACTION_UP for the same keys to prevent them from bubbling
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    return true
                }
            }
        }
        
        return super.dispatchKeyEvent(event)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            // Free up memory aggressively when OS is low on RAM
            Log.d("TVWebView", "Trimming memory")
            webView.clearCache(false)
        }
    }
}
