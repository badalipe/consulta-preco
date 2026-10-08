package com.quickprice.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class SistemaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CODIGO = "codigo_barras"
        private const val INTERVALO_CHECK = 30_000L   // 30s (era 20s)
        private const val CAMPO_BUSCA = "#btnBusca"
        private const val BTN_PESQUISAR = "#pesq_prod"
        private const val JS_TEM_CAMPO = "(document.getElementById('btnBusca') !== null)"
        private const val JS_TEM_LINK_UNITARIO =
            "(function(){var l=document.querySelector('a[href*=\"unitario\"]');return l?l.href.split('?')[0]:'';})()"
    }

    private lateinit var webView: WebView
    private lateinit var cfg: SessionConfig
    private val handler = Handler(Looper.getMainLooper())
    private var paginaPronta = false
    private var codigoAtual: String? = null
    private var coletando = false
    private var falhasConsecutivas = 0  // NOVO: conta falhas antes de agir

    private val watchdog = object : Runnable {
        override fun run() {
            verificarSessao()
            handler.postDelayed(this, INTERVALO_CHECK)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sistema)
        cfg = SessionConfig.load(this) ?: run { finish(); return }

        webView = findViewById(R.id.webView)
        webView.visibility = View.GONE  // MODO INVISÍVEL (funciona em background)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                paginaPronta = true
                falhasConsecutivas = 0  // Reseta contador quando carrega com sucesso
                handler.postDelayed({ cuidarDaPagina(clicarLink = true) }, 1200)
            }
            
            override fun onReceivedError(view: WebView, code: Int, desc: String, url: String) {
                // Só conta como falha se for erro real de rede
                falhasConsecutivas++
            }
        }

        codigoAtual = intent.getStringExtra(EXTRA_CODIGO)
        webView.loadUrl(cfg.url)
        handler.post(watchdog)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        codigoAtual = intent.getStringExtra(EXTRA_CODIGO)
        if (paginaPronta) cuidarDaPagina(clicarLink = true)
    }

    private fun cuidarDaPagina(clicarLink: Boolean) {
        if (!paginaPronta) return
        
        webView.evaluateJavascript(JS_TEM_CAMPO) { tem ->
            if (tem?.trim() == "true") {
                // Estamos na página de consulta
                falhasConsecutivas = 0
                if (codigoAtual != null) {
                    aplicarCodigoBarras(codigoAtual)
                }
                return@evaluateJavascript
            }
            
            // Não tem campo de busca - verificar se tem link pro unitario
            webView.evaluateJavascript(JS_TEM_LINK_UNITARIO) { href ->
                val link = href?.removeSurrounding("\"").orEmpty()
                if (link.isNotEmpty() && link != "null" && clicarLink) {
                    webView.loadUrl(link)
                    return@evaluateJavascript
                }
                
                // Se não achou link e não está logado, tenta login
                tentarLoginAutomatico()
            }
        }
    }

    private fun verificarSessao() {
        if (!paginaPronta) return
        
        // Só tenta reconectar se falhou 3 vezes seguidas (evita loop)
        if (falhasConsecutivas >= 3) {
            falhasConsecutivas = 0
            webView.loadUrl(cfg.url)  // Recarrega a página
        }
    }

    private fun tentarLoginAutomatico() {
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
                    else if (t !== 'hidden' && t !== 'submit' && t !== 'button'
                             && t !== 'checkbox' && t !== 'radio' && !el.readOnly) {
                        if (!user) user = el;
                    }
                }
                if (!user || !pass) return 'sem_campos';
                user.value = '${cfg.usuario}';
                pass.value = '${cfg.senha}';
                user.dispatchEvent(new Event('input', {bubbles:true}));
                user.dispatchEvent(new Event('change', {bubbles:true}));
                pass.dispatchEvent(new Event('input', {bubbles:true}));
                pass.dispatchEvent(new Event('change', {bubbles:true}));
                var btn = form.querySelector('button, input[type="submit"], input[type="button"]');
                if (btn) { btn.click(); return 'login_clicou'; }
                form.submit();
                return 'login_submit';
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { }
    }

    private fun aplicarCodigoBarras(codigo: String?) {
        if (codigo.isNullOrBlank() || coletando) return
        
        val js = """
            (function() {
                var c = document.querySelector('$CAMPO_BUSCA');
                if (!c) return 'sem_campo';
                c.value = '$codigo';
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
                codigoAtual = null
                iniciarColeta(codigo)
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
                repetirColeta(codigo, tentativa); return@evaluateJavascript 
            }
            
            try {
                val obj = JSONObject(limpo)
                if (obj.optBoolean("carregando") || obj.has("erro")) {
                    repetirColeta(codigo, tentativa); return@evaluateJavascript
                }
                
                val texto = obj.optString("texto").trim()
                if (texto.length < 2) {
                    mostrarResultado("Produto nao encontrado", "-", codigo); return@evaluateJavascript
                }
                
                val linhas = texto.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                val regexPreco = Regex("""R?\$?\s?\d{1,3}(\.\d{3})*,\d{2}""")
                val precoLinha = linhas.firstOrNull { regexPreco.containsMatchIn(it) }
                val preco = precoLinha?.let { regexPreco.find(it)?.value?.replace("R$", "")?.trim() }
                    ?.let { "R$ $it" } ?: "-"
                var nome = linhas.firstOrNull { it.length > 3 && it != precoLinha && !regexPreco.containsMatchIn(it) }
                if (nome == null) nome = "Produto"
                
                val extras = montarExtras(linhas, nome, precoLinha)
                mostrarResultado(nome, preco, codigo, obj.optString("img"), extras)
                
            } catch (e: Exception) {
                repetirColeta(codigo, tentativa)
            }
        }
    }

    private fun montarExtras(linhas: List<String>, nome: String, precoLinha: String?): List<String> {
        val resto = linhas.filter { it != nome && it != precoLinha && it.length > 1 }
        return resto.take(3).map { linha ->
            when {
                Regex("""\d{1,2}/\d{1,2}/\d{2,4}""").containsMatchIn(linha) -> "Validade|$linha"
                Regex("""(?i)\d+\s?(g|kg|ml|l|un|und)""").containsMatchIn(linha) -> "Peso/Qtd|$linha"
                else -> "Categoria|$linha"
            }
        }
    }

    private fun repetirColeta(codigo: String, tentativa: Int) {
        if (tentativa >= 10) {  // Reduzido de 16 pra 10 (5 segundos)
            mostrarResultado("Produto nao encontrado", "-", codigo); return 
        }
        handler.postDelayed({ iniciarColeta(codigo, tentativa + 1) }, 500)
    }

    private fun mostrarResultado(
        nome: String, preco: String, codigo: String,
        img: String = "", extras: List<String> = emptyList()
    ) {
        startActivity(
            Intent(this, ResultadoActivity::class.java)
                .putExtra(ResultadoActivity.EXTRA_NOME, nome)
                .putExtra(ResultadoActivity.EXTRA_PRECO, preco)
                .putExtra(ResultadoActivity.EXTRA_CODIGO, codigo)
                .putExtra(ResultadoActivity.EXTRA_IMG, img)
                .putExtra(ResultadoActivity.EXTRA_EXTRAS, ArrayList(extras))
        )
        finish()
    }

    override fun onDestroy() { 
        super.onDestroy(); 
        handler.removeCallbacks(watchdog) 
    }

    override fun onBackPressed() {
        startActivity(Intent(this, ScannerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
