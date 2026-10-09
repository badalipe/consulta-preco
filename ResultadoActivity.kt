package com.quickprice.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ResultadoActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val codigo = intent.getStringExtra("codigo") ?: "Não lido"

        // Layout simples
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 100, 50, 50)
        }

        val textStatus = TextView(this).apply {
            text = "Código: $codigo\n\nBuscando preço..."
            textSize = 20f
        }

        val textResultado = TextView(this).apply {
            textSize = 32f
            setPadding(0, 50, 0, 0)
        }

        layout.addView(textStatus)
        layout.addView(textResultado)
        setContentView(layout)

        // WebView invisível para buscar preço
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // Tenta injetar o código
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

            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                // NÃO VOLTA PARA O SCANNER - mostra erro aqui mesmo
                runOnUiThread {
                    textStatus.text = "Código: $codigo"
                    textResultado.text = "⚠️ Erro de conexão\n\nVerifique:\n• WiFi/Data ligado\n• Google Play Services atualizado\n\nToque em VOLTAR e tente novamente"
                }
            }
        }

        webView.loadUrl("https://rmarket.cartazfacil.pro/unitario.php")
    }

    override fun onBackPressed() {
        // Volta para o scanner manualmente (só quando o usuário pedir)
        super.onBackPressed()
        finish()
    }
}
