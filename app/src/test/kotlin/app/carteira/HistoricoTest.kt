package app.carteira

import app.carteira.dados.AnaliseVenda
import app.carteira.dados.Historico
import app.carteira.dados.Movimento
import app.carteira.dados.ValorHoje
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun periodoDosDividendos() {
        assertEquals("mai/2023", Historico.inicioDoPeriodo("2023 (Mai-Dez)"))
        assertEquals("2024", Historico.inicioDoPeriodo("2024"))
    }
}
