package app.carteira.dados

import kotlin.math.ceil
import kotlin.math.floor

/** Um mês do YTD: quanto a carteira e o S&P 500 mudaram desde o fim do ano anterior (0,05 = +5%). */
data class PontoYtd(val mes: String, val carteira: Double?, val sp500: Double?)

/**
 * O ano até hoje, como no Google Finance: a carteira e o S&P 500 a partir de 0% no fim de dezembro, mês a mês,
 * sem contar o dinheiro que entrou ou saiu. O primeiro ponto é dezembro do ano anterior (0%).
 */
data class SerieYtd(val ano: Int, val pontos: List<PontoYtd>) {
    /** A ponte não deu o S&P 500 de cada mês (antes da v6): só há a carteira. */
    val semSp500: Boolean get() = pontos.drop(1).all { it.sp500 == null }
}

/** Eixo de um gráfico em percentagem: de [minimo] a [maximo], com uma linha a cada [passo] (o 0 é uma delas). */
data class EixoPct(val minimo: Double, val maximo: Double, val passo: Double) {
    val marcas: List<Double> get() = (0..((maximo - minimo) / passo + 0.5).toInt()).map { minimo + it * passo }
}

object Ytd {

    /**
     * Encadeia a rentabilidade de cada mês do ano do último mês, como a tabela por ano da folha (em dólares, sem
     * dividendos): a da carteira (RoR Mensal) e a do S&P 500 (só preço, SPY).
     *
     * Sem a ponte v6, a da carteira faz-se aqui com a mesma conta da folha (valor do fim do mês − o que entrou
     * nesse mês, sobre o valor do mês anterior). O S&P 500 do mês que ainda não fechou também se faz como a folha
     * o fará, com o "S&P500 ($)" de agora. null se não houver o fim de dezembro e pelo menos um mês do ano.
     */
    fun calcular(pontos: List<PontoEvolucao>): SerieYtd? {
        val ultimo = pontos.lastOrNull() ?: return null
        val ano = ultimo.mes.take(4).toIntOrNull() ?: return null
        val base = pontos.lastOrNull { it.mes < "$ano-01" } ?: return null
        val doAno = pontos.filter { it.mes.startsWith("$ano-") }
        if (doAno.isEmpty()) return null
        val temSp500 = pontos.any { it.sp500Mensal != null }
        var carteira: Double? = 1.0
        var sp500: Double? = if (temSp500) 1.0 else null
        var anterior = base
        val serie = mutableListOf(PontoYtd(base.mes, 0.0, if (temSp500) 0.0 else null))
        for (p in doAno) {
            val investido = p.investidoUsd
            val investidoAntes = anterior.investidoUsd
            val carteiraAntes = anterior.carteiraUsd
            val sp500Agora = p.sp500Usd
            val sp500Antes = anterior.sp500Usd
            // O que entrou (ou saiu, se negativo) nesse mês: a folha conta-o no fim do mês.
            val entrou = if (investido != null && investidoAntes != null) investido - investidoAntes else null
            val mesCarteira = p.rorMensal
                ?: if (entrou != null && carteiraAntes > 0) (p.carteiraUsd - entrou) / carteiraAntes - 1 else null
            val mesSp500 = p.sp500Mensal
                ?: if (temSp500 && entrou != null && sp500Agora != null && sp500Antes != null && sp500Antes > 0) {
                    (sp500Agora - entrou) / sp500Antes - 1
                } else {
                    null
                }
            carteira = if (carteira != null && mesCarteira != null) carteira * (1 + mesCarteira) else null
            sp500 = if (sp500 != null && mesSp500 != null) sp500 * (1 + mesSp500) else null
            serie += PontoYtd(p.mes, carteira?.minus(1), sp500?.minus(1))
            anterior = p
        }
        return SerieYtd(ano, serie)
    }

    /**
     * Eixo "redondo" que inclui o 0 e os valores: no máximo 5 intervalos de 0,5, 1, 2, 2,5, 5, 10… pontos
     * percentuais (o mais pequeno que chegue, para a linha ocupar o gráfico).
     */
    fun eixo(valores: List<Double>): EixoPct {
        val minimo = minOf(0.0, valores.minOrNull() ?: 0.0)
        val maximo = maxOf(0.0, valores.maxOrNull() ?: 0.0)
        val amplitude = (maximo - minimo).coerceAtLeast(0.01)
        val passo = listOf(0.005, 0.01, 0.02, 0.025, 0.05, 0.1, 0.2, 0.25, 0.5, 1.0, 2.0, 5.0)
            .firstOrNull { amplitude / it <= 5.0 } ?: 10.0
        val baixo = floor(minimo / passo + 1e-9) * passo
        val cima = maxOf(ceil(maximo / passo - 1e-9) * passo, baixo + passo)
        return EixoPct(baixo, cima, passo)
    }
}
