package app.carteira.dados

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Rentabilidade total de cada ação e mais-valias de cada venda, a partir de todas as transações (ponte v4).
 *
 * Rentabilidade total = (o que vale hoje + o que recebeste nas vendas + dividendos − o que pagaste nas
 * compras) ÷ o que pagaste nas compras, com as comissões. Em euros (cada compra e venda com o câmbio do
 * seu dia) e na moeda das transações (dólares nas ações), que não conta o câmbio.
 *
 * Mais-valia de uma venda = o que recebeste (menos a comissão) − o custo das unidades vendidas, pelo FIFO:
 * as primeiras compradas são as primeiras vendidas, como no IRS. Tudo em euros e antes de impostos.
 *
 * Contra o S&P 500 (ponte v5): o mesmo dinheiro de cada compra vai para o VUAA nesse dia e cada venda tira
 * os mesmos euros do VUAA, como na tab Validação S&P 500 (por isso a soma das ações dá o valor da folha).
 * Rentabilidade por ano: a TIR dos mesmos fluxos, com os dividendos no mês em que os recebeste.
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
    /** O mesmo dinheiro no VUAA, nas mesmas datas; null sem a ponte v5 ou se faltar o preço do índice. */
    val espelho: Espelho? = null,
    /** Compras e vendas sem o preço do S&P 500 desse dia (ponte v5). */
    val semPrecoIndice: Int = 0,
    /** Rentabilidade por ano (TIR, em euros); null com menos de um ano ou sem a ponte v5. */
    val tir: Double? = null,
    /** Dias desde a primeira compra até hoje (ou até à última venda, se já vendeste tudo). */
    val dias: Int? = null,
    /**
     * Há quanto tempo, em média, o teu dinheiro está (ou esteve) nesta posição: cada compra pesa pelo que
     * custou e conta até ser vendida (FIFO) ou até hoje. A TIR só aparece a partir de um ano.
     */
    val idadeMediaDias: Int? = null,
    /** O que ainda tens, pelo FIFO: as compras mais antigas primeiro. */
    val lotes: List<Lote> = emptyList(),
) {
    val aberta: Boolean get() = unidadesHoje > EPS
    val lucroEur: Double get() = valorEur + vendidoEur + dividendosEur - compradoEur
    val lucroMoeda: Double get() = valorMoeda + vendidoMoeda + dividendosMoeda - compradoMoeda
    val pctEur: Double? get() = if (compradoEur > 0) lucroEur / compradoEur else null
    val pctMoeda: Double? get() = if (compradoMoeda > 0) lucroMoeda / compradoMoeda else null

    /** Lucro desta posição menos o lucro do mesmo dinheiro no VUAA (positivo: bateste o S&P 500). */
    val diferencaIndiceEur: Double? get() = espelho?.let { lucroEur - it.lucroEur }

    /** A diferença em percentagem do que pagaste nas compras. */
    val diferencaIndicePct: Double? get() = diferencaIndiceEur?.let { if (compradoEur > 0) it / compradoEur else null }

    /** Tempo suficiente para a TIR fazer sentido: o dinheiro está lá, em média, há um ano ou mais. */
    val maisDeUmAno: Boolean get() = (idadeMediaDias ?: 0) >= 365

    // ── De borla (só informativo) ──
    // Conta só o lucro: o que recebeste numa venda e era o teu dinheiro a voltar (o custo das ações vendidas) não
    // paga nada. A posição fica de borla quando o lucro já tirado paga o que custaram as ações que ainda tens.

    /** O que custaram, pelo FIFO, as ações que ainda tens (com as comissões das compras). */
    val custoAtualEur: Double get() = lotes.sumOf { it.unidades * it.custoEurPorUnidade }

    /**
     * O lucro que já tiraste desta posição: as mais-valias das vendas (FIFO) e os dividendos. null se alguma venda
     * não tiver compras que a cubram (falta o custo).
     */
    val lucroTiradoEur: Double? get() = if (vendas > 0 && maisValiasEur == null) null else (maisValiasEur ?: 0.0) + dividendosEur

    /** Parte do que custaram as ações que tens que o lucro já tirado paga (1 = de borla). */
    val pctDeBorla: Double? get() = lucroTiradoEur?.let { l -> if (custoAtualEur > 0) l / custoAtualEur else null }

    /** O lucro que falta tirar para pagar as ações que tens (0 quando já está de borla). */
    val faltaParaBorlaEur: Double get() = (custoAtualEur - (lucroTiradoEur ?: 0.0)).coerceAtLeast(0.0)

    /** O lucro já tirado (mais-valias e dividendos) paga o que custaram as ações que ainda tens: essas são lucro. */
    val deBorla: Boolean
        get() {
            val lucro = lucroTiradoEur ?: return false
            return aberta && custoAtualEur > 0 && lucro >= custoAtualEur - 0.005
        }

    /** Quanto o lucro já tirado passa do que custaram as ações que tens (0 se ainda não chega). */
    val excedenteBorlaEur: Double get() = ((lucroTiradoEur ?: 0.0) - custoAtualEur).coerceAtLeast(0.0)

    /**
     * Quantas ações terias de vender ao preço de hoje para ficar de borla, ficando ainda com alguma; null se
     * já está de borla ou se nem vendendo quase tudo chegava. Cada ação vendida soma o lucro dela ao que já
     * tiraste e deixa de ter de ser paga: o que falta desce o preço inteiro da ação.
     */
    val acoesParaFicarDeBorla: Int?
        get() {
            if (!aberta || deBorla || valorEur <= 0 || lucroTiradoEur == null) return null
            val porAcao = valorEur / unidadesHoje
            val n = kotlin.math.ceil(faltaParaBorlaEur / porAcao - 1e-9).toInt().coerceAtLeast(1)
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

/**
 * O mesmo dinheiro no VUAA (S&P 500 em euros, acumula os dividendos): cada compra compra VUAA com os mesmos
 * euros nesse dia e cada venda vende os mesmos euros de VUAA, com as mesmas comissões.
 */
data class Espelho(
    /** Unidades de VUAA que terias hoje. Negativas se as vendas tiraram mais do que o VUAA valia nesse dia. */
    val unidades: Double,
    /** O que essas unidades valem hoje. */
    val valorEur: Double,
    /** Lucro do mesmo dinheiro no VUAA: o que valeria hoje + o que tiraste − o que puseste. */
    val lucroEur: Double,
    /** Rentabilidade por ano do mesmo dinheiro no VUAA; null com menos de um ano ou se o VUAA ficasse negativo. */
    val tir: Double?,
)

/**
 * A carteira toda (ponte v5): a TIR do teu dinheiro em euros — todas as compras e vendas, os dividendos de cada
 * mês e o que vale hoje — e a do mesmo dinheiro no VUAA, nas mesmas datas.
 */
data class TirCarteira(
    /** TIR da carteira (em euros, por ano); null com menos de um ano. */
    val tir: Double?,
    /** TIR do mesmo dinheiro no VUAA; null sem os preços do índice. */
    val tirIndice: Double?,
    /** A primeira compra. */
    val desde: LocalDate,
    /** O que a carteira vale hoje (ações + ETF). */
    val valorEur: Double,
    /** O que o VUAA valeria hoje com o mesmo dinheiro. */
    val indiceEur: Double?,
    /** Dividendos recebidos, em euros ao câmbio de cada mês. */
    val dividendosEur: Double,
)

/** Ações ainda por vender de uma compra, com o custo em euros por ação (com a comissão). */
data class Lote(val data: String, val unidades: Double, val custoEurPorUnidade: Double)

/**
 * Uma venda simulada ao preço de agora: o que recebes, o custo das ações pelo FIFO, a mais-valia e o IRS
 * estimado. O imposto é 28% do saldo do ano (mais-valias − menos-valias): a venda paga a diferença entre o
 * imposto do ano com ela e sem ela (negativo se uma menos-valia baixa o imposto de outras vendas).
 */
data class SimulacaoVenda(
    val unidades: Double,
    val recebidoEur: Double,
    val custoEur: Double?,
    val ano: Int,
    val saldoAnoAntesEur: Double,
    /** Ações vendidas de compras com menos de um ano (podem ter de ser englobadas no último escalão). */
    val unidadesMenosDeUmAno: Double,
    val unidadesDepois: Double,
) {
    val maisValiaEur: Double? get() = custoEur?.let { recebidoEur - it }
    val saldoAnoDepoisEur: Double get() = saldoAnoAntesEur + (maisValiaEur ?: 0.0)
    val impostoEur: Double?
        get() = maisValiaEur?.let { TAXA_MAIS_VALIAS * (saldoAnoDepoisEur.coerceAtLeast(0.0) - saldoAnoAntesEur.coerceAtLeast(0.0)) }
    /** O que fica depois do imposto estimado (se a venda o fizer baixar, conta a poupança). */
    val liquidoEur: Double get() = recebidoEur - (impostoEur ?: 0.0)

    companion object {
        const val TAXA_MAIS_VALIAS = 0.28
    }
}

class Historico(
    val porTicker: Map<String, Rentabilidade>,
    /** Da mais recente para a mais antiga. */
    val vendas: List<Venda>,
    /** Desde quando há dividendos por empresa ("mai/2023"), ou null. */
    val dividendosDesde: String?,
    /** Preço de hoje do VUAA em euros (ponte v5), ou null. */
    val indiceHojeEur: Double? = null,
    /** O dia dos dados (o "hoje" da folha). */
    val hoje: LocalDate? = null,
    /** A ponte já manda os preços do índice e os dividendos por mês (v5). */
    val v5: Boolean = false,
    /** A TIR da carteira toda e a do mesmo dinheiro no VUAA (ponte v5). */
    val carteira: TirCarteira? = null,
) {
    val temDados: Boolean get() = porTicker.isNotEmpty()

    /** Há preço do VUAA de hoje para comparar com o S&P 500. */
    val temIndice: Boolean get() = indiceHojeEur != null

    /** Posições (abertas e fechadas) com a comparação feita, da que mais bateu o S&P 500 para a que menos. */
    val contraIndice: List<Rentabilidade>
        get() = porTicker.values.filter { it.espelho != null && it.compradoEur > 0 }
            .sortedByDescending { it.diferencaIndiceEur }

    /** Saldo das mais-valias (FIFO) das vendas de um ano. */
    fun saldoDoAno(ano: Int): Double = vendas.filter { it.ano == ano }.sumOf { it.maisValiaEur ?: 0.0 }

    /**
     * Vender agora "unidades" de uma posição, ao preço de hoje (o valor da Carteira a dividir pelas ações):
     * custo pelo FIFO e imposto estimado sobre o saldo do ano. Sem comissão de venda.
     */
    fun simularVenda(r: Rentabilidade, unidades: Double, dia: LocalDate = hoje ?: LocalDate.now()): SimulacaoVenda? {
        if (!r.aberta || r.valorEur <= 0 || unidades <= EPS) return null
        val n = minOf(unidades, r.unidadesHoje)
        val recebido = r.valorEur / r.unidadesHoje * n
        var falta = n
        var custo = 0.0
        var recentes = 0.0
        for (l in r.lotes) {
            if (falta <= EPS) break
            val q = minOf(l.unidades, falta)
            custo += q * l.custoEurPorUnidade
            val comprado = runCatching { LocalDate.parse(l.data) }.getOrNull()
            if (comprado != null && ChronoUnit.DAYS.between(comprado, dia) < 365) recentes += q
            falta -= q
        }
        return SimulacaoVenda(
            unidades = n,
            recebidoEur = recebido,
            custoEur = if (falta > EPS) null else custo,
            ano = dia.year,
            saldoAnoAntesEur = saldoDoAno(dia.year),
            unidadesMenosDeUmAno = recentes,
            unidadesDepois = (r.unidadesHoje - n).coerceAtLeast(0.0),
        )
    }

    fun de(ticker: String): Rentabilidade? = porTicker[ticker]

    /** Posições que tens em que o lucro já tirado paga as ações que ainda tens. */
    val deBorla: List<Rentabilidade> get() = porTicker.values.filter { it.deBorla }.sortedByDescending { it.pctDeBorla }

    /** Posições que tens com algum lucro já tirado, da que está mais perto de ficar de borla à mais longe. */
    val recuperacao: List<Rentabilidade>
        get() = porTicker.values.filter { it.aberta && (it.pctDeBorla ?: 0.0) >= 0.005 }.sortedByDescending { it.pctDeBorla }

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
            val dia = runCatching { LocalDate.parse(d.meta.hoje.take(10)) }.getOrNull()
            val v5 = d.versaoPonte >= 5
            return calcular(
                d.movimentos, hoje, dividendos, usdEur, nomes, d.analiseVendas, desde,
                indiceHojeEur = d.indice?.vuaaEur,
                dividendosPorMes = if (v5) d.dividendosPorMes.groupBy({ it.ticker }, { it.mes to it.usd }) else null,
                cambioMensal = d.cambioMensal,
                dia = dia,
                dividendosMensaisUsd = if (v5) dividendosMensais(d) else null,
            )
        }

        /**
         * Os dividendos de cada mês ("2024-03" → dólares) da tabela da folha com os totais por mês: inclui os meses
         * de antes de haver dividendos por empresa e as empresas sem transações.
         */
        internal fun dividendosMensais(d: Dados): Map<String, Double> {
            val m = LinkedHashMap<String, Double>()
            d.dividendos.anos.forEach { a ->
                a.meses.forEachIndexed { k, usd -> if (usd > 0) m["%04d-%02d".format(a.ano, k + 1)] = usd }
            }
            return m
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
            /** Preço de hoje do VUAA em euros; sem ele não há comparação com o S&P 500. */
            indiceHojeEur: Double? = null,
            /**
             * Dividendos em dólares por mês ("2024-03"); sem eles (ponte v4) não há TIR e os dividendos passam a
             * euros ao câmbio de hoje. Com eles, cada mês passa a euros ao câmbio desse mês, como a folha.
             */
            dividendosPorMes: Map<String, List<Pair<String, Double>>>? = null,
            cambioMensal: Map<String, Double> = emptyMap(),
            dia: LocalDate? = null,
            /** Os dividendos de cada mês da carteira toda, em dólares (para a TIR da carteira). */
            dividendosMensaisUsd: Map<String, Double>? = null,
        ): Historico {
            val hojeDia = dia ?: LocalDate.now()
            // A carteira toda: todas as compras e vendas e o mesmo dinheiro no VUAA.
            val fluxosCarteira = ArrayList<Fluxo>()
            var unidadesIndiceCarteira = 0.0
            var semPrecoCarteira = 0
            var valorCarteira = 0.0
            val porAnalisar = analise.toMutableList()
            val vendas = ArrayList<Venda>()
            val porTicker = LinkedHashMap<String, Rentabilidade>()
            val ordenados = movimentos.sortedWith(compareBy<Movimento>({ it.data }, { it.linha }))
            for ((ticker, lista) in ordenados.groupBy { it.ticker }) {
                val etf = lista.any { it.etf }
                val moeda = lista.firstNotNullOfOrNull { it.moeda } ?: if (etf) "EUR" else "USD"
                val emDolares = moeda == "USD"
                // Lotes por vender: [unidades que sobram, custo em euros por unidade], e a data de cada um.
                val lotes = ArrayDeque<DoubleArray>()
                val datasLotes = ArrayDeque<String>()
                // O mesmo dinheiro no VUAA e os fluxos para a TIR.
                var unidadesIndice = 0.0
                var semPreco = 0
                var dinheiroDias = 0.0
                val fluxos = ArrayList<Fluxo>()
                val fluxosIndice = ArrayList<Fluxo>()
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
                    val p = m.precoIndiceEur
                    if (p != null && p > 0) unidadesIndice += (if (m.compra) 1 else -1) * m.valorEur / p else semPreco++
                    val dataM = runCatching { LocalDate.parse(m.data.take(10)) }.getOrNull()
                    val caixa = if (m.compra) -(m.valorEur + m.comissaoEur) else m.valorEur - m.comissaoEur
                    if (dataM != null) {
                        fluxos += Fluxo(dataM, caixa)
                        fluxosIndice += Fluxo(dataM, caixa)
                    }
                    if (m.compra) {
                        compras++
                        compradoEur += m.valorEur + m.comissaoEur
                        compradoMoeda += m.valor + comissaoMoeda
                        unidades += m.unidades
                        lotes.addLast(doubleArrayOf(m.unidades, (m.valorEur + m.comissaoEur) / m.unidades))
                        datasLotes.addLast(m.data)
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
                            dinheiroDias += q * lote[1] * diasEntre(datasLotes.first(), m.data, hojeDia)
                            lote[0] -= q
                            falta -= q
                            if (lote[0] <= EPS) {
                                lotes.removeFirst()
                                datasLotes.removeFirst()
                            }
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
                fluxosCarteira += fluxos
                unidadesIndiceCarteira += unidadesIndice
                semPrecoCarteira += semPreco
                valorCarteira += agora?.valorEur ?: 0.0
                val porMes = dividendosPorMes?.get(ticker).orEmpty()
                val divUsd = if (dividendosPorMes != null) porMes.sumOf { it.second } else dividendosUsd[ticker] ?: 0.0
                val divEur = if (dividendosPorMes != null) porMes.sumOf { (mes, usd) -> usd * (cambioMensal[mes] ?: usdEur) } else divUsd * usdEur
                val valorHoje = agora?.valorEur ?: 0.0
                val compradoTotal = compradoEur
                // Dias de história: até hoje, ou até à última venda se já vendeste tudo.
                val primeira = lista.firstOrNull { it.compra }?.data?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
                val aberta = (agora?.unidades ?: unidades) > EPS
                val ultima = if (aberta) hojeDia else lista.lastOrNull { !it.compra }?.data
                    ?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: hojeDia
                val dias = primeira?.let { ChronoUnit.DAYS.between(it, ultima).toInt() }
                lotes.forEachIndexed { i, l -> dinheiroDias += l[0] * l[1] * diasEntre(datasLotes.elementAt(i), null, hojeDia) }
                val idadeMedia = if (compradoEur > 0) (dinheiroDias / compradoEur).toInt() else null
                val umAno = (idadeMedia ?: 0) >= 365
                // Dividendos no dia 15 do mês em que chegaram (ou hoje, se o mês ainda vai a meio).
                val tir = if (dividendosPorMes == null || !umAno) null else {
                    val f = ArrayList(fluxos)
                    porMes.forEach { (mes, usd) ->
                        fluxoDividendo(mes, usd * (cambioMensal[mes] ?: usdEur), primeira, hojeDia)?.let { f += it }
                    }
                    if (valorHoje > 0) f += Fluxo(hojeDia, valorHoje)
                    Tir.anual(f)
                }
                val espelho = if (indiceHojeEur == null || indiceHojeEur <= 0 || semPreco > 0) null else {
                    val valorIndice = unidadesIndice * indiceHojeEur
                    // Com a mesma regra da ação (um ano); o VUAA fica investido até hoje, mesmo que já tenhas vendido tudo.
                    val tirIndice = if (dividendosPorMes == null || !umAno || valorIndice < -0.005) null else {
                        val f = ArrayList(fluxosIndice)
                        if (valorIndice > 0) f += Fluxo(hojeDia, valorIndice)
                        Tir.anual(f)
                    }
                    Espelho(unidadesIndice, valorIndice, valorIndice + vendidoEur - compradoTotal, tirIndice)
                }
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
                    dividendosEur = divEur,
                    valorEur = agora?.valorEur ?: 0.0,
                    compradoMoeda = compradoMoeda,
                    vendidoMoeda = vendidoMoeda,
                    dividendosMoeda = if (emDolares) divUsd else divEur,
                    valorMoeda = agora?.valorMoeda ?: 0.0,
                    maisValiasEur = if (nVendas > 0) maisValias else null,
                    espelho = espelho,
                    semPrecoIndice = if (indiceHojeEur == null) 0 else semPreco,
                    tir = tir,
                    dias = dias,
                    idadeMediaDias = idadeMedia,
                    lotes = lotes.mapIndexed { i, l -> Lote(datasLotes.elementAt(i), l[0], l[1]) }.filter { it.unidades > EPS },
                )
            }
            vendas.sortWith(compareByDescending<Venda> { it.data }.thenByDescending { it.movimento.linha })
            val carteira = if (dividendosMensaisUsd == null || fluxosCarteira.isEmpty()) null else {
                val desde = fluxosCarteira.minOf { it.data }
                val dividendos = dividendosMensaisUsd.mapNotNull { (mes, usd) ->
                    fluxoDividendo(mes, usd * (cambioMensal[mes] ?: usdEur), desde, hojeDia)
                }
                val umAno = ChronoUnit.DAYS.between(desde, hojeDia) >= 365
                val tir = if (umAno && valorCarteira > 0) Tir.anual(fluxosCarteira + dividendos + Fluxo(hojeDia, valorCarteira)) else null
                val indice = if (indiceHojeEur != null && indiceHojeEur > 0 && semPrecoCarteira == 0) unidadesIndiceCarteira * indiceHojeEur else null
                val tirIndice = if (umAno && indice != null && indice > 0) Tir.anual(fluxosCarteira + Fluxo(hojeDia, indice)) else null
                TirCarteira(tir, tirIndice, desde, valorCarteira, indice, dividendos.sumOf { it.valor })
            }
            return Historico(porTicker, vendas, dividendosDesde, indiceHojeEur, hojeDia, dividendosPorMes != null, carteira)
        }

        /**
         * Um dividendo para a TIR: no dia 15 do mês ("aaaa-mm"), nunca depois de hoje nem antes da primeira compra.
         * Os de meses anteriores à primeira compra ficam de fora: são de transações que faltam na folha e, como
         * dinheiro a entrar antes de algum sair, davam mais do que uma TIR.
         */
        private fun fluxoDividendo(mes: String, valorEur: Double, primeira: LocalDate?, hoje: LocalDate): Fluxo? {
            val d15 = runCatching { LocalDate.parse("$mes-15") }.getOrNull() ?: return null
            if (primeira != null && d15.withDayOfMonth(1).plusMonths(1) <= primeira.withDayOfMonth(1)) return null
            val dia = if (primeira != null && d15 < primeira) primeira else d15
            return Fluxo(minOf(dia, hoje), valorEur)
        }

        /** Dias entre duas datas "aaaa-mm-dd" (fim null = hoje); 0 se alguma não se ler. */
        private fun diasEntre(inicio: String, fim: String?, hoje: LocalDate): Long {
            val a = runCatching { LocalDate.parse(inicio.take(10)) }.getOrNull() ?: return 0
            val b = if (fim == null) hoje else runCatching { LocalDate.parse(fim.take(10)) }.getOrNull() ?: return 0
            return ChronoUnit.DAYS.between(a, b).coerceAtLeast(0)
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
