package app.carteira

import app.carteira.dados.CenarioProjecao
import app.carteira.dados.ClassePatrimonio
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
            classes, rendimentoCarteiraEur = 150.0, reforcoMensalEur = 100.0,
            cenarios = listOf(CenarioProjecao("Base", 0.0)), anoHoje = 2026, horizontes = listOf(1),
        )!!
        assertEquals(8_000.0, p.carteiraEur, 1e-9)
        assertEquals(150.0 / 8_000.0, p.taxaCarteira, 1e-12)
        // Hoje: 150 € da carteira e 100 € dos certificados.
        assertEquals(250.0, p.rendimentoHojeEur, 1e-9)
        assertEquals(13_500.0, p.patrimonioHojeEur, 1e-9)
        val pt = p.pontos[0][0]
        assertEquals(2027, pt.ano)
        // A carteira passa a 9 200 € (rende 1,875%: 172,50 €); os certificados capitalizam a 2,5% (4 100 €: 102,50 €).
        assertEquals(172.5 + 102.5, pt.rendimentoAnualEur, 1e-9)
        assertEquals(9_200.0 + 4_100.0 + 1_000.0 + 500.0, pt.patrimonioEur, 1e-9)
        assertEquals(pt.rendimentoAnualEur / 12, pt.porMesEur, 1e-12)
    }

    @Test
    fun cenariosPelaOrdemDoPlanoEComBaseAoMeio() {
        val p = ProjecaoRendimentos.calcular(
            classes, 150.0, 400.0,
            listOf(CenarioProjecao("Referência", 0.05), CenarioProjecao("Base", 0.065), CenarioProjecao("Alargada", 0.08)),
            anoHoje = 2026,
        )!!
        assertEquals(1, p.indiceBase)
        assertEquals(ProjecaoRendimentos.HORIZONTES, p.pontos[0].map { it.anos })
        assertEquals(2056, p.pontos[1].last().ano)
        // Mais taxa, mais rendimento; e cada cenário sobe com os anos.
        for (h in ProjecaoRendimentos.HORIZONTES.indices) {
            assertTrue(p.pontos[0][h].rendimentoAnualEur < p.pontos[1][h].rendimentoAnualEur)
            assertTrue(p.pontos[1][h].rendimentoAnualEur < p.pontos[2][h].rendimentoAnualEur)
        }
        p.pontos.forEach { pts -> assertTrue(pts.zipWithNext().all { (a, b) -> a.rendimentoAnualEur < b.rendimentoAnualEur }) }
    }

    @Test
    fun semCarteiraOuSemCenariosNaoHaProjecao() {
        assertNull(ProjecaoRendimentos.calcular(classes, 150.0, 400.0, emptyList(), 2026))
        assertNull(
            ProjecaoRendimentos.calcular(
                listOf(classe("Cash", 500.0, 0.0)), 0.0, 400.0, listOf(CenarioProjecao("Base", 0.065)), 2026,
            ),
        )
    }
}
