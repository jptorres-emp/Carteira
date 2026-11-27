package app.carteira

import app.carteira.dados.CenarioProjecao
import app.carteira.dados.ClassePatrimonio
import app.carteira.dados.OpcaoReforco
import app.carteira.dados.ProjecaoRendimentos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** A projeção do rendimento passivo, com dados inventados. */
class ProjecoesTest {

    private fun classe(nome: String, valor: Double, rendimento: Double) =
        ClassePatrimonio(nome, investidoEur = valor, valorEur = valor, valorizacao = 0.0, rendimentoAnualEur = rendimento)

    /** 8 000 € na carteira (ações e ETFs), 4 000 € em certificados a 2,5% e 1 500 € que não rendem. */
    private val classes = listOf(
        classe("Ações", 6_000.0, 150.0),
        classe("ETFs", 2_000.0, 0.0),
        classe("Certificado de Aforro (exemplo)", 4_000.0, 100.0),
        classe("PPR (exemplo)", 1_000.0, 0.0),
        classe("Cash", 500.0, 0.0),
        classe("Criptomoedas", 0.0, 0.0),
    )

    private val cenarios = listOf(
        CenarioProjecao(ProjecaoRendimentos.TEU_RITMO, 0.09),
        CenarioProjecao(ProjecaoRendimentos.REFERENCIA, 0.05),
        CenarioProjecao(ProjecaoRendimentos.BASE, 0.065),
        CenarioProjecao(ProjecaoRendimentos.ALARGADA, 0.08),
    )
    private val reforcos = listOf(
        OpcaoReforco(ProjecaoRendimentos.TEU_REFORCO, 450.0),
        OpcaoReforco(ProjecaoRendimentos.PLANO, 400.0),
    )

    private fun projecao() = ProjecaoRendimentos.calcular(classes, 150.0, cenarios, reforcos, anoHoje = 2026)!!

    @Test
    fun carteiraSemJurosSoSomaOsReforcos() {
        val v = ProjecaoRendimentos.carteiraNoFim(1_000.0, 100.0, 0.0, listOf(1, 2, 10))
        assertEquals(listOf(2_200.0, 3_400.0, 13_000.0), v)
    }

    @Test
    fun carteiraSemReforcosCresceATaxaAnual() {
        val v = ProjecaoRendimentos.carteiraNoFim(8_000.0, 0.0, 0.06, listOf(1, 10, 30))
        assertEquals(8_000.0 * 1.06, v[0], 1e-6)
        assertEquals(8_000.0 * 1.06.pow(10), v[1], 1e-6)
        assertEquals(8_000.0 * 1.06.pow(30), v[2], 1e-5)
    }

    @Test
    fun umAnoSemCrescimento() {
        val p = ProjecaoRendimentos.calcular(
            classes, 150.0, listOf(CenarioProjecao("Base", 0.0)), listOf(OpcaoReforco("Plano", 100.0)),
            anoHoje = 2026, horizontes = listOf(1),
        )!!
        assertEquals(8_000.0, p.carteiraEur, 1e-9)
        assertEquals(150.0 / 8_000.0, p.taxaCarteira, 1e-12)
        // Hoje: 150 € da carteira e 100 € dos certificados.
        assertEquals(250.0, p.rendimentoHojeEur, 1e-9)
        assertEquals(13_500.0, p.patrimonioHojeEur, 1e-9)
        val pt = p.pontos(p.cenarios[0], 100.0)[0]
        assertEquals(2027, pt.ano)
        // A carteira passa a 9 200 € (rende 1,875%: 172,50 €); os certificados capitalizam a 2,5% (4 100 €: 102,50 €).
        assertEquals(172.5 + 102.5, pt.rendimentoAnualEur, 1e-9)
        assertEquals(9_200.0 + 4_100.0 + 1_000.0 + 500.0, pt.patrimonioEur, 1e-9)
        assertEquals(pt.rendimentoAnualEur / 12, pt.porMesEur, 1e-12)
    }

