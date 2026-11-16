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
) {
    /** Indicador do Dashboard pelo rótulo exato da folha. */
    fun indicador(rotulo: String): Indicador? = indicadores.firstOrNull { it.rotulo == rotulo }
}

data class Meta(val folha: String, val atualizadoEm: String, val hoje: String)

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
