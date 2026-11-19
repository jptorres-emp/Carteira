package app.carteira

import app.carteira.dados.ErroPonte
import app.carteira.dados.Leitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** A amostra tem a forma exata da resposta da ponte, mas é toda inventada (empresas, números e notas). */
class LeitorTest {

    private fun amostra(): String =
        javaClass.classLoader!!.getResourceAsStream("amostra.json")!!.bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test
    fun leTodasAsSeccoes() {
        val d = Leitor.lerResposta(amostra())
        assertEquals("Carteira de teste", d.meta.folha)
        assertEquals(0.865, d.cambio.usdEur!!, 1e-9)
        assertEquals(listOf("ALFA", "CAST"), d.posicoes.map { it.ticker })
        assertEquals(listOf("MUND"), d.etfs.map { it.ticker })
        assertEquals(2, d.watchlist.size)
        assertEquals(5, d.regras.size)
        assertEquals(1, d.regras.count { it.alerta })
        assertNotNull(d.indicador("VALOR TOTAL (EUR)"))
        assertNotNull(d.totaisAcoes?.valorEur)
        assertEquals(12, d.dividendos.calendario.size)
        assertEquals("Janeiro", d.dividendos.calendario.first().mes)
        assertEquals(2027, d.dividendos.previsaoAno)
        assertNotNull(d.dividendos.indicador("LÍQUIDO 2027 (€)"))
        assertEquals(listOf(2022, 2023, 2024, 2025, 2026), d.dividendos.anos.map { it.ano })
        assertEquals(12, d.dividendos.anos.last().meses.size)
        assertEquals(2, d.dividendos.esteAno!!.empresas.size)
        assertEquals(2, d.transacoes.size)
    }

    @Test
    fun posicaoTemAvaliacaoEZona() {
        val p = Leitor.lerResposta(amostra()).posicoes.first()
        assertTrue(p.quantidade > 0)
        assertNotNull(p.zona)
        assertNotNull(p.avaliacao)
        assertEquals("USD", p.avaliacao!!.moeda)
        assertTrue(p.avaliacao.temaIa)
        // "—" na folha chega a null e não rebenta a leitura.
        assertNull(p.ajusteSugeridoEur)
    }

    @Test
    fun erroDaPonteViraMensagem() {
        try {
            Leitor.lerResposta("""{"ok":false,"erro":"Chave inválida. Confirma a chave nas Definições da app."}""")
            fail("devia ter lançado um erro")
        } catch (e: ErroPonte) {
            assertTrue(e.message!!.startsWith("Chave inválida"))
        }
    }

    @Test
    fun paginaHtmlDaGoogleViraConselho() {
        try {
            Leitor.lerResposta("<!DOCTYPE html><html><body>Iniciar sessão</body></html>")
            fail("devia ter lançado um erro")
        } catch (e: ErroPonte) {
            assertTrue(e.message!!.contains("Qualquer pessoa"))
        }
    }

    @Test
    fun registoOkEErro() {
        val ok = Leitor.lerRegisto("""{"ok":true,"mensagem":"Compra registada.","aviso":null,"linha":262}""")
        assertTrue(ok.ok)
        assertEquals("Compra registada.", ok.mensagem)
        assertNull(ok.aviso)
        val erro = Leitor.lerRegisto("""{"ok":false,"erro":"Só tens 3 de BORE"}""")
        assertFalse(erro.ok)
        assertEquals("Só tens 3 de BORE", erro.mensagem)
    }

    @Test
    fun leAsSeccoesDaPonteV2() {
        val d = Leitor.lerResposta(amostra())
        assertEquals(5, d.versaoPonte)
        assertTrue(d.meta.avisos.isEmpty())
        assertEquals("2022-09", d.evolucao.first().mes)
        assertEquals("2026-11", d.evolucao.last().mes)
        assertTrue(d.evolucao.all { it.carteiraUsd > 0 && it.investidoUsd != null && it.sp500Usd != null })
        assertEquals(3, d.objetivos.size)
        assertEquals("Acima do plano", d.objetivos.last().estado)
        assertEquals(5, d.patrimonio!!.classes.size)
        assertTrue(d.comparacaoPrincipal!!.principal)
        assertTrue(d.comparacaoPrincipal!!.diferencaPct!! < 0)
        assertTrue(d.setores.any { it.acima })
        assertTrue(d.watchlist.all { it.testeTroca != null })
    }

