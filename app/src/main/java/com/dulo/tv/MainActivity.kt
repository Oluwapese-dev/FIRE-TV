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
            cursor.style.transition = 'left 0.05s linear, top 0.05s linear';
            
            var cx = window.innerWidth / 2;
            var cy = window.innerHeight / 2;
            cursor.style.left = cx + 'px';
            cursor.style.top = cy + 'px';
            
            document.body.appendChild(cursor);
            
            window.moveTvCursor = function(dx, dy) {
                cx += dx;
                cy += dy;
                if (cx < 0) cx = 0;
                if (cx > window.innerWidth) cx = window.innerWidth;
                if (cy < 0) cy = 0;
                if (cy > window.innerHeight) cy = window.innerHeight;
                
                cursor.style.left = cx + 'px';
                cursor.style.top = cy + 'px';
                
                if (cy > window.innerHeight - 60) window.scrollBy({top: 80, behavior: 'smooth'});
                if (cy < 60) window.scrollBy({top: -80, behavior: 'smooth'});
                if (cx > window.innerWidth - 60) window.scrollBy({left: 80, behavior: 'smooth'});
                if (cx < 60) window.scrollBy({left: -80, behavior: 'smooth'});
            };
            
            window.clickTvCursor = function() {
                var elem = document.elementFromPoint(cx, cy);
                if (elem) {
                    elem.click();
                    var mousedown = new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy });
                    var mouseup = new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy });
                    elem.dispatchEvent(mousedown);
                    elem.dispatchEvent(mouseup);
                }
            };
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        webView = WebView(this)
        setContentView(webView)

        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(cursorScript, null)
            }
        }
        
        webView.loadUrl("https://dulo.cx")
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.dispatchKeyEvent(event)
        
        val moveAmount = 40
        
        if (event.action == KeyEvent.ACTION_DOWN) {
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
}
