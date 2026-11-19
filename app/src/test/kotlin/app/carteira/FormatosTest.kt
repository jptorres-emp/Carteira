package app.carteira

import app.carteira.ui.Formatos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatosTest {

    @Test
    fun dinheiroAPortuguesa() {
        assertEquals("1 234,50 €", Formatos.eur(1234.5))
        assertEquals("17 894 €", Formatos.eur(17894.4, 0))
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
    fun modoPrivadoEscondeODinheiro() {
        Formatos.privado = true
        try {
            assertEquals("••• €", Formatos.eur(31894.41, 0))
            assertEquals("••• $", Formatos.usd(954.01))
            assertEquals("••• €", Formatos.eurSinal(-56.2))
            assertEquals("••• $", Formatos.usdSinal(12.0))
            assertEquals("••• £", Formatos.moeda(10.0, "GBP"))
            assertEquals("•••", Formatos.compacto(60_000.0))
            assertEquals("•••", Formatos.quantidade(7.0))
            assertEquals("••• €", Formatos.intervaloEur(41_905.0, 42_589.0))
            // Percentagens e preços de mercado ficam à vista; o que falta continua "—".
            assertEquals("+21,0%", Formatos.pctSinal(0.2096))
            assertEquals("268,15 $", Formatos.preco(268.15, "USD"))
            assertEquals("1 350,00 GBp", Formatos.preco(1350.0, "GBp"))
            assertEquals("—", Formatos.eur(null))
        } finally {
            Formatos.privado = false
        }
        assertEquals("31 894 €", Formatos.eur(31894.41, 0))
        assertEquals("7", Formatos.quantidade(7.0))
    }

    @Test
    fun intervalos() {
        assertEquals("41,9–42,6 k €", Formatos.intervaloEur(41_905.0, 42_589.0))
        assertEquals("246–263 k €", Formatos.intervaloEur(246_340.0, 262_590.0))
        assertEquals("30,6–33,0 k €", Formatos.intervaloEur(30_565.0, 32_976.0))
        assertEquals("1,20–1,35 M €", Formatos.intervaloEur(1_200_000.0, 1_350_000.0))
    }

    @Test
    fun numeroLivre() {
        assertEquals("0,4245", Formatos.numeroLivre(0.4245))
        assertEquals("12", Formatos.numeroLivre(12.0))
        assertEquals("5,5", Formatos.numeroLivre(5.5))
    }
}
