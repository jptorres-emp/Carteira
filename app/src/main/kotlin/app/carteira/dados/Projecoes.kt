package app.carteira.dados

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Quanto entra por mês nas ações e ETFs: o teu (a média real dos últimos 12 meses) ou o do plano. */
data class OpcaoReforco(val nome: String, val mensalEur: Double)

/** Um valor projetado: o percentil 10 (mercados maus), a mediana e o percentil 90 (mercados bons). */
data class Faixa(val p10: Double, val p50: Double, val p90: Double) {
    fun map(f: (Double) -> Double) = Faixa(f(p10), f(p50), f(p90))
}

/** Onde o rendimento (bruto, por ano) e o património estarão daqui a [anos] anos (no fim de [ano]); valores nominais. */
data class PontoProjecao(val anos: Int, val ano: Int, val rendimento: Faixa, val patrimonio: Faixa)

/**
 * O rendimento passivo em dinheiro daqui a 1 a 30 anos, como o fazem os simuladores de reforma e as calculadoras
 * de dividendos: simulação de Monte Carlo (milhares de percursos de mercado, mês a mês) com o resultado nos
 * percentis 10, 50 e 90, impostos sobre os dividendos e juros, e valores em euros de hoje.
 *
 * Ajusta-se sozinho à tua evolução: parte do património e das taxas de hoje (tab Investimentos), usa o teu reforço
 * real (a média dos últimos 12 meses fechados), o que pagam as tuas ações e metas, e a volatilidade real da tua
 * carteira (Dashboard). A rendibilidade esperada é a do cenário base do teu plano (tab Evolução Patrimonial, linha 3),
 * e não a do teu passado: poucos anos de histórico não chegam para projetar trinta.
 *
 * - O que já está na carteira rende [taxaExistente]: a previsão de dividendos do próximo ano (tab Dividendos, já sem
 *   as vendas do plano) ÷ o valor; sem previsão, a de hoje.
 * - O dinheiro novo entra no fim de cada mês e rende [taxaNovo]: o que pagam as tuas metas (peso alvo × yield de cada
 *   ação), com o resto no ETF global, que é de acumulação e não paga.
 * - Os dividendos são reinvestidos depois dos 28% de IRS: a carteira cresce à taxa do plano menos o imposto sobre o
 *   que paga. Os dividendos crescem ao ritmo do preço (a yield fica constante).
 * - O resto (certificados, P2P, saldos…) não recebe reforços: cada classe capitaliza à sua taxa líquida de IRS e
 *   rende a taxa bruta. As que não rendem (PPR, cripto, dinheiro parado) ficam com o valor de hoje.
 */
