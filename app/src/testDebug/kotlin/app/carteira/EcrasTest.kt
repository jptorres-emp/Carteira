package app.carteira

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.carteira.dados.FonteDados
import app.carteira.ui.AppCarteira
import app.carteira.ui.CarteiraViewModel
import app.carteira.ui.TemaCarteira
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Resposta fixa da "ponte", para os ecrãs abrirem sem rede. Guarda o último pedido enviado. */
private class FonteFalsa(private val dados: String) : FonteDados {
    var ultimoPedido: JSONObject? = null
    override suspend fun dados(): String = dados
    override suspend fun ping(): String = """{"ok":true,"versaoPonte":1,"folha":"Carteira de teste"}"""
    override suspend fun enviar(pedido: JSONObject): String {
        ultimoPedido = pedido
        return """{"ok":true,"mensagem":"Compra registada na linha 141 de Transações: 2 ALFA a 300,50 $.","aviso":null}"""
    }
}

/**
 * Percorre a app como um utilizador, com dados de exemplo, e guarda capturas de cada ecrã em
 * app/build/screenshots. Também é um teste de fumo: se um ecrã rebentar ao abrir, o teste falha.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "pt-rPT-w411dp-h891dp-xhdpi")
class EcrasTest {

    // A versão "v2" da regra muda o despacho das corrotinas; fica a mesma da Empate, que já está provada.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app get() = RuntimeEnvironment.getApplication()

    private val amostra: String by lazy {
        javaClass.classLoader!!.getResourceAsStream("amostra-ecras.json")!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun abrir(configurado: Boolean = true): FonteFalsa {
        val preferencias = app.getSharedPreferences("carteira", Context.MODE_PRIVATE)
        if (configurado) {
            preferencias.edit()
                .putString("url", "https://script.google.com/macros/s/teste/exec")
                .putString("chave", "chave-de-teste")
                .commit()
        } else {
            preferencias.edit().clear().commit()
            File(app.filesDir, "dados.json").delete()
        }
        val fonte = FonteFalsa(amostra)
        val vm = CarteiraViewModel(app) { _, _ -> fonte }
        // Relógio à mão: os indicadores de progresso são animações sem fim.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                TemaCarteira { AppCarteira(vm) }
            }
        }
        assentar()
        return fonte
    }

    private fun assentar() {
        repeat(10) { compose.mainClock.advanceTimeBy(100) }
        compose.waitForIdle()
    }

    /** Um texto no ecrã ou a descrição de um ícone (os botões só com ícone não têm texto). */
    private fun comTexto(texto: String, parcial: Boolean = false) =
        hasText(texto, substring = parcial) or hasContentDescription(texto, substring = parcial)

    private fun esperar(texto: String) {
        val limite = System.currentTimeMillis() + 15_000
        while (compose.onAllNodes(comTexto(texto, parcial = true)).fetchSemanticsNodes().isEmpty()) {
            check(System.currentTimeMillis() < limite) { "Não apareceu no ecrã: \"$texto\"" }
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(20)
        }
    }

    private fun tocar(texto: String) {
        esperar(texto)
        compose.onAllNodes(comTexto(texto) and hasClickAction()).onFirst().performClick()
        assentar()
    }

    private fun tocarDescricao(descricao: String) {
        compose.onNodeWithContentDescription(descricao).performClick()
        assentar()
    }

    private fun foto(nome: String) {
        assentar()
        val pasta = File("build/screenshots").apply { mkdirs() }
        val imagem = compose.onRoot().captureToImage().asAndroidBitmap()
        File(pasta, "$nome.png").outputStream().use { imagem.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun primeiraVezPedeALigacao() {
        abrir(configurado = false)
        esperar("Como ligar a app à folha")
        foto("00-primeira-vez")
    }

    @Test
    fun percorreOsSeparadores() {
        abrir()
        esperar("Valor da carteira")
        foto("01-resumo")
        tocar("Posições")
        esperar("ALFA")
        foto("02-posicoes")
        tocar("ALFA")
        esperar("A tua posição")
        foto("03-posicao")
        tocarDescricao("Voltar")
        tocar("Dividendos")
        esperar("Previsão para 2027")
        foto("04-dividendos")
        tocar("Watchlist")
        esperar("Core A")
        foto("05-watchlist")
        tocar("Resumo")
        tocarDescricao("Definições")
        esperar("Ligação à folha")
        foto("09-definicoes")
    }

    @Test
    fun registaUmaCompra() {
        val fonte = abrir()
        esperar("Valor da carteira")
        tocar("Registar")
        esperar("Registar compra")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("ALF")
        assentar()
        foto("06-registar-sugestoes")
        tocar("ALFA")
        val campos = compose.onAllNodes(hasSetTextAction())
        // Ticker, data, unidades, preço, comissão e câmbio.
        campos[2].performTextInput("2")
        campos[3].performTextInput("300,5")
        assentar()
        foto("07-registar-compra")
        tocar("Registar compra")
        esperar("Compra registada")
        val pedido = fonte.ultimoPedido!!
        assertEquals("transacao", pedido.getString("acao"))
        assertEquals("ALFA", pedido.getString("ticker"))
        assertEquals(2.0, pedido.getDouble("unidades"), 1e-9)
        assertEquals(300.5, pedido.getDouble("preco"), 1e-9)
    }

    @Test
    fun formularioDeDividendo() {
        abrir()
        esperar("Valor da carteira")
        tocar("Dividendos")
        tocar("Registar")
        esperar("Valor bruto recebido")
        foto("08-registar-dividendo")
    }
}
