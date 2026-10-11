package com.quickprice.app

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = WebView(this)
        webView?.settings?.javaScriptEnabled = true
        webView?.settings?.domStorageEnabled = true

        findViewById<Button>(R.id.btnScan).setOnClickListener {
            webView?.loadUrl("https://rmarket.cartazfacil.pro/unitario.php")
        }

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener {
            findViewById<TextView>(R.id.txtNome).text = "OK"
        }

        // Adiciona WebView invisivel
        webView?.visibility = android.view.View.INVISIBLE
        webView?.layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
        (findViewById<android.view.View>(android.R.id.content) as android.view.ViewGroup).addView(webView)

        // Login
        webView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("index.php")) {
                    view.evaluateJavascript("var u=document.querySelector('input[name="usuario"]');var p=document.querySelector('input[type="password"]');if(u&&p){u.value='rmarket07';p.value='sup12345';var f=p.closest('form');if(f)f.submit();}", null)
                }
            }
        }
        webView?.loadUrl("https://rmarket.cartazfacil.pro/index.php")
    }
}
