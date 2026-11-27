package app.carteira

import app.carteira.dados.BasePesos
import app.carteira.dados.ClassePatrimonio
import app.carteira.dados.GruposPatrimonio
import app.carteira.dados.ParteDoPatrimonio
import app.carteira.dados.Pesos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** As três bases da vista Pesos (só ações, ações + ETFs e o património todo), com dados inventados. */
class PesosTest {

    private fun classe(nome: String, valor: Double) =
        ClassePatrimonio(nome, investidoEur = valor, valorEur = valor, valorizacao = 0.0, rendimentoAnualEur = null)

    /** 6 000 € em ações e 2 000 € em ETFs (a carteira, 8 000 €), mais 4 000 € fora dela. */
    private val classes = listOf(
        classe("Ações", 6_000.0),
        classe("ETFs", 2_000.0),
        classe("Certificado de Aforro E (exemplo)", 1_500.0),
        classe("Certificado de Aforro F", 500.0),
        classe("PPR (exemplo)", 1_000.0),
        classe("Corretora Um (saldo €)", 600.0),
        classe("Cash", 400.0),
        classe("Criptomoedas", 0.0),
    )

    @Test
    fun soAcoesTiraOsEtfsEConverteAsRegras() {
        val b = Pesos.base(BasePesos.ACOES, 6_000.0, 2_000.0, classes)!!
        assertEquals(6_000.0, b.totalEur, 1e-9)
        assertFalse(b.incluiEtfs)
        assertTrue(b.outras.isEmpty())
        // 6% da carteira (480 €) são 8% das ações; o máximo de 10% da carteira (800 €) passa a 13,3%.
        assertEquals(0.08, b.naBase(0.06)!!, 1e-9)
        assertEquals(800.0 / 6_000.0, b.naBase(0.10)!!, 1e-9)
        assertEquals(1.0, b.peso(6_000.0), 1e-9)
        assertNull(b.naBase(null))
    }

    @Test
    fun acoesMaisEtfsEABaseDasRegras() {
        val b = Pesos.base(BasePesos.CARTEIRA, 6_000.0, 2_000.0, classes)!!
        assertEquals(8_000.0, b.totalEur, 1e-9)
        assertEquals(1.0, b.fatorRegras, 1e-9)
        assertTrue(b.incluiEtfs)
        assertTrue(b.outras.isEmpty())
        assertEquals(0.06, b.naBase(0.06)!!, 1e-9)
        assertEquals(0.25, b.peso(2_000.0), 1e-9)
    }

    @Test
    fun tudoJuntaORestoDoPatrimonioPorGrupos() {
        val b = Pesos.base(BasePesos.TUDO, 6_000.0, 2_000.0, classes)!!
        assertEquals(12_000.0, b.totalEur, 1e-9)
        assertFalse(b.semPatrimonio)
        // As ações e os ETFs ficam nas posições; os certificados e os saldos juntam-se; a cripto a zero sai.
        assertEquals(
            listOf(
                ParteDoPatrimonio("Certificados de Aforro", 2_000.0),
                ParteDoPatrimonio("PPR", 1_000.0),
                ParteDoPatrimonio("Corretoras e liquidez", 1_000.0),
            ),
            b.outras,
        )
        assertEquals(4_000.0, b.outrasEur, 1e-9)
        // 6% da carteira (480 €) são 4% do património.
        assertEquals(0.04, b.naBase(0.06)!!, 1e-9)
        assertEquals(0.5, b.peso(6_000.0), 1e-9)
    }

    @Test
    fun tudoSemPatrimonioFicaIgualACarteira() {
        val b = Pesos.base(BasePesos.TUDO, 6_000.0, 2_000.0, null)!!
        assertTrue(b.semPatrimonio)
        assertEquals(8_000.0, b.totalEur, 1e-9)
        assertEquals(1.0, b.fatorRegras, 1e-9)
        assertTrue(b.outras.isEmpty())
    }

    /** Peso e máximo mudam pelo mesmo fator: o que está acima do máximo numa base está em todas. */
    @Test
    fun oAvisoDoMaximoNaoMudaDeBase() {
        val peso = 0.105
        val maximo = 0.10
        for (base in BasePesos.entries) {
            val b = Pesos.base(base, 6_000.0, 2_000.0, classes)!!
            assertTrue(base.rotulo, b.naBase(peso)!! > b.naBase(maximo)!!)
            assertEquals(base.rotulo, peso / maximo, b.naBase(peso)!! / b.naBase(maximo)!!, 1e-9)
        }
    }

    @Test
    fun semValoresNaoHaBase() {
        assertNull(Pesos.base(BasePesos.ACOES, 0.0, 2_000.0, classes))
        assertNull(Pesos.base(BasePesos.CARTEIRA, 0.0, 0.0, classes))
        assertNull(Pesos.base(BasePesos.TUDO, 0.0, 0.0, classes))
    }

    @Test
    fun rotulosEGrupos() {
        assertEquals(listOf("Ações", "Ações + ETFs", "Tudo"), BasePesos.ROTULOS)
        assertEquals(BasePesos.ACOES, BasePesos.de("Ações"))
        assertEquals(BasePesos.TUDO, BasePesos.de("Tudo"))
        // Um valor antigo ou estranho volta à base das regras.
        assertEquals(BasePesos.CARTEIRA, BasePesos.de("outra coisa"))
        assertEquals(BasePesos.CARTEIRA, BasePesos.de(null))
        assertEquals(GruposPatrimonio.ACOES, GruposPatrimonio.de("Ações"))
        assertEquals(GruposPatrimonio.ETFS, GruposPatrimonio.de("ETFs"))
        assertEquals("P2P", GruposPatrimonio.de("P2P Lending"))
        assertEquals("Corretoras e liquidez", GruposPatrimonio.de("Depósito a prazo"))
        assertEquals("Ouro", GruposPatrimonio.de(" Ouro "))
    }
}
