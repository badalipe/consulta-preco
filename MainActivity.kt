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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val SITE = "https://rmarket.cartazfacil.pro"
    private val USER = "rmarket07"
    private val PASS = "sup12345"
    private val handler = Handler(Looper.getMainLooper())
    private var wv: WebView? = null

    private val scanLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == RESULT_OK) {
            val c = r.data?.getStringExtra("codigo")
            c?.let { buscar(it) }
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnScan).setOnClickListener {
            scanLauncher.launch(Intent(this, ScannerActivity::class.java))
        }

        findViewById<EditText>(R.id.edtCodigo).setOnEditorActionListener { v, id, _ ->
            if (id == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val c = v.text.toString()
                if (c.isNotBlank()) buscar(c)
                true
            } else false
        }

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener {
            findViewById<TextView>(R.id.txtNome).text = "Pronto"
            findViewById<TextView>(R.id.txtPreco).text = "R$ 0,00"
        }

        setup()
    }

    private fun setup() {
        wv = WebView(this)
        wv?.settings?.javaScriptEnabled = true
        wv?.settings?.domStorageEnabled = true
        wv?.visibility = View.INVISIBLE
        wv?.layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
        (findViewById<View>(android.R.id.content) as android.view.ViewGroup).addView(wv)

        login()
    }

    private fun login() {
        Toast.makeText(this, "Conectando...", Toast.LENGTH_SHORT).show()

        wv?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) {
                when {
                    url.contains("index.php") -> {
                        val js = "var u=document.querySelector('input[name=\\"usuario\\"]');" +
                                 "var p=document.querySelector('input[type=\\"password\\"]');" +
                                 "if(u&&p){u.value='" + USER + "';p.value='" + PASS + "';" +
                                 "var f=p.closest('form');if(f)f.submit();}"
                        v.evaluateJavascript(js, null)
                    }
                    url.contains("home.php") || url.contains("unitario.php") -> {
                        Toast.makeText(this@MainActivity, "OK!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        wv?.loadUrl(SITE + "/index.php")
    }

    private fun buscar(cod: String) {
        findViewById<TextView>(R.id.txtNome).text = "Buscando..."
        findViewById<TextView>(R.id.txtCodigo).text = cod

        wv?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) {
                when {
                    url.contains("unitario.php") -> {
                        val js = "var c=document.querySelector('#btnBusca');" +
                                 "if(c){c.value='" + cod + "';" +
                                 "c.dispatchEvent(new Event('input',{bubbles:true}));" +
                                 "if(window.jQuery){jQuery('#pesq_prod').click();}}"
                        v.evaluateJavascript(js, null)

                        handler.postDelayed({
                            v.evaluateJavascript(
                                "var r=document.getElementById('retorno');" +
                                "if(r&&r.innerText.trim().length>3){r.innerText;}else{'';}"
                            ) { res ->
                                mostrar(res ?: "", cod)
                            }
                        }, 4000)
                    }
                    url.contains("index.php") -> login()
                }
            }
        }
        wv?.loadUrl(SITE + "/unitario.php")
    }

    private fun mostrar(t: String, c: String) {
        val lines = t.split("
")
        var n = ""
        var p = ""
        for (l in lines) {
            val x = l.trim()
            if (x.contains("R$") || x.matches(Regex(".*\\d+,\\d{2}.*"))) p = x
            else if (x.length > 3 && n.isEmpty()) n = x
        }
        runOnUiThread {
            findViewById<TextView>(R.id.txtNome).text = n.ifEmpty { "Produto" }
            findViewById<TextView>(R.id.txtPreco).text = p.ifEmpty { "R$ -" }
        }
    }
}
