package app.carteira.dados

/*
 * O que a ponte da folha devolve. Os valores em dinheiro vêm como estão na folha: ações em dólares,
 * ETFs e totais em euros. Campos que a folha mostra como "—" chegam a null.
 */

data class Dados(
    val meta: Meta,
    val cambio: Cambio,
    val totaisAcoes: TotaisAcoes?,
    val totaisEtfs: TotaisEtfs?,
    val camadas: List<Camada>,
    val indicadores: List<Indicador>,
    val regras: List<Regra>,
    val posicoes: List<Posicao>,
    val etfs: List<Etf>,
    val tickers: List<TickerInfo>,
    val dividendos: Dividendos,
    val watchlist: List<ItemWatchlist>,
    val transacoes: List<Transacao>,
    /** Versão da ponte que respondeu (a 2 traz a evolução, os objetivos, o património e os setores). */
    val versaoPonte: Int = 1,
    val evolucao: List<PontoEvolucao> = emptyList(),
    val objetivos: List<Objetivo> = emptyList(),
    val patrimonio: Patrimonio? = null,
    val comparacoes: List<Comparacao> = emptyList(),
    val setores: List<Setor> = emptyList(),
) {
    /** Indicador do Dashboard pelo rótulo exato da folha. */
    fun indicador(rotulo: String): Indicador? = indicadores.firstOrNull { it.rotulo == rotulo }

    /** A comparação que a folha marca como "MEDIDA PRINCIPAL" (carteira toda vs VUAA, com dividendos). */
    val comparacaoPrincipal: Comparacao? get() = comparacoes.firstOrNull { it.principal } ?: comparacoes.lastOrNull()
}

/** avisos: secções que a ponte não conseguiu ler (por ex. se uma tab mudou de forma). */
data class Meta(val folha: String, val atualizadoEm: String, val hoje: String, val avisos: List<String> = emptyList())

/** Um mês da tab Evolução Patrimonial (carteira, investido e S&P 500 em dólares; património em euros). */
data class PontoEvolucao(
    val mes: String,
    val carteiraUsd: Double,
    val investidoUsd: Double?,
    val sp500Usd: Double?,
    val netWorthEur: Double?,
    val reforcoEur: Double?,
    val metaBaseEur: Double?,
)

/** Uma linha de "Estou no caminho?" (Evolução Patrimonial). */
data class Objetivo(
    val objetivo: String,
    val meta: Double?,
    val real: Double,
    val metaTexto: String?,
    val realTexto: String?,
    val diferencaTexto: String?,
    val estado: String,
)

data class Patrimonio(val totalEur: Double?, val classes: List<ClassePatrimonio>)

data class ClassePatrimonio(
    val classe: String,
    val investidoEur: Double?,
    val valorEur: Double,
    val valorizacao: Double?,
    val rendimentoAnualEur: Double?,
)

/** A carteira contra o mesmo dinheiro no índice (tab Validação S&P 500). */
data class Comparacao(
    val comparacao: String,
    val carteiraEur: Double?,
    val indiceEur: Double?,
    val diferencaEur: Double,
    val leitura: String?,
    val principal: Boolean,
) {
    /** Diferença em percentagem do valor do espelho no índice. */
    val diferencaPct: Double? get() = indiceEur?.takeIf { it > 0 }?.let { diferencaEur / it }
}

/** Peso de um setor nas ações e o limite da tab Distribuição. */
data class Setor(
    val setor: String,
    val valorEur: Double,
    val pesoAcoes: Double?,
    val numero: Double?,
    val limite: Double?,
    val estado: String?,
) {
    val acima: Boolean
        get() = estado?.trimStart()?.startsWith("🔴") == true ||
            (limite != null && pesoAcoes != null && limite > 0 && pesoAcoes > limite)
}

/** usdEur: euros por dólar (Carteira!B3). gbpUsd: dólares por libra. */
data class Cambio(val usdEur: Double?, val gbpUsd: Double?)

data class TotaisAcoes(
    val custoUsd: Double?,
    val valorUsd: Double?,
    val ganhoUsd: Double?,
    val ganhoPct: Double?,
    val dividendosAnoUsd: Double?,
    val custoEur: Double?,
    val valorEur: Double?,
    val ajusteSugeridoEur: Double?,
)

