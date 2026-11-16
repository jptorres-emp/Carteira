package app.carteira.dados

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** De onde vêm os dados: a ponte a sério ou, nos testes, uma resposta fixa. */
interface FonteDados {
    suspend fun dados(): String
    suspend fun ping(): String
    suspend fun enviar(pedido: JSONObject): String
}

/**
 * Fala com a ponte da folha (aplicação Web do Google Apps Script).
 *
 * O Google responde sempre com um redirecionamento (302) para o conteúdo final; num POST, o
 * pedido seguinte tem de ser um GET. Por isso os redirecionamentos são seguidos à mão.
 */
class Ponte(private val url: String, private val chave: String) : FonteDados {

    override suspend fun dados(): String = obter(mapOf("acao" to "dados", "chave" to chave))

    override suspend fun ping(): String = obter(mapOf("acao" to "ping", "chave" to chave))

    override suspend fun enviar(pedido: JSONObject): String {
        pedido.put("chave", chave)
        return withContext(Dispatchers.IO) { pedir("POST", url, pedido.toString()) }
    }

    private suspend fun obter(parametros: Map<String, String>): String {
        val separador = if (url.contains('?')) '&' else '?'
        val consulta = parametros.entries.joinToString("&") { (k, v) ->
            URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
        return withContext(Dispatchers.IO) { pedir("GET", "$url$separador$consulta", null) }
    }

    private fun pedir(metodo: String, endereco: String, corpo: String?): String {
        var atual = endereco
        var metodoAtual = metodo
        var corpoAtual = corpo
        repeat(6) {
            val ligacao = URL(atual).openConnection() as HttpURLConnection
            try {
                ligacao.instanceFollowRedirects = false
                ligacao.connectTimeout = 20_000
                ligacao.readTimeout = 90_000
                ligacao.requestMethod = metodoAtual
                ligacao.setRequestProperty("Accept", "application/json")
                if (corpoAtual != null) {
                    ligacao.doOutput = true
                    ligacao.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    ligacao.outputStream.use { it.write(corpoAtual!!.toByteArray(Charsets.UTF_8)) }
                }
                val codigo = ligacao.responseCode
                if (codigo in 300..399) {
                    val destino = ligacao.getHeaderField("Location")
                        ?: throw IOException("Redirecionamento sem destino ($codigo)")
                    atual = URL(URL(atual), destino).toString()
                    // Depois de um POST, o Google entrega a resposta num GET ao novo endereço.
                    if (codigo != 307 && codigo != 308) {
                        metodoAtual = "GET"
                        corpoAtual = null
                    }
                    return@repeat
                }
                val fluxo = if (codigo in 200..299) ligacao.inputStream else ligacao.errorStream
                val texto = fluxo?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (codigo !in 200..299) {
                    throw ErroPonte(
                        when (codigo) {
                            401, 403 -> "A ponte recusou o acesso ($codigo). Na implementação, escolhe " +
                                "\"Quem tem acesso: Qualquer pessoa\"."
                            404 -> "Não encontrei a ponte (404). Confirma o URL nas Definições."
                            else -> "A ponte respondeu com o erro $codigo."
                        }
                    )
                }
                return texto
            } finally {
                ligacao.disconnect()
            }
        }
        throw IOException("Demasiados redirecionamentos")
    }
}
