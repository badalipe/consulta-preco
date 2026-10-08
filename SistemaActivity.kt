package com.quickprice.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * MOTOR DE BUSCA (invisível para o usuário).
 *
 * A WebView fica escondida: loga no Cartaz Fácil, injeta o código de barras
 * em #btnBusca, dispara a busca AJAX e extrai NOME + PREÇO do resultado
 * para mostrar na ResultadoActivity (nome + preço gigante).
 *
 * Seletores reais (HTML de unitario.php, out/2026):
 *   #btnBusca (campo), #pesq_prod (botão), #retorno (resultados),
 *   .trValor (linha do produto), sessão OK = #btnBusca existe.
 */
class SistemaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CODIGO = "codigo_barras"
        private const val INTERVALO_CHECK = 15_000L
        private const val CAMPO_BUSCA = "#btnBusca"
        private const val BTN_PESQUISAR = "#pesq_prod"
        private const val JS_SESSAO_OK = "(document.getElementById('btnBusca') !== null)"
    }

    private lateinit var webView: WebView
    private lateinit var cfg: SessionConfig
    private val handler = Handler(Looper.getMainLooper())
    private var reconectando = false
    private var tentativas = 0
    private var paginaPronta = false
    private var codigoAtual: String? = null
    private var coletando = false

    private val watchdog = object : Runnable {
        override fun run() { verificarSessao(); handler.postDelayed(this, INTERVALO_CHECK) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sistema)
        cfg = SessionConfig.load(this) ?: run { finish(); return }

        webView = findViewById(R.id.webView)
                webView.visibility = View.VISIBLE       // MODO TESTE
          // motor invisível
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                paginaPronta = true
                if (reconectando) { reconectando = false; tentativas = 0 }
                garantirPaginaConsulta()
            }
            override fun onReceivedError(view: WebView, code: Int, desc: String, url: String) {
                agendarReconexao()
            }
        }

        carregarSistema()
        codigoAtual = intent.getStringExtra(EXTRA_CODIGO)
        aplicarCodigoBarras(codigoAtual)
        handler.post(watchdog)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        codigoAtual = intent.getStringExtra(EXTRA_CODIGO)
        aplicarCodigoBarras(codigoAtual)
    }

    private fun carregarSistema() {
        paginaPronta = false
        coletando = false
        webView.loadUrl(cfg.url)
    }

    private fun garantirPaginaConsulta() {
        val js = """
            (function() {
                if (location.href.indexOf('unitario.php') === -1
                    && document.getElementById('btnBusca') === null) {
                    var l = document.querySelector('a[href*="unitario.php"]');
                    if (l) { l.click(); return 'indo_para_unitario'; }
                    return 'fora_da_pagina';
                }
                return 'ok';
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { r ->
            if (r != null && r.contains("fora_da_pagina") && !reconectando) {
                tentarLoginAutomatico()
            }
        }
    }

    // Injeta o código e dispara a busca
    private fun aplicarCodigoBarras(codigo: String?) {
        if (codigo.isNullOrBlank()) return
        val js = """
            (function() {
                var c = document.querySelector('$CAMPO_BUSCA');
                if (!c) return 'sem_campo_busca';
                c.value = '$codigo';
                c.dispatchEvent(new Event('change', {bubbles:true}));
                if (window.jQuery) { jQuery('$BTN_PESQUISAR').click(); return 'busca_enviada'; }
                var b = document.querySelector('$BTN_PESQUISAR');
                if (b) { b.click(); return 'busca_enviada'; }
                return 'sem_botao';
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { r ->
            when {
                r == null -> aviso("Pagina carregando, tente de novo")
                r.contains("sem_campo_busca") -> agendarReconexao(forcar = cfg.forcarReconexao)
                r.contains("sem_botao") -> aviso("Botao pesquisar nao encontrado")
                else -> iniciarColeta(codigo)
            }
        }
    }

    // ── Coleta NOME + PREÇO do resultado (polling até a tabela carregar) ──
    private fun iniciarColeta(codigo: String, tentativa: Int = 0) {
        if (coletando) return
        coletando = true
        val js = """
            (function() {
                var ret = document.getElementById('retorno');
                if (!ret) return 'ERRO_SEM_RETORNO';
                if (ret.querySelector('.fa-spinner')) return 'CARREGANDO';
                var t = ret.innerText.trim();
                if (t.length < 3) return 'CARREGANDO';
                var row = ret.querySelector('.trValor') || ret.querySelector('tr');
                if (!row) return 'VAZIO:' + t.substring(0, 300);
                return row.innerText;
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { r ->
            coletando = false
            val texto = r?.removeSurrounding("\"")?.replace("\\n", "\n")?.trim().orEmpty()
            when {
                texto.isEmpty() || texto == "null" ->
                    repetirColeta(codigo, tentativa)
                texto == "CARREGANDO" ->
                    repetirColeta(codigo, tentativa)
                texto.startsWith("VAZIO:") ->
                    mostrarResultado("Produto nao encontrado", "-", codigo)
                texto.startsWith("ERRO_SEM_RETORNO") ->
                    repetirColeta(codigo, tentativa)
                else -> {
                    val (nome, preco) = extrairNomePreco(texto)
                    mostrarResultado(nome, preco, codigo)
                }
            }
        }
    }

    private fun repetirColeta(codigo: String, tentativa: Int) {
        if (tentativa >= 16) {   // ~8s de espera no total
            mostrarResultado("Produto nao encontrado", "-", codigo)
            return
        }
        handler.postDelayed({ iniciarColeta(codigo, tentativa + 1) }, 500)
    }

    /** Separa NOME e PREÇO do texto da linha de resultado. */
    private fun extrairNomePreco(texto: String): Pair<String, String> {
        val linhas = texto.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        // Preço: padrão brasileiro 9.999,99 / 99,99 ou com R$
        val regexPreco = Regex("""R?\$?\s?\d{1,3}(\.\d{3})*,\d{2}""")
        var preco = linhas.firstOrNull { regexPreco.containsMatchIn(it) }
            ?.let { regexPreco.find(it)?.value?.replace("R$", "")?.trim() }
            ?.let { "R$ $it" }
        var nome = linhas.firstOrNull {
            it.length > 3 && !regexPreco.containsMatchIn(it) && !it.equals("clique", true)
        }
        // Se achou preço mas não nome, usa a linha anterior ao preço
        if (nome == null && preco != null) {
            val i = linhas.indexOfFirst { regexPreco.containsMatchIn(it) }
            if (i > 0) nome = linhas[i - 1]
        }
        return Pair(nome ?: "Produto", preco ?: "-")
    }

    private fun mostrarResultado(nome: String, preco: String, codigo: String) {
        startActivity(
            Intent(this, ResultadoActivity::class.java)
                .putExtra(ResultadoActivity.EXTRA_NOME, nome)
                .putExtra(ResultadoActivity.EXTRA_PRECO, preco)
                .putExtra(ResultadoActivity.EXTRA_CODIGO, codigo)
        )
        // volta ao scanner em vez de empilhar telas
        finish()
    }

    private fun tentarLoginAutomatico() {
        val js = """
            (function() {
                var u = document.querySelector("input[name='usuario'], input[name='login'],
                    input[name='user'], #usuario, #login, input[type='text']");
                var p = document.querySelector("input[type='password']");
                if (!u || !p) return 'sem_form_login';
                u.value = '${cfg.usuario}';
                p.value = '${cfg.senha}';
                var b = document.querySelector("button[type='submit'], input[type='submit']");
                if (b) { b.click(); return 'login_enviado'; }
                var f = p.closest('form'); if (f) { f.submit(); return 'form_enviado'; }
                return 'sem_submit';
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { r ->
            if (r != null && r.contains("sem_form_login") && !reconectando) {
                agendarReconexao(forcar = cfg.forcarReconexao)
            }
        }
    }

    private fun verificarSessao() {
        if (!paginaPronta) return
        webView.evaluateJavascript(JS_SESSAO_OK) { vivo ->
            if (vivo?.trim() != "true" && !reconectando) tentarLoginAutomatico()
        }
    }

    private fun agendarReconexao(forcar: Boolean = true) {
        if (reconectando) return
        reconectando = true
        tentativas++
        val delay = kotlin.math.min(30_000L, 2_000L * tentativas)
        if (forcar) CookieManager.getInstance().removeAllCookies(null)
        aviso("Reconectando automaticamente...")
        handler.postDelayed({ carregarSistema() }, delay)
    }

    private fun aviso(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() { super.onDestroy(); handler.removeCallbacks(watchdog) }

    override fun onBackPressed() {
        // volta direto pro scanner
        startActivity(Intent(this, ScannerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