    /** O que já tens e o dinheiro novo podem render taxas diferentes (as vendas do plano e as tuas metas). */
    @Test
    fun dinheiroNovoRendeOQueAsMetasPagam() {
        val p = ProjecaoRendimentos.calcular(
            classes, 150.0, listOf(CenarioProjecao("Base", 0.0)), listOf(OpcaoReforco("O teu", 100.0)),
            anoHoje = 2026, horizontes = listOf(1),
        )!!.copy(taxaExistente = 0.02, taxaNovo = 0.01)
        val pt = p.pontos(p.cenarios[0], 100.0)[0]
        // 8 000 € a 2% (160 €) + 1 200 € de reforços a 1% (12 €) + os certificados (102,50 €).
        assertEquals(160.0 + 12.0 + 102.5, pt.rendimentoAnualEur, 1e-9)
        // Hoje continua a ser o que a carteira rende hoje (150 €) mais os certificados.
        assertEquals(250.0, p.rendimentoHojeEur, 1e-9)
    }

    @Test
    fun eurosDeHoje() {
        assertEquals(1_000.0, ProjecaoRendimentos.emEurosDeHoje(1_020.0, 1), 1e-9)
        assertEquals(1_000.0 / 1.02.pow(30), ProjecaoRendimentos.emEurosDeHoje(1_000.0, 30), 1e-9)
        assertEquals(500.0, ProjecaoRendimentos.emEurosDeHoje(500.0, 0), 0.0)
    }

    /** Por omissão: o cenário base do plano com o teu reforço real; o teu ritmo e o reforço do plano à escolha. */
    @Test
    fun omissaoEOpcoes() {
        val p = projecao()
        assertEquals(ProjecaoRendimentos.BASE, p.cenarios[p.indiceCenarioPadrao].nome)
        assertEquals(ProjecaoRendimentos.TEU_REFORCO, p.reforcos[p.indiceReforcoPadrao].nome)
        assertEquals(0.09, p.teuRitmo!!.taxa, 0.0)
        assertEquals(450.0, p.teuReforco!!.mensalEur, 0.0)
        assertEquals(400.0, p.reforcoDoPlano!!.mensalEur, 0.0)
        assertEquals(0.065, p.taxaBase!!, 0.0)
        assertEquals(ProjecaoRendimentos.HORIZONTES, p.pontos(p.cenarios[2], 450.0).map { it.anos })
        assertEquals(2056, p.pontos(p.cenarios[2], 450.0).last().ano)
    }

    /** A projeção segue a tua evolução: mais reforço ou mais rendibilidade dão mais rendimento, em todos os anos. */
    @Test
    fun maisReforcoOuMaisRitmoDaoMais() {
        val p = projecao()
        val base = p.cenarios[2]
        val comPlano = p.pontos(base, 400.0)
        val comOTeu = p.pontos(base, 450.0)
        val aoTeuRitmo = p.pontos(p.cenarios[0], 450.0)
        for (h in ProjecaoRendimentos.HORIZONTES.indices) {
            assertTrue(comPlano[h].rendimentoAnualEur < comOTeu[h].rendimentoAnualEur)
            assertTrue(comOTeu[h].rendimentoAnualEur < aoTeuRitmo[h].rendimentoAnualEur)
        }
        // Pela ordem dos cenários do plano, e a subir com os anos.
        val porCenario = p.cenarios.drop(1).map { p.pontos(it, 450.0) }
        for (h in ProjecaoRendimentos.HORIZONTES.indices) {
            assertTrue(porCenario[0][h].rendimentoAnualEur < porCenario[1][h].rendimentoAnualEur)
            assertTrue(porCenario[1][h].rendimentoAnualEur < porCenario[2][h].rendimentoAnualEur)
        }
        porCenario.forEach { pts -> assertTrue(pts.zipWithNext().all { (a, b) -> a.rendimentoAnualEur < b.rendimentoAnualEur }) }
    }

    @Test
    fun semCarteiraSemRitmosOuSemReforcosNaoHaProjecao() {
        assertNull(ProjecaoRendimentos.calcular(classes, 150.0, emptyList(), reforcos, 2026))
        assertNull(ProjecaoRendimentos.calcular(classes, 150.0, cenarios, emptyList(), 2026))
        assertNull(ProjecaoRendimentos.calcular(listOf(classe("Cash", 500.0, 0.0)), 0.0, cenarios, reforcos, 2026))
    }
}
