package app.carteira

import app.carteira.dados.AnaliseVenda
import app.carteira.dados.Historico
import app.carteira.dados.Movimento
import app.carteira.dados.ValorHoje
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Contas da rentabilidade total e das mais-valias (FIFO), com transações inventadas. */
class HistoricoTest {

    private fun acao(linha: Int, data: String, tipo: String, ticker: String, un: Double, preco: Double, cambio: Double, comissao: Double) =
        Movimento(linha, data, tipo, "Ação", ticker, un, preco, "USD", un * preco, cambio, un * preco * cambio, comissao)

    private fun etf(linha: Int, data: String, tipo: String, ticker: String, un: Double, preco: Double, comissao: Double) =
        Movimento(linha, data, tipo, "ETF", ticker, un, preco, "EUR", un * preco, 0.9, un * preco, comissao)

    // ALFA: compra 10 a 100 $ (900 €) e 5 a 120 $ (552 €), vende 8 a 150 $ (1140 €); 1 € de comissão em cada.
    private val alfa = listOf(
        acao(1, "2023-01-10", "Compra", "ALFA", 10.0, 100.0, 0.90, 1.0),
        acao(2, "2024-01-10", "Compra", "ALFA", 5.0, 120.0, 0.92, 1.0),
        acao(3, "2025-01-10", "Venda", "ALFA", 8.0, 150.0, 0.95, 1.0),
    )
    // BETA (ETF): compra 3 a 50 €, vende tudo a 40 € com 0,50 € de comissão.
    private val beta = listOf(
        etf(4, "2024-03-01", "Compra", "BETA", 3.0, 50.0, 0.0),
        etf(5, "2025-03-01", "Venda", "BETA", 3.0, 40.0, 0.5),
    )
    private val hoje = mapOf("ALFA" to ValorHoje(7.0, 1019.2, 1120.0))

    @Test
    fun rentabilidadeTotalContaVendasEDividendos() {
        val h = Historico.calcular(alfa + beta, hoje, mapOf("ALFA" to 20.0), usdEur = 0.91)
        val r = h.de("ALFA")!!
        assertTrue(r.aberta)
        assertEquals(1454.0, r.compradoEur, 1e-9)       // 901 + 553
        assertEquals(1139.0, r.vendidoEur, 1e-9)        // 1140 − 1
        assertEquals(18.2, r.dividendosEur, 1e-9)       // 20 $ × 0,91
        assertEquals(722.4, r.lucroEur, 1e-9)           // 1019,2 + 1139 + 18,2 − 1454
        assertEquals(722.4 / 1454.0, r.pctEur!!, 1e-12)
        // Em dólares: as comissões passam a dólares com o câmbio de cada dia.
        val comprado = 1000 + 1 / 0.90 + 600 + 1 / 0.92
        val vendido = 1200 - 1 / 0.95
        assertEquals(comprado, r.compradoMoeda, 1e-9)
        assertEquals((1120 + vendido + 20 - comprado) / comprado, r.pctMoeda!!, 1e-12)
        assertEquals(2, r.compras)
        assertEquals(1, r.vendas)
        assertEquals("2023-01-10", r.primeiraCompra)
    }

    @Test
    fun maisValiaPeloFifo() {
        val h = Historico.calcular(alfa, hoje, emptyMap(), usdEur = 0.91)
        val v = h.vendas.single()
        // As 8 vendidas saem do primeiro lote: 8 × (901 € / 10) = 720,80 €.
        assertEquals(720.8, v.custoEur!!, 1e-9)
        assertEquals(418.2, v.maisValiaEur!!, 1e-9)
        assertEquals(418.2, h.de("ALFA")!!.maisValiasEur!!, 1e-9)
        assertEquals(2025, v.ano)
        assertEquals(listOf(2025), h.anos.map { it.ano })
    }

    @Test
    fun vendaQueApanhaDoisLotes() {
        val mov = alfa + acao(6, "2025-06-01", "Venda", "ALFA", 4.0, 160.0, 0.90, 0.0)
        val h = Historico.calcular(mov, emptyMap(), emptyMap(), usdEur = 0.91)
        // Sobram 2 do 1.º lote (90,10 €) e 2 do 2.º (553 € / 5 = 110,60 €).
        val segunda = h.vendas.first()
        assertEquals("2025-06-01", segunda.data)
        assertEquals(2 * 90.1 + 2 * 110.6, segunda.custoEur!!, 1e-9)
    }

