package app.carteira.dados

/*
 * Rentabilidade total de cada ação e mais-valias de cada venda, a partir de todas as transações (ponte v4).
 *
 * Rentabilidade total = (o que vale hoje + o que recebeste nas vendas + dividendos − o que pagaste nas
 * compras) ÷ o que pagaste nas compras, com as comissões. Em euros (cada compra e venda com o câmbio do
 * seu dia) e na moeda das transações (dólares nas ações), que não conta o câmbio.
 *
 * Mais-valia de uma venda = o que recebeste (menos a comissão) − o custo das unidades vendidas, pelo FIFO:
 * as primeiras compradas são as primeiras vendidas, como no IRS. Tudo em euros e antes de impostos.
 */

data class Rentabilidade(
    val ticker: String,
    val nome: String?,
    val etf: Boolean,
    /** Moeda das transações: "USD" nas ações, "EUR" nos ETF. */
    val moeda: String,
    val compras: Int,
    val vendas: Int,
    val primeiraCompra: String?,
    val ultimaVenda: String?,
    val unidadesHoje: Double,
    /** Compras com as comissões. */
    val compradoEur: Double,
    /** Vendas menos as comissões. */
    val vendidoEur: Double,
    /** Dividendos brutos, ao câmbio de hoje. */
    val dividendosEur: Double,
    /** O que tens hoje (0 se já vendeste tudo). */
    val valorEur: Double,
    val compradoMoeda: Double,
    val vendidoMoeda: Double,
    val dividendosMoeda: Double,
    val valorMoeda: Double,
    /** Soma das mais-valias das vendas (FIFO, em euros); null se alguma venda não tiver compras que a cubram. */
    val maisValiasEur: Double?,
) {
    val aberta: Boolean get() = unidadesHoje > EPS
    val lucroEur: Double get() = valorEur + vendidoEur + dividendosEur - compradoEur
    val lucroMoeda: Double get() = valorMoeda + vendidoMoeda + dividendosMoeda - compradoMoeda
    val pctEur: Double? get() = if (compradoEur > 0) lucroEur / compradoEur else null
    val pctMoeda: Double? get() = if (compradoMoeda > 0) lucroMoeda / compradoMoeda else null

    /** O que já voltou do que pagaste: vendas (menos comissões) e dividendos. */
    val recuperadoEur: Double get() = vendidoEur + dividendosEur

    /** Parte do que pagaste que já voltou (1 = tudo). */
    val pctRecuperado: Double? get() = if (compradoEur > 0) recuperadoEur / compradoEur else null

    /** O que falta voltar para cobrir o que pagaste (0 quando já está de borla). */
    val faltaRecuperarEur: Double get() = (compradoEur - recuperadoEur).coerceAtLeast(0.0)

    /** As vendas e os dividendos já cobrem tudo o que pagaste: as ações que ainda tens são lucro. */
    val deBorla: Boolean get() = aberta && compradoEur > 0 && recuperadoEur >= compradoEur - 0.005

    /**
     * Quantas ações terias de vender ao preço de hoje para ficar de borla, ficando ainda com alguma; null se
     * já está de borla ou se nem vendendo quase tudo chegava.
     */
    val acoesParaFicarDeBorla: Int?
        get() {
            if (!aberta || deBorla || valorEur <= 0) return null
            val porAcao = valorEur / unidadesHoje
            val n = kotlin.math.ceil(faltaRecuperarEur / porAcao - 1e-9).toInt().coerceAtLeast(1)
            return if (n < unidadesHoje - EPS) n else null
        }
}

/** Uma venda com o custo pelo FIFO. */
data class Venda(
    val movimento: Movimento,
    val nome: String?,
    /** O que entrou, em euros: o valor da venda menos a comissão. */
    val recebidoEur: Double,
    /** Custo em euros das unidades vendidas, com as comissões das compras; null se faltarem compras. */
    val custoEur: Double?,
    /** A linha da tab Vendas (o que as ações valeriam hoje), se houver. */
    val analise: AnaliseVenda?,
) {
    val ticker: String get() = movimento.ticker
    val data: String get() = movimento.data
    val ano: Int? get() = movimento.data.take(4).toIntOrNull()
    val maisValiaEur: Double? get() = custoEur?.let { recebidoEur - it }
    val maisValiaPct: Double? get() = custoEur?.takeIf { it > 0 }?.let { (recebidoEur - it) / it }
}

/** As vendas de um ano. semCusto: vendas sem compras que as cubram (ficam fora da soma). */
data class AnoVendas(val ano: Int, val vendas: Int, val recebidoEur: Double, val maisValiasEur: Double, val semCusto: Int)

