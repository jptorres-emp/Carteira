package app.carteira

import app.carteira.dados.AnoDividendos
import app.carteira.dados.ClassePatrimonio
import app.carteira.dados.DividendoMes
import app.carteira.dados.Rendimentos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** O que o património rende por ano: dividendos, juros e P2P, com dados inventados. */
class RendimentosTest {

    private fun classe(nome: String, valor: Double, rendimento: Double?) =
        ClassePatrimonio(nome, investidoEur = valor, valorEur = valor, valorizacao = 0.0, rendimentoAnualEur = rendimento)

    private val classes = listOf(
        classe("Ações", 20_000.0, 500.0),
        classe("ETFs", 8_000.0, 0.0),
        classe("Certificado de Aforro E (exemplo)", 7_000.0, 300.0),
        classe("Certificado de Aforro F", 4_000.0, 100.0),
        classe("PPR (exemplo)", 2_000.0, 0.0),
        classe("P2P Lending", 1_000.0, 60.0),
        classe("Corretora Um (saldo €)", 1_000.0, 20.0),
        classe("Banco Dois (saldo €)", 500.0, 10.0),
        classe("Criptomoedas", 500.0, null),
    )

    /** O ETF EMRG paga 10 $ por mês desde out/25; a ALFA (uma ação) paga 30 $ em março e setembro. */
    private val porMes = (0L..13L).map { k -> DividendoMes("EMRG", java.time.YearMonth.of(2025, 10).plusMonths(k).toString(), 10.0) } +
        listOf(DividendoMes("ALFA", "2026-03", 30.0), DividendoMes("ALFA", "2026-09", 30.0))

    private val anos = listOf(
        AnoDividendos(2025, List(12) { 20.0 }, 240.0),
        AnoDividendos(2026, List(12) { m -> if (m < 11) 25.0 else 0.0 }, 275.0),
    )

    private fun calcular(cls: List<ClassePatrimonio> = classes, etfs: Set<String> = setOf("MUND", "EMRG")) = Rendimentos.de(
        classes = cls,
        patrimonioEur = 44_000.0,
        etfs = etfs,
        dividendosPorMes = porMes,
        anos = anos,
        cambioMensal = mapOf("2025-12" to 0.8),
        usdEur = 0.9,
        hoje = "2026-11-16",
    )!!

    @Test
    fun juntaDividendosJurosEP2P() {
        val r = calcular()
        assertEquals(listOf("Dividendos", "Juros", "P2P"), r.tipos.map { it.tipo })
        // ETFs a 0 na folha: contam os 12 meses de dez/25 a nov/26 do EMRG (dez/25 a 0,8 €/$, o resto a 0,9).
        val etfs = 10.0 * 0.8 + 11 * 10.0 * 0.9
        val dividendos = r.tipos[0]
        assertEquals(500.0 + etfs, dividendos.valorEur, 1e-9)
        assertEquals(listOf("Ações", "ETFs"), dividendos.partes.map { it.nome })
        assertTrue(dividendos.partes[1].pagoUltimos12m)
        assertFalse(dividendos.partes[0].pagoUltimos12m)
        // Os dois certificados juntam-se; os saldos das contas também.
        val juros = r.tipos[1]
        assertEquals(listOf("Certificados de Aforro" to 400.0, "Saldos nas contas" to 30.0), juros.partes.map { it.nome to it.valorEur })
        assertEquals(60.0, r.tipos[2].valorEur, 1e-9)
        val total = 500.0 + etfs + 430.0 + 60.0
        assertEquals(total, r.totalEur, 1e-9)
        assertEquals(total / 12, r.porMesEur, 1e-9)
        assertEquals(total / 44_000.0, r.taxa!!, 1e-12)
        assertEquals(total * 0.72, r.liquidoEur, 1e-9)
        assertTrue(r.temPagosUltimos12m)
    }

    @Test
    fun dividendosRecebidosNosUltimos12MesesENoAno() {
        val r = calcular()
        // dez/25: 20 $ a 0,8; jan a nov/26: 25 $ a 0,9.
        assertEquals(20.0 * 0.8 + 11 * 25.0 * 0.9, r.dividendos12mEur!!, 1e-9)
        assertEquals(11 * 25.0 * 0.9, r.dividendosAnoEur!!, 1e-9)
        assertEquals(2026, r.ano)
    }

    @Test
    fun seAFolhaJaDaORendimentoDosEtfsUsaODaFolha() {
        val cls = classes.map { if (it.classe == "ETFs") it.copy(rendimentoAnualEur = 40.0) else it }
        val r = calcular(cls)
        val etfs = r.tipos[0].partes.first { it.nome == "ETFs" }
        assertEquals(40.0, etfs.valorEur, 1e-9)
        assertFalse(etfs.pagoUltimos12m)
        assertFalse(r.temPagosUltimos12m)
    }

    @Test
    fun semEtfsQuePaguemNaoHaParteDosEtfs() {
        val r = calcular(etfs = setOf("MUND"))
        assertEquals(listOf("Ações"), r.tipos[0].partes.map { it.nome })
        assertEquals(500.0, r.tipos[0].valorEur, 1e-9)
    }

    @Test
    fun tiposDasClasses() {
        assertEquals("Dividendos" to "Ações", Rendimentos.tipoDe("Ações"))
        assertEquals("Juros" to "Certificados de Aforro", Rendimentos.tipoDe("Certificado de Aforro E (subs. 01/01/2020)"))
        assertEquals("Juros" to "Depósitos", Rendimentos.tipoDe("Depósito a prazo"))
        assertEquals("Juros" to "Saldos nas contas", Rendimentos.tipoDe("Corretora (saldo €)"))
        assertEquals("Juros" to "Saldos nas contas", Rendimentos.tipoDe("Cash"))
        assertEquals("P2P" to "P2P", Rendimentos.tipoDe("P2P Lending"))
        assertEquals("Outros" to "PPR", Rendimentos.tipoDe("PPR Exemplo"))
        assertEquals("Rendas" to "Apartamento arrendado (imóvel)", Rendimentos.tipoDe("Apartamento arrendado (imóvel)"))
    }

    @Test
    fun semClassesNaoHaRendimentos() {
        assertNull(
            Rendimentos.de(emptyList(), null, emptySet(), emptyList(), emptyList(), emptyMap(), 0.9, "2026-11-16"),
        )
    }
}
