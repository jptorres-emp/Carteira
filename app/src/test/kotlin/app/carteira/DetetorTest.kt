package app.carteira

import app.carteira.alertas.ArquivoAlertas
import app.carteira.alertas.Detetor
import app.carteira.alertas.EstadoAlertas
import app.carteira.alertas.PreferenciasAlertas
import app.carteira.alertas.TipoAlerta
import app.carteira.dados.Cotacao
import app.carteira.dados.Mercado
import app.carteira.dados.TipoCotacao
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** As decisões dos alertas: o que avisa, o que não avisa e quando não repete. */
class DetetorTest {

    private val prefs = PreferenciasAlertas()
    private val segunda = LocalDateTime.of(2026, 11, 16, 15, 0)

    private fun posicao(ticker: String, sinal: String, varDia: Double? = 0.0, preco: Double = 100.0) =
        Cotacao(TipoCotacao.POSICAO, ticker, "Empresa $ticker", preco, "USD", varDia, "⚪ Manter", sinal, "Core A")

    private fun watch(ticker: String, sinal: String, preco: Double = 50.0) =
        Cotacao(TipoCotacao.WATCHLIST, ticker, "Empresa $ticker", preco, null, null, null, sinal, "POTENCIAL CORE A", 60.0, 0.2, "✓ Avaliar troca")

    private fun mercado(dia: String, vararg c: Cotacao) = Mercado(dia, dia + "T15:00:00Z", c.toList())

    /** Corre várias verificações seguidas e devolve os alertas de cada uma. */
    private fun correr(vararg passos: Pair<LocalDateTime, Mercado>, p: PreferenciasAlertas = prefs): List<List<app.carteira.alertas.Alerta>> {
        var estado: EstadoAlertas? = null
        return passos.map { (quando, m) ->
            val (novos, depois) = Detetor.comparar(estado, m, p, quando)
            // Passa pelo JSON, como na app, para testar também o que fica guardado.
            estado = ArquivoAlertas.estadoDeJson(ArquivoAlertas.estadoParaJson(depois))
            novos
        }
    }

    @Test
    fun primeiraVerificacaoSoRegista() {
        val r = correr(segunda to mercado("2026-11-16", posicao("ALFA", "🟢🟢 Reforçar forte até ao máx.", varDia = -0.09)))
        assertTrue(r[0].isEmpty())
    }

    @Test
    fun sinalQuePassaACompraAvisa() {
        val r = correr(
            segunda to mercado("2026-11-16", posicao("ALFA", "⚪ Manter (core)")),
            segunda.plusHours(2) to mercado("2026-11-16", posicao("ALFA", "🟢 Reforçar até ao alvo")),
            segunda.plusHours(4) to mercado("2026-11-16", posicao("ALFA", "🟢🟢 Reforçar forte até ao máx.")),
        )
        assertEquals(listOf(TipoAlerta.COMPRA), r[1].map { it.tipo })
        assertEquals("ALFA · Reforçar até ao alvo", r[1][0].titulo)
        assertEquals("P:ALFA", r[1][0].chave)
        // Passar de 🟢 a 🟢🟢 é um sinal mais forte: avisa outra vez, mesmo dentro da pausa do primeiro.
        assertEquals("ALFA · Reforçar forte até ao máx.", r[2].single().titulo)
    }

    @Test
    fun zonaVerdeSemSinalDeCompraNaoAvisa() {
        // Um satélite em 🟢 Reforçar continua "🟡 Em zona · satélite só compra em 🟢🟢": as tuas regras mandam esperar.
        val r = correr(
            segunda to mercado("2026-11-16", posicao("CAST", "⚪ Manter (satélite)")),
            segunda.plusHours(2) to mercado("2026-11-16", posicao("CAST", "🟡 Em zona · satélite só compra em 🟢🟢")),
        )
        assertTrue(r[1].isEmpty())
    }

