package com.quickprice.app

import android.annotation.SuppressLint
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

class ResultadoActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resultado)

        val codigo = intent.getStringExtra("codigo") ?: ""

        findViewById<TextView>(R.id.txtCodigo).text = codigo

        // Botão voltar para o MENU (não para o scanner)
        findViewById<TextView>(R.id.btnVoltar).setOnClickListener {
            finish() // Volta para o MainActivity
        }

        // Botão escanear outro
        findViewById<Button>(R.id.btnScan).setOnClickListener {
            finish() // Volta para o MainActivity
        }

        // Busca manual
        val edt = findViewById<EditText>(R.id.edtCodigo)
        edt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                buscar(edt.text.toString())
                true
            } else false
        }

        // Busca o produto
        buscar(codigo)

        // Carrega imagem com efeito Netflix
        carregarImagem(codigo)
    }

    private fun carregarImagem(codigo: String) {
        if (codigo.isEmpty()) return

        Thread {
            try {
                val url = java.net.URL("https://world.openfoodfacts.org/api/v2/product/$codigo.json")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())

                if (json.optInt("status", 0) == 1) {
                    val prod = json.optJSONObject("product")
                    val foto = prod?.optString("image_front_url").orEmpty()

                    if (foto.isNotEmpty() && foto.startsWith("http")) {
                        runOnUiThread {
                            Glide.with(this)
                                .asBitmap()
                                .load(foto)
                                .into(object : CustomTarget<Bitmap>() {
                                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                        // Imagem principal com fade lateral
                                        val comFade = aplicarFadeLateral(resource)
                                        findViewById<ImageView>(R.id.imgProduto).setImageBitmap(comFade)
                                        findViewById<ImageView>(R.id.imgProduto).visibility = android.view.View.VISIBLE

                                        // Fundo desfocado
                                        val blur = blurBitmap(resource, 25f)
                                        findViewById<ImageView>(R.id.imgFundo).setImageBitmap(blur)
                                        findViewById<ImageView>(R.id.imgFundo).visibility = android.view.View.VISIBLE
                                    }
                                    override fun onLoadCleared(placeholder: Drawable?) {}
                                })
                        }
                    }
                }
            } catch (e: Exception) { }
        }.start()
    }

    private fun aplicarFadeLateral(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val paint = Paint()
        val fadeWidth = (width * 0.15f).toInt()

        // Fade esquerda
        val gradientLeft = LinearGradient(
            0f, 0f, fadeWidth.toFloat(), 0f,
            Color.TRANSPARENT, Color.BLACK,
            Shader.TileMode.CLAMP
        )
        paint.shader = gradientLeft
        canvas.drawRect(0f, 0f, fadeWidth.toFloat(), height.toFloat(), paint)

        // Fade direita
        val gradientRight = LinearGradient(
            (width - fadeWidth).toFloat(), 0f, width.toFloat(), 0f,
            Color.BLACK, Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        paint.shader = gradientRight
        canvas.drawRect((width - fadeWidth).toFloat(), 0f, width.toFloat(), height.toFloat(), paint)

        return output
    }

    private fun blurBitmap(bitmap: Bitmap, radius: Float): Bitmap {
        val width = Math.round(bitmap.width * 0.5f)
        val height = Math.round(bitmap.height * 0.5f)
        val inputBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
        val outputBitmap = Bitmap.createBitmap(inputBitmap)

        val rs = RenderScript.create(this)
        val theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        val tmpIn = Allocation.createFromBitmap(rs, inputBitmap)
        val tmpOut = Allocation.createFromBitmap(rs, outputBitmap)
        theIntrinsic.setRadius(radius)
        theIntrinsic.forEach(tmpOut)
        tmpOut.copyTo(outputBitmap)

        return outputBitmap
    }

    private fun buscar(codigo: String) {
        if (codigo.isBlank()) return

        findViewById<TextView>(R.id.txtNome).text = "Buscando..."
        findViewById<TextView>(R.id.txtPreco).text = "..."

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val js = """
                    (function() {
                        var campo = document.querySelector('#btnBusca');
                        if (campo) {
                            campo.value = '$codigo';
                            campo.dispatchEvent(new Event('input', {bubbles:true}));
                            if (window.jQuery) {
                                jQuery('#pesq_prod').click();
                            }
                        }
                    })();
                """.trimIndent()
                webView.evaluateJavascript(js, null)

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    webView.evaluateJavascript("""
                        (function() {
                            var ret = document.getElementById('retorno');
                            if (ret && ret.innerText.trim().length > 3) {
                                return ret.innerText;
                            }
                            return '';
                        })();
                    """.trimIndent()) { resultado ->
                        if (!resultado.isNullOrBlank() && resultado != "null") {
                            val texto = resultado.removeSurrounding("\"")
                            processarResultado(texto, codigo)
                        } else {
                            mostrarErro("Produto não encontrado")
                        }
                    }
                }, 2000)
            }

            override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
                mostrarErro("Erro de conexão")
            }
        }

        // SERVIDOR LOCAL (igual ao equipamento Sweda)
        webView.loadUrl("http://10.10.56.103/unitario.php")
    }

    private fun processarResultado(texto: String, codigo: String) {
        val linhas = texto.split("\n")
        var nome = ""
        var preco = ""
        var categoria = ""
        var peso = ""
        var validade = ""

        for (linha in linhas) {
            val limpa = linha.trim()
            when {
                limpa.contains("R$") || Regex("\\d+,\\d{2}").containsMatchIn(limpa) -> preco = limpa
                limpa.contains("g", ignoreCase = true) && Regex("\\d+").containsMatchIn(limpa) -> peso = limpa
                Regex("\\d{2}/\\d{2}/\\d{4}").containsMatchIn(limpa) -> validade = limpa
                limpa.length > 3 && nome.isEmpty() -> nome = limpa
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
            findViewById<TextView>(R.id.txtPreco).text = "Tente novamente"
        }
    }
}