    @Test
    fun leOPlanoDaPonteV3() {
        val d = Leitor.lerResposta(amostra())
        val p = d.plano!!
        assertEquals(400.0, p.reforcoMensalEur!!, 1e-9)
        assertEquals(0.05, p.taxaReferencia!!, 1e-9)
        assertEquals(0.065, p.taxaBase!!, 1e-9)
        assertEquals(0.08, p.taxaAlargada!!, 1e-9)
        // Todos os meses da tabela, de set/22 a dez/30, também os que ainda não chegaram.
        assertEquals(100, p.meses.size)
        assertEquals("2022-09", p.meses.first().mes)
        assertEquals("2030-12", p.meses.last().mes)
        assertTrue(p.meses.all { it.referenciaEur!! <= it.baseEur!! && it.baseEur!! <= it.alargadaEur!! && it.historicaEur != null })
        // A evolução traz as três metas de cada mês passado, iguais às do plano.
        assertTrue(d.evolucao.all { it.metaReferenciaEur != null && it.metaBaseEur != null && it.metaAlargadaEur != null })
        assertEquals(p.meses[10].baseEur!!, d.evolucao[10].metaBaseEur!!, 1e-9)
        assertEquals(p.meses[10].alargadaEur!!, d.evolucao[10].metaAlargadaEur!!, 1e-9)
        // O que está gravado na folha: os meses fechados sim, o corrente ainda não.
        assertEquals(d.evolucao[10].netWorthEur!!, d.evolucao[10].netWorthFolhaEur!!, 1e-9)
        assertNull(d.evolucao.last().netWorthFolhaEur)
        assertNotNull(d.evolucao.last().netWorthEur)
    }

    @Test
    fun leAsVendasDaPonteV4() {
        val d = Leitor.lerResposta(amostra())
        assertEquals(11, d.movimentos.size)
        val m = d.movimentos.first()
        assertEquals("ALFA", m.ticker)
        assertEquals("Compra", m.tipo)
        assertEquals(6.0, m.unidades, 1e-9)
        assertEquals(0.93, m.cambio!!, 1e-9)
        assertEquals(669.6, m.valorEur, 1e-9)
        assertEquals(4, d.dividendosPorEmpresa!!.periodos.size)
        assertEquals(2, d.analiseVendas.size)
        val h = d.historico
        assertTrue(h.temDados)
        assertEquals(listOf("ZETA"), h.fechadas.map { it.ticker })
        assertEquals("mai/2023", h.dividendosDesde)
        assertTrue(h.vendas.all { it.analise != null && it.custoEur != null })
        assertEquals("Alfa Software Inc", h.de("ALFA")!!.nome)
        assertTrue(h.de("ALFA")!!.aberta)
    }

    @Test
    fun lePrecosDoIndiceEDividendosPorMesDaPonteV5() {
        val d = Leitor.lerResposta(amostra())
        assertTrue(d.movimentos.all { it.precoIndiceEur != null && it.precoIndiceEur!! > 0 && it.precoSpy != null })
        val i = d.indice!!
        assertEquals("2026-11-16", i.data)
        assertEquals(130.0582, i.vuaaEur, 1e-9)
        assertEquals(32, d.dividendosPorMes.size)
        assertEquals(60, d.cambioMensal.size)
        assertTrue(d.cambioMensal.keys.all { Regex("\\d{4}-\\d{2}").matches(it) })
        // Os dividendos por mês somam o mesmo que os totais por empresa.
        d.dividendosPorEmpresa!!.empresas.forEach { e ->
            assertEquals(e.totalUsd, d.dividendosPorMes.filter { it.ticker == e.ticker }.sumOf { it.usd }, 0.011)
        }
        val h = d.historico
        assertTrue(h.v5 && h.temIndice)
        assertTrue(h.porTicker.values.all { it.espelho != null && it.semPrecoIndice == 0 })
        // A diferença para o índice é o que a ação vale hoje + dividendos − o que o VUAA valeria.
        val alfa = h.de("ALFA")!!
        assertEquals(alfa.valorEur + alfa.dividendosEur - alfa.espelho!!.valorEur, alfa.diferencaIndiceEur!!, 1e-9)
    }

    @Test
    fun ponteV4NaoTemIndice() {
        val v4 = org.json.JSONObject(amostra()).apply {
            put("versaoPonte", 4)
            getJSONObject("dados").apply {
                remove("indice")
                remove("dividendosPorMes")
                remove("cambioMensal")
                val mv = getJSONArray("movimentos")
                for (k in 0 until mv.length()) mv.getJSONObject(k).apply { remove("precoIndiceEur"); remove("precoSpy") }
            }
        }
        val d = Leitor.lerResposta(v4.toString())
        assertNull(d.indice)
        assertTrue(d.dividendosPorMes.isEmpty() && d.cambioMensal.isEmpty())
        assertTrue(d.movimentos.all { it.precoIndiceEur == null })
        val h = d.historico
        assertTrue(h.temDados)
        assertFalse(h.temIndice || h.v5)
        assertTrue(h.porTicker.values.all { it.espelho == null && it.tir == null && it.semPrecoIndice == 0 })
    }

