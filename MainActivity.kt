package com.quickprice.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        val txtInfo = TextView(this).apply {
            text = "Descobrindo servidor na rede..."
            textSize = 18f
            setPadding(0, 0, 0, 30)
        }

        val txtMeuIP = TextView(this).apply {
            text = ""
            textSize = 16f
            setPadding(0, 0, 0, 20)
        }

        val txtStatus = TextView(this).apply {
            text = ""
            textSize = 14f
            setPadding(0, 0, 0, 30)
        }

        val edtIP = EditText(this).apply {
            hint = "Ou digite o IP manual (ex: 192.168.1.100)"
            setText("10.10.56.103")
        }

        val btnTestar = Button(this).apply {
            text = "TESTAR IP DIGITADO"
            setOnClickListener {
                val ip = edtIP.text.toString()
                txtStatus.text = "Testando $ip..."
                Thread {
                    testarServidor(ip, txtStatus)
                }.start()
            }
        }

        val btnVarredura = Button(this).apply {
            text = "VARRER REDE LOCAL"
            setOnClickListener {
                txtStatus.text = "Varrendo rede..."
                Thread {
                    varrerRede(txtStatus)
                }.start()
            }
        }

        val btnContinuar = Button(this).apply {
            text = "CONTINUAR PARA O APP"
            setOnClickListener {
                startActivity(android.content.Intent(this@MainActivity, ScannerActivity::class.java))
            }
        }

        layout.addView(txtInfo)
        layout.addView(txtMeuIP)
        layout.addView(txtStatus)
        layout.addView(edtIP)
        layout.addView(btnTestar)
        layout.addView(btnVarredura)
        layout.addView(btnContinuar)

        setContentView(layout)

        // Mostra IP do celular
        Thread {
            val meuIP = getMeuIP()
            runOnUiThread {
                txtMeuIP.text = "Meu IP: $meuIP"
            }
        }.start()
    }

    private fun getMeuIP(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val ip = addr.hostAddress
                        if (ip.indexOf(':') < 0) { // IPv4
                            return ip
                        }
                    }
                }
            }
            "Não encontrado"
        } catch (e: Exception) {
            "Erro: ${e.message}"
        }
    }

    private fun testarServidor(ip: String, txtStatus: TextView) {
        val caminhos = listOf("", "/unitario.php", "/index.php", "/")
        var encontrou = false

        for (caminho in caminhos) {
            try {
                val url = URL("http://$ip$caminho")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val code = conn.responseCode

                if (code == 200) {
                    runOnUiThread {
                        txtStatus.text = "✅ ENCONTRADO! http://$ip$caminho (HTTP $code)"
                    }
                    encontrou = true
                    break
                }
            } catch (e: Exception) {
                // Continua tentando
            }
        }

        if (!encontrou) {
            runOnUiThread {
                txtStatus.text = "❌ Não encontrou servidor em $ip"
            }
        }
    }

    private fun varrerRede(txtStatus: TextView) {
        val meuIP = getMeuIP()
        if (meuIP == "Não encontrado") {
            runOnUiThread {
                txtStatus.text = "❌ Não consegui descobrir seu IP"
            }
            return
        }

        // Extrai a base do IP (ex: 192.168.1)
        val partes = meuIP.split(".")
        if (partes.size != 4) {
            runOnUiThread {
                txtStatus.text = "❌ IP inválido: $meuIP"
            }
            return
        }

        val base = "${partes[0]}.${partes[1]}.${partes[2]}"

        runOnUiThread {
            txtStatus.text = "Varrendo $base.1 até $base.254..."
        }

        var encontrados = 0
        for (i in 1..254) {
            val ip = "$base.$i"
            try {
                val url = URL("http://$ip/unitario.php")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 500
                conn.readTimeout = 500
                val code = conn.responseCode

                if (code == 200) {
                    encontrados++
                    runOnUiThread {
                        txtStatus.text = "✅ ENCONTRADO: http://$ip/unitario.php"
                    }
                    break
                }
            } catch (e: Exception) {
                // Ignora hosts offline
            }

            // Atualiza progresso a cada 50 IPs
            if (i % 50 == 0) {
                runOnUiThread {
                    txtStatus.text = "Varrendo... $i/254 ($encontrados encontrados)"
                }
            }
        }

        if (encontrados == 0) {
            runOnUiThread {
                txtStatus.text = "❌ Nenhum servidor encontrado na rede"
            }
        }
    }
}