    @Test
    fun posicaoFechadaEEtf() {
        val h = Historico.calcular(alfa + beta, hoje, emptyMap(), usdEur = 0.91)
        val b = h.de("BETA")!!
        assertFalse(b.aberta)
        assertTrue(b.etf)
        assertEquals("EUR", b.moeda)
        assertEquals(-30.5, b.lucroEur, 1e-9)          // 119,50 − 150
        assertEquals(b.pctEur!!, b.pctMoeda!!, 1e-12)  // num ETF em euros é igual
        assertEquals(listOf("BETA"), h.fechadas.map { it.ticker })
        // Mais recente primeiro: BETA (março 2025) antes de ALFA (janeiro 2025).
        assertEquals(listOf("BETA", "ALFA"), h.vendas.map { it.ticker })
        assertEquals(-30.5 + 418.2, h.maisValiasTotalEur, 1e-9)
    }

    @Test
    fun vendaSemComprasQueACubramNaoEntraNaSoma() {
        val mov = listOf(
            acao(1, "2024-01-10", "Compra", "GAMA", 2.0, 10.0, 0.9, 0.0),
            acao(2, "2024-05-10", "Venda", "GAMA", 3.0, 12.0, 0.9, 0.0),
        )
        val h = Historico.calcular(mov, emptyMap(), emptyMap(), usdEur = 0.9)
        assertNull(h.vendas.single().custoEur)
        assertNull(h.de("GAMA")!!.maisValiasEur)
        val ano = h.anos.single()
        assertEquals(1, ano.semCusto)
        assertEquals(0.0, ano.maisValiasEur, 1e-9)
    }

    @Test
    fun juntaALinhaDaTabVendas() {
        val certa = AnaliseVenda("2025-01-10", "ALFA", "Alfa Software", 8.0, 150.0, 1200.0, 170.0, 1360.0, 0.0, -160.0, -0.1176, "🔴 Devias ter mantido")
        val outra = certa.copy(data = "2025-01-11", veredito = "🟢 Vendeste bem")
        val h = Historico.calcular(alfa, hoje, emptyMap(), usdEur = 0.91, nomes = mapOf("ALFA" to "Alfa Software"), analise = listOf(outra, certa))
        val v = h.vendas.single()
        assertEquals("🔴 Devias ter mantido", v.analise!!.veredito)
        assertEquals("Alfa Software", v.nome)
    }

    @Test
    fun deBorlaQuandoVendasEDividendosCobremOQuePagaste() {
        val mov = listOf(
            acao(1, "2023-01-10", "Compra", "DELTA", 10.0, 50.0, 0.90, 0.0), // 450 €
            acao(2, "2025-01-10", "Venda", "DELTA", 6.0, 100.0, 0.90, 0.0), // 540 €
        )
        val h = Historico.calcular(mov, mapOf("DELTA" to ValorHoje(4.0, 360.0, 400.0)), mapOf("DELTA" to 10.0), usdEur = 0.9)
        val r = h.de("DELTA")!!
        assertTrue(r.deBorla)
        assertEquals(549.0, r.recuperadoEur, 1e-9) // 540 + 10 $ × 0,9
        assertEquals(549.0 / 450.0, r.pctRecuperado!!, 1e-12)
        assertEquals(0.0, r.faltaRecuperarEur, 1e-9)
        assertNull(r.acoesParaFicarDeBorla)
        assertEquals(listOf("DELTA"), h.deBorla.map { it.ticker })
    }

    @Test
    fun quantoFaltaParaFicarDeBorla() {
        val h = Historico.calcular(alfa, hoje, mapOf("ALFA" to 20.0), usdEur = 0.91)
        val r = h.de("ALFA")!!
        assertFalse(r.deBorla)
        // Voltaram 1139 € das vendas e 18,20 € de dividendos, de 1454 € pagos.
        assertEquals(1157.2 / 1454.0, r.pctRecuperado!!, 1e-12)
        assertEquals(296.8, r.faltaRecuperarEur, 1e-9)
        // Cada ação vale 1019,20 € / 7 = 145,60 €: faltam 3 (296,80 / 145,60 = 2,04).
        assertEquals(3, r.acoesParaFicarDeBorla)
        assertEquals(listOf("ALFA"), h.recuperacao.map { it.ticker })
        assertTrue(h.deBorla.isEmpty())
    }

    @Test
    fun periodoDosDividendos() {
        assertEquals("mai/2023", Historico.inicioDoPeriodo("2023 (Mai-Dez)"))
        assertEquals("2024", Historico.inicioDoPeriodo("2024"))
    }

    // ── Ponte v5: contra o S&P 500 (VUAA), TIR e simulação de venda ──

