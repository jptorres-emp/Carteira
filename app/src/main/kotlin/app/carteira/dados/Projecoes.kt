package app.carteira.dados

import kotlin.math.pow

/** Um ritmo de crescimento da carteira: o teu (o que a carteira rendeu de facto) ou um cenário do plano. */
data class CenarioProjecao(val nome: String, val taxa: Double)

/** Quanto entra por mês nas ações e ETFs: o teu (a média real dos últimos 12 meses) ou o do plano. */
data class OpcaoReforco(val nome: String, val mensalEur: Double)

/** Onde o património e o que rende estarão daqui a [anos] anos (no fim de [ano]). */
data class PontoProjecao(val anos: Int, val ano: Int, val rendimentoAnualEur: Double, val patrimonioEur: Double) {
    val porMesEur: Double get() = rendimentoAnualEur / 12
}

/**
 * O rendimento passivo em dinheiro daqui a 1 a 30 anos. Ajusta-se sozinho à tua evolução: parte do património e
 * das taxas de hoje (tab Investimentos), usa o teu reforço real (a média dos últimos 12 meses fechados) e oferece,
 * ao lado dos cenários do plano (tab Evolução Patrimonial, linha 3), o teu ritmo: o TWR anualizado com dividendos
 * da carteira (Dashboard). Valores brutos; nominais, ou em euros de hoje com [INFLACAO] ao ano.
 *
 * - O que já está na carteira cresce à taxa escolhida (dividendos reinvestidos) e rende [taxaExistente]: a previsão
 *   de dividendos do próximo ano (tab Dividendos, já sem as vendas do plano) ÷ o valor; sem previsão, a de hoje.
 * - O dinheiro novo entra no fim de cada mês, cresce à mesma taxa e rende [taxaNovo]: o que pagam as tuas metas
 *   (peso alvo × yield de cada ação), com o resto no ETF global, que é de acumulação e não paga.
 * - O resto (certificados, P2P, saldos…) não recebe reforços: cada classe capitaliza à sua taxa de hoje e rende-a.
 *   As que não rendem (PPR, cripto, dinheiro parado) ficam com o valor de hoje.
 */
