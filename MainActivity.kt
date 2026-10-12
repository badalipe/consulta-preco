package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val SITE = "https://rmarket.cartazfacil.pro"
    private val USER = "rmarket07"
    private val PASS = "sup12345"
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var webViewContainer: FrameLayout
    private lateinit var mainContent: View
    private var wv: WebView? = null
    private var cloudflareResolvido = false
    private var codigoPendente = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webViewContainer = findViewById(R.id.webViewContainer)
        mainContent = findViewById(R.id.mainContent)

        setupWebView()
        setupBotoes()

        // Verifica se já tem cookie salvo
        verificarSessao()
    }

    private fun setupWebView() {
        wv = WebView(this)
        wv?.settings?.javaScriptEnabled = true
        wv?.settings?.domStorageEnabled = true
        wv?.settings?.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        wv?.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        webViewContainer.addView(wv)
    }

    private fun setupBotoes() {
        findViewById<Button>(R.id.btnScan).setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 100)
        }

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener {
            if (webViewContainer.visibility == View.VISIBLE) {
                // Se WebView visível, esconde
                webViewContainer.visibility = View.GONE
                mainContent.visibility = View.VISIBLE
            } else {
                limpar()
            }
        }

        findViewById<EditText>(R.id.edtCodigo).setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || 
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                val codigo = v.text.toString().trim()
                if (codigo.isNotEmpty()) {
                    if (cloudflareResolvido) {
                        buscarPreco(codigo)
                    } else {
                        codigoPendente = codigo
                        mostrarWebViewParaLogin()
                    }
                }
                true
            } else false
        }
    }

    private fun verificarSessao() {
        // Tenta acessar unitario.php para ver se está logado
        wv?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                when {
                    url.contains("challenge-platform") || url.contains("cdn-cgi") -> {
                        // Cloudflare detectado - precisa resolver manualmente
                        cloudflareResolvido = false
                        Toast.makeText(this@MainActivity, "Resolva o Cloudflare", Toast.LENGTH_LONG).show()
                        mostrarWebViewParaLogin()
                    }
                    url.contains("index.php") || url.contains("login") -> {
                        // Precisa fazer login
                        fazerLoginNoWebView(view)
                    }
                    url.contains("home.php") || url.contains("unitario.php") -> {
                        // Logado com sucesso!
                        cloudflareResolvido = true
                        webViewContainer.visibility = View.GONE
                        mainContent.visibility = View.VISIBLE
                        Toast.makeText(this@MainActivity, "Conectado!", Toast.LENGTH_SHORT).show()

                        // Se tem código pendente, busca
                        if (codigoPendente.isNotEmpty()) {
                            buscarPreco(codigoPendente)
                            codigoPendente = ""
                        }
                    }
                }
            }
        }

        wv?.loadUrl(SITE + "/unitario.php")
    }

    private fun mostrarWebViewParaLogin() {
        webViewContainer.visibility = View.VISIBLE
        mainContent.visibility = View.GONE

        Toast.makeText(this, "Faça login e resolva o Cloudflare", Toast.LENGTH_LONG).show()

        wv?.loadUrl(SITE + "/index.php")
    }

    private fun fazerLoginNoWebView(view: WebView) {
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val codigo = data?.getStringExtra("codigo")
            codigo?.let {
                findViewById<TextView>(R.id.txtCodigo).text = it
                findViewById<EditText>(R.id.edtCodigo).setText(it)

                if (cloudflareResolvido) {
                    buscarPreco(it)
                } else {
                    codigoPendente = it
                    mostrarWebViewParaLogin()
                }
            }
        }
    }

    private fun buscarPreco(codigo: String) {
        findViewById<TextView>(R.id.txtNome).text = "Buscando..."
        findViewById<TextView>(R.id.txtPreco).text = "..."

        // Usa o WebView para buscar (mas invisível)
        webViewContainer.visibility = View.GONE
        mainContent.visibility = View.VISIBLE

        wv?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("unitario.php")) {
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
                            extrairPreco(res ?: "", codigo)
                        }
                    }, 5000)
                }
            }
        }

        wv?.loadUrl(SITE + "/unitario.php")
    }

    private fun extrairPreco(texto: String, codigo: String) {
        val linhas = texto.split("
")
        var nome = ""
        var preco = ""

        for (linha in linhas) {
            val l = linha.trim()
            when {
                l.contains("R$") || Regex("[0-9]+,[0-9]{2}").containsMatchIn(l) -> preco = l
                l.length > 5 && !l.contains("Código") && !l.contains("Barras") && nome.isEmpty() -> nome = l
            }
        }

        runOnUiThread {
            findViewById<TextView>(R.id.txtNome).text = nome.ifEmpty { "Produto não encontrado" }
            findViewById<TextView>(R.id.txtPreco).text = preco.ifEmpty { "R$ -" }
            findViewById<TextView>(R.id.txtCodigo).text = codigo

            // Volta para scanner após 5 segundos
            handler.postDelayed({
                val intent = Intent(this, ScannerActivity::class.java)
                startActivityForResult(intent, 100)
            }, 5000)
        }
    }

    private fun limpar() {
        findViewById<TextView>(R.id.txtNome).text = "Escaneie ou digite o código"
        findViewById<TextView>(R.id.txtCodigo).text = "-"
        findViewById<TextView>(R.id.txtPreco).text = "R$ 0,00"
        findViewById<EditText>(R.id.edtCodigo).setText("")
    }
}
