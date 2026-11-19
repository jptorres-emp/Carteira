package app.carteira

import app.carteira.dados.AnalisePatrimonio
import app.carteira.dados.MesPlano
import app.carteira.dados.Plano
import app.carteira.dados.PontoEvolucao
import app.carteira.dados.PosicaoPlano
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** O separador Património: contas feitas sobre séries inventadas, com resultados conhecidos. */
class AnalisePatrimonioTest {

    private fun ponto(mes: String, valor: Double?, reforco: Double? = 300.0, metaBase: Double? = null) = PontoEvolucao(
        mes = mes,
        carteiraUsd = 1.0,
        investidoUsd = null,
        sp500Usd = null,
        netWorthEur = valor,
        reforcoEur = reforco,
        metaBaseEur = metaBase,
    )

    /** jan/24 a nov/26: começa em 9 000 € e sobe 500 € por mês, com 300 € de reforço por mês. */
    private fun serieLinear(): List<PontoEvolucao> = (0L..34L).map { k ->
        ponto(AnalisePatrimonio.somarMeses("2024-01", k), 9_000.0 + 500.0 * k)
    }

    @Test
    fun resumoRitmoEReforcos() {
        val a = AnalisePatrimonio.de(serieLinear(), plano = null, agoraEur = null, hoje = "2026-11-16")!!
        assertEquals("2026-11", a.mesAtual)
        assertEquals(26_000.0, a.atualEur, 1e-9)
        assertEquals(500.0, a.vsMesAnteriorEur!!, 1e-9)
        assertEquals(20_500.0, a.fimAnoAnteriorEur!!, 1e-9)
        assertEquals(5_500.0, a.esteAnoEur!!, 1e-9)
        assertEquals(5_500.0 / 20_500.0, a.esteAnoPct!!, 1e-12)
        assertEquals(11 * 300.0, a.reforcosEsteAnoEur!!, 1e-9)
        // Ritmo: de nov/25 (20 000 €) a nov/26 (26 000 €), por mês.
        assertEquals(500.0, a.ritmoMensal12mEur!!, 1e-9)
        assertEquals(300.0, a.reforcoMedio12mEur!!, 1e-9)
        assertEquals("2024-01", a.inicio.mes)
        assertEquals(17_000.0, a.desdeInicioEur, 1e-9)
        // O reforço do primeiro mês já está no valor de partida.
        assertEquals(34 * 300.0, a.reforcosDesdeInicioEur, 1e-9)
        // Sem plano nem metas na evolução, não há leitura face ao plano.
        assertNull(a.metaDoMes)
        assertNull(a.metaHoje)
        assertNull(a.posicaoPlano)
        assertNull(a.plano)
        assertNull(a.fimDoPlano)
    }

    @Test
    fun anoAAno() {
        val a = AnalisePatrimonio.de(serieLinear(), plano = null, agoraEur = null, hoje = "2026-11-16")!!
        assertEquals(listOf(2024, 2025, 2026), a.anos.map { it.ano })
        val (y24, y25, y26) = a.anos
        // 2024 começa em janeiro: cresce face ao primeiro mês e os reforços não contam o de janeiro.
        assertEquals(14_500.0, y24.fimEur, 1e-9)
        assertEquals(5_500.0, y24.crescimentoEur, 1e-9)
        assertEquals(5_500.0 / 9_000.0, y24.crescimentoPct!!, 1e-12)
        assertEquals(11 * 300.0, y24.reforcosEur, 1e-9)
        assertFalse(y24.parcial)
        // 2025: de dez/24 a dez/25.
        assertEquals(6_000.0, y25.crescimentoEur, 1e-9)
        assertEquals(12 * 300.0, y25.reforcosEur, 1e-9)
        assertFalse(y25.parcial)
        // 2026 ainda não acabou.
        assertEquals(5_500.0, y26.crescimentoEur, 1e-9)
        assertEquals("2026-11", y26.mesFim)
        assertTrue(y26.parcial)
    }