    @Test
    fun sinalQueOscilaNaoRepeteDuranteTresDias() {
        val r = correr(
            segunda to mercado("2026-11-16", posicao("ALFA", "⚪ Manter (core)")),
            segunda.plusHours(2) to mercado("2026-11-16", posicao("ALFA", "🟢 Reforçar")),
            segunda.plusHours(4) to mercado("2026-11-16", posicao("ALFA", "⚪ Manter (core)")),
            segunda.plusDays(1) to mercado("2026-11-17", posicao("ALFA", "🟢 Reforçar")),
            segunda.plusDays(4) to mercado("2026-11-20", posicao("ALFA", "⚪ Manter (core)")),
            segunda.plusDays(4).plusHours(2) to mercado("2026-11-20", posicao("ALFA", "🟢 Reforçar")),
        )
        assertEquals(1, r[1].size)
        assertTrue(r[3].isEmpty())
        assertEquals(1, r[5].size)
    }

    @Test
    fun watchlistQueFicaSubvalorizadaAvisaComOTesteDeTroca() {
        val r = correr(
            segunda to mercado("2026-11-16", watch("KAPA", "🟡 Perto do justo")),
            segunda.plusHours(2) to mercado("2026-11-16", watch("KAPA", "🟢 Subvalorizada")),
        )
        assertEquals(TipoAlerta.WATCHLIST, r[1].single().tipo)
        assertEquals("W:KAPA", r[1].single().chave)
        assertTrue(r[1].single().texto.contains("✓ Avaliar troca"))
    }

    @Test
    fun variacaoGrandeAvisaUmaVezEDepoisNoDobro() {
        val r = correr(
            segunda to mercado("2026-11-16", posicao("JADE", "⚪ Manter (core)", varDia = -0.01)),
            segunda.plusHours(2) to mercado("2026-11-16", posicao("JADE", "⚪ Manter (core)", varDia = -0.062)),
            segunda.plusHours(4) to mercado("2026-11-16", posicao("JADE", "⚪ Manter (core)", varDia = -0.07)),
            segunda.plusHours(6) to mercado("2026-11-16", posicao("JADE", "⚪ Manter (core)", varDia = -0.11)),
            segunda.plusDays(1) to mercado("2026-11-17", posicao("JADE", "⚪ Manter (core)", varDia = 0.055)),
        )
        assertEquals("JADE −6,2% hoje", r[1].single().titulo)
        assertTrue(r[2].isEmpty())
        assertEquals("JADE −11,0% hoje", r[3].single().titulo)
        assertTrue(r[4].single().positivo)
    }

    @Test
    fun watchlistUsaOUltimoPrecoDoDiaAnterior() {
        val r = correr(
            segunda.withHour(21) to mercado("2026-11-16", watch("KAPA", "🟡 Perto do justo", preco = 50.0)),
            segunda.plusDays(1).withHour(10) to mercado("2026-11-17", watch("KAPA", "🟡 Perto do justo", preco = 52.0)),
            segunda.plusDays(1).withHour(15) to mercado("2026-11-17", watch("KAPA", "🟡 Perto do justo", preco = 46.5)),
        )
        assertTrue(r[1].isEmpty()) // +4%: abaixo do limiar
        assertEquals("KAPA −7,0% desde 16/11", r[2].single().titulo)
    }

    @Test
    fun vendasSoAvisamSeLigadas() {
        val passos = arrayOf(
            segunda to mercado("2026-11-16", posicao("BORE", "⚪ Manter (satélite)")),
            segunda.plusHours(2) to mercado("2026-11-16", posicao("BORE", "🟠 Reduzir até ao alvo")),
        )
        assertTrue(correr(*passos)[1].isEmpty())
        assertEquals(TipoAlerta.VENDA, correr(*passos, p = prefs.copy(vendas = true))[1].single().tipo)
    }

    @Test
    fun historicoSobreviveAoJson() {
        val r = correr(
            segunda to mercado("2026-11-16", posicao("ALFA", "⚪ Manter (core)")),
            segunda.plusHours(2) to mercado("2026-11-16", posicao("ALFA", "🟢 Reforçar")),
        )
        val volta = ArquivoAlertas.historicoDeJson(ArquivoAlertas.historicoParaJson(r[1]))
        assertEquals(r[1], volta)
        assertEquals("2026-11-16T17:00", volta.single().quando)
    }
}
