package app.carteira.dados

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow

/** Dinheiro que sai da tua conta (negativo: compras) ou que entra (positivo: vendas, dividendos, o que vale hoje). */
data class Fluxo(val data: LocalDate, val valor: Double)

/**
 * Taxa interna de rentabilidade (TIR) por ano, como a função XIRR do Excel: a taxa que faz o valor atual de
 * todos os fluxos dar zero. Conta com as datas de cada compra e venda, por isso dá para comparar posições
 * com idades diferentes. Null se não houver dinheiro a sair e a entrar, ou se a equação não tiver solução.
 */
object Tir {
    fun anual(fluxos: List<Fluxo>): Double? {
        val f = fluxos.filter { abs(it.valor) > 1e-9 }.sortedBy { it.data }
        if (f.none { it.valor > 0 } || f.none { it.valor < 0 }) return null
        val inicio = f.first().data
        val anos = DoubleArray(f.size) { ChronoUnit.DAYS.between(inicio, f[it].data) / 365.0 }
        val valores = DoubleArray(f.size) { f[it].valor }
        if (anos.last() <= 0.0) return null
        fun vpl(r: Double): Double {
            var s = 0.0
            for (i in valores.indices) s += valores[i] / (1 + r).pow(anos[i])
            return s
        }
        // Procura todas as mudanças de sinal numa grelha larga e afina cada uma por bisseção. Com vendas pelo
        // meio pode haver mais de uma solução: aí a TIR não diz nada de útil e fica null.
        val solucoes = ArrayList<Double>()
        var a = GRELHA[0]
        var fa = vpl(a)
        for (k in 1 until GRELHA.size) {
            val b = GRELHA[k]
            val fb = vpl(b)
            if (fa.isFinite() && fb.isFinite()) {
                if (fa == 0.0) solucoes += a
                else if (fa * fb < 0) solucoes += bissecao(::vpl, a, b, fa)
            }
            a = b
            fa = fb
        }
        if (fa == 0.0) solucoes += a
        if (solucoes.isEmpty() || solucoes.max() - solucoes.min() > 0.005) return null
        return solucoes.first()
    }

    private fun bissecao(f: (Double) -> Double, inicio: Double, fim: Double, fInicio: Double): Double {
        var a = inicio
        var b = fim
        var fa = fInicio
        repeat(200) {
            val m = (a + b) / 2
            val fm = f(m)
            if (fm == 0.0 || (b - a) / 2 < 1e-12) return m
            if (fa * fm < 0) b = m else { a = m; fa = fm }
        }
        return (a + b) / 2
    }

    private val GRELHA = doubleArrayOf(
        -0.999, -0.99, -0.95, -0.9, -0.8, -0.7, -0.6, -0.5, -0.4, -0.3, -0.2, -0.1, 0.0, 0.05, 0.1, 0.15, 0.2,
        0.3, 0.4, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 5.0, 10.0, 25.0, 100.0,
    )
}
