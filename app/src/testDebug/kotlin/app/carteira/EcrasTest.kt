package app.carteira

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.carteira.alertas.VerificadorAlertas
import app.carteira.dados.FonteDados
import app.carteira.ui.AppCarteira
import app.carteira.ui.CarteiraViewModel
import app.carteira.ui.TemaCarteira
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Resposta da "ponte" para os ecrãs abrirem sem rede (dados inventados). Guarda o último pedido enviado. */
private class FonteFalsa(var dados: String) : FonteDados {
    var ultimoPedido: JSONObject? = null
    override suspend fun dados(): String = dados
    override suspend fun ping(): String = """{"ok":true,"versaoPonte":2,"folha":"Carteira de teste"}"""
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
    private lateinit var vm: CarteiraViewModel

    private val amostra: String by lazy {
        javaClass.classLoader!!.getResourceAsStream("amostra-ecras.json")!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @After
    fun repor() {
        VerificadorAlertas.criarFonte = { url, chave -> app.carteira.dados.Ponte(url, chave) }
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
        vm = CarteiraViewModel(app) { _, _ -> fonte }
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

    private fun esperar(texto: String) = esperarQue("Não apareceu no ecrã: \"$texto\"") {
        compose.onAllNodes(comTexto(texto, parcial = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun esperarQue(mensagem: String, condicao: () -> Boolean) {
        val limite = System.currentTimeMillis() + 15_000
        while (!condicao()) {
            check(System.currentTimeMillis() < limite) { mensagem }
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

    /** Desliza a lista do ecrã até ao item n (o item fica no topo). */
    private fun deslizarPara(indice: Int) {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(indice)
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
        esperar("Ligação à folha")
        foto("00-primeira-vez")
        val campos = compose.onAllNodes(hasSetTextAction())
        campos[0].performTextInput("https://script.google.com/macros/s/teste/exec")
        campos[1].performTextInput("chave-de-teste")
        assentar()
        tocar("Testar e guardar")
        // A ligação fica guardada, a app volta ao início e mostra os dados da folha.
        esperar("Ligada à folha")
        esperar("Valor da carteira")
        foto("00b-ligada")
    }

    @Test
    fun ecraInicialTemTodosOsCartoes() {
        abrir()
        esperar("Valor da carteira")
        foto("01-inicio")
        // Cada cartão tem de existir (e não rebentar ao desenhar).
        listOf(
            "Evolução", "Últimos 12 meses", "Estou no caminho?", "Precisa de atenção", "Hoje", "Alocação",
            "Dividendos", "Oportunidades na watchlist", "Património total", "Desempenho e risco", "Últimos movimentos",
        ).forEach { titulo ->
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(titulo))
            assentar()
        }
        // Itens: 0 principal, 1 convite dos alertas, 2 evolução, 3 indicadores, 4 objetivos, 5 atenção,
        // 6 hoje, 7 alocação, 8 dividendos, 9 oportunidades, 10 património, 11 desempenho, 12 movimentos.
        deslizarPara(2)
        foto("01b-inicio-evolucao")
        deslizarPara(4)
        foto("01c-inicio-objetivos")
        deslizarPara(6)
        foto("01d-inicio-alocacao")
        tocar("Setores")
        foto("01e-inicio-setores")
        deslizarPara(8)
        foto("01f-inicio-dividendos")
        deslizarPara(10)
        foto("01g-inicio-patrimonio")
    }

    @Test
    fun percorreOsSeparadores() {
        abrir()
        esperar("Valor da carteira")
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
        tocar("Início")
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

    /** Liga os alertas, a verificação em segundo plano encontra mudanças, avisa e a app mostra-as. */
    @Test
    fun alertasDoInicioAoFim() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val fonte = abrir()
        esperar("Valor da carteira")
        tocar("Ativar alertas")
        // Ao ligar, a app regista o ponto de partida.
        esperarQue("Não guardou o estado dos alertas") { File(app.filesDir, "alertas-estado.json").exists() }
        tocarDescricao("Definições")
        esperar("Sinal de compra nas posições")
        foto("09b-definicoes-alertas")

        // Na folha: ALFA passa a sinal de compra, KAPA fica subvalorizada e GIRA cai 7,2%.
        fonte.dados = comMudancas(amostra)
        VerificadorAlertas.criarFonte = { _, _ -> fonte }
        val trabalhador = TestListenableWorkerBuilder<VerificadorAlertas>(app)
            .setInputData(workDataOf(VerificadorAlertas.FORCAR to true))
            .build()
        assertEquals(ListenableWorker.Result.success(), runBlocking { trabalhador.doWork() })

        val avisos = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
        val titulos = avisos.mapNotNull { it.extras.getCharSequence("android.title")?.toString() }
        assertTrue(titulos.toString(), titulos.contains("ALFA · Reforçar até ao alvo"))
        assertTrue(titulos.toString(), titulos.contains("KAPA · abaixo do valor justo"))
        assertTrue(titulos.toString(), titulos.contains("GIRA −7,2% hoje"))

        // Tocar na notificação de resumo abre a lista de alertas.
        compose.runOnIdle { vm.abrir(MainActivity.ABRIR_ALERTAS) }
        assentar()
        esperar("KAPA · abaixo do valor justo")
        foto("10-alertas")
        tocar("ALFA · Reforçar até ao alvo")
        esperar("A tua posição")
    }

    private fun comMudancas(json: String): String {
        val raiz = JSONObject(json)
        val d = raiz.getJSONObject("dados")
        val posicoes = d.getJSONArray("posicoes")
        for (k in 0 until posicoes.length()) {
            val p = posicoes.getJSONObject(k)
            when (p.getString("ticker")) {
                "ALFA" -> p.put("sinal", "🟢 Reforçar até ao alvo")
                "GIRA" -> p.put("varDiaPct", -0.072)
            }
        }
        val watchlist = d.getJSONArray("watchlist")
        for (k in 0 until watchlist.length()) {
            val w = watchlist.getJSONObject(k)
            if (w.getString("ticker") == "KAPA") w.put("sinal", "🟢 Subvalorizada")
        }
        return raiz.toString()
    }
}
