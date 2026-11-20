package app.carteira

import app.carteira.dados.PontoEvolucao
import app.carteira.dados.Ytd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** O YTD do gráfico contra o S&P 500, com meses inventados. */
class YtdTest {

    private fun mes(
        mes: String,
        carteira: Double,
        investido: Double,
        sp500: Double?,
        ror: Double? = null,
        sp500Mensal: Double? = null,
    ) = PontoEvolucao(mes, carteira, investido, sp500, null, null, null, rorMensal = ror, sp500Mensal = sp500Mensal)

    // Ponte v6: a folha dá a rentabilidade de cada mês; o S&P 500 de março (o mês atual) ainda não fechou.
    private val v6 = listOf(
        mes("2025-11", 9000.0, 8000.0, 9500.0, ror = 0.01, sp500Mensal = 0.02),
        mes("2025-12", 10000.0, 9000.0, 10500.0, ror = 0.0, sp500Mensal = 0.01),
        mes("2026-01", 11000.0, 9500.0, 11200.0, ror = 0.05, sp500Mensal = 0.02),
        mes("2026-02", 11500.0, 10000.0, 11600.0, ror = -0.02, sp500Mensal = -0.01),
        // Entraram 500 $: (12 600 − 500) / 11 600 − 1 = +4,31% no S&P 500, como a folha fará no fim do mês.
        mes("2026-03", 12000.0, 10500.0, 12600.0, ror = 0.03),
    )

    @Test
    fun encadeiaOsMesesDesdeOFimDeDezembro() {
        val s = Ytd.calcular(v6)!!
        assertEquals(2026, s.ano)
        assertEquals(listOf("2025-12", "2026-01", "2026-02", "2026-03"), s.pontos.map { it.mes })
        assertEquals(0.0, s.pontos[0].carteira!!, 0.0)
        assertEquals(0.0, s.pontos[0].sp500!!, 0.0)
        assertEquals(0.05, s.pontos[1].carteira!!, 1e-12)
        assertEquals(1.05 * 0.98 - 1, s.pontos[2].carteira!!, 1e-12)
        assertEquals(1.05 * 0.98 * 1.03 - 1, s.pontos[3].carteira!!, 1e-12)
        assertEquals(1.02 * 0.99 - 1, s.pontos[2].sp500!!, 1e-12)
        assertEquals(1.02 * 0.99 * (12100.0 / 11600.0) - 1, s.pontos[3].sp500!!, 1e-12)
        assertFalse(s.semSp500)
    }

    @Test
    fun semAPonteV6SoHaACarteira() {
        // Sem a rentabilidade da folha, a da carteira faz-se com a mesma conta: (fim − o que entrou) / mês anterior.
        val v5 = v6.map { it.copy(rorMensal = null, sp500Mensal = null) }
        val s = Ytd.calcular(v5)!!
        assertEquals((11000.0 - 500.0) / 10000.0 - 1, s.pontos[1].carteira!!, 1e-12)
        assertEquals(((11000.0 - 500.0) / 10000.0) * ((11500.0 - 500.0) / 11000.0) - 1, s.pontos[2].carteira!!, 1e-12)
        assertTrue(s.semSp500)
        assertNull(s.pontos[1].sp500)
    }

    @Test
    fun janeiroEMesesQueFaltam() {
        // Em janeiro: o fim de dezembro e o mês de agora.
        val jan = Ytd.calcular(v6.take(3))!!
        assertEquals(listOf("2025-12", "2026-01"), jan.pontos.map { it.mes })
        // Sem o fim de dezembro não há YTD.
        assertNull(Ytd.calcular(v6.drop(2)))
        assertNull(Ytd.calcular(emptyList()))
        // Um mês sem valores corta a linha a partir dele.
        val falha = v6.mapIndexed { i, p -> if (i == 3) p.copy(rorMensal = null, investidoUsd = null) else p }
        val s = Ytd.calcular(falha)!!
        assertEquals(0.05, s.pontos[1].carteira!!, 1e-12)
        assertNull(s.pontos[2].carteira)
        assertNull(s.pontos[3].carteira)
    }

    @Test
    fun eixoRedondoComOZero() {
        val a = Ytd.eixo(listOf(0.0961, 0.1329, -0.05))
        assertEquals(-0.05, a.minimo, 1e-12)
        assertEquals(0.15, a.maximo, 1e-12)
        assertEquals(0.05, a.passo, 1e-12)
        assertEquals(listOf(-0.05, 0.0, 0.05, 0.1, 0.15), a.marcas.map { Math.round(it * 100) / 100.0 })
        // Só subidas: o eixo começa no 0.
        val e = Ytd.eixo(listOf(0.0, 0.034, 0.046))
        assertEquals(0.0, e.minimo, 1e-12)
        assertEquals(0.05, e.maximo, 1e-12)
        assertEquals(0.01, e.passo, 1e-12)
        // O ano real até 19/11: de −7,6% a +13,7% → de −10% a +15%, de 5 em 5.
        val r = Ytd.eixo(listOf(-0.0758, 0.1373, 0.046))
        assertEquals(-0.10, r.minimo, 1e-12)
        assertEquals(0.15, r.maximo, 1e-12)
        assertEquals(0.05, r.passo, 1e-12)
        // Tudo a 0 (só o fim de dezembro): ainda há um intervalo.
        val zero = Ytd.eixo(listOf(0.0))
        assertTrue(zero.maximo > zero.minimo)
    }
}