    @Test
    fun ponteV3NaoTemVendas() {
        val v3 = org.json.JSONObject(amostra()).apply {
            put("versaoPonte", 3)
            getJSONObject("dados").apply {
                remove("movimentos")
                remove("dividendosPorEmpresa")
                remove("analiseVendas")
            }
        }
        val d = Leitor.lerResposta(v3.toString())
        assertTrue(d.movimentos.isEmpty())
        assertNull(d.dividendosPorEmpresa)
        assertFalse(d.historico.temDados)
    }

    @Test
    fun ponteV2NaoTemPlano() {
        val v2 = org.json.JSONObject(amostra()).apply {
            put("versaoPonte", 2)
            val d = getJSONObject("dados")
            d.remove("plano")
            val evolucao = d.getJSONArray("evolucao")
            for (k in 0 until evolucao.length()) {
                evolucao.getJSONObject(k).apply {
                    remove("metaReferenciaEur")
                    remove("metaAlargadaEur")
                }
            }
        }.toString()
        val d = Leitor.lerResposta(v2)
        assertEquals(2, d.versaoPonte)
        assertNull(d.plano)
        assertTrue(d.evolucao.all { it.metaReferenciaEur == null && it.metaAlargadaEur == null && it.metaBaseEur != null })
        // Um plano sem meses é o mesmo que não ter plano.
        val vazio = org.json.JSONObject(amostra()).apply {
            getJSONObject("dados").put("plano", org.json.JSONObject().put("reforcoMensalEur", 400).put("meses", org.json.JSONArray()))
        }.toString()
        assertNull(Leitor.lerResposta(vazio).plano)
    }

    @Test
    fun pontesAntigasContinuamALer() {
        // Uma resposta da ponte v1 não tem as secções novas: ficam vazias, sem erro.
        val v1 = org.json.JSONObject(amostra()).apply {
            put("versaoPonte", 1)
            val d = getJSONObject("dados")
            listOf("evolucao", "plano", "objetivos", "patrimonio", "comparacoes", "setores").forEach { d.remove(it) }
        }.toString()
        val d = Leitor.lerResposta(v1)
        assertEquals(1, d.versaoPonte)
        assertTrue(d.evolucao.isEmpty() && d.objetivos.isEmpty() && d.setores.isEmpty())
        assertNull(d.plano)
        assertNull(d.patrimonio)
        assertNull(d.comparacaoPrincipal)
    }

    @Test
    fun mercadoParaOsAlertas() {
        // Dos dados completos (ponte v1)...
        val deDados = Leitor.lerMercado(amostra())
        assertTrue(deDados.cotacoes.any { it.chave == "P:ALFA" && it.sinal != null })
        assertTrue(deDados.cotacoes.any { it.chave == "E:MUND" && it.moeda == "EUR" })
        assertTrue(deDados.cotacoes.any { it.chave.startsWith("W:") && it.valorJusto != null })
        // ... e da resposta leve da ponte v2.
        val leve = """{"ok":true,"versaoPonte":2,"alertas":{"meta":{"hoje":"2026-11-16","atualizadoEm":"x"},
            "posicoes":[{"ticker":"ALFA","empresa":"Alfa","camada":"Core A","precoUsd":268.15,"varDiaPct":0.01,"zona":"⚪ Manter","sinal":"⚪ Manter (core)"}],
            "etfs":[{"ticker":"MUND","nome":"Mundo","precoEur":131.25,"varDiaPct":-0.001}],
            "watchlist":[{"tabela":"POTENCIAL CORE A","ticker":"KAPA","empresa":"Kapa","preco":152.3,"valorJusto":175,"margem":0.149,"sinal":"🟡 Perto do justo","testeTroca":"⏳"}]}}"""
        val m = Leitor.lerMercado(leve)
        assertEquals("2026-11-16", m.hoje)
        assertEquals(listOf("P:ALFA", "E:MUND", "W:KAPA"), m.cotacoes.map { it.chave })
    }

    @Test
    fun ping() {
        assertEquals("Portefolio", Leitor.lerPing("""{"ok":true,"versaoPonte":1,"folha":"Portefolio"}"""))
    }
}