/** O que a folha diz que tens hoje de um ticker (Carteira): unidades e valor em euros e na moeda das transações. */
data class ValorHoje(val unidades: Double, val valorEur: Double, val valorMoeda: Double)

class Historico(
    val porTicker: Map<String, Rentabilidade>,
    /** Da mais recente para a mais antiga. */
    val vendas: List<Venda>,
    /** Desde quando há dividendos por empresa ("mai/2023"), ou null. */
    val dividendosDesde: String?,
) {
    val temDados: Boolean get() = porTicker.isNotEmpty()

    fun de(ticker: String): Rentabilidade? = porTicker[ticker]

    /** Posições que tens em que as vendas e os dividendos já cobrem o que pagaste. */
    val deBorla: List<Rentabilidade> get() = porTicker.values.filter { it.deBorla }.sortedByDescending { it.pctRecuperado }

    /** Posições que tens e que já recuperaram alguma coisa, da que está mais perto de ficar de borla à mais longe. */
    val recuperacao: List<Rentabilidade>
        get() = porTicker.values.filter { it.aberta && (it.pctRecuperado ?: 0.0) >= 0.005 }.sortedByDescending { it.pctRecuperado }

    /** Tickers já vendidos por inteiro, do que deu mais lucro para o que deu menos. */
    val fechadas: List<Rentabilidade> get() = porTicker.values.filter { !it.aberta }.sortedByDescending { it.lucroEur }

    /** Vendas por ano, do mais recente para o mais antigo. */
    val anos: List<AnoVendas>
        get() = vendas.filter { it.ano != null }.groupBy { it.ano!! }.map { (ano, l) ->
            AnoVendas(ano, l.size, l.sumOf { it.recebidoEur }, l.sumOf { it.maisValiaEur ?: 0.0 }, l.count { it.custoEur == null })
        }.sortedByDescending { it.ano }

    val maisValiasTotalEur: Double get() = vendas.sumOf { it.maisValiaEur ?: 0.0 }

    companion object {
        val VAZIO = Historico(emptyMap(), emptyList(), null)

        fun de(d: Dados): Historico {
            if (d.movimentos.isEmpty()) return VAZIO
            // Euros por dólar de hoje; se faltar, o que a Carteira usou para passar as posições a euros.
            val usdEur = d.cambio.usdEur
                ?: d.posicoes.firstNotNullOfOrNull { p ->
                    if (p.valorUsd != null && p.valorEur != null && p.valorUsd > 0) p.valorEur / p.valorUsd else null
                }
                ?: 1.0
            val hoje = HashMap<String, ValorHoje>()
            d.posicoes.forEach { p ->
                hoje[p.ticker] = ValorHoje(p.quantidade, p.valorEur ?: (p.valorUsd ?: 0.0) * usdEur, p.valorUsd ?: (p.valorEur ?: 0.0) / usdEur)
            }
            d.etfs.forEach { e -> hoje[e.ticker] = ValorHoje(e.quantidade, e.valorEur ?: 0.0, e.valorEur ?: 0.0) }
            val nomes = HashMap<String, String>()
            d.analiseVendas.forEach { a -> a.empresa?.let { nomes[a.ticker] = it } }
            d.tickers.forEach { t -> t.nome?.let { nomes[t.ticker] = it } }
            d.etfs.forEach { e -> e.nome?.let { nomes[e.ticker] = it } }
            d.posicoes.forEach { p -> p.empresa?.let { nomes[p.ticker] = it } }
            val dividendos = d.dividendosPorEmpresa?.empresas?.associate { it.ticker to it.totalUsd }.orEmpty()
            val desde = d.dividendosPorEmpresa?.periodos?.firstOrNull()?.let(::inicioDoPeriodo)
            return calcular(d.movimentos, hoje, dividendos, usdEur, nomes, d.analiseVendas, desde)
        }

        /** As contas, separadas de Dados para se poderem testar com poucas linhas. */
        fun calcular(
            movimentos: List<Movimento>,
            hoje: Map<String, ValorHoje>,
            dividendosUsd: Map<String, Double>,
            usdEur: Double,
            nomes: Map<String, String> = emptyMap(),
            analise: List<AnaliseVenda> = emptyList(),
            dividendosDesde: String? = null,
        ): Historico {
            val porAnalisar = analise.toMutableList()
            val vendas = ArrayList<Venda>()
            val porTicker = LinkedHashMap<String, Rentabilidade>()
            val ordenados = movimentos.sortedWith(compareBy<Movimento>({ it.data }, { it.linha }))
            for ((ticker, lista) in ordenados.groupBy { it.ticker }) {
                val etf = lista.any { it.etf }
                val moeda = lista.firstNotNullOfOrNull { it.moeda } ?: if (etf) "EUR" else "USD"
                val emDolares = moeda == "USD"
                // Lotes por comprar: [unidades que sobram, custo em euros por unidade].
                val lotes = ArrayDeque<DoubleArray>()
                var compradoEur = 0.0
                var vendidoEur = 0.0
                var compradoMoeda = 0.0
                var vendidoMoeda = 0.0
                var unidades = 0.0
                var maisValias: Double? = 0.0
                var compras = 0
                var nVendas = 0
                for (m in lista) {
                    // A comissão está em euros: passa a dólares com o câmbio dessa transação.
                    val comissaoMoeda = if (emDolares && m.cambio != null && m.cambio > 0) m.comissaoEur / m.cambio else m.comissaoEur
                    if (m.compra) {
                        compras++
                        compradoEur += m.valorEur + m.comissaoEur
                        compradoMoeda += m.valor + comissaoMoeda
                        unidades += m.unidades
                        lotes.addLast(doubleArrayOf(m.unidades, (m.valorEur + m.comissaoEur) / m.unidades))
                    } else {
                        nVendas++
                        val recebido = m.valorEur - m.comissaoEur
                        vendidoEur += recebido
                        vendidoMoeda += m.valor - comissaoMoeda
                        unidades -= m.unidades
                        var falta = m.unidades
                        var custo = 0.0
                        while (falta > EPS && lotes.isNotEmpty()) {
                            val lote = lotes.first()
                            val q = minOf(lote[0], falta)
                            custo += q * lote[1]
                            lote[0] -= q
                            falta -= q
                            if (lote[0] <= EPS) lotes.removeFirst()
                        }
                        val custoConhecido = if (falta > EPS) null else custo
                        maisValias = if (custoConhecido == null || maisValias == null) null else maisValias + recebido - custoConhecido
                        val a = porAnalisar.firstOrNull { it.corresponde(m, exato = true) }
                            ?: porAnalisar.firstOrNull { it.corresponde(m, exato = false) }
                        if (a != null) porAnalisar.remove(a)
                        vendas += Venda(m, nomes[ticker], recebido, custoConhecido, a)
                    }
                }
                val agora = hoje[ticker]
                val divUsd = dividendosUsd[ticker] ?: 0.0
                porTicker[ticker] = Rentabilidade(
                    ticker = ticker,
                    nome = nomes[ticker],
                    etf = etf,
                    moeda = moeda,
                    compras = compras,
                    vendas = nVendas,
                    primeiraCompra = lista.firstOrNull { it.compra }?.data,
                    ultimaVenda = lista.lastOrNull { !it.compra }?.data,
                    unidadesHoje = agora?.unidades ?: unidades.coerceAtLeast(0.0),
                    compradoEur = compradoEur,
                    vendidoEur = vendidoEur,
                    dividendosEur = divUsd * usdEur,
                    valorEur = agora?.valorEur ?: 0.0,
                    compradoMoeda = compradoMoeda,
                    vendidoMoeda = vendidoMoeda,
                    dividendosMoeda = if (emDolares) divUsd else divUsd * usdEur,
                    valorMoeda = agora?.valorMoeda ?: 0.0,
                    maisValiasEur = if (nVendas > 0) maisValias else null,
                )
            }
            vendas.sortWith(compareByDescending<Venda> { it.data }.thenByDescending { it.movimento.linha })
            return Historico(porTicker, vendas, dividendosDesde)
        }

        /** A mesma venda na tab Vendas: ticker, data e unidades (e o preço, se exato). */
        private fun AnaliseVenda.corresponde(m: Movimento, exato: Boolean): Boolean =
            ticker == m.ticker && data == m.data &&
                (unidades == null || kotlin.math.abs(unidades - m.unidades) < EPS) &&
                (!exato || precoVenda == null || m.preco == null || kotlin.math.abs(precoVenda - m.preco) < 0.005)

        /** "2023 (Mai-Dez)" → "mai/2023"; "2024" → "2024". */
        internal fun inicioDoPeriodo(rotulo: String): String {
            val m = Regex("""(\d{4})\s*\((\p{L}+)""").find(rotulo) ?: return rotulo.take(4)
            return m.groupValues[2].lowercase() + "/" + m.groupValues[1]
        }
    }
}

private const val EPS = 1e-6
