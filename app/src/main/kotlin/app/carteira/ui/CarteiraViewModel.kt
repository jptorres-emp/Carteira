package app.carteira.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.carteira.alertas.Alerta
import app.carteira.alertas.Notificacoes
import app.carteira.alertas.ServicoAlertas
import app.carteira.alertas.VerificadorAlertas
import app.carteira.dados.Dados
import app.carteira.dados.FonteDados
import app.carteira.dados.Leitor
import app.carteira.dados.Mercado
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
    data object Evolucao : Ecra
    data object Posicoes : Ecra
    data object Dividendos : Ecra
    data object Watchlist : Ecra
    data class Posicao(val ticker: String) : Ecra
    data class Etf(val ticker: String) : Ecra
    data class Registar(val modo: ModoRegisto, val ticker: String? = null, val tipo: String? = null) : Ecra

    /** Gravar o património de um mês na folha (mes: "aaaa-mm"; null = o que a app sugere). */
    data class AtualizarPatrimonio(val mes: String? = null) : Ecra
    data object Definicoes : Ecra
    data object Alertas : Ecra
}

enum class ModoRegisto { TRANSACAO, DIVIDENDO }

/** As duas vistas do separador Evolução. */
enum class VistaEvolucao(val rotulo: String) { PATRIMONIO("Património"), CARTEIRA("Carteira") }

/** O que o utilizador escolheu para os alertas (guardado no telemóvel). */
data class DefinicoesAlertas(
    val ativo: Boolean = false,
    val compras: Boolean = true,
    val watchlist: Boolean = true,
    val vendas: Boolean = false,
    val variacao: Boolean = true,
    val limiar: Double = 0.05,
    val intervaloHoras: Int = 2,
    val soMercadoAberto: Boolean = true,
)

