package app.carteira

import app.carteira.dados.Fluxo
import app.carteira.dados.Tir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** A TIR por ano (como o XIRR do Excel), com fluxos inventados. Os valores esperados vêm do scipy. */
class TirTest {

    private fun f(data: String, valor: Double) = Fluxo(LocalDate.parse(data), valor)

    @Test
    fun dezPorCentoNumAno() {
        assertEquals(0.10, Tir.anual(listOf(f("2021-01-01", -1000.0), f("2022-01-01", 1100.0)))!!, 1e-10)
    }

    @Test
    fun variasComprasEVendas() {
        val t = Tir.anual(listOf(f("2020-01-01", -1000.0), f("2021-07-01", -500.0), f("2022-03-15", 300.0), f("2023-01-01", 1500.0)))
        assertEquals(0.07943446805473534, t!!, 1e-9)
    }

    @Test
    fun aOrdemDosFluxosNaoConta() {
        val t = Tir.anual(listOf(f("2023-01-01", 1500.0), f("2020-01-01", -1000.0), f("2022-03-15", 300.0), f("2021-07-01", -500.0)))
        assertEquals(0.07943446805473534, t!!, 1e-9)
    }

    @Test
    fun perdaQuaseTotal() {
        assertEquals(-0.98, Tir.anual(listOf(f("2021-01-01", -100.0), f("2022-01-01", 2.0)))!!, 1e-9)
    }

    @Test
    fun semDinheiroAEntrarOuASairNaoHaTir() {
        assertNull(Tir.anual(listOf(f("2021-01-01", -100.0), f("2022-01-01", -50.0))))
        assertNull(Tir.anual(listOf(f("2021-01-01", 100.0))))
        assertNull(Tir.anual(emptyList()))
        assertNull(Tir.anual(listOf(f("2021-01-01", -100.0), f("2021-01-01", 120.0))))
    }

    @Test
    fun duasSolucoesDaNull() {
        // −100, +230 um ano depois e −132 dois anos depois: 10% e 20% resolvem os dois a equação.
        assertNull(Tir.anual(listOf(f("2021-01-01", -100.0), f("2022-01-01", 230.0), f("2023-01-01", -132.0))))
    }
}