    /** Uma transação de ações já com o preço do VUAA (€) desse dia. */
    private fun comIndice(linha: Int, data: String, tipo: String, ticker: String, un: Double, preco: Double, cambio: Double, comissao: Double, vuaa: Double?) =
        acao(linha, data, tipo, ticker, un, preco, cambio, comissao).copy(precoIndiceEur = vuaa)

    // GAMA: compra 10 a 100 $ (900 € + 1 €) com o VUAA a 90 €, compra 5 a 120 $ (600 €) com o VUAA a 100 €,
    // vende 6 a 150 $ (900 € − 1 €) com o VUAA a 120 €. Hoje: 9 ações valem 1350 € e o VUAA está a 130 €.
    private val gama = listOf(
        comIndice(1, "2023-01-02", "Compra", "GAMA", 10.0, 100.0, 0.90, 1.0, 90.0),
        comIndice(2, "2024-01-02", "Compra", "GAMA", 5.0, 120.0, 1.0, 0.0, 100.0),
        comIndice(3, "2025-01-02", "Venda", "GAMA", 6.0, 150.0, 1.0, 1.0, 120.0),
    )
    private val hojeGama = mapOf("GAMA" to ValorHoje(9.0, 1350.0, 1350.0))
    // 10 $ em jun/2024 (câmbio desse mês 0,90) e 10 $ em jun/2025 (sem câmbio do mês: o de hoje, 0,95).
    private val divGama = mapOf("GAMA" to listOf("2024-06" to 10.0, "2025-06" to 10.0))
    private val dia = LocalDate.parse("2026-01-02")

    private fun historicoGama(mov: List<app.carteira.dados.Movimento> = gama) = Historico.calcular(
        mov, hojeGama, emptyMap(), usdEur = 0.95, indiceHojeEur = 130.0, dividendosPorMes = divGama,
        cambioMensal = mapOf("2024-06" to 0.90), dia = dia,
    )

    @Test
    fun mesmoDinheiroNoVuaa() {
        val h = historicoGama()
        val r = h.de("GAMA")!!
        assertEquals(18.5, r.dividendosEur, 1e-9)          // 10 $ × 0,90 + 10 $ × 0,95
        assertEquals(766.5, r.lucroEur, 1e-9)              // 1350 + 899 + 18,5 − 1501
        val e = r.espelho!!
        // VUAA: 900/90 + 600/100 − 900/120 = 8,5 unidades × 130 € = 1105 €.
        assertEquals(8.5, e.unidades, 1e-12)
        assertEquals(1105.0, e.valorEur, 1e-9)
        assertEquals(503.0, e.lucroEur, 1e-9)              // 1105 + 899 − 1501, com as mesmas comissões
        assertEquals(263.5, r.diferencaIndiceEur!!, 1e-9)  // = 1350 + 18,5 − 1105
        assertEquals(263.5 / 1501.0, r.diferencaIndicePct!!, 1e-12)
        assertEquals(0, r.semPrecoIndice)
        assertTrue(h.temIndice)
        assertEquals(listOf("GAMA"), h.contraIndice.map { it.ticker })
    }

    @Test
    fun tirDaAcaoEDoVuaa() {
        val r = historicoGama().de("GAMA")!!
        // Dinheiro investido, em média, há 818 dias (cada compra pesa pelo custo, até ser vendida ou até hoje).
        assertEquals(818, r.idadeMediaDias)
        assertTrue(r.maisDeUmAno)
        assertEquals(1096, r.dias)
        // Valores do scipy (brentq) para os mesmos fluxos, com os dividendos no dia 15 de cada mês.
        assertEquals(0.20654587962617724, r.tir!!, 1e-9)
        assertEquals(0.14361557878215936, r.espelho!!.tir!!, 1e-9)
    }

    @Test
    fun semUmAnoNaoHaTir() {
        val mov = listOf(comIndice(1, "2025-10-01", "Compra", "GAMA", 9.0, 100.0, 1.0, 0.0, 120.0))
        val r = historicoGama(mov).de("GAMA")!!
        assertTrue(r.idadeMediaDias!! < 365)
        assertNull(r.tir)
        assertNotNull(r.espelho)
        assertNull(r.espelho!!.tir)
    }

    @Test
    fun semPrecoDoIndiceNaoCompara() {
        val mov = gama.mapIndexed { i, m -> if (i == 1) m.copy(precoIndiceEur = null) else m }
        val r = historicoGama(mov).de("GAMA")!!
        assertNull(r.espelho)
        assertNull(r.diferencaIndiceEur)
        assertEquals(1, r.semPrecoIndice)
    }

