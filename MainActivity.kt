package com.quickprice.app

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.visibility = android.view.View.INVISIBLE
        wv.layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
        (findViewById<android.view.View>(android.R.id.content) as android.view.ViewGroup).addView(wv)

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("index.php")) {
                    view.evaluateJavascript("document.forms[0].submit()", null)
                }
            }
        }

        findViewById<Button>(R.id.btnScan).setOnClickListener {
            wv.loadUrl("https://rmarket.cartazfacil.pro/unitario.php")
        }

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener {
            findViewById<TextView>(R.id.txtNome).text = "OK"
        }

        wv.loadUrl("https://rmarket.cartazfacil.pro/index.php")
    }
}
