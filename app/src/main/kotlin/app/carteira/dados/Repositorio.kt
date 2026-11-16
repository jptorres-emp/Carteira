package app.carteira.dados

import android.content.Context
import java.io.File

/** Definições (link e chave da ponte) e a última cópia dos dados, para abrir sem rede. */
class Repositorio(contexto: Context) {
    private val preferencias = contexto.getSharedPreferences("carteira", Context.MODE_PRIVATE)
    private val ficheiroCache = File(contexto.filesDir, "dados.json")

    var url: String
        get() = preferencias.getString("url", "") ?: ""
        set(valor) = preferencias.edit().putString("url", valor.trim()).apply()

    var chave: String
        get() = preferencias.getString("chave", "") ?: ""
        set(valor) = preferencias.edit().putString("chave", valor.trim()).apply()

    val configurado: Boolean get() = url.isNotBlank() && chave.isNotBlank()

    fun guardarCache(texto: String) {
        runCatching { ficheiroCache.writeText(texto) }
    }

    fun lerCache(): Dados? = runCatching {
        if (ficheiroCache.exists()) Leitor.lerResposta(ficheiroCache.readText()) else null
    }.getOrNull()

    fun apagarCache() {
        runCatching { ficheiroCache.delete() }
    }
}