data class Estado(
    val dados: Dados? = null,
    val aCarregar: Boolean = false,
    val erro: String? = null,
    val configurado: Boolean = false,
    /** Mensagem curta para a barra de avisos (consumida pelo ecrã). */
    val mensagem: String? = null,
    val aEnviar: Boolean = false,
    val pilha: List<Ecra> = listOf(Ecra.Resumo),
    /** Histórico de alertas, do mais recente para o mais antigo. */
    val alertas: List<Alerta> = emptyList(),
    val alertasNaoVistos: Int = 0,
    val defAlertas: DefinicoesAlertas = DefinicoesAlertas(),
    val aVerificarAlertas: Boolean = false,
    /** Mostrar no ecrã inicial o convite para ativar os alertas. */
    val conviteAlertas: Boolean = false,
    /** Empresa da watchlist a abrir e mostrar (vinda de um alerta). */
    val destaqueWatchlist: String? = null,
    /** Modo privado: os valores em dinheiro aparecem escondidos. */
    val privado: Boolean = false,
    val vistaEvolucao: VistaEvolucao = VistaEvolucao.PATRIMONIO,
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
    private val servico = ServicoAlertas(app)
    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    val url: String get() = repositorio.url
    val chave: String get() = repositorio.chave

    init {
        val cache = repositorio.lerCache()
        val configurado = repositorio.configurado
        Formatos.privado = repositorio.privado
        _estado.update {
            it.copy(
                dados = cache,
                configurado = configurado,
                privado = repositorio.privado,
                pilha = if (configurado) listOf(Ecra.Resumo) else listOf(Ecra.Resumo, Ecra.Definicoes),
            )
        }
        carregarAlertas()
        if (configurado) atualizar()
    }

    // ── Navegação ──

    fun irPara(ecra: Ecra) = _estado.update { e ->
        when (ecra) {
            Ecra.Resumo, Ecra.Evolucao, Ecra.Posicoes, Ecra.Dividendos, Ecra.Watchlist -> e.copy(pilha = listOf(ecra))
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

    /** Abre o que um alerta (ou uma notificação) aponta: "P:ALFA", "E:MUND", "W:KAPA" ou a lista de alertas. */
    fun abrir(chave: String) {
        // A verificação em segundo plano pode ter juntado alertas entretanto.
        carregarAlertas()
        val d = _estado.value.dados
        val ticker = chave.substringAfter(':', "")
        when {
            chave.startsWith("P:") && d?.posicoes?.any { it.ticker == ticker } == true -> irPara(Ecra.Posicao(ticker))
            chave.startsWith("E:") && d?.etfs?.any { it.ticker == ticker } == true -> irPara(Ecra.Etf(ticker))
            chave.startsWith("W:") -> {
                irPara(Ecra.Watchlist)
                _estado.update { it.copy(destaqueWatchlist = ticker) }
            }
            else -> irPara(Ecra.Alertas)
        }
    }

    fun destaqueMostrado() = _estado.update { it.copy(destaqueWatchlist = null) }

    fun escolherVistaEvolucao(vista: VistaEvolucao) = _estado.update { it.copy(vistaEvolucao = vista) }

    /** Liga ou desliga o modo privado (fica guardado no telemóvel). */
    fun alternarPrivacidade() {
        val novo = !_estado.value.privado
        repositorio.privado = novo
        Formatos.privado = novo
        _estado.update { it.copy(privado = novo) }
    }

    /** Só apaga se ainda for a mesma mensagem (entretanto pode ter chegado outra). */
    fun mensagemMostrada(mensagem: String) =
        _estado.update { if (it.mensagem == mensagem) it.copy(mensagem = null) else it }

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
                carregarAlertas()
                if (repositorio.alertasAtivos) procurarAlertas(dados)
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
                if (mudou) {
                    repositorio.apagarCache()
                    repositorio.apagarAlertas()
                }
                _estado.update { it.copy(configurado = true, dados = if (mudou) null else it.dados) }
                if (mudou) carregarAlertas()
                VerificadorAlertas.agendar(getApplication())
                atualizar()
                aoTerminar(true, "Ligada à folha \"$folha\".")
            } catch (e: Exception) {
                aoTerminar(false, mensagemDe(e))
            }
        }
    }

    // ── Alertas ──

    private fun carregarAlertas() {
        val historico = servico.historico()
        val vistos = repositorio.alertasVistosAte
        _estado.update {
            it.copy(
                alertas = historico,
                alertasNaoVistos = historico.count { a -> a.quando > vistos },
                defAlertas = DefinicoesAlertas(
                    ativo = repositorio.alertasAtivos,
                    compras = repositorio.alertaCompras,
                    watchlist = repositorio.alertaWatchlist,
                    vendas = repositorio.alertaVendas,
                    variacao = repositorio.alertaVariacao,
                    limiar = repositorio.limiarVariacao,
                    intervaloHoras = repositorio.intervaloHoras,
                    soMercadoAberto = repositorio.soMercadoAberto,
                ),
                conviteAlertas = !repositorio.alertasAtivos && !repositorio.conviteAlertasDispensado,
            )
        }
    }

    /** Com a app aberta: compara os dados acabados de ler com a última verificação. */
    private fun procurarAlertas(dados: Dados) {
        viewModelScope.launch {
            val novos = runCatching { servico.processar(Mercado.de(dados)) }.getOrDefault(emptyList())
            if (novos.isNotEmpty()) {
                carregarAlertas()
                mostrar(if (novos.size == 1) "Alerta: ${novos[0].titulo}" else "${novos.size} alertas novos — toca no sino para os ver")
            }
        }
    }

    fun definirAlertas(nova: DefinicoesAlertas) {
        val antes = _estado.value.defAlertas
        repositorio.alertasAtivos = nova.ativo
        repositorio.alertaCompras = nova.compras
        repositorio.alertaWatchlist = nova.watchlist
        repositorio.alertaVendas = nova.vendas
        repositorio.alertaVariacao = nova.variacao
        repositorio.limiarVariacao = nova.limiar
        repositorio.intervaloHoras = nova.intervaloHoras
        repositorio.soMercadoAberto = nova.soMercadoAberto
        _estado.update { it.copy(defAlertas = nova, conviteAlertas = it.conviteAlertas && !nova.ativo) }
        VerificadorAlertas.agendar(getApplication())
        // Ao ligar, regista já o ponto de partida: a próxima verificação compara com ele.
        val d = _estado.value.dados
        if (nova.ativo && !antes.ativo && d != null) {
            viewModelScope.launch { runCatching { servico.processar(Mercado.de(d)) } }
        }
    }

    fun verificarAlertasAgora() {
        if (_estado.value.aVerificarAlertas) return
        if (!repositorio.configurado) {
            mostrar("Liga primeiro a app à folha.")
            return
        }
        _estado.update { it.copy(aVerificarAlertas = true) }
        viewModelScope.launch {
            try {
                val novos = servico.verificar(criarFonte(repositorio.url, repositorio.chave))
                Notificacoes.mostrar(getApplication(), novos)
                carregarAlertas()
                mostrar(
                    when (novos.size) {
                        0 -> "Sem alertas novos. A partir de agora aviso-te das mudanças."
                        1 -> "1 alerta novo: ${novos[0].titulo}"
                        else -> "${novos.size} alertas novos"
                    },
                )
            } catch (e: Exception) {
                mostrar(mensagemDe(e))
            } finally {
                _estado.update { it.copy(aVerificarAlertas = false) }
            }
        }
    }

    fun notificacaoDeTeste() {
        if (!Notificacoes.mostrarTeste(getApplication())) {
            mostrar("O Android não deixa a app mostrar notificações. Autoriza-as nas definições do telemóvel.")
        }
    }

    fun marcarAlertasVistos() {
        val ultimo = _estado.value.alertas.firstOrNull()?.quando ?: return
        repositorio.alertasVistosAte = ultimo
        _estado.update { it.copy(alertasNaoVistos = 0) }
    }

    fun dispensarConviteAlertas() {
        repositorio.conviteAlertasDispensado = true
        _estado.update { it.copy(conviteAlertas = false) }
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

    /** Grava o património (net worth) de um mês na tab Evolução Patrimonial. */
    fun registarPatrimonio(mes: String, valor: Double, aoTerminar: (Boolean) -> Unit) {
        val pedido = JSONObject()
            .put("acao", "patrimonio")
            .put("mes", mes)
            .put("valor", valor)
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