    @Test
    fun pontePorAtualizarNaoTemIndiceNemTir() {
        // Sem preço de hoje do VUAA nem dividendos por mês (ponte v4): fica tudo como antes.
        val h = Historico.calcular(gama, hojeGama, mapOf("GAMA" to 20.0), usdEur = 0.95, dia = dia)
        val r = h.de("GAMA")!!
        assertFalse(h.temIndice)
        assertNull(r.espelho)
        assertEquals(0, r.semPrecoIndice)
        assertNull(r.tir)
        assertEquals(19.0, r.dividendosEur, 1e-9)          // 20 $ ao câmbio de hoje
    }

    @Test
    fun simularVendaPeloFifoComImposto() {
        val h = historicoGama()
        val r = h.de("GAMA")!!
        // Sobram 4 do 1.º lote (90,10 € cada) e 5 do 2.º (120 € cada).
        assertEquals(listOf(4.0, 5.0), r.lotes.map { it.unidades })
        val s = h.simularVenda(r, 5.0, dia)!!
        assertEquals(750.0, s.recebidoEur, 1e-9)           // 5 × 1350 / 9
        assertEquals(480.4, s.custoEur!!, 1e-9)            // 4 × 90,10 + 1 × 120
        assertEquals(269.6, s.maisValiaEur!!, 1e-9)
        assertEquals(0.0, s.saldoAnoAntesEur, 1e-9)        // ainda não vendeste nada em 2026
        assertEquals(0.28 * 269.6, s.impostoEur!!, 1e-9)
        assertEquals(750.0 - 0.28 * 269.6, s.liquidoEur, 1e-9)
        assertEquals(0.0, s.unidadesMenosDeUmAno, 1e-12)
        assertEquals(4.0, s.unidadesDepois, 1e-12)
        // Mais do que tens: vende só as 9.
        assertEquals(9.0, h.simularVenda(r, 50.0, dia)!!.unidades, 1e-12)
        assertNull(h.simularVenda(r, 0.0, dia))
    }

    @Test
    fun tirDaCarteiraToda() {
        // GAMA e um ETF (DELTA) comprado a 100 € e vendido a 50 €; os dividendos vêm dos totais de cada mês
        // (também os que não estão por empresa), ao câmbio desse mês (0,90 em jun/2024; os outros ao de hoje, 0,95).
        val delta = listOf(
            etf(10, "2025-06-01", "Compra", "DELTA", 4.0, 100.0, 0.0).copy(precoIndiceEur = 125.0),
            etf(11, "2026-01-01", "Venda", "DELTA", 4.0, 50.0, 0.0).copy(precoIndiceEur = 128.0),
        )
        val h = Historico.calcular(
            gama + delta, hojeGama, emptyMap(), usdEur = 0.95, indiceHojeEur = 130.0, dividendosPorMes = divGama,
            cambioMensal = mapOf("2024-06" to 0.90), dia = dia,
            dividendosMensaisUsd = mapOf("2023-11" to 5.0, "2024-06" to 10.0, "2025-06" to 10.0),
        )
        val c = h.carteira!!
        assertEquals(LocalDate.parse("2023-01-02"), c.desde)
        assertEquals(1350.0, c.valorEur, 1e-9)
        assertEquals(5 * 0.95 + 9.0 + 9.5, c.dividendosEur, 1e-9)
        // Valores do scipy (brentq) para os mesmos fluxos.
        assertEquals(0.1530030442460011, c.tir!!, 1e-9)
        // O mesmo dinheiro no VUAA: 10,1375 unidades × 130 € = 1317,88 €.
        assertEquals(1317.875, c.indiceEur!!, 1e-9)
        assertEquals(0.13888799391676532, c.tirIndice!!, 1e-9)
        // Sem os totais de cada mês (ponte v4) não há TIR da carteira.
        assertNull(historicoGama().carteira)
        // Uma transação sem o preço do índice: há TIR da carteira, mas não a do VUAA.
        val semPreco = Historico.calcular(
            gama + delta.map { it.copy(precoIndiceEur = null) }, hojeGama, emptyMap(), usdEur = 0.95, indiceHojeEur = 130.0,
            dividendosPorMes = divGama, dia = dia, dividendosMensaisUsd = emptyMap(),
        ).carteira!!
        assertNotNull(semPreco.tir)
        assertNull(semPreco.indiceEur)
        assertNull(semPreco.tirIndice)
    }

