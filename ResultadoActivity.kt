package com.quickprice.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ResultadoActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val codigo = intent.getStringExtra("codigo") ?: "Não lido"

        // Layout simples apenas com texto
        val textView = TextView(this).apply {
            text = "Código lido:\n\n$codigo\n\nBuscando preço..."
            textSize = 24f
            setPadding(50, 100, 50, 50)
        }
        setContentView(textView)

        // WebView invisível para buscar preço
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // Tenta injetar o código no site do Cartaz Fácil
                val js = """
                    (function() {
                        var campo = document.querySelector('#btnBusca');
                        if (campo) {
                            campo.value = '$codigo';
                            if (window.jQuery) {
                                jQuery('#pesq_prod').click();
                            }
                        }
                    })();
                """.trimIndent()
                webView.evaluateJavascript(js, null)
            }
        }

        webView.loadUrl("https://rmarket.cartazfacil.pro/unitario.php")
    }
}