data class TotaisEtfs(val custoEur: Double?, val valorEur: Double?, val ganhoEur: Double?, val ganhoPct: Double?)

data class Camada(
    val camada: String,
    val numero: Double?,
    val numeroIdeal: Double?,
    val pesoHoje: Double?,
    val pesoIdeal: Double?,
    val valorEur: Double?,
    val diferencaEur: Double?,
)

/** Quadro do Dashboard ou da tab Dividendos: rótulo, número e o texto tal como a folha o mostra. */
data class Indicador(val rotulo: String, val valor: Double, val texto: String)

data class Regra(val regra: String, val hoje: String, val limite: String, val estado: String, val nota: String?) {
    val alerta: Boolean get() = estado.startsWith("⚠")
    val ok: Boolean get() = estado.startsWith("✓")
}

data class Posicao(
    val ticker: String,
    val empresa: String?,
    val setor: String?,
    val camada: String?,
    val quantidade: Double,
    val precoCompraUsd: Double?,
    val precoUsd: Double?,
    val varDiaPct: Double?,
    val pe: Double?,
    val custoUsd: Double?,
    val valorUsd: Double?,
    val ganhoUsd: Double?,
    val ganhoPct: Double?,
    val pesoAcoes: Double?,
    val divAnualPorAcaoUsd: Double?,
    val yieldPct: Double?,
    val yieldCustoPct: Double?,
    val frequencia: String?,
    val risco: Double?,
    val rating: String?,
    val custoEur: Double?,
    val valorEur: Double?,
    val ganhoEur: Double?,
    val pesoTotal: Double?,
    val pesoAlvo: Double?,
    val pesoMaximo: Double?,
    val zona: String?,
    val sinal: String?,
    val ajusteSugeridoEur: Double?,
    val avaliacao: Avaliacao?,
) {
    val nome: String get() = empresa ?: ticker

    /** Ganho em euros sobre o custo em euros (o "Ganho %" da folha é em dólares e inclui o efeito do câmbio). */
    val ganhoPctEur: Double?
        get() = if (ganhoEur != null && custoEur != null && custoEur > 0) ganhoEur / custoEur else null
}

data class Avaliacao(
    val moeda: String?,
    val precoLocal: Double?,
    val rendImplicita: Double?,
    val hurdle: Double?,
    val valorIntrinseco: Double?,
    val potencial: Double?,
    val reforcarForteAbaixo: Double?,
    val reforcarAbaixo: Double?,
    val comprarAbaixo: Double?,
    val reduzirAcima: Double?,
    val venderAcima: Double?,
    val pesoVsMaximo: String?,
    val temaIa: Boolean,
    val tese: String?,
    val alarme: String?,
    val fiscalidade: String?,
    val fonte: String?,
)

data class Etf(
    val ticker: String,
    val nome: String?,
    val quantidade: Double,
    val precoCompraEur: Double?,
    val precoEur: Double?,
    val varDiaPct: Double?,
    val custoEur: Double?,
    val valorEur: Double?,
    val ganhoEur: Double?,
    val ganhoPct: Double?,
    val pesoEtfs: Double?,
)

data class TickerInfo(val ticker: String, val nome: String?, val classe: String, val quantidade: Double)

data class Dividendos(
    val indicadores: List<Indicador>,
    val previsaoAno: Int?,
    val calendario: List<MesCalendario>,
    val anos: List<AnoDividendos>,
    val esteAno: EsteAno?,
) {
    fun indicador(rotulo: String): Indicador? = indicadores.firstOrNull { it.rotulo == rotulo }
}

data class MesCalendario(
    val mes: String,
    val previstoBrutoUsd: Double?,
    val previstoLiquidoEur: Double?,
    val recebidoAnoAnteriorUsd: Double?,
    val recebidoAnoUsd: Double?,
    val quemPaga: String?,
)

data class AnoDividendos(val ano: Int, val meses: List<Double>, val totalUsd: Double?)

data class EsteAno(val ano: Int, val empresas: List<DividendoEmpresa>)

data class DividendoEmpresa(val ticker: String, val empresa: String?, val meses: List<Double>, val totalUsd: Double)

