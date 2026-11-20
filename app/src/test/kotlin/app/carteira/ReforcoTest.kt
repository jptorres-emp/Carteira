package app.carteira

import app.carteira.dados.Cambio
import app.carteira.dados.Dados
import app.carteira.dados.Dividendos
import app.carteira.dados.Etf
import app.carteira.dados.Meta
import app.carteira.dados.MesReforco
import app.carteira.dados.Posicao
import app.carteira.dados.Reforco
import app.carteira.dados.Regra
import app.carteira.dados.RegistoReforco
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** O reforço do mês pela regra 10 da folha, com uma carteira inventada. */
class ReforcoTest {

    private fun posicao(ticker: String, camada: String, sinal: String, ajuste: Double?, precoUsd: Double, peso: Double, alvo: Double) = Posicao(
        ticker = ticker, empresa = "$ticker Inc", setor = null, camada = camada, quantidade = 10.0, precoCompraUsd = null,
        precoUsd = precoUsd, varDiaPct = null, pe = null, custoUsd = null, valorUsd = null, ganhoUsd = null, ganhoPct = null,
        pesoAcoes = null, divAnualPorAcaoUsd = null, yieldPct = null, yieldCustoPct = null, frequencia = null, risco = null,
        rating = null, custoEur = null, valorEur = precoUsd * 10 * 0.92, ganhoEur = null, pesoTotal = peso, pesoAlvo = alvo,
        pesoMaximo = alvo * 1.25, zona = null, sinal = sinal, ajusteSugeridoEur = ajuste, avaliacao = null,
    )

    private fun etf(ticker: String, nome: String, preco: Double, valor: Double) =
        Etf(ticker, nome, valor / preco, null, preco, null, null, valor, null, null, null)

    private fun dados(posicoes: List<Posicao>, etfs: List<Etf>, pesoAcoes: String? = "0,7924", limite: String = "0,8") = Dados(
        meta = Meta("Teste", "2026-11-20T10:00:00", "2026-11-20"),
        cambio = Cambio(0.92, null),
        totaisAcoes = null, totaisEtfs = null, camadas = emptyList(), indicadores = emptyList(),
        regras = listOfNotNull(
            Regra("Nº de posições contadas", "27", "20–27", "✓", null),
            pesoAcoes?.let { Regra("Peso das ações na carteira", it, limite, "✓", "Alvo = soma dos alvos") },
        ),
        posicoes = posicoes, etfs = etfs, tickers = emptyList(),
        dividendos = Dividendos(emptyList(), null, emptyList(), emptyList(), null),
        watchlist = emptyList(), transacoes = emptyList(),
    )

    private val carteira = listOf(
        posicao("AAA", "Core A", "🟢 Comprar até ao alvo", 847.0, 145.0, 0.03, 0.05),
        posicao("BBB", "Core B", "🟢🟢 Reforçar forte até ao máx.", 498.0, 330.0, 0.026, 0.03),
        // Satélite em 🟢🟢: pela regra 10, o dinheiro novo não vai para satélites.
        posicao("CCC", "Satélite", "🟢🟢 Reforçar forte", 300.0, 50.0, 0.01, 0.02),
        posicao("DDD", "Core A", "⚪ Manter (core)", null, 100.0, 0.05, 0.05),
        // Em 🟢 mas já acima do alvo: a regra 10 só põe dinheiro novo em core abaixo do alvo.
        posicao("EEE", "Core A", "🟢 Reforçar", 200.0, 80.0, 0.06, 0.05),
    )
    private val etfs = listOf(etf("EMOB", "Emergentes Obrigações em Dólares", 90.0, 3000.0), etf("GLOB", "Global Ações Acumulação", 130.0, 3100.0))

