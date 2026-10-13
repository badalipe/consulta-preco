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
    private val SERVER_IP = "10.10.254.73"

    // Portas para testar
    private val portas = listOf(80, 443, 8080, 8343, 3000, 5000, 7000, 9000)
    private val caminhos = listOf(
        "/SMProdutosAPI/api/v4/produtos/precos-produtos",
        "/api/produtos",
        "/api/preco",
        "/produtos",
        "/preco",
        "/webservice/produtos",
        "/rest/produtos"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnScan = findViewById<Button>(R.id.btnScan)
        btnScan.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 100)
        }

        // Testa todas as portas
        testarPortas()
    }

    private fun testarPortas() {
        val txtStatus = findViewById<TextView>(R.id.txtNome)
        txtStatus.text = "Testando portas..."

        Thread {
            var encontrou = false

            for (porta in portas) {
                for (caminho in caminhos) {
                    val urlStr = "http://" + SERVER_IP + ":" + porta + caminho

                    runOnUiThread {
                        txtStatus.text = "Testando " + porta + "..."
                    }

                    try {
                        val url = URL(urlStr)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.connectTimeout = 3000
                        conn.instanceFollowRedirects = false

                        val code = conn.responseCode

                        if (code != 404 && code != 000) {
                            runOnUiThread {
                                txtStatus.text = "ENCONTRADO! " + porta + " -> HTTP " + code
                            }
                            encontrou = true
                            break
                        }
                    } catch (e: Exception) {
                        // Continua testando
                    }
                }
                if (encontrou) break
            }

            if (!encontrou) {
                runOnUiThread {
                    txtStatus.text = "Nenhuma porta aberta encontrada"
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
                // Aqui vai a busca quando encontrar a porta
            }
        }
    }
}
