package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val SITE = "https://rmarket.cartazfacil.pro"
    private val USER = "rmarket07"
    private val PASS = "sup12345"
    private val handler = Handler(Looper.getMainLooper())
    private var wv: WebView? = null
    private var logado = false
    private var codigoPendente = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout simples
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 100, 40, 40)
            setBackgroundColor(0xFF000000.toInt())
        }

        val txtStatus = TextView(this).apply {
            text = "Iniciando..."
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 40)
        }

        val txtNome = TextView(this).apply {
            text = ""
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 20, 0, 20)
        }

        val txtPreco = TextView(this).apply {
            text = ""
            textSize = 64f
            setTextColor(0xFF00FF00.toInt())
            setPadding(0, 20, 0, 20)
        }

        layout.addView(txtStatus)
        layout.addView(txtNome)
        layout.addView(txtPreco)

        setContentView(layout)

        // WebView invisivel para login
        wv = WebView(this)
        wv?.settings?.javaScriptEnabled = true
        wv?.settings?.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        wv?.visibility = View.INVISIBLE
        wv?.layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
        (findViewById<View>(android.R.id.content) as android.view.ViewGroup).addView(wv)

        // Faz login
        fazerLogin(txtStatus)
    }

    private fun fazerLogin(status: TextView) {
        status.text = "Fazendo login..."

        wv?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                when {
                    url.contains("index.php") -> {
                        val js = "var u=document.getElementById('usuario');" +
                                 "var p=document.getElementById('senha');" +
                                 "if(!u){u=document.querySelector('input[name=usuario]');}" +
                                 "if(!p){p=document.querySelector('input[type=password]');}" +
                                 "if(u&&p){u.value='" + USER + "';p.value='" + PASS + "';" +
                                 "var f=p.closest('form');if(f)f.submit();}"
                        view.evaluateJavascript(js, null)
                    }
                    url.contains("home.php") || url.contains("unitario.php") -> {
                        logado = true
                        status.text = "Pronto! Escaneie um codigo."
                        abrirScanner()
                    }
                }
            }
        }
        wv?.loadUrl(SITE + "/index.php")
    }

    private fun abrirScanner() {
        val intent = Intent(this, ScannerActivity::class.java)
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val codigo = data?.getStringExtra("codigo")
            codigo?.let {
                codigoPendente = it
                buscarPreco(it)
            }
        }
    }

    private fun buscarPreco(codigo: String) {
        val txtStatus = findViewById<TextView>(R.id.txtStatus)
        val txtNome = findViewById<TextView>(R.id.txtNome)
        val txtPreco = findViewById<TextView>(R.id.txtPreco)

        txtStatus.text = "Buscando: " + codigo

        if (!logado) {
            txtStatus.text = "Deslogado! Faca login novamente."
            return
        }

        wv?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (url.contains("unitario.php")) {
                    val js = "var c=document.getElementById('btnBusca');" +
                             "if(c){c.value='" + codigo + "';" +
                             "c.dispatchEvent(new Event('input',{bubbles:true}));" +
                             "if(window.jQuery){jQuery('#pesq_prod').click();}}"
                    view.evaluateJavascript(js, null)

                    handler.postDelayed({
                        view.evaluateJavascript(
                            "var r=document.getElementById('retorno');" +
                            "if(r){r.innerText;}else{'';}"
                        ) { res ->
                            mostrarResultado(res ?: "", codigo)
                        }
                    }, 3000)
                }
            }
        }
        wv?.loadUrl(SITE + "/unitario.php")
    }

    private fun mostrarResultado(texto: String, codigo: String) {
        val txtNome = findViewById<TextView>(R.id.txtNome)
        val txtPreco = findViewById<TextView>(R.id.txtPreco)
        val txtStatus = findViewById<TextView>(R.id.txtStatus)

        val linhas = texto.split("
")
        var nome = ""
        var preco = ""

        for (linha in linhas) {
            val l = linha.trim()
            if (l.contains("R$") || Regex("\d+,\d{2}").containsMatchIn(l)) {
                preco = l
            } else if (l.length > 5 && !l.contains("Código") && nome.isEmpty()) {
                nome = l
            }
        }

        runOnUiThread {
            txtStatus.text = "Codigo: " + codigo
            txtNome.text = nome.ifEmpty { "Produto nao encontrado" }
            txtPreco.text = preco.ifEmpty { "" }

            // Volta para scanner após 5 segundos
            handler.postDelayed({
                abrirScanner()
            }, 5000)
        }
    }
}
