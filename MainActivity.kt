package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    // CONFIGURACAO PROXY
    private val PROXY_IP = "1.1.1.1"
    private val PROXY_PORTA = 3720
    private val PROXY_USER = "enilso.barbosa"
    private val PROXY_PASS = "terra,2026"

    // SERVIDOR TOTVS
    private val SERVER = "http://10.10.254.73:8343"
    private val API = "/SMProdutosAPI/api/v4/produtos/precos-produtos"
    private val EMPRESA = "4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configura autenticacao do proxy automaticamente
        configurarProxy()

        val txtStatus = findViewById<TextView>(R.id.txtNome)
        txtStatus.text = "Proxy configurado! Testando..."

        val btnScan = findViewById<Button>(R.id.btnScan)
        btnScan.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 100)
        }

        // Testa conexao
        testarConexao()
    }

    private fun configurarProxy() {
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(PROXY_USER, PROXY_PASS.toCharArray())
            }
        })
    }

    private fun testarConexao() {
        Thread {
            try {
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(PROXY_IP, PROXY_PORTA))
                val url = URL(SERVER + API + "?nroEmpresa=" + EMPRESA)
                val conn = url.openConnection(proxy) as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.setRequestProperty("Accept", "application/json")

                val code = conn.responseCode

                runOnUiThread {
                    val txtStatus = findViewById<TextView>(R.id.txtNome)
                    if (code == 200) {
                        txtStatus.text = "CONECTADO! Pronto para escanear."
                        Toast.makeText(this, "Conectado via proxy!", Toast.LENGTH_LONG).show()
                    } else {
                        txtStatus.text = "Servidor respondeu: " + code
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val txtStatus = findViewById<TextView>(R.id.txtNome)
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
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(PROXY_IP, PROXY_PORTA))
                val url = URL(SERVER + API + "?codigoAcesso=" + codigoBarras + "&nroEmpresa=" + EMPRESA)
                val conn = url.openConnection(proxy) as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.setRequestProperty("Accept", "application/json")

                val response = conn.inputStream.bufferedReader().readText()

                runOnUiThread {
                    processarJSON(response)
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

            // Volta para scanner
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
