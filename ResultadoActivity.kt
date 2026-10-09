package com.quickprice.app

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
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
        const val EXTRA_IMG = "img"        // ← AQUI ESTÁ A CONSTANTE QUE FALTAVA
        const val EXTRA_EXTRAS = "extras"  // ← E ESTA
        private const val PREF_HIST = "historico"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resultado)

        val nomeInicial = intent.getStringExtra(EXTRA_NOME) ?: "Produto"
        val preco = intent.getStringExtra(EXTRA_PRECO) ?: "-"
        val codigo = intent.getStringExtra(EXTRA_CODIGO) ?: ""
        val img = intent.getStringExtra(EXTRA_IMG).orEmpty()
        val extras = intent.getStringArrayListExtra(EXTRA_EXTRAS) ?: arrayListOf()

        findViewById<TextView>(R.id.txtNome).text = nomeInicial
        findViewById<TextView>(R.id.txtPreco).text = preco
        findViewById<TextView>(R.id.txtCodigo).text = codigo.ifEmpty { "-" }

        val imgView = findViewById<ImageView>(R.id.imgProduto)
        if (img.isNotEmpty() && img.startsWith("http")) {
            imgView.visibility = View.VISIBLE
            Glide.with(this).load(img).into(imgView)
        } else {
            consultarFotoOpenFoodFacts(codigo, imgView) { nomeOf ->
                if (nomeInicial == "Produto" && nomeOf.isNotEmpty()) {
                    findViewById<TextView>(R.id.txtNome).text = nomeOf
                }
            }
        }

        val labels = listOf(R.id.lblInfo1, R.id.lblInfo2, R.id.lblInfo3)
        val valores = listOf(R.id.valInfo1, R.id.valInfo2, R.id.valInfo3)
        extras.take(3).forEachIndexed { i, item ->
            val parts = item.split("|", limit = 2)
            findViewById<TextView>(labels[i]).text = parts.getOrElse(0) { "Info" }
            findViewById<TextView>(valores[i]).text = parts.getOrElse(1) { "" }
        }

        salvarHistorico(nomeInicial, preco, codigo)

        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                .startTone(ToneGenerator.TONE_PROP_BEEP, 250)
        }

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener { voltarAoScanner() }
        findViewById<TextView>(R.id.btnHistorico).setOnClickListener { mostrarHistorico() }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnScan)
            .setOnClickListener { voltarAoScanner() }

        val edt = findViewById<EditText>(R.id.edtCodigo)
        edt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                buscar(edt.text.toString())
                true
            } else false
        }
    }

    private fun consultarFotoOpenFoodFacts(codigo: String, imgView: ImageView, onNome: (String) -> Unit) {
        if (codigo.isEmpty()) return
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("https://world.openfoodfacts.org/api/v2/product/$codigo.json")
                conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                if (json.optInt("status", 0) == 1) {
                    val prod = json.optJSONObject("product")
                    val foto = prod?.optString("image_front_url").orEmpty()
                    val nome = prod?.optString("product_name").orEmpty()
                    runOnUiThread {
                        if (foto.startsWith("http")) {
                            imgView.visibility = View.VISIBLE
                            Glide.with(this).load(foto).into(imgView)
                        }
                        onNome(nome)
                    }
                }
            } catch (e: Exception) {
                // sem foto, ok
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    private fun buscar(codigo: String) {
        val limpo = codigo.trim()
        if (limpo.isEmpty()) return
        startActivity(
            Intent(this, SistemaActivity::class.java)
                .putExtra(SistemaActivity.EXTRA_CODIGO, limpo)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    private fun voltarAoScanner() {
        startActivity(Intent(this, ScannerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
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

    private fun mostrarHistorico() {
        val array = JSONArray(getSharedPreferences(PREF_HIST, MODE_PRIVATE)
            .getString("itens", "[]"))
        if (array.length() == 0) {
            Toast.makeText(this, "Sem histórico ainda", Toast.LENGTH_SHORT).show()
            return
        }
        val titulos = ArrayList<String>()
        val codigos = ArrayList<String>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            titulos.add("${o.optString("nome")} — ${o.optString("preco")}")
            codigos.add(o.optString("codigo"))
        }
        AlertDialog.Builder(this)
            .setTitle("Consultas recentes")
            .setItems(titulos.toTypedArray()) { _, which -> buscar(codigos[which]) }
            .setNegativeButton("Fechar", null)
            .show()
    }
}
