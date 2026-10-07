package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Ponto de entrada. Se o app abriu via link, salva as credenciais e já
 * cai direto no scanner. Se abriu normal, usa a sessão salva.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) Link com credenciais → salva e segue
        SessionConfig.fromIntent(intent)?.let { cfg ->
            SessionConfig.save(this, cfg)
            abrirScanner()
            return
        }
        // 2) Sem link: se já tem sessão salva, vai direto pro scanner
        if (SessionConfig.load(this) != null) {
            abrirScanner()
        }
        // 3) Sem nada: fica na tela inicial aguardando o link
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        SessionConfig.fromIntent(intent)?.let { cfg ->
            SessionConfig.save(this, cfg)
            abrirScanner()
        }
    }

    private fun abrirScanner() {
        startActivity(Intent(this, ScannerActivity::class.java))
        finish()
    }
}
