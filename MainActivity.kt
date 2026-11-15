package com.quickprice.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val SERVER_IP = "10.10.254.73"

    private val reconhecimentoVoz = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode == RESULT_OK) {
            val textoReconhecido = resultado.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()

            if (!textoReconhecido.isNullOrBlank()) {
                preencherBusca(textoReconhecido)
            } else {
                Toast.makeText(this, "Não foi possível reconhecer a fala. Tente novamente.", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

        findViewById<Button>(R.id.btnBuscaVoz).setOnClickListener {
            iniciarBuscaPorVoz()
        }

        findViewById<EditText>(R.id.edtCodigo).setOnEditorActionListener { _, _, _ ->
            val consulta = findViewById<EditText>(R.id.edtCodigo).text.toString().trim()
            if (consulta.isNotEmpty()) {
                preencherBusca(consulta)
                true
            } else {
                false
            }
        }

        // Testa todas as portas
        testarPortas()
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
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Não há um serviço de reconhecimento de voz disponível neste dispositivo.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun preencherBusca(consulta: String) {
        findViewById<EditText>(R.id.edtCodigo).setText(consulta)
        findViewById<EditText>(R.id.edtCodigo).setSelection(consulta.length)
        findViewById<TextView>(R.id.txtCodigo).text = consulta
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
                preencherBusca(it)
            }
        }
    }
}
