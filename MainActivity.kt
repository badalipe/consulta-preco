package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var edtCodigo: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        edtCodigo = findViewById(R.id.edtCodigo)

        findViewById<Button>(R.id.btnScan).setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 100)
        }

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener {
            limpar()
        }

        edtCodigo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val codigo = edtCodigo.text.toString().trim()
                if (codigo.isNotEmpty()) {
                    buscarPreco(codigo)
                }
                true
            } else false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val codigo = data?.getStringExtra("codigo")
            codigo?.let {
                findViewById<TextView>(R.id.txtCodigo).text = it
                edtCodigo.setText(it)
                buscarPreco(it)
            }
        }
    }

    private fun buscarPreco(codigo: String) {
        findViewById<TextView>(R.id.txtNome).text = "Buscando..."
        findViewById<TextView>(R.id.txtPreco).text = "..."

        Thread {
            try {
                val url = URL("https://rmarket.cartazfacil.pro/inc/buscaProduto.php?buscaProd=" + codigo + "&sessao=")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

                val response = conn.inputStream.bufferedReader().readText()

                // DEBUG: Mostra tamanho da resposta
                runOnUiThread {
                    Toast.makeText(this, "Resposta: " + response.length + " chars", Toast.LENGTH_LONG).show()
                }

                runOnUiThread {
                    extrairPreco(response, codigo)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    findViewById<TextView>(R.id.txtNome).text = "Erro: " + e.message
                }
            }
        }.start()
    }

    private fun extrairPreco(html: String, codigo: String) {
        // Tenta múltiplos padrões de regex

        // Padrão 1: descRetornoProd
        var nomeRegex = Regex("<small class='descRetornoProd[0-9]+'[^>]*>([^<]+)")
        var nomeMatch = nomeRegex.find(html)
        var nome = nomeMatch?.groupValues?.get(1)?.trim()

        // Padrão 2: Qualquer small com classe descRetorno
        if (nome == null) {
            nomeRegex = Regex("<small[^>]*class='[^']*descRetorno[^']*'[^>]*>([^<]+)")
            nomeMatch = nomeRegex.find(html)
            nome = nomeMatch?.groupValues?.get(1)?.trim()
        }

        // Padrão 3: Qualquer texto com MARGARINA, ARROZ, etc
        if (nome == null) {
            val produtoRegex = Regex("([A-Z][A-Z\s]{10,50})")
            val produtoMatch = produtoRegex.find(html)
            nome = produtoMatch?.groupValues?.get(1)?.trim()
        }

        // Extrai preço - múltiplos padrões
        var preco = ""
        val precoPatterns = listOf(
            Regex("Valores:\s*([0-9]+,[0-9]{2})"),
            Regex("R\$\s*([0-9]+,[0-9]{2})"),
            Regex("([0-9]+,[0-9]{2})")
        )

        for (pattern in precoPatterns) {
            val match = pattern.find(html)
            if (match != null) {
                preco = match.groupValues[1]
                break
            }
        }

        // Se não encontrou nada, mostra debug
        if (nome == null && preco.isEmpty()) {
            findViewById<TextView>(R.id.txtNome).text = "Nao encontrado (debug)"
            findViewById<TextView>(R.id.txtPreco).text = "R$ -"
        } else {
            findViewById<TextView>(R.id.txtNome).text = nome ?: "Sem nome"
            findViewById<TextView>(R.id.txtPreco).text = if (preco.isNotEmpty()) "R$ " + preco else "R$ -"
        }

        findViewById<TextView>(R.id.txtCodigo).text = codigo
    }

    private fun limpar() {
        findViewById<TextView>(R.id.txtNome).text = "Escaneie ou digite o código"
        findViewById<TextView>(R.id.txtCodigo).text = "-"
        findViewById<TextView>(R.id.txtPreco).text = "R$ 0,00"
        edtCodigo.setText("")
    }
}
