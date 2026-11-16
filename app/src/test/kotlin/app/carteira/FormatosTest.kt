package app.carteira

import app.carteira.ui.Formatos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatosTest {

    @Test
    fun dinheiroAPortuguesa() {
        assertEquals("1 234,50 €", Formatos.eur(1234.5))
        assertEquals("48 918 €", Formatos.eur(48918.4, 0))
        assertEquals("954,01 $", Formatos.usd(954.014))
        assertEquals("—", Formatos.usd(null))
        assertEquals("+1 235 €", Formatos.eurSinal(1234.5))
        assertEquals("−56 €", Formatos.eurSinal(-56.2))
    }

    @Test
    fun percentagens() {
        assertEquals("21,0%", Formatos.pct(0.2096))
        assertEquals("+21,0%", Formatos.pctSinal(0.2096))
        assertEquals("−1,23%", Formatos.pctSinal(-0.0123, 2))
        assertEquals("0,0%", Formatos.pctSinal(0.0))
    }

    @Test
    fun datas() {
        assertEquals("2026-11-16", Formatos.dataIso("16/11/2026"))
        assertEquals("2026-01-05", Formatos.dataIso("5/1/2026"))
        assertNull(Formatos.dataIso("31/02/2026"))
        assertNull(Formatos.dataIso("2026-11-16"))
        assertEquals("16/11/2026", Formatos.data("2026-11-16"))
        assertEquals("16/11 às 09:40", Formatos.momento("2026-11-16T09:40:35+00:00"))
    }

    @Test
    fun lerNumeros() {
        assertEquals(1234.5, Formatos.lerNumero("1 234,5")!!, 1e-9)
        assertEquals(0.9078, Formatos.lerNumero("0.9078")!!, 1e-9)
        assertEquals(0.9078, Formatos.lerNumero("0,9078")!!, 1e-9)
        assertEquals(1234.56, Formatos.lerNumero("1.234,56")!!, 1e-9)
        assertNull(Formatos.lerNumero("abc"))
        assertNull(Formatos.lerNumero(""))
    }

    @Test
    fun numeroLivre() {
        assertEquals("0,4245", Formatos.numeroLivre(0.4245))
        assertEquals("12", Formatos.numeroLivre(12.0))
        assertEquals("5,5", Formatos.numeroLivre(5.5))
    }
}
