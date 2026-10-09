package com.quickprice.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ResultadoActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val codigo = intent.getStringExtra("codigo") ?: ""

        // Layout: Nome grande em cima, Preço gigante embaixo (igual equipamento Sweda)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt()) // Fundo preto
            setPadding(40, 100, 40, 100)
        }

        val txtNome = TextView(this).apply {
            text = "Buscando..."
            textSize = 32f
            setTextColor(0xFFFFFFFF.toInt()) // Branco
            setPadding(0, 0, 0, 50)
        }

        val txtPreco = TextView(this).apply {
            text = ""
            textSize = 80f
            setTextColor(0xFF00FF00.toInt()) // Verde
            setPadding(0, 50, 0, 100)
        }

        val btnVoltar = Button(this).apply {
            text = "ESCANEAR OUTRO PRODUTO"
            textSize = 20f
            setOnClickListener {
                finish()
            }
        }

        layout.addView(txtNome)
        layout.addView(txtPreco)
        layout.addView(btnVoltar)
        setContentView(layout)

        // WebView invisível - conecta no servidor LOCAL (igual ao equipamento)
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // Injeta o código no campo de busca
                val js = """
                    (function() {
                        var campo = document.querySelector('#btnBusca');
                        if (campo) {
                            campo.value = '$codigo';
                            campo.dispatchEvent(new Event('input', {bubbles:true}));
                            if (window.jQuery) {
                                jQuery('#pesq_prod').click();
                            }
                        }
                    })();
                """.trimIndent()
                webView.evaluateJavascript(js, null)

                // Aguarda 2 segundos e lê o resultado
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    webView.evaluateJavascript("""
                        (function() {
                            var ret = document.getElementById('retorno');
                            if (ret && ret.innerText.trim().length > 3) {
                                return ret.innerText;
                            }
                            return '';
                        })();
                    """.trimIndent()) { resultado ->
                        if (!resultado.isNullOrBlank() && resultado != "null") {
                            val texto = resultado.removeSurrounding("\"")
                            val linhas = texto.split("\n")
                            var nome = ""
                            var preco = ""

                            for (linha in linhas) {
                                if (linha.contains("R$") || Regex("\\d+,\\d{2}").containsMatchIn(linha)) {
                                    preco = linha.trim()
                                } else if (linha.trim().length > 3 && nome.isEmpty()) {
                                    nome = linha.trim()
                                }
                            }

                            runOnUiThread {
                                txtNome.text = nome.ifEmpty { "Produto" }
                                txtPreco.text = preco.ifEmpty { "R$ -" }
                            }
                        } else {
                            runOnUiThread {
                                txtNome.text = "Produto não encontrado"
                                txtPreco.text = ""
                            }
                        }
                    }
                }, 2000)
            }

            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                runOnUiThread {
                    txtNome.text = "Erro de conexão"
                    txtPreco.text = "Verifique o WiFi"
                }
            }
        }

        // USA O SERVIDOR LOCAL (igual ao equipamento Sweda)
        webView.loadUrl("http://10.10.56.103/unitario.php")
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