    @Test
    fun primeiroOsCoreEm2VerdesDepoisOsOutrosEOResto() {
        val p = Reforco.planear(dados(carteira, etfs), 800.0, fracoes = false)
        assertFalse(p.soEtf)
        assertEquals(listOf("BBB", "AAA"), p.compras.map { it.ticker })
        // BBB: 1 ação de 330 $ × 0,92 = 303,60 €; AAA: 3 de 145 $ × 0,92 = 133,40 € (400,20 €).
        assertEquals(1.0, p.compras[0].unidades, 0.0)
        assertEquals(303.6, p.compras[0].custoEur, 1e-9)
        assertEquals(3.0, p.compras[1].unidades, 0.0)
        assertEquals(400.2, p.compras[1].custoEur, 1e-9)
        assertEquals("USD", p.compras[1].moeda)
        assertEquals(145.0, p.compras[1].preco, 0.0)
        assertEquals("Core A · Comprar até ao alvo", p.compras[1].motivo)
        // Sobram 96,20 €: não chega para um GLOB (130 €).
        assertEquals(96.2, p.sobraEur, 1e-9)
        assertEquals("GLOB", p.etfGlobal)
        assertEquals(800.0, p.investidoEur + p.sobraEur, 1e-9)
    }

    @Test
    fun comFracoesUsaTudo() {
        val p = Reforco.planear(dados(carteira, etfs), 800.0, fracoes = true)
        assertEquals(listOf("BBB", "AAA"), p.compras.map { it.ticker })
        assertEquals(1.6403, p.compras[0].unidades, 0.0)   // 498 € / 303,60 €, com 4 casas para baixo
        assertTrue(p.compras[0].custoEur <= 498.0)
        assertTrue(p.sobraEur < 0.05)
    }

    @Test
    fun maisDinheiroQueOsAjustesVaiParaOEtf() {
        val p = Reforco.planear(dados(carteira, etfs), 2000.0, fracoes = false)
        // BBB até 498 € (1 ação), AAA até 847 € (6 ações = 800,40 €), o resto (896 €) no GLOB: 6 × 130 €.
        assertEquals(listOf("BBB", "AAA", "GLOB"), p.compras.map { it.ticker })
        assertEquals(6.0, p.compras[1].unidades, 0.0)
        assertEquals(6.0, p.compras[2].unidades, 0.0)
        assertEquals("EUR", p.compras[2].moeda)
        assertEquals(2000.0 - 303.6 - 800.4 - 780.0, p.sobraEur, 1e-9)
    }

    @Test
    fun acoesAcimaDoAlvoVaiTudoParaOEtf() {
        val p = Reforco.planear(dados(carteira, etfs, pesoAcoes = "0,8231"), 800.0, fracoes = false)
        assertTrue(p.soEtf)
        assertEquals(0.8231, p.pesoAcoes!!, 1e-12)
        assertEquals(0.8, p.limiteAcoes!!, 1e-12)
        assertEquals(listOf("GLOB"), p.compras.map { it.ticker })
        assertEquals(6.0, p.compras[0].unidades, 0.0)
        assertEquals(20.0, p.sobraEur, 1e-9)
    }

    @Test
    fun ficaDeForaQuemNaoChegaAUmaAcao() {
        val pouco = listOf(posicao("BBB", "Core B", "🟢🟢 Reforçar forte até ao máx.", 100.0, 330.0, 0.026, 0.03))
        val p = Reforco.planear(dados(pouco, etfs), 800.0, fracoes = false)
        assertEquals(listOf("GLOB"), p.compras.map { it.ticker })
        val f = p.deFora.single()
        assertEquals("BBB", f.ticker)
        assertFalse(f.faltouDinheiro)   // a folha só deixa 100 €, menos do que uma ação
        // Com pouco dinheiro, falta dinheiro.
        val q = Reforco.planear(dados(carteira, etfs), 120.0, fracoes = false)
        assertTrue(q.compras.isEmpty())
        assertTrue(q.deFora.all { it.faltouDinheiro })
        assertEquals(120.0, q.sobraEur, 1e-9)
    }