    @Test
    fun dividendosDeAntesDaPrimeiraCompraFicamDeForaDaTir() {
        // Um dividendo de nov/2022, antes da 1.ª compra (jan/2023), é de uma transação que falta na folha: entrava
        // dinheiro antes de algum sair e havia duas TIR. Fica de fora (na carteira e na posição); o resto não muda.
        val c = Historico.calcular(
            gama, hojeGama, emptyMap(), usdEur = 0.95, indiceHojeEur = 130.0, dividendosPorMes = divGama,
            cambioMensal = mapOf("2024-06" to 0.90), dia = dia,
            dividendosMensaisUsd = mapOf("2022-11" to 50.0, "2023-11" to 5.0),
        )
        val sem = Historico.calcular(
            gama, hojeGama, emptyMap(), usdEur = 0.95, indiceHojeEur = 130.0, dividendosPorMes = divGama,
            cambioMensal = mapOf("2024-06" to 0.90), dia = dia,
            dividendosMensaisUsd = mapOf("2023-11" to 5.0),
        )
        assertEquals(5 * 0.95, c.carteira!!.dividendosEur, 1e-9)
        assertEquals(sem.carteira!!.tir!!, c.carteira!!.tir!!, 1e-12)
        val divCedo = mapOf("GAMA" to listOf("2022-12" to 30.0) + divGama.getValue("GAMA"))
        val p = Historico.calcular(
            gama, hojeGama, emptyMap(), usdEur = 0.95, indiceHojeEur = 130.0, dividendosPorMes = divCedo,
            cambioMensal = mapOf("2024-06" to 0.90), dia = dia,
        ).de("GAMA")!!
        assertEquals(historicoGama().de("GAMA")!!.tir!!, p.tir!!, 1e-12)
        // Um dividendo do mesmo mês da 1.ª compra conta, no dia da compra se o dia 15 vier antes.
        assertNotNull(
            Historico.calcular(
                gama, hojeGama, emptyMap(), usdEur = 0.95, indiceHojeEur = 130.0, dividendosPorMes = divGama, dia = dia,
                dividendosMensaisUsd = mapOf("2023-01" to 5.0),
            ).carteira!!.tir,
        )
    }

    @Test
    fun menosValiaDoAnoBaixaOImposto() {
        // DELTA (ETF) vendido com 200 € de prejuízo em 2026: o saldo do ano começa em −200 €.
        val delta = listOf(
            etf(10, "2025-06-01", "Compra", "DELTA", 4.0, 100.0, 0.0),
            etf(11, "2026-01-01", "Venda", "DELTA", 4.0, 50.0, 0.0),
        )
        val h = historicoGama(gama + delta)
        val s = h.simularVenda(h.de("GAMA")!!, 5.0, dia)!!
        assertEquals(-200.0, s.saldoAnoAntesEur, 1e-9)
        assertEquals(69.6, s.saldoAnoDepoisEur, 1e-9)
        assertEquals(0.28 * 69.6, s.impostoEur!!, 1e-9)    // só paga sobre o que passa de zero
        // Uma compra recente: as ações dela têm menos de um ano.
        val recente = gama + comIndice(4, "2025-09-01", "Compra", "GAMA", 2.0, 100.0, 1.0, 0.0, 125.0)
        val h2 = Historico.calcular(
            recente, mapOf("GAMA" to ValorHoje(11.0, 1650.0, 1650.0)), emptyMap(), usdEur = 0.95,
            indiceHojeEur = 130.0, dividendosPorMes = divGama, dia = dia,
        )
        val s2 = h2.simularVenda(h2.de("GAMA")!!, 11.0, dia)!!
        assertEquals(2.0, s2.unidadesMenosDeUmAno, 1e-12)
        // Uma venda com prejuízo quando o ano já tem mais-valias: o imposto do ano baixa.
        val h3 = Historico.calcular(
            gama, mapOf("GAMA" to ValorHoje(9.0, 450.0, 450.0)), emptyMap(), usdEur = 0.95,
            indiceHojeEur = 130.0, dividendosPorMes = divGama, dia = LocalDate.parse("2025-03-01"),
        )
        val s3 = h3.simularVenda(h3.de("GAMA")!!, 5.0, LocalDate.parse("2025-03-01"))!!
        // Em 2025 já vendeste 6 GAMA com 899 − 540,60 = 358,40 € de mais-valia.
        assertEquals(358.4, s3.saldoAnoAntesEur, 1e-9)
        assertEquals(250.0 - 480.4, s3.maisValiaEur!!, 1e-9)
        assertEquals(0.28 * (358.4 - 230.4) - 0.28 * 358.4, s3.impostoEur!!, 1e-9)
    }
}
