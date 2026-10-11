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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val SITE = "https://rmarket.cartazfacil.pro"
    private val USER = "rmarket07"
    private val PASS = "sup12345"

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val scannerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val codigo = result.data?.getStringExtra("codigo")
            codigo?.let {
                findViewById<TextView>(R.id.txtCodigo).text = it
                buscar(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnScan).setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            scannerLauncher.launch(intent)
        }

        val edt = findViewById<EditText>(R.id.edtCodigo)
        edt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val codigo = edt.text.toString()
                if (codigo.isNotBlank()) {
                    findViewById<TextView>(R.id.txtCodigo).text = codigo
                    buscar(codigo)
                }
                true
            } else false
        }

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener {
            limpar()
        }

        setupWebView()
    }

    private fun setupWebView() {
        webView = WebView(this)
        webView?.settings?.javaScriptEnabled = true
        webView?.settings?.domStorageEnabled = true

        CookieManager.getInstance().setAcceptCookie(true)

        webView?.visibility = View.INVISIBLE
        webView?.layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
        (findViewById<View>(android.R.id.content) as android.view.ViewGroup).addView(webView)

        login()
    }

    private fun login() {
        Toast.makeText(this, "Conectando...", Toast.LENGTH_SHORT).show()

        webView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("index.php")) {
                    // Login simples
                    val js = "var u=document.querySelector('input[name="usuario"]');" +
                             "var p=document.querySelector('input[type="password"]');" +
                             "if(u&&p){u.value='" + USER + "';p.value='" + PASS + "';" +
                             "var f=p.closest('form');if(f)f.submit();}"
                    view.evaluateJavascript(js, null)
                } else if (url.contains("home.php") || url.contains("unitario.php")) {
                    Toast.makeText(this@MainActivity, "OK!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        webView?.loadUrl(SITE + "/index.php")
    }

    private fun buscar(codigo: String) {
        findViewById<TextView>(R.id.txtNome).text = "Buscando..."

        webView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("unitario.php")) {
                    val js = "var c=document.querySelector('#btnBusca');" +
                             "if(c){c.value='" + codigo + "';" +
                             "c.dispatchEvent(new Event('input',{bubbles:true}));" +
                             "if(window.jQuery){jQuery('#pesq_prod').click();}}"
                    view.evaluateJavascript(js, null)

                    handler.postDelayed({
                        view.evaluateJavascript(
                            "var r=document.getElementById('retorno');" +
                            "if(r&&r.innerText.trim().length>3){r.innerText;}else{'';}"
                        ) { res ->
                            mostrar(res ?: "", codigo)
                        }
                    }, 4000)
                } else if (url.contains("index.php")) {
                    login()
                }
            }
        }

        webView?.loadUrl(SITE + "/unitario.php")
    }

    private fun mostrar(texto: String, codigo: String) {
        val linhas = texto.split("
")
        var nome = ""
        var preco = ""

        for (linha in linhas) {
            val l = linha.trim()
            if (l.contains("R$") || Regex("\d+,\d{2}").containsMatchIn(l)) {
                preco = l
            } else if (l.length > 3 && nome.isEmpty()) {
                nome = l
            }
        }

        runOnUiThread {
            findViewById<TextView>(R.id.txtNome).text = nome.ifEmpty { "Produto" }
            findViewById<TextView>(R.id.txtPreco).text = preco.ifEmpty { "R$ -" }
            findViewById<TextView>(R.id.txtCodigo).text = codigo
        }
    }

    private fun limpar() {
        findViewById<TextView>(R.id.txtNome).text = "Escaneie ou digite"
        findViewById<TextView>(R.id.txtPreco).text = "R$ 0,00"
        findViewById<TextView>(R.id.txtCodigo).text = "-"
        findViewById<TextView>(R.id.txtCategoria).text = "-"
        findViewById<TextView>(R.id.txtPeso).text = "-"
        findViewById<TextView>(R.id.txtValidade).text = "-"
        findViewById<EditText>(R.id.edtCodigo).setText("")
    }
}
