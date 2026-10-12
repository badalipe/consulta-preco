package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val API_TOKEN = "3hjr6ken8b91xo7ym2wpcsqagifvu45n"
    private val LOJA_ID = "7"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnScan).setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 100)
        }

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener {
            limpar()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val codigo = data?.getStringExtra("codigo")
            codigo?.let {
                findViewById<TextView>(R.id.txtCodigo).text = it
                buscarPrecoAPI(it)
            }
        }
    }

    private fun buscarPrecoAPI(codigo: String) {
        findViewById<TextView>(R.id.txtNome).text = "Buscando..."
        findViewById<TextView>(R.id.txtPreco).text = "..."

        Thread {
            try {
                // Tenta a API pública primeiro
                val urlStr = "https://apicartazfacil.com/public/api/produtos?codigo=$codigo&loja_id=$LOJA_ID&token=$API_TOKEN"
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Accept", "application/json")

                val response = conn.inputStream.bufferedReader().readText()

                runOnUiThread {
                    processarJSON(response, codigo)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    // Se API falhar, tenta método alternativo
                    buscarPrecoSite(codigo)
                }
            }
        }.start()
    }

    private fun processarJSON(json: String, codigo: String) {
        try {
            // Tenta extrair dados do JSON
            val nomeRegex = Regex("\"nome\":\"([^\"]+)\"")
            val precoRegex = Regex("\"preco\":\"([^\"]+)\"")

            val nome = nomeRegex.find(json)?.groupValues?.get(1) ?: "Produto"
            val preco = precoRegex.find(json)?.groupValues?.get(1) ?: ""

            findViewById<TextView>(R.id.txtNome).text = nome
            findViewById<TextView>(R.id.txtPreco).text = if (preco.isNotEmpty()) "R$ $preco" else "R$ -"

            // Volta para scanner
            handler.postDelayed({
                val intent = Intent(this, ScannerActivity::class.java)
                startActivityForResult(intent, 100)
            }, 5000)
        } catch (e: Exception) {
            findViewById<TextView>(R.id.txtNome).text = "Erro ao processar"
        }
    }

    private fun buscarPrecoSite(codigo: String) {
        // Método alternativo via site
        Thread {
            try {
                val url = URL("https://rmarket.cartazfacil.pro/inc/buscaProduto.php?buscaProd=$codigo&sessao=")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 10000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

                val response = conn.inputStream.bufferedReader().readText()

                runOnUiThread {
                    extrairDoHTML(response)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    findViewById<TextView>(R.id.txtNome).text = "Erro: ${e.message}"
                }
            }
        }.start()
    }

    private fun extrairDoHTML(html: String) {
        val nomeRegex = Regex("<small class='descRetornoProd\d+'[^>]*>([^<]+)")
        val precoRegex = Regex("Valores:\s*(\d+,\d{2})")

        val nome = nomeRegex.find(html)?.groupValues?.get(1)?.trim() ?: "Não encontrado"
        val preco = precoRegex.find(html)?.groupValues?.get(1) ?: ""

        findViewById<TextView>(R.id.txtNome).text = nome
        findViewById<TextView>(R.id.txtPreco).text = if (preco.isNotEmpty()) "R$ $preco" else "R$ -"
    }

    private fun limpar() {
        findViewById<TextView>(R.id.txtNome).text = "Escaneie ou digite o código"
        findViewById<TextView>(R.id.txtCodigo).text = "-"
        findViewById<TextView>(R.id.txtPreco).text = "R$ 0,00"
    }
}
