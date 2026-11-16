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
    fun ping() {
        assertEquals("Portefolio", Leitor.lerPing("""{"ok":true,"versaoPonte":1,"folha":"Portefolio"}"""))
    }
}