data class ItemWatchlist(
    val tabela: String,
    val ticker: String,
    val empresa: String?,
    val nota: String?,
    val preco: Double?,
    val intervalo52s: String?,
    val pe: Double?,
    val peJusto: Double?,
    val valorJusto: Double?,
    val margem: Double?,
    val sinal: String?,
    val prioridade: String?,
    val moat: String?,
    val camadaPotencial: String?,
    val crescimento: Double?,
    val fcfYield: Double?,
    /** "✓ Avaliar troca com XYZ: +9,1%/ano", "⏳ ...", "✗ ...". */
    val testeTroca: String? = null,
)

data class Transacao(
    val linha: Int,
    val data: String?,
    val tipo: String?,
    val classe: String?,
    val ticker: String,
    val unidades: Double?,
    val preco: Double?,
    val moeda: String?,
    val valor: Double?,
    val comissao: Double?,
)

/** Resposta da ponte a um registo (compra, venda ou dividendo). */
data class RespostaRegisto(val ok: Boolean, val mensagem: String, val aviso: String?)

/**
 * Os sinais da folha. A zona diz só onde está o preço; o SINAL junta as tuas regras (camada, peso,
 * alvo, saídas planeadas). Ex.: um satélite em 🟢 Reforçar tem o sinal "🟡 Em zona · satélite só
 * compra em 🟢🟢". Por isso os alertas e os filtros usam o sinal.
 */
object Sinais {
    private val emojiInicial = Regex("^[\\p{So}\\p{Sk}\\uFE0F\\u200D\\s]+")

    private fun inicio(s: String?) = s?.trimStart().orEmpty()

    fun compra(sinal: String?) = inicio(sinal).startsWith("🟢")
    fun compraForte(sinal: String?) = inicio(sinal).startsWith("🟢🟢")
    fun reduzir(sinal: String?) = inicio(sinal).startsWith("🟠")
    fun sair(sinal: String?) = inicio(sinal).startsWith("🔴")
    fun emZona(sinal: String?) = inicio(sinal).startsWith("🟡")

    /** "🟢🟢 Reforçar forte até ao máx." → "Reforçar forte até ao máx.". */
    fun semEmoji(s: String?): String = s?.trim()?.replace(emojiInicial, "")?.ifEmpty { s.trim() } ?: ""
}

/** De onde vem o preço de um alerta. */
enum class TipoCotacao { POSICAO, ETF, WATCHLIST }

/** Preço, variação e sinal de uma posição, ETF ou empresa da watchlist, para os alertas. */
data class Cotacao(
    val tipo: TipoCotacao,
    val ticker: String,
    val nome: String,
    val preco: Double?,
    /** "USD" (ações), "EUR" (ETFs) ou null (watchlist, na moeda da bolsa). */
    val moeda: String?,
    val varDiaPct: Double?,
    val zona: String?,
    val sinal: String?,
    /** Camada da posição ou tabela da watchlist. */
    val grupo: String?,
    val valorJusto: Double? = null,
    val margem: Double? = null,
    val testeTroca: String? = null,
) {
    /** "P:ALFA", "E:MUND", "W:KAPA". */
    val chave: String get() = tipo.name.first() + ":" + ticker
}

/** O que os alertas comparam de uma verificação para a seguinte. */
data class Mercado(val hoje: String, val atualizadoEm: String, val cotacoes: List<Cotacao>) {
    companion object {
        fun de(d: Dados) = Mercado(
            hoje = d.meta.hoje,
            atualizadoEm = d.meta.atualizadoEm,
            cotacoes = d.posicoes.map {
                Cotacao(TipoCotacao.POSICAO, it.ticker, it.nome, it.precoUsd, "USD", it.varDiaPct, it.zona, it.sinal, it.camada)
            } + d.etfs.map {
                Cotacao(TipoCotacao.ETF, it.ticker, it.nome ?: it.ticker, it.precoEur, "EUR", it.varDiaPct, null, null, "ETFs")
            } + d.watchlist.map {
                Cotacao(
                    TipoCotacao.WATCHLIST, it.ticker, it.empresa ?: it.ticker, it.preco, null, null, null, it.sinal,
                    it.tabela, it.valorJusto, it.margem, it.testeTroca,
                )
            },
        )
    }
}
