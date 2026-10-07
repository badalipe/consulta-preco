package com.quickprice.app

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Configuração de sessão: credenciais vêm pelo deep link e ficam salvas
 * para reconexão automática sempre que a sessão cair ou houver duplo login.
 *
 * Formatos de link aceitos:
 *   consultapreco://abrir?url=https://rmarket.cartazfacil.pro/unitario.php&usuario=LOJA01&senha=1234&forcar=true
 *   https://rmarket.cartazfacil.pro/app?usuario=LOJA01&senha=1234
 */
data class SessionConfig(
    val url: String,
    val usuario: String,
    val senha: String,
    val forcarReconexao: Boolean = true   // preferência: força reconexão derrubando sessão dupla
) {
    companion object {
        private const val PREFS = "sessao"
        private const val K_URL = "url"
        private const val K_USER = "usuario"
        private const val K_PASS = "senha"
        private const val K_FORCE = "forcar"

        fun fromIntent(intent: Intent?): SessionConfig? {
            val uri: Uri = intent?.data ?: return null
            val usuario = uri.getQueryParameter("usuario") ?: return null
            val senha = uri.getQueryParameter("senha") ?: return null
            val url = uri.getQueryParameter("url")
                ?: "https://rmarket.cartazfacil.pro/unitario.php"
            val forcar = uri.getQueryParameter("forcar")?.toBooleanStrictOrNull() ?: true
            return SessionConfig(url, usuario, senha, forcar)
        }

        fun save(ctx: Context, cfg: SessionConfig) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(K_URL, cfg.url)
                .putString(K_USER, cfg.usuario)
                .putString(K_PASS, cfg.senha)
                .putBoolean(K_FORCE, cfg.forcarReconexao)
                .apply()
        }

        fun load(ctx: Context): SessionConfig? {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val u = p.getString(K_USER, null) ?: return null
            val s = p.getString(K_PASS, null) ?: return null
            return SessionConfig(
                url = p.getString(K_URL, "https://rmarket.cartazfacil.pro/unitario.php")!!,
                usuario = u, senha = s,
                forcarReconexao = p.getBoolean(K_FORCE, true)
            )
        }
    }
}