    @Test
    fun primeiroAnoAMeioEParcial() {
        val serie = (0L..15L).map { k -> ponto(AnalisePatrimonio.somarMeses("2022-09", k), 4_000.0 + 400.0 * k) }
        val a = AnalisePatrimonio.de(serie, plano = null, agoraEur = null, hoje = "2023-12-20")!!
        val y22 = a.anos.first()
        assertEquals(2022, y22.ano)
        assertTrue(y22.parcial)
        assertEquals("2022-09", y22.mesInicio)
        assertEquals(1_200.0, y22.crescimentoEur, 1e-9)
        assertEquals(3 * 300.0, y22.reforcosEur, 1e-9)
        // Com menos de 12 meses antes, o ritmo de 2023 conta desde o início.
        val curta = AnalisePatrimonio.de(serie.take(6), plano = null, agoraEur = null, hoje = "2023-02-10")!!
        assertEquals(400.0, curta.ritmoMensal12mEur!!, 1e-9)
    }

    @Test
    fun marcos() {
        val a = AnalisePatrimonio.de(serieLinear(), plano = null, agoraEur = null, hoje = "2026-11-16")!!
        // 10 000 € em mar/24 (9 000 + 2 × 500) e 25 000 € em set/26 (9 000 + 32 × 500); o mais recente primeiro.
        assertEquals(listOf(25_000.0 to "2026-09", 10_000.0 to "2024-03"), a.marcosAtingidos.map { it.valorEur to it.atingidoEm })
        assertEquals(25_000.0, a.marcoAnteriorEur!!, 1e-9)
        assertEquals(50_000.0, a.proximoMarcoEur!!, 1e-9)
        assertEquals(24_000.0, a.faltaParaMarcoEur!!, 1e-9)
        // 24 000 € a 500 €/mês: 48 meses depois de nov/26.
        assertEquals("2030-11", a.marcoAoRitmo)
        assertNull(a.marcoPeloPlano)
    }

    @Test
    fun marcosAntesDoInicioNaoContam() {
        val serie = (0L..22L).map { k -> ponto(AnalisePatrimonio.somarMeses("2025-01", k), 30_000.0 + 1_000.0 * k) }
        val a = AnalisePatrimonio.de(serie, plano = null, agoraEur = null, hoje = "2026-11-02")!!
        assertEquals(listOf(50_000.0), a.marcosAtingidos.map { it.valorEur })
        assertEquals("2026-09", a.marcosAtingidos.single().atingidoEm)
        assertEquals(75_000.0, a.proximoMarcoEur!!, 1e-9)
    }

    @Test
    fun acrescentaOValorDeAgoraQuandoFaltaOMes() {
        val serie = listOf(
            ponto("2026-08", 24_000.0),
            ponto("2026-09", 24_500.0),
            ponto("2026-10", 25_100.0),
            // Linha de novembro já criada na folha, mas ainda sem valor.
            ponto("2026-11", null, reforco = null),
        )
        val a = AnalisePatrimonio.de(serie, plano = null, agoraEur = 25_400.0, hoje = "2026-11-16")!!
        assertEquals(listOf("2026-08", "2026-09", "2026-10", "2026-11"), a.serie.map { it.mes })
        assertEquals(25_400.0, a.atualEur, 1e-9)
        assertEquals(300.0, a.vsMesAnteriorEur!!, 1e-9)
        assertNull(a.serie.last().reforcoEur)
        // Só com o valor de agora, sem histórico: um ponto, sem ritmo.
        val so = AnalisePatrimonio.de(emptyList(), plano = null, agoraEur = 25_400.0, hoje = "2026-11-16")!!
        assertEquals(1, so.serie.size)
        assertNull(so.ritmoMensal12mEur)
        assertNull(so.marcoAoRitmo)
    }

    @Test
    fun oMesCorrenteUsaSempreOValorDeAgora() {
        // Novembro foi gravado na folha a meio do mês (26 000 €); agora vale 26 400 €.
        val a = AnalisePatrimonio.de(serieLinear(), plano = null, agoraEur = 26_400.0, hoje = "2026-11-25")!!
        assertEquals(26_400.0, a.atualEur, 1e-9)
        assertEquals(900.0, a.vsMesAnteriorEur!!, 1e-9)
        assertEquals(35, a.serie.size)
        // O reforço do mês continua o da tabela.
        assertEquals(300.0, a.serie.last().reforcoEur!!, 1e-9)
        // Os meses que já fecharam ficam como estão na folha.
        val dezembro = AnalisePatrimonio.de(serieLinear(), plano = null, agoraEur = 26_400.0, hoje = "2026-12-02")!!
        assertEquals("2026-12", dezembro.mesAtual)
        assertEquals(26_000.0, dezembro.serie[dezembro.serie.size - 2].valorEur, 1e-9)
    }

