package com.quickprice.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ResultadoActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resultado)

        val codigo = intent.getStringExtra("codigo") ?: ""

        // Preenche código
        findViewById<TextView>(R.id.txtCodigo).text = codigo

        // Botão voltar
        findViewById<TextView>(R.id.btnVoltar).setOnClickListener {
            finish()
        }

        // Botão escanear
        findViewById<Button>(R.id.btnScan).setOnClickListener {
            finish()
        }

        // Busca manual
        val edt = findViewById<EditText>(R.id.edtCodigo)
        edt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                buscar(edt.text.toString())
                true
            } else false
        }

        // Busca o produto no servidor LOCAL
        buscar(codigo)
    }

    private fun buscar(codigo: String) {
        if (codigo.isBlank()) return

        findViewById<TextView>(R.id.txtNome).text = "Buscando..."
        findViewById<TextView>(R.id.txtPreco).text = "..."

        // WebView invisível - conecta no servidor LOCAL (igual ao equipamento)
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
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
                            processarResultado(texto, codigo)
                        } else {
                            mostrarErro("Produto não encontrado")
                        }
                    }
                }, 2000)
            }

            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                mostrarErro("Erro de conexão")
            }
        }

        // SERVIDOR LOCAL (igual ao equipamento Sweda)
        webView.loadUrl("http://10.10.56.103/unitario.php")
    }

    private fun processarResultado(texto: String, codigo: String) {
        val linhas = texto.split("\n")
        var nome = ""
        var preco = ""
        var categoria = ""
        var peso = ""
        var validade = ""

        for (linha in linhas) {
            val limpa = linha.trim()
            when {
                limpa.contains("R$") || Regex("\\d+,\\d{2}").containsMatchIn(limpa) -> preco = limpa
                limpa.contains("g", ignoreCase = true) && Regex("\\d+").containsMatchIn(limpa) -> peso = limpa
                Regex("\\d{2}/\\d{2}/\\d{4}").containsMatchIn(limpa) -> validade = limpa
                limpa.length > 3 && nome.isEmpty() -> nome = limpa
            }
        }

        runOnUiThread {
            findViewById<TextView>(R.id.txtNome).text = nome.ifEmpty { "Produto" }
            findViewById<TextView>(R.id.txtPreco).text = preco.ifEmpty { "R$ -" }
            findViewById<TextView>(R.id.txtCodigo).text = codigo
            findViewById<TextView>(R.id.txtCategoria).text = categoria.ifEmpty { "-" }
            findViewById<TextView>(R.id.txtPeso).text = peso.ifEmpty { "-" }
            findViewById<TextView>(R.id.txtValidade).text = validade.ifEmpty { "-" }
        }
    }

    private fun mostrarErro(msg: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.txtNome).text = msg
            findViewById<TextView>(R.id.txtPreco).text = "Tente novamente"
        }
    }
}
