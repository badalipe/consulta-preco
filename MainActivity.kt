package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.Socket

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val SERVER_IP = "10.10.254.212"
    private val SERVER_PORT = 63784

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnScan = findViewById<Button>(R.id.btnScan)
        btnScan.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 100)
        }

        // Testa conexão com servidor TOTVS
        testarConexao()
    }

    private fun testarConexao() {
        Thread {
            try {
                val socket = Socket(SERVER_IP, SERVER_PORT)
                val conectado = socket.isConnected
                socket.close()

                runOnUiThread {
                    val txtNome = findViewById<TextView>(R.id.txtNome)
                    if (conectado) {
                        txtNome.text = "Servidor TOTVS OK!"
                    } else {
                        txtNome.text = "Servidor nao responde"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val txtNome = findViewById<TextView>(R.id.txtNome)
                    txtNome.text = "Erro: " + e.message
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
                // Aqui vai a consulta no TOTVS
            }
        }
    }
}
