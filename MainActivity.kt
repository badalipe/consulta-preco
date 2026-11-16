package com.quickprice.app

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import jp.wasabeef.glide.transformations.BlurTransformation
import org.json.JSONObject
import org.jsoup.Jsoup
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "consulta_preco_config"
        private const val PREF_FIRST_UNLOCK_DONE = "first_unlock_done"
        private const val SESSION_CHECK_INTERVAL_MS = 10_000L
        private const val MAX_LOGIN_ATTEMPTS = 3
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val imageExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var imageRequestToken = 0
    private var lastOffNameSearchAtMs = 0L
    private var scanToneGenerator: ToneGenerator? = null
    private lateinit var productCache: ProductCache
    private var barcodeForCurrentSearch: String? = null
    private var externalProductNameForCurrentBarcode: String? = null
    private var barcodeLookupFailed = false
    private var productFoundForCurrentSearch = false
    private var fallbackAttemptedForCurrentSearch = false

    private data class ExternalProductInfo(val name: String, val imageUrl: String?)

    private lateinit var webViewSession: WebView
    private lateinit var txtStatus: TextView
    private lateinit var txtNome: TextView
    private lateinit var txtCodigo: TextView
    private lateinit var txtPreco: TextView
    private lateinit var txtCategoria: TextView
    private lateinit var txtPeso: TextView
    private lateinit var txtValidade: TextView
    private lateinit var edtCodigo: EditText
    private lateinit var imgProduto: ImageView
    private lateinit var imgProdutoGlow: ImageView

    private var acessoLiberado = false
    private var sessaoPronta = false
    private var loginEmAndamento = false
    private var reconexaoAgendada = false
    private var bloqueioDoSite = false
    private var tentativasLogin = 0
    private var atrasoReconexaoMs = 1_000L
    private var consultaPendente: String? = null
    private var idConsultaAtual = 0

    private val reconhecimentoVoz = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode == RESULT_OK) {
            val texto = resultado.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()

            if (!texto.isNullOrBlank()) {
                pesquisarProduto(texto)
            } else {
                Toast.makeText(this, "Não foi possível reconhecer a fala. Tente novamente.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val sessionWatchdog = object : Runnable {
        override fun run() {
            if (!acessoLiberado || !::webViewSession.isInitialized) return
            verificarSessaoNoServidor()
            mainHandler.postDelayed(this, SESSION_CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        productCache = ProductCache(applicationContext)

        txtStatus = findViewById(R.id.txtStatus)
        txtNome = findViewById(R.id.txtNome)
        txtCodigo = findViewById(R.id.txtCodigo)
        txtPreco = findViewById(R.id.txtPreco)
        txtCategoria = findViewById(R.id.txtCategoria)
        txtPeso = findViewById(R.id.txtPeso)
        txtValidade = findViewById(R.id.txtValidade)
        edtCodigo = findViewById(R.id.edtCodigo)
        imgProduto = findViewById(R.id.imgProduto)
        imgProdutoGlow = findViewById(R.id.imgProdutoGlow)

        findViewById<FloatingActionButton>(R.id.btnScan).setOnClickListener {
            startActivityForResult(Intent(this, ScannerActivity::class.java), 100)
        }

        findViewById<ImageButton>(R.id.btnBuscaVoz).setOnClickListener {
            iniciarBuscaPorVoz()
        }

        edtCodigo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val consulta = edtCodigo.text.toString().trim()
                if (consulta.isNotEmpty()) {
                    esconderTeclado()
                    pesquisarProduto(consulta)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }

        val jaDesbloqueado = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(PREF_FIRST_UNLOCK_DONE, false)
        if (jaDesbloqueado) {
            acessoLiberado = true
            iniciarSessaoServico()
        } else {
            solicitarSenhaInicial()
        }
    }

    override fun onResume() {
        super.onResume()
        if (acessoLiberado && ::webViewSession.isInitialized) {
            mainHandler.removeCallbacks(sessionWatchdog)
            verificarSessaoNoServidor()
            mainHandler.postDelayed(sessionWatchdog, SESSION_CHECK_INTERVAL_MS)
        }
    }

    override fun onPause() {
        mainHandler.removeCallbacks(sessionWatchdog)
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        imageRequestToken += 1
        imageExecutor.shutdownNow()
        scanToneGenerator?.let { tone -> runCatching { tone.stopTone(); tone.release() } }
        scanToneGenerator = null
        if (::webViewSession.isInitialized) {
            webViewSession.stopLoading()
            webViewSession.destroy()
        }
        if (::productCache.isInitialized) productCache.close()
        super.onDestroy()
    }

    private fun solicitarSenhaInicial() {
        if (BuildConfig.APP_FIRST_PASSWORD_HASH.isBlank()) {
            txtStatus.text = "Configuração de acesso não disponível neste APK"
            return
        }

        val campoSenha = EditText(this).apply {
            hint = "Senha de acesso"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines = 1
        }

        val dialogo = AlertDialog.Builder(this)
            .setTitle("Acesso ao Consulta Preço")
            .setMessage("Informe a senha na primeira utilização deste aplicativo.")
            .setView(campoSenha)
            .setPositiveButton("Entrar", null)
            .setNegativeButton("Sair") { _, _ -> finish() }
            .create()

        dialogo.setCancelable(false)
        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val senhaDigitada = campoSenha.text.toString()
                if (sha256(senhaDigitada) == BuildConfig.APP_FIRST_PASSWORD_HASH) {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_FIRST_UNLOCK_DONE, true)
                        .apply()
                    acessoLiberado = true
                    dialogo.dismiss()
                    iniciarSessaoServico()
                } else {
                    campoSenha.error = "Senha incorreta"
                    campoSenha.text?.clear()
                }
            }
        }
        dialogo.show()
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun iniciarSessaoServico() {
        if (BuildConfig.CARTAZ_USERNAME.isBlank() || BuildConfig.CARTAZ_PASSWORD.isBlank()) {
            txtStatus.text = "Configuração do serviço indisponível"
            return
        }

        webViewSession = findViewById(R.id.webViewSession)
        webViewSession.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptCookie(true)

        webViewSession.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.postDelayed({ inspecionarPagina() }, 400L)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    sessaoPronta = false
                    agendarReconexao()
                }
            }
        }

        txtStatus.text = "A ligar ao serviço..."
        carregarConsultaUnitario()
        mainHandler.removeCallbacks(sessionWatchdog)
        mainHandler.postDelayed(sessionWatchdog, SESSION_CHECK_INTERVAL_MS)
    }

    private fun urlConsultaUnitario(): String = "${BuildConfig.CARTAZ_BASE_URL}/unitario.php"

    private fun carregarConsultaUnitario() {
        if (!::webViewSession.isInitialized || bloqueioDoSite) return
        val separador = if (urlConsultaUnitario().contains("?")) "&" else "?"
        webViewSession.loadUrl(
            urlConsultaUnitario() + separador + "app_reconectar=" + System.currentTimeMillis()
        )
    }

    private fun inspecionarPagina() {
        if (!::webViewSession.isInitialized || isFinishing) return

        val script = """
            (function() {
                var bodyText = document.body ? document.body.innerText : '';
                var fullText = (document.title || '') + ' ' + bodyText;
                return {
                    href: location.href,
                    title: document.title || '',
                    body: bodyText.slice(0, 1800),
                    hasUser: !!document.querySelector('#usuario'),
                    hasPassword: !!document.querySelector('#senha'),
                    hasSearch: !!document.querySelector('#btnBusca'),
                    blocked: /Sorry, you have been blocked|Attention Required|access denied/i.test(fullText)
                };
            })()
        """.trimIndent()

        webViewSession.evaluateJavascript(script) { raw ->
            val state = runCatching { JSONObject(raw ?: "{}") }.getOrNull() ?: return@evaluateJavascript
            tratarEstadoPagina(state)
        }
    }

    private fun tratarEstadoPagina(state: JSONObject) {
        val href = state.optString("href", "")
        val body = state.optString("body", "").lowercase(Locale.ROOT)
        val bloqueado = state.optBoolean("blocked")

        if (bloqueado) {
            bloqueioDoSite = true
            sessaoPronta = false
            txtStatus.text = "O site bloqueou o acesso automático"
            return
        }

        if (state.optBoolean("hasUser") && state.optBoolean("hasPassword")) {
            sessaoPronta = false
            if (loginEmAndamento) {
                loginEmAndamento = false
                if (tentativasLogin >= MAX_LOGIN_ATTEMPTS) {
                    txtStatus.text = "Não foi possível iniciar sessão"
                    agendarReconexao(30_000L)
                } else {
                    agendarReconexao(1_500L)
                }
            } else if (body.contains("inválid") || body.contains("incorret")) {
                txtStatus.text = "Não foi possível validar o acesso"
            } else {
                enviarLoginAutomatico()
            }
            return
        }

        if (href.contains("home.php", ignoreCase = true)) {
            sessaoPronta = false
            webViewSession.loadUrl(urlConsultaUnitario())
            return
        }

        if (state.optBoolean("hasSearch")) {
            sessaoPronta = true
            loginEmAndamento = false
            bloqueioDoSite = false
            tentativasLogin = 0
            atrasoReconexaoMs = 1_000L
            txtStatus.text = "Pronto para pesquisar"
            consultaPendente?.let { enviarConsultaAoSite(it, idConsultaAtual) }
            return
        }

        if (href.contains("unitario.php", ignoreCase = true)) {
            webViewSession.postDelayed({ inspecionarPagina() }, 1_000L)
        } else {
            sessaoPronta = false
            agendarReconexao()
        }
    }

    private fun enviarLoginAutomatico() {
        if (bloqueioDoSite || !::webViewSession.isInitialized) return
        if (tentativasLogin >= MAX_LOGIN_ATTEMPTS) {
            txtStatus.text = "A restabelecer ligação..."
            agendarReconexao(30_000L)
            return
        }

        tentativasLogin += 1
        loginEmAndamento = true
        val usuarioJs = JSONObject.quote(BuildConfig.CARTAZ_USERNAME)
        val senhaJs = JSONObject.quote(BuildConfig.CARTAZ_PASSWORD)
        val script = """
            (function() {
                var usuario = document.querySelector('#usuario');
                var senha = document.querySelector('#senha');
                if (!usuario || !senha) return 'form_missing';
                usuario.value = $usuarioJs;
                senha.value = $senhaJs;
                usuario.dispatchEvent(new Event('input', {bubbles:true}));
                senha.dispatchEvent(new Event('input', {bubbles:true}));
                var form = usuario.form || senha.form;
                var submit = form ? form.querySelector('input[type="submit"],button[type="submit"],button') : null;
                if (submit) { submit.click(); return 'submitted'; }
                if (form && form.requestSubmit) { form.requestSubmit(); return 'submitted'; }
                if (form) { form.submit(); return 'submitted'; }
                return 'form_missing';
            })()
        """.trimIndent()

        webViewSession.evaluateJavascript(script) { result ->
            if (result == null || result.contains("form_missing")) {
                loginEmAndamento = false
                txtStatus.text = "Não foi possível iniciar sessão"
                agendarReconexao()
            }
        }
    }

    private fun verificarSessaoNoServidor() {
        if (!::webViewSession.isInitialized || bloqueioDoSite) return
        if (!sessaoPronta) {
            agendarReconexao()
            return
        }

        val unitarioJs = JSONObject.quote(urlConsultaUnitario())
        val script = """
            (async function() {
                try {
                    var response = await fetch($unitarioJs + '?app_verificar_sessao=' + Date.now(), {
                        credentials: 'include',
                        cache: 'no-store',
                        redirect: 'follow'
                    });
                    var html = await response.text();
                    var url = response.url || '';
                    if (url.indexOf('index.php') >= 0 || html.indexOf('id="usuario"') >= 0 || html.indexOf("id='usuario'") >= 0) return 'expired';
                    if (html.indexOf('id="btnBusca"') >= 0 || html.indexOf("id='btnBusca'") >= 0) return 'ok';
                    if (/Sorry, you have been blocked|Attention Required|access denied/i.test(html)) return 'blocked';
                    return 'unknown';
                } catch (e) {
                    return 'offline';
                }
            })()
        """.trimIndent()

        webViewSession.evaluateJavascript(script) { raw ->
            val state = raw?.trim()?.trim('"') ?: "offline"
            when (state) {
                "ok" -> atrasoReconexaoMs = 1_000L
                "expired", "unknown" -> {
                    sessaoPronta = false
                    txtStatus.text = "A restabelecer ligação..."
                    reconexaoAgendada = false
                    carregarConsultaUnitario()
                }
                "blocked" -> {
                    bloqueioDoSite = true
                    sessaoPronta = false
                    txtStatus.text = "O site bloqueou o acesso automático"
                }
                else -> {
                    sessaoPronta = false
                    txtStatus.text = "Sem conexão; tentando reconectar..."
                    agendarReconexao()
                }
            }
        }
    }

    private fun agendarReconexao(atrasoSolicitadoMs: Long? = null) {
        if (!acessoLiberado || !::webViewSession.isInitialized || bloqueioDoSite || reconexaoAgendada) return
        reconexaoAgendada = true
        val atraso = atrasoSolicitadoMs ?: atrasoReconexaoMs
        atrasoReconexaoMs = (atraso * 2).coerceAtMost(15_000L)
        mainHandler.postDelayed({
            reconexaoAgendada = false
            if (!sessaoPronta && !bloqueioDoSite) carregarConsultaUnitario()
        }, atraso)
    }

    private fun iniciarBuscaPorVoz() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga o nome do produto ou o código de barras")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            reconhecimentoVoz.launch(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Não há um serviço de reconhecimento de voz disponível neste dispositivo.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun pesquisarProduto(consulta: String) {
        val termo = consulta.trim()
        if (termo.isEmpty()) return

        idConsultaAtual += 1
        val idConsulta = idConsultaAtual
        val ean = normalizarEan(termo)
        val cached = ean?.let { productCache.find(it) }
        val termoPesquisa = cached?.productCode?.takeIf { it.isNotBlank() } ?: termo
        barcodeForCurrentSearch = ean
        externalProductNameForCurrentBarcode = cached?.name?.takeIf { it.isNotBlank() }
        barcodeLookupFailed = false
        productFoundForCurrentSearch = false
        fallbackAttemptedForCurrentSearch = false

        limparImagemProduto()
        edtCodigo.setText(termo)
        edtCodigo.setSelection(termo.length)
        txtNome.text = "Pesquisando: $termo"
        txtCodigo.text = "Consulta: $termo"
        txtPreco.text = "R$ —"
        txtStatus.text = if (sessaoPronta) "Consultando preço..." else "Reconectando sessão..."

        if (cached != null) {
            if (cached.name.isNotBlank()) txtNome.text = cached.name
            if (cached.productCode.isNotBlank()) txtCodigo.text = "Código: ${cached.productCode}"
            if (cached.family.isNotBlank()) txtCategoria.text = cached.family
            if (cached.imageUrl.isNotBlank()) mostrarImagemUrl(cached.imageUrl)
        }

        // A primeira consulta da foto começa logo após a leitura do EAN, sem esperar pelo preço.
        if (ean != null && cached?.imageUrl.isNullOrBlank()) {
            carregarImagemProduto(ean, cached?.name.orEmpty(), ean)
        }

        consultaPendente = termoPesquisa
        if (sessaoPronta) {
            enviarConsultaAoSite(termoPesquisa, idConsulta)
        } else {
            carregarConsultaUnitario()
        }
    }

    private fun enviarConsultaAoSite(termo: String, idConsulta: Int) {
        if (!sessaoPronta || !::webViewSession.isInitialized) {
            consultaPendente = termo
            return
        }

        val termoJs = JSONObject.quote(termo)
        val script = """
            (function() {
                var campo = document.querySelector('#btnBusca');
                var botao = document.querySelector('#pesq_prod');
                if (!campo || !botao) return 'not_ready';
                campo.value = $termoJs;
                campo.dispatchEvent(new Event('input', {bubbles:true}));
                campo.dispatchEvent(new Event('change', {bubbles:true}));
                botao.click();
                return 'sent';
            })()
        """.trimIndent()

        webViewSession.evaluateJavascript(script) { result ->
            if (idConsulta != idConsultaAtual) return@evaluateJavascript
            if (result?.contains("sent") == true) {
                consultaPendente = null
                mainHandler.postDelayed({ lerResultadoDaBusca(termo, idConsulta, 0) }, 900L)
            } else {
                consultaPendente = termo
                sessaoPronta = false
                carregarConsultaUnitario()
            }
        }
    }

    private fun lerResultadoDaBusca(termo: String, idConsulta: Int, tentativa: Int) {
        if (idConsulta != idConsultaAtual || !::webViewSession.isInitialized) return

        val script = """
            (function() {
                var area = document.querySelector('#lado_direito');
                if (!area) return {ready:false, hasProduct:false, name:'', code:'', price:'', family:''};
                var rows = Array.from(area.querySelectorAll('tr')).map(function(row) {
                    return Array.from(row.querySelectorAll('td')).map(function(cell) {
                        return (cell.innerText || '').trim();
                    });
                });
                var product = rows.find(function(cells) {
                    return cells.length >= 2 && /^\d+$/.test(cells[0]) && cells[1].length > 2;
                });
                var options = Array.from(document.querySelectorAll('#select_valores option'))
                    .map(function(option) { return (option.textContent || '').trim(); });
                var priceText = options.find(function(text) { return /Valores\s*:/i.test(text); }) || '';
                var match = priceText.match(/Valores\s*:\s*(?:R\$\s*)?([\d.]+,\d{2})/i);
                var description = product ? product[1] : '';
                var familyMatch = description.match(/Familia:\s*(.*)$/i);
                var family = familyMatch ? familyMatch[1].trim() : '';
                var name = description.replace(/\s*Familia:.*$/i, '').trim();
                return {
                    ready: true,
                    hasProduct: !!product,
                    code: product ? product[0] : '',
                    name: name,
                    family: family,
                    price: match ? match[1] : ''
                };
            })()
        """.trimIndent()

        webViewSession.evaluateJavascript(script) { raw ->
            if (idConsulta != idConsultaAtual) return@evaluateJavascript
            val data = runCatching { JSONObject(raw ?: "{}") }.getOrNull()
            if (data == null || !data.optBoolean("ready") ||
                (data.optBoolean("hasProduct") && data.optString("price").isBlank())
            ) {
                if (tentativa < 8) {
                    mainHandler.postDelayed({ lerResultadoDaBusca(termo, idConsulta, tentativa + 1) }, 500L)
                } else {
                    txtStatus.text = "Não foi possível ler o preço; tentando novamente"
                    verificarSessaoNoServidor()
                }
                return@evaluateJavascript
            }

            if (!data.optBoolean("hasProduct")) {
                if (tentativa < 5) {
                    mainHandler.postDelayed({ lerResultadoDaBusca(termo, idConsulta, tentativa + 1) }, 500L)
                } else {
                    barcodeLookupFailed = barcodeForCurrentSearch != null
                    if (tentarPesquisaPorNome(idConsulta)) return@evaluateJavascript
                    txtNome.text = "Produto não encontrado"
                    txtCodigo.text = "Consulta: $termo"
                    txtPreco.text = "R$ —"
                    txtStatus.text = "Nenhum resultado para esta busca"
                }
                return@evaluateJavascript
            }

            val nome = data.optString("name").ifBlank { termo }
            val codigo = data.optString("code").ifBlank { termo }
            val preco = data.optString("price")
            val familia = data.optString("family")
            productFoundForCurrentSearch = true
            txtNome.text = nome
            txtCodigo.text = "Código: $codigo"
            txtPreco.text = if (preco.isNotBlank()) "R$ $preco" else "R$ —"
            txtCategoria.text = familia.ifBlank { "—" }
            txtPeso.text = Regex("\\b\\d+(?:[,.]\\d+)?\\s?(?:kg|g|ml|l)\\b", RegexOption.IGNORE_CASE)
                .find(nome)?.value ?: "—"
            txtValidade.text = "—"
            val ean = normalizarEan(edtCodigo.text.toString())
            if (ean != null) productCache.saveProduct(ean, codigo, nome, familia)
            val imagemEmCache = ean?.let { productCache.find(it)?.imageUrl }.orEmpty()
            if (imagemEmCache.isNotBlank()) {
                mostrarImagemUrl(imagemEmCache)
            } else {
                val codigoImagem = ean ?: codigo
                carregarImagemProduto(codigoImagem, nome, ean)
            }
            txtStatus.text = if (preco.isNotBlank()) "Preço atualizado" else "Produto encontrado; preço indisponível"
        }
    }

    private fun limparImagemProduto() {
        imageRequestToken += 1
        if (!::imgProduto.isInitialized) return
        runCatching { Glide.with(this).clear(imgProduto) }
        if (::imgProdutoGlow.isInitialized) runCatching { Glide.with(this).clear(imgProdutoGlow) }
        if (::imgProdutoGlow.isInitialized) imgProdutoGlow.setImageResource(R.drawable.ic_placeholder_box)
        imgProduto.setImageResource(R.drawable.ic_placeholder_box)
    }

    private fun carregarImagemProduto(
        codigo: String,
        nomeProduto: String,
        cacheBarcode: String? = null,
        searchId: Int = idConsultaAtual
    ) {
        val ean = normalizarEan(codigo)
        val token = ++imageRequestToken
        if (ean == null && nomeProduto.trim().length < 3) {
            imgProduto.setImageResource(R.drawable.ic_placeholder_box)
            return
        }

        runCatching { Glide.with(this).clear(imgProduto) }
        imgProduto.setImageResource(R.drawable.ic_placeholder_box)
        imageExecutor.execute {
            val infoExterna = if (ean != null) {
                runCatching { buscarProdutoOpenFoodFacts(ean) }.getOrNull()
            } else null
            val nomeExterno = infoExterna?.name.orEmpty()
            val barcode = cacheBarcode?.takeIf { it.isNotBlank() }
            if (barcode != null && nomeExterno.isNotBlank()) {
                runCatching { productCache.saveDiscoveredName(barcode, nomeExterno) }
                mainHandler.post {
                    if (searchId != idConsultaAtual || barcodeForCurrentSearch != barcode) return@post
                    externalProductNameForCurrentBarcode = nomeExterno
                    if (barcodeLookupFailed && !productFoundForCurrentSearch) {
                        tentarPesquisaPorNome(searchId)
                    }
                }
            }
            val urlPorCodigo = infoExterna?.imageUrl ?: if (ean != null) {
                runCatching { buscarImagemBeepScan(ean) }.getOrNull()
            } else null
            val nomeParaImagem = nomeProduto.ifBlank { nomeExterno }
            val url = urlPorCodigo
                ?: runCatching { buscarImagemOpenFoodFactsPorNome(nomeParaImagem) }.getOrNull()
            if (!url.isNullOrBlank() && barcode != null) {
                runCatching { productCache.saveImage(barcode, url) }
            }
            if (token != imageRequestToken) return@execute

            runOnUiThread {
                if (token != imageRequestToken || isFinishing || isDestroyed) return@runOnUiThread
                if (url.isNullOrBlank()) {
                    imgProduto.setImageResource(R.drawable.ic_placeholder_box)
                } else {
                    mostrarImagemUrl(url)
                }
            }
        }
    }

    private fun mostrarImagemUrl(url: String) {
        if (!::imgProduto.isInitialized || url.isBlank()) return
        if (::imgProdutoGlow.isInitialized) {
            Glide.with(this)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transform(BlurTransformation(24, 3))
                .into(imgProdutoGlow)
        }
        Glide.with(this)
            .load(url)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_placeholder_box)
            .error(R.drawable.ic_placeholder_box)
            .centerInside()
            .into(imgProduto)
    }

    private fun tentarPesquisaPorNome(idConsulta: Int): Boolean {
        if (idConsulta != idConsultaAtual || productFoundForCurrentSearch || !barcodeLookupFailed ||
            fallbackAttemptedForCurrentSearch
        ) return false
        val nome = externalProductNameForCurrentBarcode?.trim().orEmpty()
        if (nome.length < 3 || nome.equals(edtCodigo.text.toString().trim(), ignoreCase = true)) return false

        fallbackAttemptedForCurrentSearch = true
        barcodeLookupFailed = false
        txtNome.text = nome
        txtStatus.text = "Código não localizado; a procurar pelo nome..."
        enviarConsultaAoSite(nome, idConsulta)
        return true
    }

    private fun normalizarEan(codigo: String): String? {
        if (!codigo.all { it in '0'..'9' || it.isWhitespace() || it == '-' }) return null
        val digitos = codigo.filter { it in '0'..'9' }
        return digitos.takeIf { it.length in 8..14 }
    }

    private fun buscarImagemBeepScan(ean: String): String? {
        val pagina = Jsoup.connect("https://www.beepscan.com/barcode/$ean")
            .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .timeout(8_000)
            .get()

        val candidatos = buildList {
            pagina.selectFirst("meta[property=og:image]")?.attr("abs:content")?.let(::add)
            pagina.select("img[src]").forEach { imagem ->
                imagem.attr("abs:src").takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        return candidatos.firstOrNull(::urlDeImagemValido)
    }

    private fun buscarProdutoOpenFoodFacts(ean: String): ExternalProductInfo? {
        val resposta = Jsoup.connect(
            "https://world.openfoodfacts.org/api/v2/product/$ean?fields=code,product_name,product_name_pt,image_front_url,image_url,image_small_url"
        )
            .ignoreContentType(true)
            .userAgent("ConsultaPreco/1.0 (Android)")
            .timeout(8_000)
            .execute()
            .body()

        val produto = JSONObject(resposta).optJSONObject("product") ?: return null
        val nome = listOf("product_name_pt", "product_name")
            .map { produto.optString(it).trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val imagem = listOf("image_front_url", "image_url", "image_small_url")
            .map { produto.optString(it) }
            .firstOrNull(::urlDeImagemValido)
        return if (nome.isNotBlank() || imagem != null) ExternalProductInfo(nome, imagem) else null
    }

    private fun buscarImagemOpenFoodFactsPorNome(nome: String): String? {
        val termo = nome.trim()
        if (termo.length < 3) return null

        val esperaMs = 6_500L - (System.currentTimeMillis() - lastOffNameSearchAtMs)
        if (esperaMs > 0) Thread.sleep(esperaMs)
        lastOffNameSearchAtMs = System.currentTimeMillis()

        val resposta = Jsoup.connect("https://search.openfoodfacts.org/search")
            .data("q", termo)
            .data("langs", "pt,en")
            .data("page_size", "10")
            .data("fields", "code,product_name,image_front_url,image_url")
            .ignoreContentType(true)
            .userAgent("ConsultaPreco/1.0 (Android)")
            .timeout(8_000)
            .execute()
            .body()

        val hits = JSONObject(resposta).optJSONArray("hits") ?: return null
        val palavras = termo.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }
            .toSet()

        for (indice in 0 until hits.length()) {
            val produto = hits.optJSONObject(indice) ?: continue
            val nomeCatalogo = produto.optString("product_name").lowercase(Locale.ROOT)
            val palavrasCatalogo = nomeCatalogo.split(Regex("[^\\p{L}\\p{N}]+"))
            if (palavras.isNotEmpty() && palavras.intersect(palavrasCatalogo.toSet()).isEmpty()) continue

            val url = listOf("image_front_url", "image_url")
                .map { produto.optString(it) }
                .firstOrNull(::urlDeImagemValido)
            if (url != null) return url
        }
        return null
    }

    private fun urlDeImagemValido(url: String): Boolean {
        val limpa = url.substringBefore('?')
        return url.startsWith("https://", ignoreCase = true) &&
            !limpa.endsWith(".svg", ignoreCase = true) &&
            !url.contains("logo", ignoreCase = true) &&
            !url.contains("favicon", ignoreCase = true) &&
            !url.contains("placeholder", ignoreCase = true)
    }

    private fun reproduzirBipScanner() {
        scanToneGenerator?.let { tone -> runCatching { tone.stopTone(); tone.release() } }
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }.getOrNull() ?: return
        scanToneGenerator = tone
        val iniciou = runCatching {
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        }.getOrDefault(false)
        if (!iniciou) {
            runCatching { tone.release() }
            if (scanToneGenerator === tone) scanToneGenerator = null
            return
        }

        mainHandler.postDelayed({
            if (scanToneGenerator === tone) {
                runCatching { tone.stopTone(); tone.release() }
                scanToneGenerator = null
            }
        }, 350L)
    }

    private fun esconderTeclado() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(edtCodigo.windowToken, 0)
    }

    @Deprecated("Deprecated in Android, kept for compatibility with ScannerActivity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val codigo = data?.getStringExtra("codigo")
            if (!codigo.isNullOrBlank()) {
                reproduzirBipScanner()
                pesquisarProduto(codigo)
            }
        }
    }
}
