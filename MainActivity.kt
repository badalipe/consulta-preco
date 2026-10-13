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
    private val SERVER = "http://10.10.254.73:8343"
    private val API = "/SMProdutosAPI/api/v4/produtos/precos-produtos"
    private val EMPRESA = "4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnScan = findViewById<Button>(R.id.btnScan)
        btnScan.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 100)
        }

        // Testa API sem seguir redirecionamentos
        testarAPI()
    }

    private fun testarAPI() {
        val txtStatus = findViewById<TextView>(R.id.txtNome)
        txtStatus.text = "Testando conexão..."

        Thread {
            try {
                val url = URL(SERVER + API + "?nroEmpresa=" + EMPRESA)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.instanceFollowRedirects = false  // NAO segue redirecionamentos
                conn.setRequestProperty("Accept", "application/json")

                val code = conn.responseCode
                val location = conn.getHeaderField("Location")

                runOnUiThread {
                    when {
                        code == 200 -> {
                            txtStatus.text = "API TOTVS OK!"
                        }
                        code == 302 || code == 301 -> {
                            txtStatus.text = "Redireciona para: " + (location ?: "desconhecido")
                        }
                        code == 401 || code == 403 -> {
                            txtStatus.text = "Nao autorizado (precisa login)"
                        }
                        else -> {
                            txtStatus.text = "HTTP " + code
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    txtStatus.text = "Erro: " + e.message
                }
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val codigo = data?.getStringExtra("codigo")
            codigo?.let {
                val txtCodigo = findViewById<TextView>(R.id.txtCodigo)
                txtCodigo.text = it
                buscarPreco(it)
            }
        }
    }

    private fun buscarPreco(codigoBarras: String) {
        val txtNome = findViewById<TextView>(R.id.txtNome)
        val txtPreco = findViewById<TextView>(R.id.txtPreco)

        txtNome.text = "Buscando..."
        txtPreco.text = "..."

        Thread {
            try {
                val url = URL(SERVER + API + "?codigoAcesso=" + codigoBarras + "&nroEmpresa=" + EMPRESA)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("Accept", "application/json")

                val code = conn.responseCode
                val response = conn.inputStream.bufferedReader().readText()

                runOnUiThread {
                    when {
                        code == 200 -> {
                            processarJSON(response)
                        }
                        code == 302 || code == 301 -> {
                            txtNome.text = "API redireciona (precisa auth)"
                            txtPreco.text = "R$ -"
                        }
                        code == 401 || code == 403 -> {
                            txtNome.text = "Nao autorizado"
                            txtPreco.text = "R$ -"
                        }
                        code == 404 -> {
                            txtNome.text = "Produto nao encontrado"
                            txtPreco.text = "R$ -"
                        }
                        else -> {
                            txtNome.text = "HTTP " + code
                            txtPreco.text = "R$ -"
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    txtNome.text = "Erro: " + e.message
                    txtPreco.text = "R$ -"
                }
            }
        }.start()
    }

    private fun processarJSON(json: String) {
        val txtNome = findViewById<TextView>(R.id.txtNome)
        val txtPreco = findViewById<TextView>(R.id.txtPreco)

        try {
            val nomeRegex = Regex("\"descricao\":\"([^\"]+)\"")
            val precoRegex = Regex("\"preco\":([0-9.]+)")

            val nomeMatch = nomeRegex.find(json)
            val precoMatch = precoRegex.find(json)

            val nome = nomeMatch?.groupValues?.get(1) ?: "Nao encontrado"
            val preco = precoMatch?.groupValues?.get(1) ?: ""

            txtNome.text = nome
            txtPreco.text = if (preco.isNotEmpty()) "R$ " + preco else "R$ -"

            handler.postDelayed({
                val intent = Intent(this, ScannerActivity::class.java)
                startActivityForResult(intent, 100)
            }, 5000)

        } catch (e: Exception) {
            txtNome.text = "Erro ao processar"
            txtPreco.text = "R$ -"
        }
    }
}
