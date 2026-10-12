package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
    private lateinit var mainContent: android.view.View
    private var wv: WebView? = null
    private var logado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webViewContainer = findViewById(R.id.webViewContainer)
        mainContent = findViewById(R.id.mainContent)

        setupWebView()

        val btnScan = findViewById<Button>(R.id.btnScan)
        btnScan.setOnClickListener {
            if (logado) {
                val intent = Intent(this, ScannerActivity::class.java)
                startActivityForResult(intent, 100)
            } else {
                Toast.makeText(this, "Faca login primeiro!", Toast.LENGTH_SHORT).show()
                mostrarWebView()
            }
        }

        val btnVoltar = findViewById<TextView>(R.id.btnVoltar)
        btnVoltar.setOnClickListener {
            if (webViewContainer.visibility == View.VISIBLE) {
                webViewContainer.visibility = View.GONE
                mainContent.visibility = View.VISIBLE
            }
        }
    }

    private fun setupWebView() {
        wv = WebView(this)
        wv?.settings?.javaScriptEnabled = true
        wv?.settings?.domStorageEnabled = true

        wv?.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        webViewContainer.addView(wv)
        mostrarWebView()
    }

    private fun mostrarWebView() {
        webViewContainer.visibility = View.VISIBLE
        mainContent.visibility = View.GONE

        wv?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                when {
                    url.contains("index.php") || url.contains("login") -> {
                        // Faz login automatico
                        val js = "var u=document.querySelector('input[name="usuario"]');" +
                                 "var p=document.querySelector('input[type="password"]');" +
                                 "if(u&&p){u.value='" + USER + "';p.value='" + PASS + "';" +
                                 "var f=p.closest('form');if(f)f.submit();}"
                        view.evaluateJavascript(js, null)
                    }
                    url.contains("home.php") || url.contains("unitario.php") -> {
                        // Logado!
                        logado = true
                        webViewContainer.visibility = View.GONE
                        mainContent.visibility = View.VISIBLE
                        Toast.makeText(this@MainActivity, "Conectado! Agora escaneie.", Toast.LENGTH_LONG).show()
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
                val txtCodigo = findViewById<TextView>(R.id.txtCodigo)
                txtCodigo.text = it

                // Busca preco
                buscarPreco(it)
            }
        }
    }

    private fun buscarPreco(codigo: String) {
        val txtNome = findViewById<TextView>(R.id.txtNome)
        val txtPreco = findViewById<TextView>(R.id.txtPreco)

        txtNome.text = "Buscando..."
        txtPreco.text = "..."

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
                            "if(r&&r.innerText.trim().length>3){r.innerText;}else{'';}"
                        ) { res ->
                            mostrarResultado(res ?: "", codigo)
                        }
                    }, 5000)
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
            if (l.contains("R$") || l.contains(",")) {
                if (preco.isEmpty()) preco = l
            } else if (l.length > 5) {
                if (nome.isEmpty()) nome = l
            }
        }

        val txtNome = findViewById<TextView>(R.id.txtNome)
        val txtPreco = findViewById<TextView>(R.id.txtPreco)

        if (nome.isEmpty()) {
            txtNome.text = "Nao encontrado"
        } else {
            txtNome.text = nome
        }

        if (preco.isEmpty()) {
            txtPreco.text = "R$ -"
        } else {
            txtPreco.text = preco
        }

        // Volta para scanner
        handler.postDelayed({
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 100)
        }, 5000)
    }
}