data class ProjecaoRendimentos(
    /** O teu ritmo (se houver) e os cenários do plano, por esta ordem. */
    val cenarios: List<CenarioProjecao>,
    /** O teu reforço (se houver) e o do plano, por esta ordem. */
    val reforcos: List<OpcaoReforco>,
    val carteiraEur: Double,
    /** O que a carteira rende hoje em % do valor (ações e ETFs, tab Investimentos). */
    val taxaCarteira: Double,
    /** O que a carteira de hoje vai render a partir do próximo ano (previsão de dividendos, sem as vendas do plano). */
    val taxaExistente: Double = taxaCarteira,
    /** O que rende o dinheiro novo: as tuas metas (peso alvo × yield), com o resto no ETF de acumulação. */
    val taxaNovo: Double = taxaExistente,
    /** Quanto do dinheiro novo vai para ações pelas metas (a soma dos pesos alvo); o resto vai para o ETF. */
    val parteAcoesNovo: Double? = null,
    /** O resto do património: o valor e a taxa de cada classe. */
    val outras: List<Pair<Double, Double>>,
    val anoHoje: Int,
    val horizontes: List<Int> = HORIZONTES,
    /** A tua evolução, para comparar com o plano (null se a folha não a tiver). */
    val ritmoUltimos12m: Double? = null,
) {
    val rendimentoHojeEur: Double get() = carteiraEur * taxaCarteira + outras.sumOf { (v, t) -> v * t }
    val patrimonioHojeEur: Double get() = carteiraEur + outras.sumOf { it.first }

    /** Por omissão: o cenário base do plano (o teu ritmo vem de poucos anos) com o teu reforço real. */
    val indiceCenarioPadrao: Int get() = cenarios.indexOfFirst { it.nome == BASE }.takeIf { it >= 0 } ?: 0
    val indiceReforcoPadrao: Int get() = 0

    val teuRitmo: CenarioProjecao? get() = cenarios.firstOrNull { it.nome == TEU_RITMO }
    val teuReforco: OpcaoReforco? get() = reforcos.firstOrNull { it.nome == TEU_REFORCO }
    val reforcoDoPlano: OpcaoReforco? get() = reforcos.firstOrNull { it.nome == PLANO }
    val taxaBase: Double? get() = cenarios.firstOrNull { it.nome == BASE }?.taxa

    /** O rendimento e o património em cada horizonte (nominais), para um ritmo e um reforço mensal. */
    fun pontos(cenario: CenarioProjecao, reforcoMensalEur: Double): List<PontoProjecao> {
        val existente = carteiraNoFim(carteiraEur, 0.0, cenario.taxa, horizontes)
        val novo = carteiraNoFim(0.0, reforcoMensalEur, cenario.taxa, horizontes)
        return horizontes.mapIndexed { k, anos ->
            var rendimento = existente[k] * taxaExistente + novo[k] * taxaNovo
            var patrimonio = existente[k] + novo[k]
            outras.forEach { (valor, taxa) ->
                val v = valor * (1 + taxa).pow(anos)
                patrimonio += v
                rendimento += v * taxa
            }
            PontoProjecao(anos, anoHoje + anos, rendimento, patrimonio)
        }
    }

    companion object {
        const val TEU_RITMO = "O teu ritmo"
        const val REFERENCIA = "Referência"
        const val BASE = "Base"
        const val ALARGADA = "Alargada"
        const val TEU_REFORCO = "O teu"
        const val PLANO = "Plano"
        val HORIZONTES = listOf(1, 3, 5, 10, 20, 30)

        /** Quanto se pode levantar por ano do património sem o gastar (a regra dos 4%), para comparar. */
        const val LEVANTAMENTO = 0.04

        /** A inflação para os euros de hoje (o objetivo do BCE). */
        const val INFLACAO = 0.02

        /** Um valor daqui a [anos] anos em euros de hoje. */
        fun emEurosDeHoje(valorEur: Double, anos: Int): Double = valorEur / (1 + INFLACAO).pow(anos)

        /** Os indicadores da Dashboard com a rendibilidade da carteira (TWR anualizado, com e sem dividendos). */
        const val INDICADOR_TWR_DIVIDENDOS = "TTWROR Anualizado c/ Dividendos"
        const val INDICADOR_TWR = "TTWROR Anualizado"
        const val INDICADOR_12M = "Retorno Rolling 12 Meses"

        /**
         * reforcoReal12mEur: a média dos reforços dos últimos 12 meses fechados (AnalisePatrimonio), que é o que a
         * folha compara com o plano. Sem plano nem rendibilidade real, não há projeção.
         */
        fun de(d: Dados, r: Rendimentos, reforcoReal12mEur: Double?): ProjecaoRendimentos? {
            val classes = d.patrimonio?.classes ?: return null
            val ano = d.meta.hoje.take(4).toIntOrNull() ?: return null
            val plano = d.plano
            val twr = d.indicador(INDICADOR_TWR_DIVIDENDOS)?.valor ?: d.indicador(INDICADOR_TWR)?.valor
            val cenarios = listOfNotNull(
                twr?.takeIf { it > -1 }?.let { CenarioProjecao(TEU_RITMO, it) },
                plano?.taxaReferencia?.let { CenarioProjecao(REFERENCIA, it) },
                plano?.taxaBase?.let { CenarioProjecao(BASE, it) },
                plano?.taxaAlargada?.let { CenarioProjecao(ALARGADA, it) },
            )
            // Um ano a vender mais do que se compra não é um reforço negativo para sempre: conta como zero.
            val reforcos = listOfNotNull(
                reforcoReal12mEur?.let { OpcaoReforco(TEU_REFORCO, it.coerceAtLeast(0.0)) },
                plano?.reforcoMensalEur?.let { OpcaoReforco(PLANO, it) },
            )
            // O rendimento da carteira é o do cartão (com os ETFs pelo que pagaram, se a folha os tiver a 0).
            val rendimentoCarteira = r.tipos.firstOrNull { it.tipo == Rendimentos.DIVIDENDOS }?.valorEur ?: 0.0
            val base = calcular(classes, rendimentoCarteira, cenarios, reforcos, ano) ?: return null
            // O que já tens: a previsão de dividendos do próximo ano (já sem as vendas do plano), se for atual.
            val previsaoEur = d.dividendos.previsaoAno?.takeIf { it >= ano }?.let {
                val usd = d.dividendos.calendario.mapNotNull { m -> m.previstoBrutoUsd }.sum()
                d.cambio.usdEur?.let { fx -> usd * fx }
            }?.takeIf { it > 0 }
            val taxaExistente = previsaoEur?.let { it / base.carteiraEur } ?: base.taxaCarteira
            // O dinheiro novo: as metas (peso alvo × yield de cada ação); o resto vai para o ETF, que não paga.
            val comAlvo = d.posicoes.filter { (it.pesoAlvo ?: 0.0) > 0 }
            val parteAcoes = comAlvo.sumOf { it.pesoAlvo ?: 0.0 }.coerceAtMost(1.0)
            val taxaNovo = if (parteAcoes > 0) comAlvo.sumOf { (it.pesoAlvo ?: 0.0) * (it.yieldPct ?: 0.0) } else taxaExistente
            return base.copy(
                taxaExistente = taxaExistente,
                taxaNovo = taxaNovo,
                parteAcoesNovo = parteAcoes.takeIf { it > 0 },
                ritmoUltimos12m = d.indicador(INDICADOR_12M)?.valor,
            )
        }

        /**
         * classes: a tab Investimentos (as de ações e ETFs são a carteira); rendimentoCarteiraEur: o que a carteira
         * rende hoje por ano. Devolve null sem ritmos, sem reforços ou sem carteira.
         */
        fun calcular(
            classes: List<ClassePatrimonio>,
            rendimentoCarteiraEur: Double,
            cenarios: List<CenarioProjecao>,
            reforcos: List<OpcaoReforco>,
            anoHoje: Int,
            horizontes: List<Int> = HORIZONTES,
        ): ProjecaoRendimentos? {
            if (cenarios.isEmpty() || reforcos.isEmpty() || horizontes.isEmpty()) return null
            val (daCarteira, resto) = classes.filter { it.valorEur > 0 }
                .partition { Rendimentos.tipoDe(it.classe).first == Rendimentos.DIVIDENDOS }
            val carteira = daCarteira.sumOf { it.valorEur }
            if (carteira <= 0) return null
            return ProjecaoRendimentos(
                cenarios = cenarios,
                reforcos = reforcos,
                carteiraEur = carteira,
                taxaCarteira = rendimentoCarteiraEur.coerceAtLeast(0.0) / carteira,
                // O resto: cada classe com a sua taxa (rendimento ÷ valor).
                outras = resto.map { c -> c.valorEur to ((c.rendimentoAnualEur ?: 0.0).coerceAtLeast(0.0) / c.valorEur) },
                anoHoje = anoHoje,
                horizontes = horizontes,
            )
        }

        /** O valor da carteira ao fim de cada horizonte: cresce à taxa anual (composta ao mês) com o reforço no fim de cada mês. */
        fun carteiraNoFim(inicialEur: Double, reforcoMensalEur: Double, taxaAnual: Double, horizontes: List<Int>): List<Double> {
            val mensal = (1 + taxaAnual).pow(1.0 / 12) - 1
            val ate = horizontes.maxOrNull() ?: return emptyList()
            val porAno = DoubleArray(ate + 1)
            var v = inicialEur
            porAno[0] = v
            for (mes in 1..ate * 12) {
                v = v * (1 + mensal) + reforcoMensalEur
                if (mes % 12 == 0) porAno[mes / 12] = v
            }
            return horizontes.map { porAno[it] }
        }
    }
}
