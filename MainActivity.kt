package com.quickprice.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Abre direto na tela de resultado (tela principal)
        startActivity(android.content.Intent(this, ResultadoActivity::class.java))
        finish()
    }
}
