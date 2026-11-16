package app.carteira.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.carteira.dados.Dados
import app.carteira.dados.FonteDados
import app.carteira.dados.Leitor
import app.carteira.dados.Ponte
import app.carteira.dados.Repositorio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Ecrãs da app. Os de detalhe e de registo empilham-se por cima dos separadores. */
sealed interface Ecra {
    data object Resumo : Ecra
    data object Posicoes : Ecra
    data object Dividendos : Ecra
    data object Watchlist : Ecra
    data class Posicao(val ticker: String) : Ecra
    data class Etf(val ticker: String) : Ecra
    data class Registar(val modo: ModoRegisto, val ticker: String? = null, val tipo: String? = null) : Ecra
    data object Definicoes : Ecra
}

enum class ModoRegisto { TRANSACAO, DIVIDENDO }

data class Estado(
    val dados: Dados? = null,
    val aCarregar: Boolean = false,
    val erro: String? = null,
    val configurado: Boolean = false,
    /** Mensagem curta para a barra de avisos (consumida pelo ecrã). */
    val mensagem: String? = null,
    val aEnviar: Boolean = false,
    val pilha: List<Ecra> = listOf(Ecra.Resumo),
) {
    val ecra: Ecra get() = pilha.last()
}

class CarteiraViewModel(
    app: Application,
    private val criarFonte: (url: String, chave: String) -> FonteDados,
) : AndroidViewModel(app) {
    /** O que o Android usa: a ponte a sério. Os testes passam uma fonte falsa. */
    constructor(app: Application) : this(app, { url, chave -> Ponte(url, chave) })

    private val repositorio = Repositorio(app)
    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    val url: String get() = repositorio.url
    val chave: String get() = repositorio.chave

    init {
        val cache = repositorio.lerCache()
        val configurado = repositorio.configurado
        _estado.update {
            it.copy(
                dados = cache,
                configurado = configurado,
                pilha = if (configurado) listOf(Ecra.Resumo) else listOf(Ecra.Resumo, Ecra.Definicoes),
            )
        }
        if (configurado) atualizar()
    }

    // ── Navegação ──

    fun irPara(ecra: Ecra) = _estado.update { e ->
        when (ecra) {
            Ecra.Resumo, Ecra.Posicoes, Ecra.Dividendos, Ecra.Watchlist -> e.copy(pilha = listOf(ecra))
            else -> e.copy(pilha = e.pilha + ecra)
        }
    }

    /** Devolve false se já está no ecrã de base (aí o botão Voltar sai da app). */
    fun voltar(): Boolean {
        val pilha = _estado.value.pilha
        if (pilha.size <= 1) return false
        _estado.update { it.copy(pilha = pilha.dropLast(1)) }
        return true
    }

    fun mensagemMostrada() = _estado.update { it.copy(mensagem = null) }

    fun mostrar(mensagem: String) = _estado.update { it.copy(mensagem = mensagem) }

    // ── Dados ──

    fun atualizar() {
        if (!repositorio.configurado) {
            _estado.update { it.copy(erro = "Liga a app à folha nas Definições.") }
            return
        }
        if (_estado.value.aCarregar) return
        _estado.update { it.copy(aCarregar = true, erro = null) }
        viewModelScope.launch {
            try {
                val texto = criarFonte(repositorio.url, repositorio.chave).dados()
                val dados = Leitor.lerResposta(texto)
                repositorio.guardarCache(texto)
                _estado.update { it.copy(dados = dados, aCarregar = false, erro = null) }
            } catch (e: Exception) {
                _estado.update { it.copy(aCarregar = false, erro = mensagemDe(e)) }
            }
        }
    }

    /** Testa o link e a chave; se responderem, guarda-os e carrega os dados. */
    fun testarEGuardar(url: String, chave: String, aoTerminar: (Boolean, String) -> Unit) {
        val u = url.trim()
        val c = chave.trim()
        if (!u.startsWith("https://")) {
            aoTerminar(false, "O link tem de começar por https:// (é o URL da aplicação Web).")
            return
        }
        if (c.isEmpty()) {
            aoTerminar(false, "Falta a chave.")
            return
        }
        viewModelScope.launch {
            try {
                val folha = Leitor.lerPing(criarFonte(u, c).ping())
                val mudou = u != repositorio.url || c != repositorio.chave
                repositorio.url = u
                repositorio.chave = c
                if (mudou) repositorio.apagarCache()
                _estado.update { it.copy(configurado = true, dados = if (mudou) null else it.dados) }
                atualizar()
                aoTerminar(true, "Ligada à folha \"$folha\".")
            } catch (e: Exception) {
                aoTerminar(false, mensagemDe(e))
            }
        }
    }

    // ── Registos ──

    fun registarTransacao(
        data: String,
        tipo: String,
        classe: String,
        ticker: String,
        unidades: Double,
        preco: Double,
        comissao: Double,
        cambio: Double?,
        aoTerminar: (Boolean) -> Unit,
    ) {
        val pedido = JSONObject()
            .put("acao", "transacao")
            .put("data", data)
            .put("tipo", tipo)
            .put("classe", classe)
            .put("ticker", ticker)
            .put("unidades", unidades)
            .put("preco", preco)
            .put("comissao", comissao)
        if (cambio != null) pedido.put("cambio", cambio)
        enviar(pedido, aoTerminar)
    }

    fun registarDividendo(data: String, ticker: String, valor: Double, moeda: String, aoTerminar: (Boolean) -> Unit) {
        val pedido = JSONObject()
            .put("acao", "dividendo")
            .put("data", data)
            .put("ticker", ticker)
            .put("valor", valor)
            .put("moeda", moeda)
        enviar(pedido, aoTerminar)
    }

    private fun enviar(pedido: JSONObject, aoTerminar: (Boolean) -> Unit) {
        if (_estado.value.aEnviar) return
        _estado.update { it.copy(aEnviar = true) }
        viewModelScope.launch {
            try {
                val resposta = Leitor.lerRegisto(criarFonte(repositorio.url, repositorio.chave).enviar(pedido))
                val texto = listOfNotNull(resposta.mensagem, resposta.aviso).joinToString(" ")
                _estado.update { it.copy(aEnviar = false, mensagem = texto) }
                aoTerminar(resposta.ok)
                if (resposta.ok) atualizar()
            } catch (e: Exception) {
                _estado.update { it.copy(aEnviar = false, mensagem = mensagemDe(e)) }
                aoTerminar(false)
            }
        }
    }

    private fun mensagemDe(e: Exception): String = when (e) {
        is app.carteira.dados.ErroPonte -> e.message ?: "Erro na ponte"
        is java.net.UnknownHostException -> "Sem ligação à internet."
        is java.net.SocketTimeoutException -> "A folha demorou demasiado a responder. Tenta outra vez."
        else -> "Não consegui falar com a folha: ${e.message ?: e.javaClass.simpleName}"
    }
}
