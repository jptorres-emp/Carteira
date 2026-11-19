package app.carteira

import app.carteira.dados.Avaliacao
import app.carteira.dados.Gatilhos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatilhosTest {

    /** Uma avaliação inventada: reforçar forte 7, reforçar 7,5, comprar 8, reduzir 12, vender 13. */
    private fun avaliacao(preco: Double) = Avaliacao(
        moeda = "USD", precoLocal = preco, rendImplicita = 0.1, hurdle = 0.1, valorIntrinseco = 10.0, potencial = 0.0,
        reforcarForteAbaixo = 7.0, reforcarAbaixo = 7.5, comprarAbaixo = 8.0, reduzirAcima = 12.0, venderAcima = 13.0,
        pesoVsMaximo = null, temaIa = false, tese = null, alarme = null, fiscalidade = null, fonte = null,
    )

    @Test
    fun coreCompraAbaixoDoComprarENaoVendePeloPreco() {
        val g = Gatilhos.de("Core A", avaliacao(10.0))!!
        assertEquals(8.0, g.comprar!!, 1e-9)
        assertEquals(7.5, g.reforcar!!, 1e-9)
        assertEquals(7.0, g.reforcarForte!!, 1e-9)
        assertNull(g.reduzir)
        assertNull(g.vender)
        assertEquals(-0.2, g.distancia(g.comprar)!!, 1e-9)
        assertFalse(g.naZonaDeCompra)
        assertTrue(Gatilhos.de("Core B", avaliacao(7.9))!!.naZonaDeCompra)
    }

    @Test
    fun sateliteSoCompraNoReforcarForteEVendePelosPrecos() {
        val g = Gatilhos.de("Satélite", avaliacao(12.5))!!
        assertEquals(7.0, g.comprar!!, 1e-9)
        assertNull(g.reforcar)
        assertEquals(12.0, g.reduzir!!, 1e-9)
        assertEquals(13.0, g.vender!!, 1e-9)
        assertTrue(g.acimaDeReduzir)
        assertFalse(g.acimaDeVender)
        // Entre o «comprar» (8) e o «reforçar forte» (7), o satélite ainda não compra.
        assertFalse(Gatilhos.de("Satélite", avaliacao(7.8))!!.naZonaDeCompra)
    }

    @Test
    fun apostaNaoCompraESairNaoTemPrecos() {
        val aposta = Gatilhos.de("Aposta", avaliacao(10.0))!!
        assertNull(aposta.comprar)
        assertEquals(13.0, aposta.vender!!, 1e-9)
        val sair = Gatilhos.de("Sair 2027", avaliacao(10.0))!!
        assertFalse(sair.temPrecos)
        assertNull(Gatilhos.de("Residual", avaliacao(10.0)))
        assertNull(Gatilhos.de("Core A", null))
    }
}
