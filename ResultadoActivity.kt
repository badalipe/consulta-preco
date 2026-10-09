package com.quickprice.app

import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ResultadoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOME = "nome"
        const val EXTRA_PRECO = "preco"
        const val EXTRA_CODIGO = "codigo"
        const val EXTRA_IMG = "img"
        const val EXTRA_EXTRAS = "extras"
        private const val PREF_HIST = "historico"
        private const val CAMPO_BUSCA = "#btnBusca"
        private const val BTN_PESQUISAR = "#pesq_prod"
    }

    private lateinit var webView: WebView
    private lateinit var cfg: SessionConfig
    private val handler = Handler(Looper.getMainLooper())
    private var paginaPronta = false
    private var codigoAtual: String? = null
    private var coletando = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resultado)

        // Recupera dados
        val codigo = intent.getStringExtra(EXTRA_CODIGO) ?: ""
        val nomeInicial = intent.getStringExtra(EXTRA_NOME) ?: ""
        val precoInicial = intent.getStringExtra(EXTRA_PRECO) ?: ""

        cfg = SessionConfig.load(this) ?: run { finish(); return }

        // Se veio preço pronto (não precisa buscar)
        if (precoInicial.isNotEmpty() && precoInicial != "-") {
            exibirResultado(nomeInicial, precoInicial, codigo, "", arrayListOf())
            return
        }

        // Senão, busca o preço
        codigoAtual = codigo
        configurarWebView()
        buscarFotoOpenFoodFacts(codigo)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configurarWebView() {
        webView = WebView(this)
        webView.visibility = View.GONE
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                paginaPronta = true
                handler.postDelayed({ processarPagina() }, 1500)
            }
        }

        addContentView(webView, android.view.ViewGroup.LayoutParams(1, 1))
        webView.loadUrl(cfg.url)
    }

    private fun processarPagina() {
        if (!paginaPronta || codigoAtual == null) return

        // Tenta injetar o código
        val js = """
            (function() {
                var c = document.querySelector('$CAMPO_BUSCA');
                if (!c) return 'sem_campo';
                c.value = '${codigoAtual}';
                c.dispatchEvent(new Event('input', {bubbles:true}));
                c.dispatchEvent(new Event('change', {bubbles:true}));
                if (window.jQuery) { jQuery('$BTN_PESQUISAR').click(); return 'ok_jquery'; }
                var b = document.querySelector('$BTN_PESQUISAR');
                if (b) { b.click(); return 'ok'; }
                return 'sem_botao';
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { r ->
            if (r != null && r.contains("ok")) {
                iniciarColeta(codigoAtual!!)
            } else {
                // Se não tem campo de busca, pode estar na tela de login
                // Tenta fazer login automático
                fazerLogin()
            }
        }
    }

    private fun fazerLogin() {
        val js = """
            (function() {
                var form = document.querySelector('form');
                if (!form) return 'sem_form';
                var inputs = form.querySelectorAll('input');
                var user = null, pass = null;
                for (var i = 0; i < inputs.length; i++) {
                    var el = inputs[i];
                    var t = (el.type || '').toLowerCase();
                    if (t === 'password') { pass = el; }
                    else if (t !== 'hidden' && t !== 'submit' && t !== 'button' && !el.readOnly) {
                        if (!user) user = el;
                    }
                }
                if (!user || !pass) return 'sem_campos';
                user.value = '${cfg.usuario}';
                pass.value = '${cfg.senha}';
                user.dispatchEvent(new Event('input', {bubbles:true}));
                pass.dispatchEvent(new Event('input', {bubbles:true}));
                var btn = form.querySelector('button, input[type="submit"], input[type="button"]');
                if (btn) { btn.click(); return 'login_ok'; }
                form.submit();
                return 'login_submit';
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { r ->
            if (r != null && r.contains("login")) {
                // Login feito, aguarda carregar e tenta injetar de novo
                handler.postDelayed({ processarPagina() }, 2000)
            } else {
                exibirResultado("Produto não encontrado", "-", codigoAtual ?: "", "", arrayListOf())
            }
        }
    }

    private fun iniciarColeta(codigo: String, tentativa: Int = 0) {
        if (coletando) return
        coletando = true

        val js = """
            (function() {
                var ret = document.getElementById('retorno');
                if (!ret) return JSON.stringify({erro:'sem_retorno'});
                if (ret.querySelector('.fa-spinner')) return JSON.stringify({carregando:true});
                if (ret.innerText.trim().length < 3) return JSON.stringify({carregando:true});
                var row = ret.querySelector('.trValor') || ret.querySelector('tr') || ret;
                var img = row.querySelector('img') || ret.querySelector('img');
                return JSON.stringify({texto: row.innerText, img: img ? img.src : ''});
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { r ->
            coletando = false
            val limpo = r?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\n", "\n").orEmpty()

            if (limpo.isEmpty() || limpo == "null") { 
                repetirColeta(codigo, tentativa)
                return@evaluateJavascript 
            }

            try {
                val obj = JSONObject(limpo)
                if (obj.optBoolean("carregando") || obj.has("erro")) {
                    repetirColeta(codigo, tentativa)
                    return@evaluateJavascript
                }

                val texto = obj.optString("texto").trim()
                if (texto.length < 2) {
                    exibirResultado("Produto não encontrado", "-", codigo, "", arrayListOf())
                    return@evaluateJavascript
                }

                val linhas = texto.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                val regexPreco = Regex("""R?\$?\s?\d{1,3}(\.\d{3})*,\d{2}""")
                val precoLinha = linhas.firstOrNull { regexPreco.containsMatchIn(it) }
                val preco = precoLinha?.let { regexPreco.find(it)?.value?.replace("R$", "")?.trim() }
                    ?.let { "R$ $it" } ?: "-"
                var nome = linhas.firstOrNull { it.length > 3 && it != precoLinha && !regexPreco.containsMatchIn(it) }
                if (nome == null) nome = "Produto"

                exibirResultado(nome, preco, codigo, obj.optString("img"), arrayListOf())

            } catch (e: Exception) {
                repetirColeta(codigo, tentativa)
            }
        }
    }

    private fun repetirColeta(codigo: String, tentativa: Int) {
        if (tentativa >= 10) {
            exibirResultado("Produto não encontrado", "-", codigo, "", arrayListOf())
            return 
        }
        handler.postDelayed({ iniciarColeta(codigo, tentativa + 1) }, 500)
    }

    private fun exibirResultado(nome: String, preco: String, codigo: String, img: String, extras: ArrayList<String>) {
        // Preenche a tela
        findViewById<TextView>(R.id.txtNome).text = nome
        findViewById<TextView>(R.id.txtPreco).text = preco
        findViewById<TextView>(R.id.txtCodigo).text = codigo.ifEmpty { "-" }

        val imgView = findViewById<ImageView>(R.id.imgProduto)
        if (img.isNotEmpty() && img.startsWith("http")) {
            imgView.visibility = View.VISIBLE
            Glide.with(this).load(img).into(imgView)
        }

        salvarHistorico(nome, preco, codigo)

        // Bipe
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                .startTone(ToneGenerator.TONE_PROP_BEEP, 250)
        } catch (e: Exception) { }

        // Botões
        findViewById<TextView>(R.id.btnVoltar).setOnClickListener { 
            startActivity(Intent(this, ScannerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnScan)
            .setOnClickListener { 
                startActivity(Intent(this, ScannerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }

        val edt = findViewById<EditText>(R.id.edtCodigo)
        edt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                buscarManual(edt.text.toString())
                true
            } else false
        }
    }

    private fun buscarFotoOpenFoodFacts(codigo: String) {
        if (codigo.isEmpty()) return
        Thread {
            try {
                val url = URL("https://world.openfoodfacts.org/api/v2/product/$codigo.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                if (json.optInt("status", 0) == 1) {
                    val prod = json.optJSONObject("product")
                    val foto = prod?.optString("image_front_url").orEmpty()
                    runOnUiThread {
                        if (foto.startsWith("http")) {
                            findViewById<ImageView>(R.id.imgProduto).visibility = View.VISIBLE
                            Glide.with(this).load(foto).into(findViewById(R.id.imgProduto))
                        }
                    }
                }
            } catch (e: Exception) { }
        }.start()
    }

    private fun buscarManual(codigo: String) {
        val limpo = codigo.trim()
        if (limpo.isEmpty()) return
        codigoAtual = limpo
        paginaPronta = false
        webView.loadUrl(cfg.url)
    }

    private fun salvarHistorico(nome: String, preco: String, codigo: String) {
        if (codigo.isEmpty()) return
        val prefs = getSharedPreferences(PREF_HIST, MODE_PRIVATE)
        val array = JSONArray(prefs.getString("itens", "[]"))
        val novo = JSONObject().put("nome", nome).put("preco", preco).put("codigo", codigo)
        val atualizado = JSONArray().put(novo)
        for (i in 0 until minOf(array.length(), 19)) atualizado.put(array.getJSONObject(i))
        prefs.edit().putString("itens", atualizado.toString()).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
