package com.quickprice.app

import android.content.Intent
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

        // Botão SCAN - Abre ScannerActivity
        findViewById<Button>(R.id.btnScan).setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivity(intent)
        }

        // Botão voltar
        findViewById<TextView>(R.id.btnVoltar).setOnClickListener {
            findViewById<TextView>(R.id.txtNome).text = "Pronto"
            findViewById<TextView>(R.id.txtPreco).text = "R$ 0,00"
        }

        // WebView para login (invisível)
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

        wv.loadUrl("https://rmarket.cartazfacil.pro/index.php")
    }
}