    @Test
    fun escolheOEtfGlobal() {
        val outros = listOf(etf("OBRG", "Obrigações do Tesouro 7-10", 96.8, 5000.0), etf("MUND", "Mundo Ações Acumulação", 131.25, 3281.25))
        val d = dados(carteira, outros)
        assertEquals("MUND", Reforco.etfGlobal(d)?.ticker)
        assertEquals("OBRG", Reforco.etfGlobal(d, "OBRG")?.ticker)
        assertEquals("GLOB", Reforco.etfGlobal(dados(carteira, etfs), "NADA")?.ticker)
        // Um ETF de um setor não é global, mesmo com "World" no nome e mais valor.
        val comSetor = etfs + etf("TECW", "World Information Technology", 50.0, 9000.0)
        assertEquals("GLOB", Reforco.etfGlobal(dados(carteira, comSetor))?.ticker)
        // Sem nenhum global pelo nome, fica o ETF de ações com mais valor.
        val semGlobal = listOf(etf("INDI", "Índia Acumulação", 8.0, 250.0), etf("SPTI", "S&P 500 Tecnologia", 30.0, 900.0), outros[0])
        assertEquals("SPTI", Reforco.etfGlobal(dados(carteira, semGlobal))?.ticker)
        assertNull(Reforco.etfGlobal(dados(carteira, listOf(etf("OBRG", "Obrigações do Tesouro", 96.8, 500.0)))))
    }

    @Test
    fun semARegraDoPesoDasAcoesSegueOsCore() {
        val p = Reforco.planear(dados(carteira, etfs, pesoAcoes = null), 800.0, fracoes = false)
        assertFalse(p.soEtf)
        assertNull(p.pesoAcoes)
        assertEquals(listOf("BBB", "AAA"), p.compras.map { it.ticker })
    }

    @Test
    fun numerosDaFolha() {
        assertEquals(0.7924, Reforco.numero("0,7924")!!, 1e-12)
        assertEquals(0.701, Reforco.numero("70,1%")!!, 1e-12)
        assertEquals(0.8, Reforco.numero("≤ 80%")!!, 1e-12)
        assertEquals(1234.5, Reforco.numero("1234,5")!!, 1e-12)
        assertNull(Reforco.numero("—"))
        assertNull(Reforco.numero(null))
    }

    @Test
    fun compraRegistadaNaoVoltaASerSugerida() {
        // Registaste 1 BBB (303,60 €) e a app ainda tem a quantidade de antes (10): sai do Ajuste sugerido do BBB.
        val d = dados(carteira, etfs)
        val registados = listOf(RegistoReforco("BBB", 1.0, 303.6, 10.0))
        val porRefletir = Reforco.porRefletir(d, registados)
        assertEquals(setOf("BBB"), porRefletir.keys)
        assertEquals(303.6, porRefletir.getValue("BBB"), 1e-9)
        val p = Reforco.planear(d, 800.0 - 303.6, fracoes = false, jaComprado = porRefletir)
        // Ao BBB só restavam 194,40 €: menos do que uma ação, por isso o dinheiro vai para o AAA.
        assertEquals(listOf("AAA"), p.compras.map { it.ticker })
        assertEquals(3.0, p.compras[0].unidades, 0.0)
        assertEquals(96.2, p.sobraEur, 1e-9)

        // Quando a folha já mostra a compra (11 BBB), não há nada a descontar.
        val depois = dados(carteira.map { if (it.ticker == "BBB") it.copy(quantidade = 11.0, ajusteSugeridoEur = 194.4) else it }, etfs)
        assertTrue(Reforco.porRefletir(depois, registados).isEmpty())
        // E a meio caminho (por exemplo, só parte das unidades), desconta-se a parte que falta.
        val meio = dados(carteira.map { if (it.ticker == "BBB") it.copy(quantidade = 10.5) else it }, etfs)
        assertEquals(151.8, Reforco.porRefletir(meio, registados).getValue("BBB"), 1e-9)
    }

    @Test
    fun oReforcoDoMesGuardaSeEVoltaIgual() {
        val r = MesReforco("2026-11", 1500.0, listOf(RegistoReforco("BBB", 1.0, 303.6, 10.0), RegistoReforco("GLOB", 2.5, 325.0, 23.8)))
        assertEquals(r, MesReforco.deTexto(r.paraTexto()))
        assertEquals(628.6, r.registadoEur, 1e-9)
        assertEquals(MesReforco("2026-12"), MesReforco.deTexto(MesReforco("2026-12").paraTexto()))
        assertNull(MesReforco.deTexto(""))
        assertNull(MesReforco.deTexto("lixo"))
        assertNull(MesReforco.deTexto(null))
        // Um registo estragado fica de fora; o resto lê-se.
        assertEquals(listOf("AAA"), MesReforco.deTexto("2026-11|800.0|AAA;1.0;100.0;3.0|BBB;x;1;2")?.registados?.map { it.ticker })
    }
}