data class ProjecaoRendimentos(
    /** O teu reforço (se houver) e o do plano, por esta ordem. */
    val reforcos: List<OpcaoReforco>,
    val carteiraEur: Double,
    /** O que a carteira rende hoje em % do valor (ações e ETFs, tab Investimentos). */
    val taxaCarteira: Double,
    /** O resto do património: o valor e a taxa bruta de cada classe. */
    val outras: List<Pair<Double, Double>>,
    val anoHoje: Int,
    /** A rendibilidade anual mediana da carteira (o cenário base do plano), antes de impostos. */
    val taxaMediana: Double,
    /** A volatilidade anual da carteira (o desvio-padrão dos retornos). */
    val volatilidade: Double = VOLATILIDADE_PADRAO,
    /** O que a carteira de hoje vai render a partir do próximo ano (previsão de dividendos, sem as vendas do plano). */
    val taxaExistente: Double = taxaCarteira,
    /** O que rende o dinheiro novo: as tuas metas (peso alvo × yield), com o resto no ETF de acumulação. */
    val taxaNovo: Double = taxaExistente,
    /** Quanto do dinheiro novo vai para ações pelas metas (a soma dos pesos alvo); o resto vai para o ETF. */
    val parteAcoesNovo: Double? = null,
    val horizontes: List<Int> = HORIZONTES,
    /** A tua evolução, para comparar com o plano (null se a folha não a tiver). */
    val twrReal: Double? = null,
    val ritmoUltimos12m: Double? = null,
    /** True se a volatilidade veio da tua carteira (Dashboard); false se é a de referência. */
    val volatilidadeReal: Boolean = false,
) {
    val rendimentoHojeEur: Double get() = carteiraEur * taxaCarteira + outras.sumOf { (v, t) -> v * t }
    val patrimonioHojeEur: Double get() = carteiraEur + outras.sumOf { it.first }

    val indiceReforcoPadrao: Int get() = 0
    val teuReforco: OpcaoReforco? get() = reforcos.firstOrNull { it.nome == TEU_REFORCO }
    val reforcoDoPlano: OpcaoReforco? get() = reforcos.firstOrNull { it.nome == PLANO }

    /** Crescimento anual depois do imposto sobre o que a carteira paga (os dividendos reinvestidos já líquidos). */
    private fun crescimentoLiquido(taxaDividendos: Double) = taxaMediana - IRS * taxaDividendos

    /** O valor e o rendimento bruto do resto do património ao fim de [anos] anos. */
    private fun outrasNoAno(anos: Int): Pair<Double, Double> {
        var valor = 0.0
        var rendimento = 0.0
        outras.forEach { (v, t) ->
            val x = v * (1 + t * (1 - IRS)).pow(anos)
            valor += x
            rendimento += x * t
        }
        return valor to rendimento
    }

    /**
     * Simulação de Monte Carlo: [simulacoes] percursos, mês a mês, com retornos lognormais (mediana anual
     * [taxaMediana] menos o imposto, volatilidade [volatilidade]). Com a mesma semente dá sempre o mesmo resultado.
     */
    fun simular(reforcoMensalEur: Double, simulacoes: Int = SIMULACOES, semente: Long = SEMENTE): List<PontoProjecao> {
        val meses = (horizontes.maxOrNull() ?: return emptyList()) * 12
        val sigma = volatilidade / sqrt(12.0)
        val muExistente = ln(1 + crescimentoLiquido(taxaExistente)) / 12
        val muNovo = ln(1 + crescimentoLiquido(taxaNovo)) / 12
        val indice = horizontes.withIndex().associate { (k, anos) -> anos * 12 to k }
        val rendimentos = Array(horizontes.size) { DoubleArray(simulacoes) }
        val patrimonios = Array(horizontes.size) { DoubleArray(simulacoes) }
        val outrasPorHorizonte = horizontes.map { outrasNoAno(it) }
        val aleatorio = Random(semente)
        var guardado = Double.NaN
        fun normal(): Double {
            if (!guardado.isNaN()) {
                val z = guardado
                guardado = Double.NaN
                return z
            }
            // Box-Muller: dois números normais a partir de dois uniformes.
            val u1 = aleatorio.nextDouble().coerceAtLeast(1e-300)
            val u2 = aleatorio.nextDouble()
            val r = sqrt(-2 * ln(u1))
            guardado = r * sin(2 * Math.PI * u2)
            return r * cos(2 * Math.PI * u2)
        }
        for (s in 0 until simulacoes) {
            var existente = carteiraEur
            var novo = 0.0
            for (m in 1..meses) {
                val z = if (sigma > 0) normal() else 0.0
                existente *= exp(muExistente + sigma * z)
                novo = novo * exp(muNovo + sigma * z) + reforcoMensalEur
                val k = indice[m] ?: continue
                val (valorOutras, rendimentoOutras) = outrasPorHorizonte[k]
                rendimentos[k][s] = existente * taxaExistente + novo * taxaNovo + rendimentoOutras
                patrimonios[k][s] = existente + novo + valorOutras
            }
        }
        return horizontes.mapIndexed { k, anos ->
            PontoProjecao(anos, anoHoje + anos, faixa(rendimentos[k]), faixa(patrimonios[k]))
        }
    }

    /** O mesmo cálculo sem volatilidade (o percurso mediano "em linha reta"), para conferir a simulação. */
    fun linear(reforcoMensalEur: Double): List<PontoProjecao> = copy(volatilidade = 0.0).simular(reforcoMensalEur, simulacoes = 1)

    companion object {
        const val TEU_REFORCO = "O teu"
        const val PLANO = "Plano"
        val HORIZONTES = listOf(1, 3, 5, 10, 20, 30)
        const val SIMULACOES = 2_000
        const val SEMENTE = 2026L
        const val IRS = Rendimentos.IRS

        /** Sem a volatilidade da tua carteira, a de uma carteira de ações global (referência). */
        const val VOLATILIDADE_PADRAO = 0.16

        /** Quanto se pode levantar por ano do património: a regra clássica (EUA) e uma mais prudente para a Europa. */
        const val LEVANTAMENTO = 0.04
        const val LEVANTAMENTO_PRUDENTE = 0.035

        /** A inflação para os euros de hoje (o objetivo do BCE). */
        const val INFLACAO = 0.02

        /** Os indicadores da Dashboard usados: rendibilidade (TWR anualizado) e volatilidade da carteira. */
        const val INDICADOR_TWR_DIVIDENDOS = "TTWROR Anualizado c/ Dividendos"
        const val INDICADOR_TWR = "TTWROR Anualizado"
        const val INDICADOR_12M = "Retorno Rolling 12 Meses"
        const val INDICADOR_VOLATILIDADE = "Volatilidade Anualizada"

        /** Um valor daqui a [anos] anos em euros de hoje. */
        fun emEurosDeHoje(valorEur: Double, anos: Int): Double = valorEur / (1 + INFLACAO).pow(anos)

        /** Os percentis 10, 50 e 90 (interpolados) de uma amostra. */
        fun faixa(amostra: DoubleArray): Faixa {
            val ordenada = amostra.sortedArray()
            fun percentil(p: Double): Double {
                if (ordenada.size == 1) return ordenada[0]
                val x = p * (ordenada.size - 1)
                val i = x.toInt()
                val f = x - i
                return if (i + 1 < ordenada.size) ordenada[i] * (1 - f) + ordenada[i + 1] * f else ordenada[i]
            }
            return Faixa(percentil(0.10), percentil(0.50), percentil(0.90))
        }

        /**
         * reforcoReal12mEur: a média dos reforços dos últimos 12 meses fechados (AnalisePatrimonio), que é o que a
         * folha compara com o plano. Sem o plano (taxa base) não há projeção.
         */
        fun de(d: Dados, r: Rendimentos, reforcoReal12mEur: Double?): ProjecaoRendimentos? {
            val classes = d.patrimonio?.classes ?: return null
            val ano = d.meta.hoje.take(4).toIntOrNull() ?: return null
            val plano = d.plano ?: return null
            val taxa = plano.taxaBase ?: return null
            // Um ano a vender mais do que se compra não é um reforço negativo para sempre: conta como zero.
            val reforcos = listOfNotNull(
                reforcoReal12mEur?.let { OpcaoReforco(TEU_REFORCO, it.coerceAtLeast(0.0)) },
                plano.reforcoMensalEur?.let { OpcaoReforco(PLANO, it) },
            )
            // O rendimento da carteira é o do cartão (com os ETFs pelo que pagaram, se a folha os tiver a 0).
            val rendimentoCarteira = r.tipos.firstOrNull { it.tipo == Rendimentos.DIVIDENDOS }?.valorEur ?: 0.0
            val base = calcular(classes, rendimentoCarteira, reforcos, ano, taxa) ?: return null
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
            // A volatilidade da tua carteira, dentro de limites razoáveis para uma carteira de ações.
            val vol = d.indicador(INDICADOR_VOLATILIDADE)?.valor?.takeIf { it in 0.05..0.40 }
            return base.copy(
                taxaExistente = taxaExistente,
                taxaNovo = taxaNovo,
                parteAcoesNovo = parteAcoes.takeIf { it > 0 },
                volatilidade = vol ?: VOLATILIDADE_PADRAO,
                volatilidadeReal = vol != null,
                twrReal = (d.indicador(INDICADOR_TWR_DIVIDENDOS) ?: d.indicador(INDICADOR_TWR))?.valor,
                ritmoUltimos12m = d.indicador(INDICADOR_12M)?.valor,
            )
        }

        /**
         * classes: a tab Investimentos (as de ações e ETFs são a carteira); rendimentoCarteiraEur: o que a carteira
         * rende hoje por ano; taxaMediana: a rendibilidade anual mediana. Devolve null sem reforços ou sem carteira.
         */
        fun calcular(
            classes: List<ClassePatrimonio>,
            rendimentoCarteiraEur: Double,
            reforcos: List<OpcaoReforco>,
            anoHoje: Int,
            taxaMediana: Double,
            horizontes: List<Int> = HORIZONTES,
        ): ProjecaoRendimentos? {
            if (reforcos.isEmpty() || horizontes.isEmpty()) return null
            val (daCarteira, resto) = classes.filter { it.valorEur > 0 }
                .partition { Rendimentos.tipoDe(it.classe).first == Rendimentos.DIVIDENDOS }
            val carteira = daCarteira.sumOf { it.valorEur }
            if (carteira <= 0) return null
            return ProjecaoRendimentos(
                reforcos = reforcos,
                carteiraEur = carteira,
                taxaCarteira = rendimentoCarteiraEur.coerceAtLeast(0.0) / carteira,
                // O resto: cada classe com a sua taxa (rendimento ÷ valor).
                outras = resto.map { c -> c.valorEur to ((c.rendimentoAnualEur ?: 0.0).coerceAtLeast(0.0) / c.valorEur) },
                anoHoje = anoHoje,
                taxaMediana = taxaMediana,
                horizontes = horizontes,
            )
        }
    }
}
