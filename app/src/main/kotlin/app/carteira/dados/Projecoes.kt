package app.carteira.dados

import kotlin.math.pow

/** Um cenário do plano: "Base" a 6,5% ao ano. */
data class CenarioProjecao(val nome: String, val taxa: Double)

/** Onde o património e o que rende estarão daqui a [anos] anos (no fim de [ano]). */
data class PontoProjecao(val anos: Int, val ano: Int, val rendimentoAnualEur: Double, val patrimonioEur: Double) {
    val porMesEur: Double get() = rendimentoAnualEur / 12
}

/**
 * O rendimento passivo daqui a 1 a 30 anos, nos cenários do plano (tab Evolução Patrimonial: reforço mensal e
 * taxas de referência, base e alargada). Valores brutos e nominais (sem descontar a inflação).
 *
 * - Ações e ETFs (a carteira) recebem o reforço do plano no fim de cada mês e crescem à taxa do cenário com os
 *   dividendos reinvestidos; rendem o que rendem hoje em % do valor (dividendos e distribuições).
 * - O resto (certificados, P2P, saldos…) não recebe reforços: cada classe capitaliza à sua taxa de hoje e rende-a.
 *   As que não rendem (PPR, cripto, dinheiro parado) ficam com o valor de hoje.
 */
data class ProjecaoRendimentos(
    val cenarios: List<CenarioProjecao>,
    /** Por cenário (pela mesma ordem), um ponto por horizonte. */
    val pontos: List<List<PontoProjecao>>,
    val reforcoMensalEur: Double,
    val carteiraEur: Double,
    /** O que a carteira rende hoje em % do valor. */
    val taxaCarteira: Double,
    val rendimentoHojeEur: Double,
    val patrimonioHojeEur: Double,
    val anoHoje: Int,
) {
    /** O cenário base (o do meio), se houver. */
    val indiceBase: Int get() = cenarios.indexOfFirst { it.nome == BASE }.takeIf { it >= 0 } ?: 0

    companion object {
        const val REFERENCIA = "Referência"
        const val BASE = "Base"
        const val ALARGADA = "Alargada"
        val HORIZONTES = listOf(1, 3, 5, 10, 20, 30)

        /** Quanto se pode levantar por ano do património sem o gastar (a regra dos 4%), para comparar. */
        const val LEVANTAMENTO = 0.04

        fun de(d: Dados, r: Rendimentos): ProjecaoRendimentos? {
            val plano = d.plano ?: return null
            val classes = d.patrimonio?.classes ?: return null
            val ano = d.meta.hoje.take(4).toIntOrNull() ?: return null
            // O rendimento da carteira é o do cartão (com os ETFs pelo que pagaram, se a folha os tiver a 0).
            val rendimentoCarteira = r.tipos.firstOrNull { it.tipo == Rendimentos.DIVIDENDOS }?.valorEur ?: 0.0
            return calcular(
                classes = classes,
                rendimentoCarteiraEur = rendimentoCarteira,
                reforcoMensalEur = plano.reforcoMensalEur ?: return null,
                cenarios = listOfNotNull(
                    plano.taxaReferencia?.let { CenarioProjecao(REFERENCIA, it) },
                    plano.taxaBase?.let { CenarioProjecao(BASE, it) },
                    plano.taxaAlargada?.let { CenarioProjecao(ALARGADA, it) },
                ),
                anoHoje = ano,
            )
        }

        /**
         * classes: a tab Investimentos (as de ações e ETFs são a carteira); rendimentoCarteiraEur: o que a carteira
         * rende hoje por ano. Devolve null sem cenários ou sem carteira.
         */
        fun calcular(
            classes: List<ClassePatrimonio>,
            rendimentoCarteiraEur: Double,
            reforcoMensalEur: Double,
            cenarios: List<CenarioProjecao>,
            anoHoje: Int,
            horizontes: List<Int> = HORIZONTES,
        ): ProjecaoRendimentos? {
            if (cenarios.isEmpty() || horizontes.isEmpty()) return null
            val (daCarteira, resto) = classes.filter { it.valorEur > 0 }
                .partition { Rendimentos.tipoDe(it.classe).first == Rendimentos.DIVIDENDOS }
            val carteira = daCarteira.sumOf { it.valorEur }
            if (carteira <= 0) return null
            val taxaCarteira = rendimentoCarteiraEur.coerceAtLeast(0.0) / carteira
            // O resto: cada classe com a sua taxa (rendimento ÷ valor).
            val outras = resto.map { c -> c.valorEur to ((c.rendimentoAnualEur ?: 0.0).coerceAtLeast(0.0) / c.valorEur) }
            val pontos = cenarios.map { c ->
                val valoresCarteira = carteiraNoFim(carteira, reforcoMensalEur, c.taxa, horizontes)
                horizontes.mapIndexed { k, anos ->
                    val vCarteira = valoresCarteira[k]
                    var rendimento = vCarteira * taxaCarteira
                    var patrimonio = vCarteira
                    outras.forEach { (valor, taxa) ->
                        val v = valor * (1 + taxa).pow(anos)
                        patrimonio += v
                        rendimento += v * taxa
                    }
                    PontoProjecao(anos, anoHoje + anos, rendimento, patrimonio)
                }
            }
            return ProjecaoRendimentos(
                cenarios = cenarios,
                pontos = pontos,
                reforcoMensalEur = reforcoMensalEur,
                carteiraEur = carteira,
                taxaCarteira = taxaCarteira,
                rendimentoHojeEur = rendimentoCarteiraEur.coerceAtLeast(0.0) + outras.sumOf { (v, t) -> v * t },
                patrimonioHojeEur = carteira + outras.sumOf { it.first },
                anoHoje = anoHoje,
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
