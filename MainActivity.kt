package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Ponto de entrada do app.
 * 1) Abriu por link com credenciais -> salva e vai ao scanner
 * 2) Ja tem sessao salva -> vai direto ao scanner
 * 3) Nada disso -> usa o login padrao da loja (embutido no app)
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) Veio por link com credenciais
        SessionConfig.fromIntent(intent)?.let { cfg ->
            SessionConfig.save(this, cfg)
            abrirScanner()
            return
        }

        // 2) Ja tem login salvo
        if (SessionConfig.load(this) != null) {
            abrirScanner()
            return
        }

        // 3) Login padrao da loja embutido
        SessionConfig.save(
            this,
            SessionConfig(
                url = "https://rmarket.cartazfacil.pro/unitario.php",
                usuario = "rmarket07",
                senha = "sup12345",
                forcarReconexao = true
            )
        )
        abrirScanner()
    }

    override fun onNewIntent(intent: Intent) {
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