    @Test
    fun semValoresNaoHaAnalise() {
        assertNull(AnalisePatrimonio.de(listOf(ponto("2026-10", null)), plano = null, agoraEur = null, hoje = "2026-11-16"))
        // Sem data de hoje não se sabe a que mês pertence o valor de agora.
        assertNull(AnalisePatrimonio.de(emptyList(), plano = null, agoraEur = 25_000.0, hoje = ""))
    }

    /** Metas de out/26 a dez/28: a base sobe 1 000 € por mês; a referência e a alargada ficam 1 000 € abaixo e acima. */
    private fun plano(): Plano {
        val meses = (0L..26L).map { k ->
            val base = 30_000.0 + 1_000.0 * k
            MesPlano(AnalisePatrimonio.somarMeses("2026-10", k), base - 1_000.0, base, base + 1_000.0, base + 500.0)
        }
        return Plano(reforcoMensalEur = 800.0, taxaReferencia = 0.05, taxaBase = 0.065, taxaAlargada = 0.08, meses = meses)
    }

    private fun comAtual(valor: Double, hoje: String = "2026-11-15") = AnalisePatrimonio.de(
        evolucao = listOf(ponto("2026-09", 29_000.0), ponto("2026-10", 29_800.0)),
        plano = plano(),
        agoraEur = valor,
        hoje = hoje,
    )!!

    @Test
    fun metaDeHojeFicaEntreOsFinsDeMes() {
        val a = comAtual(30_600.0)
        assertEquals(31_000.0, a.metaDoMes!!.baseEur!!, 1e-9)
        // Dia 15 de 30: a meio caminho entre a meta de outubro e a de novembro.
        val hoje = a.metaHoje!!
        assertEquals(29_500.0, hoje.referenciaEur!!, 1e-9)
        assertEquals(30_500.0, hoje.baseEur!!, 1e-9)
        assertEquals(31_500.0, hoje.alargadaEur!!, 1e-9)
        assertEquals(31_000.0, hoje.historicaEur!!, 1e-9)
        // No último dia do mês é a meta do fim do mês.
        assertEquals(31_000.0, comAtual(30_600.0, hoje = "2026-11-30").metaHoje!!.baseEur!!, 1e-9)
    }

    @Test
    fun posicaoFaceAoPlano() {
        // Acima da meta de hoje, mesmo ainda abaixo da do fim do mês (faltam reforços).
        assertEquals(PosicaoPlano.ACIMA_BASE, comAtual(30_600.0).posicaoPlano)
        assertEquals(PosicaoPlano.ENTRE_REFERENCIA_E_BASE, comAtual(30_000.0).posicaoPlano)
        assertEquals(PosicaoPlano.ABAIXO_REFERENCIA, comAtual(29_000.0).posicaoPlano)
    }

    @Test
    fun datasDoPlano() {
        val a = comAtual(30_600.0)
        assertEquals(50_000.0, a.proximoMarcoEur!!, 1e-9)
        // A base passa os 50 000 € em jun/28 (31 000 € em nov/26 + 19 × 1 000 €).
        assertEquals("2028-06", a.marcoPeloPlano)
        assertEquals("2028-12", a.fimDoPlano!!.mes)
        assertEquals(800.0, a.plano!!.reforcoMensalEur!!, 1e-9)
        assertEquals(33_000.0, a.metaDe("2027-01")!!.baseEur!!, 1e-9)
        assertNull(a.metaDe("2026-09"))
    }

    @Test
    fun ponteV2UsaAsMetasDaEvolucao() {
        val serie = listOf(
            ponto("2026-09", 29_000.0, metaBase = 28_500.0),
            ponto("2026-10", 29_800.0, metaBase = 29_400.0),
            ponto("2026-11", 30_000.0, metaBase = 30_300.0),
        )
        val a = AnalisePatrimonio.de(serie, plano = null, agoraEur = null, hoje = "2026-11-10")!!
        assertEquals(30_300.0, a.metaDoMes!!.baseEur!!, 1e-9)
        assertEquals(29_400.0 + 900.0 / 3, a.metaHoje!!.baseEur!!, 1e-9)
        assertEquals(PosicaoPlano.ACIMA_BASE, a.posicaoPlano)
        assertNotNull(a.plano)
        assertNull(a.plano!!.reforcoMensalEur)
        assertEquals(29_400.0, a.metaDe("2026-10")!!.baseEur!!, 1e-9)
        // Sem metas para a frente, não há "até 2030".
        assertEquals("2026-11", a.fimDoPlano!!.mes)
    }
}
