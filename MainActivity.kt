package com.quickprice.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class MainActivity : AppCompatActivity() {

    private val scannerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val codigo = result.data?.getStringExtra("codigo")
            codigo?.let {
                findViewById<TextView>(R.id.txtCodigo).text = it
                buscarPreco(it)
                carregarImagem(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)  // Abre DIRETO na tela principal

        // Botão ESCANEAR
        findViewById<Button>(R.id.btnScan).setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            scannerLauncher.launch(intent)
        }

        // Digitação manual
        val edt = findViewById<EditText>(R.id.edtCodigo)
        edt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val codigo = edt.text.toString()
                if (codigo.isNotBlank()) {
                    findViewById<TextView>(R.id.txtCodigo).text = codigo
                    buscarPreco(codigo)
                    carregarImagem(codigo)
                }
                true
            } else false
        }

        // Botão voltar (limpa tela)
        findViewById<TextView>(R.id.btnVoltar).setOnClickListener {
            limparTela()
        }
    }

    private fun limparTela() {
        findViewById<TextView>(R.id.txtNome).text = "Escaneie ou digite o código"
        findViewById<TextView>(R.id.txtPreco).text = "R$ 0,00"
        findViewById<TextView>(R.id.txtCodigo).text = "-"
        findViewById<TextView>(R.id.txtCategoria).text = "-"
        findViewById<TextView>(R.id.txtPeso).text = "-"
        findViewById<TextView>(R.id.txtValidade).text = "-"
        findViewById<EditText>(R.id.edtCodigo).setText("")
        findViewById<ImageView>(R.id.imgProduto).visibility = View.GONE
        findViewById<ImageView>(R.id.imgFundo).visibility = View.GONE
    }

    private fun carregarImagem(codigo: String) {
        if (codigo.isEmpty() || codigo == "-") return

        Thread {
            try {
                val url = java.net.URL("https://world.openfoodfacts.org/api/v2/product/$codigo.json")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())

                if (json.optInt("status", 0) == 1) {
                    val prod = json.optJSONObject("product")
                    val foto = prod?.optString("image_front_url").orEmpty()

                    if (foto.isNotEmpty()) {
                        runOnUiThread {
                            Glide.with(this)
                                .asBitmap()
                                .load(foto)
                                .into(object : CustomTarget<Bitmap>() {
                                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                        // Efeito fumaça/netflix
                                        val comFade = aplicarFade(resource)
                                        findViewById<ImageView>(R.id.imgProduto).setImageBitmap(comFade)
                                        findViewById<ImageView>(R.id.imgProduto).visibility = View.VISIBLE

                                        val blur = blurBitmap(resource, 25f)
                                        findViewById<ImageView>(R.id.imgFundo).setImageBitmap(blur)
                                        findViewById<ImageView>(R.id.imgFundo).visibility = View.VISIBLE
                                    }
                                    override fun onLoadCleared(placeholder: Drawable?) {}
                                })
                        }
                    }
                }
            } catch (e: Exception) { }
        }.start()
    }

    private fun aplicarFade(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val paint = Paint()
        val fadeW = (w * 0.2f).toInt()

        val gl = LinearGradient(0f, 0f, fadeW.toFloat(), 0f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        paint.shader = gl
        canvas.drawRect(0f, 0f, fadeW.toFloat(), h.toFloat(), paint)

        val gr = LinearGradient((w - fadeW).toFloat(), 0f, w.toFloat(), 0f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        paint.shader = gr
        canvas.drawRect((w - fadeW).toFloat(), 0f, w.toFloat(), h.toFloat(), paint)

        return output
    }

    private fun blurBitmap(bitmap: Bitmap, radius: Float): Bitmap {
        val w = (bitmap.width * 0.5f).toInt()
        val h = (bitmap.height * 0.5f).toInt()
        val input = Bitmap.createScaledBitmap(bitmap, w, h, false)
        val output = Bitmap.createBitmap(input)

        val rs = RenderScript.create(this)
        val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        val tmpIn = Allocation.createFromBitmap(rs, input)
        val tmpOut = Allocation.createFromBitmap(rs, output)
        blur.setRadius(radius)
        blur.forEach(tmpOut)
        tmpOut.copyTo(output)

        return output
    }

    private fun buscarPreco(codigo: String) {
        if (codigo.isBlank() || codigo == "-") return

        findViewById<TextView>(R.id.txtNome).text = "Buscando..."

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.visibility = View.INVISIBLE
        webView.layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
        (findViewById<View>(android.R.id.content) as android.view.ViewGroup).addView(webView)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val js = "(function(){var c=document.querySelector('#btnBusca');if(c){c.value='$codigo';c.dispatchEvent(new Event('input',{bubbles:true}));if(window.jQuery){jQuery('#pesq_prod').click();}}})();"
                webView.evaluateJavascript(js, null)

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    webView.evaluateJavascript("(function(){var r=document.getElementById('retorno');if(r&&r.innerText.trim().length>3){return r.innerText;}return '';})()") { res ->
                        if (!res.isNullOrBlank() && res != "null") {
                            processar(res.removeSurrounding("\""), codigo)
                        } else {
                            mostrarErro("Produto não encontrado")
                        }
                    }
                }, 2000)
            }
        }

        webView.loadUrl("http://10.10.56.103/unitario.php")
    }

    private fun processar(texto: String, codigo: String) {
        val linhas = texto.split("\n")
        var nome = ""
        var preco = ""
        var categoria = ""
        var peso = ""
        var validade = ""

        for (linha in linhas) {
            val l = linha.trim()
            when {
                l.contains("R$") || Regex("\\d+,\\d{2}").containsMatchIn(l) -> preco = l
                l.contains("g", ignoreCase = true) && Regex("\\d+").containsMatchIn(l) -> peso = l
                Regex("\\d{2}/\\d{2}/\\d{4}").containsMatchIn(l) -> validade = l
                l.length > 3 && nome.isEmpty() -> nome = l
            }
        }

        runOnUiThread {
            findViewById<TextView>(R.id.txtNome).text = nome.ifEmpty { "Produto" }
            findViewById<TextView>(R.id.txtPreco).text = preco.ifEmpty { "R$ -" }
            findViewById<TextView>(R.id.txtCodigo).text = codigo
            findViewById<TextView>(R.id.txtCategoria).text = categoria.ifEmpty { "-" }
            findViewById<TextView>(R.id.txtPeso).text = peso.ifEmpty { "-" }
            findViewById<TextView>(R.id.txtValidade).text = validade.ifEmpty { "-" }
        }
    }

    private fun mostrarErro(msg: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.txtNome).text = msg
        }
    }
}
