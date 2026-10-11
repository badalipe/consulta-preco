package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val SITE = "https://rmarket.cartazfacil.pro"
    private val USER = "rmarket07"
    private val PASS = "sup12345"
    private val handler = Handler(Looper.getMainLooper())
    private var wv: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // WebView configurado como navegador real
        wv = WebView(this)
        wv?.settings?.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        wv?.visibility = View.INVISIBLE
        wv?.layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
        (findViewById<View>(android.R.id.content) as android.view.ViewGroup).addView(wv)

        // Botão SCAN
        findViewById<Button>(R.id.btnScan).setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 100)
        }

        // Botão voltar
        findViewById<TextView>(R.id.btnVoltar).setOnClickListener {
            findViewById<TextView>(R.id.txtNome).text = "Pronto"
            findViewById<TextView>(R.id.txtPreco).text = "R$ 0,00"
        }

        // Campo de busca manual
        findViewById<EditText>(R.id.edtCodigo).setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val codigo = v.text.toString()
                if (codigo.isNotBlank()) {
                    buscarProduto(codigo)
                }
                true
            } else false
        }

        // Faz login
        fazerLogin()
    }

    private fun fazerLogin() {
        Toast.makeText(this, "Conectando...", Toast.LENGTH_SHORT).show()

        wv?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                when {
                    url.contains("challenge-platform") || url.contains("cdn-cgi") -> {
                        // Cloudflare challenge - aguardar
                        Toast.makeText(this@MainActivity, "Verificando seguranca...", Toast.LENGTH_SHORT).show()
                    }
                    url.contains("index.php") || url.contains("login") -> {
                        // Faz login
                        val js = """(function(){
                            var u = document.querySelector('input[name="usuario"]') || document.querySelector('input[name="login"]');
                            var p = document.querySelector('input[type="password"]');
                            if(u && p) {
                                u.value = '$USER';
                                p.value = '$PASS';
                                var f = p.closest('form');
                                if(f) f.submit();
                            }
                        })();"""
                        view.evaluateJavascript(js, null)
                    }
                    url.contains("home.php") || url.contains("unitario.php") -> {
                        Toast.makeText(this@MainActivity, "Conectado!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        wv?.loadUrl(SITE + "/index.php")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val codigo = data?.getStringExtra("codigo")
            codigo?.let {
                findViewById<TextView>(R.id.txtCodigo).text = it
                buscarProduto(it)
            }
        }
    }

    private fun buscarProduto(codigo: String) {
        findViewById<TextView>(R.id.txtNome).text = "Buscando..."

        wv?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                when {
                    url.contains("unitario.php") -> {
                        // Injeta código e busca
                        val js = """(function(){
                            var c = document.getElementById('btnBusca');
                            if(c) {
                                c.value = '$codigo';
                                c.dispatchEvent(new Event('input', {bubbles:true}));
                                if(window.jQuery) {
                                    jQuery('#pesq_prod').click();
                                }
                            }
                        })();"""
                        view.evaluateJavascript(js, null)

                        // Aguarda resultado
                        handler.postDelayed({
                            view.evaluateJavascript(
                                """(function(){
                                    var r = document.getElementById('retorno');
                                    if(r && r.innerText.trim().length > 3) {
                                        return r.innerText;
                                    }
                                    return '';
                                })();"""
                            ) { res ->
                                mostrarResultado(res ?: "", codigo)
                            }
                        }, 5000)
                    }
                    url.contains("index.php") || url.contains("login") -> {
                        // Sessão caiu, faz login novamente
                        fazerLogin()
                    }
                }
            }
        }

        wv?.loadUrl(SITE + "/unitario.php")
    }

    private fun mostrarResultado(texto: String, codigo: String) {
        val linhas = texto.split("
")
        var nome = ""
        var preco = ""

        for (linha in linhas) {
            val l = linha.trim()
            when {
                l.contains("R$") || Regex("\d+,\d{2}").containsMatchIn(l) -> preco = l
                l.length > 5 && !l.contains("Código") && !l.contains("Barras") && nome.isEmpty() -> nome = l
            }
        }

        runOnUiThread {
            findViewById<TextView>(R.id.txtNome).text = nome.ifEmpty { "Produto nao encontrado" }
            findViewById<TextView>(R.id.txtPreco).text = preco.ifEmpty { "R$ -" }
            findViewById<TextView>(R.id.txtCodigo).text = codigo
        }
    }
}
