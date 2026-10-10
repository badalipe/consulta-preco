package com.quickprice.app

import android.os.Bundle
import android.widget.Button
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
            setPadding(40, 80, 40, 40)
        }

        val txtStatus = TextView(this).apply {
            text = "Toque no botão para varrer a rede"
            textSize = 20f
            setPadding(0, 0, 0, 40)
        }

        val txtResultado = TextView(this).apply {
            text = ""
            textSize = 24f
            setTextColor(0xFF00FF00.toInt())
            setPadding(0, 40, 0, 0)
        }

        val btnVarredura = Button(this).apply {
            text = "VARRER REDE E ENCONTRAR SERVIDOR"
            textSize = 18f
            setOnClickListener {
                txtStatus.text = "Varrendo..."
                txtResultado.text = ""
                varrerRede(txtStatus, txtResultado)
            }
        }

        layout.addView(txtStatus)
        layout.addView(btnVarredura)
        layout.addView(txtResultado)

        setContentView(layout)

        // Mostra IP atual
        Thread {
            val ip = getMeuIP()
            runOnUiThread {
                txtStatus.text = "Seu IP: " + ip + "

Toque para varrer a rede"
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
                        if (ip.indexOf(':') < 0 && !ip.startsWith("127")) {
                            return ip
                        }
                    }
                }
            }
            "Nao encontrado"
        } catch (e: Exception) {
            "Erro"
        }
    }

    private fun varrerRede(txtStatus: TextView, txtResultado: TextView) {
        val meuIP = getMeuIP()
        if (meuIP == "Nao encontrado" || meuIP == "Erro") {
            txtStatus.text = "Nao consegui descobrir seu IP"
            return
        }

        val partes = meuIP.split(".")
        if (partes.size != 4) {
            txtStatus.text = "IP invalido: " + meuIP
            return
        }

        val base = partes[0] + "." + partes[1] + "." + partes[2]

        Thread {
            var encontrados = 0
            for (i in 1..254) {
                val ip = base + "." + i
                try {
                    val url = URL("http://" + ip + "/unitario.php")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 500
                    conn.readTimeout = 500
                    val code = conn.responseCode

                    if (code == 200) {
                        encontrados++
                        runOnUiThread {
                            txtResultado.text = "SERVIDOR ENCONTRADO:\n\n" + ip + "\n\nUse este IP!"
                            txtStatus.text = "Encontrado!"
                        }
                        break
                    }
                } catch (e: Exception) {
                }

                if (i % 50 == 0) {
                    runOnUiThread {
                        txtStatus.text = "Varrendo " + i + "/254..."
                    }
                }
            }

            if (encontrados == 0) {
                runOnUiThread {
                    txtResultado.text = "Nenhum servidor encontrado"
                    txtStatus.text = "Tente na rede do trabalho"
                }
            }
        }.start()
    }
}
