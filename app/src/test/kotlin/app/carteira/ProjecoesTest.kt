package app.carteira

import app.carteira.dados.ClassePatrimonio
import app.carteira.dados.OpcaoReforco
import app.carteira.dados.ProjecaoRendimentos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/** A projeção do rendimento passivo (Monte Carlo, impostos e euros de hoje), com dados inventados. */
class ProjecoesTest {

    private fun classe(nome: String, valor: Double, rendimento: Double) =
        ClassePatrimonio(nome, investidoEur = valor, valorEur = valor, valorizacao = 0.0, rendimentoAnualEur = rendimento)

    /** 8 000 € na carteira (ações e ETFs), 4 000 € em certificados a 2,5% e 1 500 € que não rendem. */
    private fun classes(rendimentoAcoes: Double = 150.0) = listOf(
        classe("Ações", 6_000.0, rendimentoAcoes),
        classe("ETFs", 2_000.0, 0.0),
        classe("Certificado de Aforro (exemplo)", 4_000.0, 100.0),
        classe("PPR (exemplo)", 1_000.0, 0.0),
        classe("Cash", 500.0, 0.0),
        classe("Criptomoedas", 0.0, 0.0),
    )

    private val reforcos = listOf(
        OpcaoReforco(ProjecaoRendimentos.TEU_REFORCO, 450.0),
        OpcaoReforco(ProjecaoRendimentos.PLANO, 400.0),
    )

    private fun projecao(rendimentoAcoes: Double = 150.0, taxa: Double = 0.065, horizontes: List<Int> = ProjecaoRendimentos.HORIZONTES) =
        ProjecaoRendimentos.calcular(classes(rendimentoAcoes), rendimentoAcoes, reforcos, 2026, taxa, horizontes)!!

    @Test
    fun hojeEOQueSeLeDaFolha() {
        val p = projecao()
        assertEquals(8_000.0, p.carteiraEur, 1e-9)
        assertEquals(150.0 / 8_000.0, p.taxaCarteira, 1e-12)
        // Hoje: 150 € da carteira e 100 € dos certificados.
        assertEquals(250.0, p.rendimentoHojeEur, 1e-9)
        assertEquals(13_500.0, p.patrimonioHojeEur, 1e-9)
        assertEquals(ProjecaoRendimentos.TEU_REFORCO, p.reforcos[p.indiceReforcoPadrao].nome)
        assertEquals(450.0, p.teuReforco!!.mensalEur, 0.0)
        assertEquals(400.0, p.reforcoDoPlano!!.mensalEur, 0.0)
    }

    /** Sem rendimentos na carteira não há imposto: cresce à taxa do plano; os certificados capitalizam líquidos de IRS. */
    @Test
    fun linearSemRendimentoNaCarteira() {
        val p = projecao(rendimentoAcoes = 0.0, taxa = 0.0, horizontes = listOf(1))
        val pt = p.linear(100.0)[0]
        assertEquals(2027, pt.ano)
        // Carteira: 8 000 + 12 × 100. Certificados: 4 000 × (1 + 2,5% × 72%) = 4 072 €, que rendem 2,5% (101,80 €).
        assertEquals(9_200.0 + 4_072.0 + 1_000.0 + 500.0, pt.patrimonio.p50, 1e-6)
        assertEquals(4_072.0 * 0.025, pt.rendimento.p50, 1e-9)
        // Sem volatilidade, os três percentis são iguais.
        assertEquals(pt.rendimento.p10, pt.rendimento.p90, 0.0)
    }

    /** O que já tens e o dinheiro novo rendem taxas diferentes e pagam IRS sobre os dividendos reinvestidos. */
    @Test
    fun dinheiroNovoRendeOQueAsMetasPagam() {
        val p = projecao(taxa = 0.0, horizontes = listOf(1)).copy(taxaExistente = 0.02, taxaNovo = 0.01)
        val pt = p.linear(100.0)[0]
        // A carteira de hoje perde o imposto sobre os 2% que paga: cresce 0 − 28% × 2% ao ano.
        val existente = 8_000.0 * (1 - 0.28 * 0.02)
        // O dinheiro novo cresce 0 − 28% × 1% ao ano, com 100 € no fim de cada mês.
        val g = (1 - 0.28 * 0.01).pow(1.0 / 12)
        val novo = (0 until 12).sumOf { 100.0 * g.pow(it) }
        val certificados = 4_000.0 * (1 + 0.025 * 0.72)
        assertEquals(existente * 0.02 + novo * 0.01 + certificados * 0.025, pt.rendimento.p50, 1e-6)
        assertEquals(existente + novo + certificados + 1_500.0, pt.patrimonio.p50, 1e-6)
    }

    @Test
    fun monteCarloComPercentisOrdenadosEReprodutivel() {
        val p = projecao().copy(volatilidade = 0.16)
        val a = p.simular(450.0, simulacoes = 500)
        val b = p.simular(450.0, simulacoes = 500)
        assertEquals(a, b)
        a.forEach { pt ->
            assertTrue(pt.rendimento.p10 < pt.rendimento.p50 && pt.rendimento.p50 < pt.rendimento.p90)
            assertTrue(pt.patrimonio.p10 < pt.patrimonio.p50 && pt.patrimonio.p50 < pt.patrimonio.p90)
        }
        // A mediana fica perto do percurso em linha reta; o intervalo alarga-se com os anos.
        val linear = p.linear(450.0)
        assertTrue(abs(a[0].patrimonio.p50 / linear[0].patrimonio.p50 - 1) < 0.03)
        val largura = a.map { it.patrimonio.p90 / it.patrimonio.p10 }
        assertTrue(largura.zipWithNext().all { (x, y) -> x < y })
        // Mais volatilidade, intervalo mais largo.
        val calma = p.copy(volatilidade = 0.08).simular(450.0, simulacoes = 500).last()
        assertTrue(calma.patrimonio.p90 / calma.patrimonio.p10 < a.last().patrimonio.p90 / a.last().patrimonio.p10)
    }

    @Test
    fun maisReforcoDaMais() {
        val p = projecao().copy(volatilidade = 0.16)
        val comPlano = p.simular(400.0, simulacoes = 300)
        val comOTeu = p.simular(450.0, simulacoes = 300)
        comPlano.indices.forEach { h -> assertTrue(comPlano[h].rendimento.p50 < comOTeu[h].rendimento.p50) }
    }

    @Test
    fun percentisEEurosDeHoje() {
        val f = ProjecaoRendimentos.faixa(DoubleArray(11) { (it + 1).toDouble() })
        assertEquals(2.0, f.p10, 1e-12)
        assertEquals(6.0, f.p50, 1e-12)
        assertEquals(10.0, f.p90, 1e-12)
        assertEquals(1_000.0, ProjecaoRendimentos.emEurosDeHoje(1_020.0, 1), 1e-9)
        assertEquals(1_000.0 / 1.02.pow(30), ProjecaoRendimentos.emEurosDeHoje(1_000.0, 30), 1e-9)
    }

    @Test
    fun semCarteiraOuSemReforcosNaoHaProjecao() {
        assertNull(ProjecaoRendimentos.calcular(classes(), 150.0, emptyList(), 2026, 0.065))
        assertNull(ProjecaoRendimentos.calcular(listOf(classe("Cash", 500.0, 0.0)), 0.0, reforcos, 2026, 0.065))
    }
}
