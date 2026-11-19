package app.carteira

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.Density
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.carteira.alertas.VerificadorAlertas
import app.carteira.dados.FonteDados
import app.carteira.dados.Ponte
import app.carteira.ui.AppCarteira
import app.carteira.ui.CarteiraViewModel
import app.carteira.ui.Formatos
import app.carteira.ui.TemaCarteira
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    override suspend fun ping(): String = """{"ok":true,"versaoPonte":4,"folha":"Carteira de teste"}"""
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
        VerificadorAlertas.criarFonte = { url, chave -> Ponte(url, chave) }
        Formatos.privado = false
    }

    /** escalaLetra: como o tamanho do texto nas definições do telemóvel (1,3 = letra grande). */
    private fun abrir(configurado: Boolean = true, escalaLetra: Float = 1f): FonteFalsa {
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
            val densidade = LocalDensity.current
            CompositionLocalProvider(
                LocalCursorBlinkEnabled provides false,
                LocalDensity provides Density(densidade.density, densidade.fontScale * escalaLetra),
            ) {
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

    /** Os separadores de baixo (há outros textos iguais, como o filtro "Carteira"). */
    private fun tocarSeparador(nome: String) {
        compose.onNode(hasText(nome) and isSelectable()).performClick()
        assentar()
    }

    /** Toca num mês do gráfico mês a mês dos dividendos (cada mês tem a descrição "Março: …"). */
    private fun tocarMes(mes: String) {
        val nos = compose.onAllNodes(hasContentDescription(mes, substring = true) and isSelectable())
        assertEquals("meses com a descrição \"$mes\"", 1, nos.fetchSemanticsNodes().size)
        nos.onFirst().performClick()
        assentar()
    }

    /** Fotografa a lista do ecrã item a item, do primeiro ao último (para rever o layout). */
    private fun percorrer(prefixo: String, maximo: Int = 20) {
        for (k in 0 until maximo) {
            try {
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(k)
            } catch (e: IllegalArgumentException) {
                break // passou do último item
            }
            assentar()
            foto("$prefixo-${k.toString().padStart(2, '0')}")
        }
    }

    /** Junta a vistas os textos que estão no ecrã (inteiros). */
    private fun registar(marcas: List<String>, vistas: MutableSet<String>) {
        marcas.filterTo(vistas) { compose.onAllNodes(hasText(it)).fetchSemanticsNodes().isNotEmpty() }
    }

    /**
     * Abre uma posição pela caixa de pesquisa das Posições: com o atalho das vendas no topo, uma linha a meio
     * do ecrã pode ficar por baixo da barra dos separadores.
     */
    private fun abrirPosicao(ticker: String) {
        compose.onAllNodes(hasSetTextAction())[0].performTextInput(ticker)
        assentar()
        compose.onAllNodes(hasText(ticker) and hasClickAction() and !hasSetTextAction()).onFirst().performClick()
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

    /** Os meses (nomes inteiros) que estão no ecrã. */
    private fun mesesNoEcra(vararg meses: String) =
        meses.filter { compose.onAllNodes(hasText(it)).fetchSemanticsNodes().isNotEmpty() }

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
        // Só o estado atual da carteira; um texto de cada cartão (e nenhum pode rebentar ao desenhar).
        val marcas = listOf("Valor da carteira", "Ativar alertas", "Precisa de atenção", "Sobem mais", "Alocação")
        val vistas = mutableSetOf<String>()
        // Itens: 0 principal, 1 convite dos alertas, 2 atenção, 3 hoje, 4 alocação, 5 rodapé.
        // (Desliza por índice: o deslizar "até um texto" anima e, com o relógio parado, não acaba.)
        for (k in 0..5) {
            deslizarPara(k)
            registar(marcas, vistas)
            when (k) {
                2 -> foto("01b-inicio-atencao")
                3 -> foto("01c-inicio-hoje")
                4 -> {
                    foto("01d-inicio-alocacao")
                    tocar("Setores")
                    foto("01e-inicio-setores")
                }
            }
        }
        assertEquals(marcas.toSet(), vistas)
        // A evolução, os objetivos e os dividendos saíram do Início.
        listOf("Estou no caminho?", "Desempenho e risco", "Oportunidades na watchlist", "Últimos movimentos").forEach {
            assertTrue("$it ainda está no Início", compose.onAllNodes(hasText(it)).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    fun percorreOsSeparadores() {
        abrir()
        esperar("Valor da carteira")
        tocar("Posições")
        esperar("ALFA")
        // Cada posição mostra os preços de compra e venda que contam para a camada (ALFA é Core A: só compra).
        foto("02-posicoes")
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("ALFA")
        assentar()
        esperar("compra ≤ 212,50")
        compose.onAllNodes(hasText("ALFA") and hasClickAction() and !hasSetTextAction()).onFirst().performClick()
        assentar()
        esperar("Quando comprar e vender")
        esperar("Não vende pelo preço")
        foto("03-posicao")
        tocarDescricao("Voltar")
        // Um satélite: só compra em 🟢🟢; reduz e vende pelos preços da folha (BORE já passou o de reduzir).
        // Procura-se pela caixa de pesquisa (deslizar até ao nó usa animações, que o relógio à mão não avança).
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("BORE")
        assentar()
        esperar("vende ≥ 147,00")
        compose.onAllNodes(hasText("BORE") and hasClickAction() and !hasSetTextAction()).onFirst().performClick()
        assentar()
        esperar("Quando comprar e vender")
        esperar("Reduzir acima de")
        foto("03b-posicao-satelite")
        tocarDescricao("Voltar")
        tocar("Dividendos")
        esperar("Mês a mês em 2027")
        foto("04-dividendos")
        tocar("Watchlist")
        esperar("Core A")
        esperar("Oportunidades agora")
        foto("05-watchlist")
        tocarSeparador("Evolução")
        esperar("Património total")
        tocar("Início")
        tocarDescricao("Definições")
        esperar("Ligação à folha")
        foto("09-definicoes")
    }

    @Test
    fun ecraDosDividendos() {
        abrir()
        esperar("Valor da carteira")
        tocar("Dividendos")
        esperar("Mês a mês em 2027")
        val marcas = listOf("Recebido em 2026", "Previsão 2027 (líquido)", "Mês a mês em 2027", "Quem paga", "Por ano",
            "Recebido em 2026, por empresa")
        val vistas = mutableSetOf<String>()
        registar(marcas, vistas)
        // O mês a mês abre no mês de hoje (novembro); tocar em março mostra março.
        deslizarPara(2)
        assertTrue(compose.onAllNodes(hasText("Novembro")).fetchSemanticsNodes().isNotEmpty())
        tocarMes("Março")
        esperar("Previsto (bruto)")
        foto("04b-dividendos-mes")
        val titulos = listOf("Janeiro", "Fevereiro", "Março", "Novembro").filter {
            compose.onAllNodes(hasText(it)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("mês mostrado depois de tocar em março", listOf("Março"), titulos)
        registar(marcas, vistas)
        deslizarPara(3)
        registar(marcas, vistas)
        foto("04c-dividendos-anos")
        deslizarPara(4)
        registar(marcas, vistas)
        foto("04d-dividendos-empresas")
        // 23 empresas: mostra 10 e o botão abre as outras.
        tocar("Ver todas (23)")
        esperar("Mostrar menos")
        assertEquals(marcas.toSet(), vistas)
    }

    @Test
    fun separadorEvolucao() {
        abrir()
        esperar("Valor da carteira")
        tocarSeparador("Evolução")
        esperar("Património total")
        val marcas = listOf("Património total", "Acima da meta base", "Registo na folha", "Mês a mês", "Face ao plano",
            "Onde o plano te leva (dez/30)", "Marcos", "Próximo marco", "Ano a ano", "Composição hoje")
        val vistas = mutableSetOf<String>()
        registar(marcas, vistas)
        foto("11-evolucao")
        // Itens: 0 filtros, 1 topo, 2 registo, 3 gráfico, 4 plano, 5 marcos, 6 ano a ano, 7 composição, 8 rodapé.
        for (k in 2..8) {
            deslizarPara(k)
            registar(marcas, vistas)
            when (k) {
                2 -> foto("11b-evolucao-registo")
                3 -> {
                    foto("11c-evolucao-grafico")
                    tocar("Até 2030")
                    foto("11d-evolucao-ate-2030")
                }
                4 -> foto("11e-evolucao-plano")
                5 -> foto("11f-evolucao-marcos")
                6 -> foto("11g-evolucao-anos")
                7 -> foto("11h-evolucao-composicao")
            }
        }
        assertEquals(marcas.toSet(), vistas)

        // A outra vista: a carteira face ao S&P 500, indicadores, objetivos, risco e movimentos.
        deslizarPara(0)
        tocar("Carteira")
        esperar("Carteira vs S&P 500")
        val marcasCarteira = listOf("Carteira vs S&P 500", "Últimos 12 meses", "Estou no caminho?", "Desempenho e risco",
            "Últimos movimentos")
        val vistasCarteira = mutableSetOf<String>()
        for (k in 0..6) {
            deslizarPara(k)
            registar(marcasCarteira, vistasCarteira)
            when (k) {
                0 -> foto("12-evolucao-carteira")
                2 -> foto("12b-evolucao-indicadores")
                4 -> foto("12c-evolucao-risco")
            }
        }
        assertEquals(marcasCarteira.toSet(), vistasCarteira)
    }

    @Test
    fun atualizaOPatrimonioDoMes() {
        val fonte = abrir()
        esperar("Valor da carteira")
        tocarSeparador("Evolução")
        deslizarPara(2)
        esperar("por gravar")
        tocar("Atualizar património")
        esperar("Gravar na folha")
        foto("13-atualizar-patrimonio")
        // Novembro ainda não está na folha: o campo vem com o valor de agora.
        assertTrue(compose.onAllNodes(hasText("31894,41")).fetchSemanticsNodes().isNotEmpty())
        tocar("Gravar na folha")
        val pedido = fonte.ultimoPedido!!
        assertEquals("patrimonio", pedido.getString("acao"))
        assertEquals("2026-11", pedido.getString("mes"))
        assertEquals(31_894.41, pedido.getDouble("valor"), 1e-6)
        // Outubro já tem valor na folha: o campo mostra-o para o corrigir.
        tocar("Atualizar património")
        tocar("out/26")
        esperar("31123,37")
        foto("13b-atualizar-outubro")
    }

    @Test
    fun modoPrivadoEscondeOsValores() {
        abrir()
        esperar("Valor da carteira")
        val total = compose.onAllNodes(hasText("€", substring = true)).fetchSemanticsNodes().size
        tocarDescricao("Esconder valores")
        assertTrue(Formatos.privado)
        esperar("•••")
        // Nenhum valor em euros fica à vista no Início (só os escondidos).
        val emEuros = compose.onAllNodes(hasText("€", substring = true)).fetchSemanticsNodes()
            .flatMap { n -> n.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }.map { it.text } }
            .filter { it.contains("€") && !it.contains("•••") }
        assertEquals("valores em euros à vista: $emEuros", emptyList<String>(), emEuros)
        assertTrue(total > 0)
        foto("14-privado-inicio")
        tocarSeparador("Evolução")
        esperar("Património total")
        foto("14b-privado-evolucao")
        tocar("Posições")
        esperar("ALFA")
        foto("14c-privado-posicoes")
        // Volta a mostrar e fica guardado.
        tocarDescricao("Mostrar valores")
        assertTrue(!Formatos.privado)
        esperar("ALFA")
    }

    /** Vendas (ponte v4): a rentabilidade total na lista e no detalhe, e o ecrã das vendas e posições fechadas. */
    @Test
    fun vendasERentabilidade() {
        abrir()
        esperar("Valor da carteira")
        tocar("Posições")
        esperar("De borla e vendas")
        // Cada posição mostra quanto do que pagaste já voltou em vendas e dividendos (JADE já está de borla).
        esperar("🎁 de borla")
        esperar("recuperado ")
        foto("07-posicoes-recuperado")
        tocar("🎁 De borla")
        esperar("JADE")
        foto("07a-posicoes-de-borla")
        tocar("JADE")
        esperar("Quando comprar e vender")
        // Itens do detalhe: 0 cabeçalho, 1 botões, 2 comprar e vender, 3 a tua posição, 4 custo recuperado.
        deslizarPara(4)
        esperar("Custo recuperado e rentabilidade")
        esperar("De borla: as vendas e os dividendos já pagaram tudo")
        foto("07b-posicao-de-borla")
        tocarDescricao("Voltar")
        abrirPosicao("ALFA")
        esperar("Quando comprar e vender")
        deslizarPara(4)
        esperar("Já recuperaste")
        esperar("Recebeste nas vendas")
        esperar("Mais-valias das vendas")
        foto("07b2-posicao-recuperado")
        tocarDescricao("Voltar")
        tocar("De borla e vendas")
        esperar("De borla?")
        foto("07c-de-borla-e-vendas")
        // Itens: 0 de borla, 1 título, 2 mais-valias, 3 por ano, 4 filtros dos anos.
        deslizarPara(4)
        tocar("2025")
        esperar("MARE")
        foto("07d-vendas-2025")
        tocar("Todas")
        val marcas = listOf(
            "De borla?", "a mais do que pagaste", "faltam ", "Mais-valias por ano", "Posições fechadas", "ZETA", "MARE",
            "NORA", "Vendeste bem", "Devias ter mantido",
        )
        val vistas = mutableSetOf<String>()
        for (k in 0 until 30) {
            try {
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(k)
            } catch (e: IllegalArgumentException) {
                break // passou do último item
            }
            assentar()
            marcas.filterTo(vistas) { compose.onAllNodes(hasText(it, substring = true)).fetchSemanticsNodes().isNotEmpty() }
            foto("07e-vendas-${k.toString().padStart(2, '0')}")
        }
        assertEquals(marcas.toSet(), vistas)
    }

    /** Tema escuro (a app segue o do telemóvel): os ecrãs principais, para rever cores e contraste. */
    @Test
    @Config(qualifiers = "+night")
    fun temaEscuro() {
        abrir()
        esperar("Valor da carteira")
        percorrer("30-escuro-inicio", maximo = 6)
        tocarSeparador("Evolução")
        esperar("Património total")
        percorrer("31-escuro-evolucao", maximo = 4)
        tocar("Posições")
        esperar("ALFA")
        foto("32-escuro-posicoes")
        tocar("Dividendos")
        esperar("Mês a mês em 2027")
        deslizarPara(2)
        // As setas mudam de mês: de novembro para outubro.
        val seta = compose.onNodeWithContentDescription("Mês anterior")
        val limites = seta.fetchSemanticsNode().boundsInRoot
        seta.performClick()
        assentar()
        foto("33-escuro-dividendos")
        val depoisDoToque = mesesNoEcra("Outubro", "Novembro")
        if (depoisDoToque != listOf("Outubro")) {
            // Diagnóstico: o toque não mudou o mês. A ação de clique (sem toque) muda?
            seta.performSemanticsAction(SemanticsActions.OnClick)
            assentar()
            fail(
                "Depois do toque: $depoisDoToque; depois da ação: ${mesesNoEcra("Setembro", "Outubro", "Novembro")}; " +
                    "seta em $limites\n" + compose.onRoot().printToString(),
            )
        }
        tocar("Watchlist")
        esperar("Core A")
        foto("34-escuro-watchlist")
    }

    /** Telemóvel estreito (360 dp) com letra grande (1,3): todos os ecrãs, item a item, para rever o layout. */
    @Test
    @Config(qualifiers = "pt-rPT-w360dp-h780dp-xhdpi")
    fun ecraEstreitoELetraGrande() {
        abrir(escalaLetra = 1.3f)
        esperar("Valor da carteira")
        percorrer("20-inicio")
        tocarSeparador("Evolução")
        esperar("Património total")
        percorrer("21-evolucao")
        deslizarPara(0)
        tocar("Carteira")
        esperar("Carteira vs S&P 500")
        percorrer("22-carteira")
        tocar("Posições")
        esperar("De borla e vendas")
        foto("23-posicoes")
        // Com letra grande, a ALFA fica abaixo do ecrã: procura-se pela caixa de pesquisa.
        abrirPosicao("ALFA")
        esperar("Quando comprar e vender")
        percorrer("24-posicao", maximo = 8)
        tocarDescricao("Voltar")
        tocar("De borla e vendas")
        esperar("De borla?")
        percorrer("24b-vendas", maximo = 30)
        tocarDescricao("Voltar")
        tocar("Dividendos")
        esperar("Mês a mês em 2027")
        percorrer("25-dividendos")
        tocar("Watchlist")
        esperar("Core A")
        foto("26-watchlist")
        tocarSeparador("Evolução")
        deslizarPara(0)
        tocar("Património")
        deslizarPara(2)
        tocar("Atualizar património")
        esperar("Gravar na folha")
        foto("27-atualizar-patrimonio")
        tocarDescricao("Voltar")
        tocarSeparador("Início")
        tocar("Registar")
        esperar("Registar compra")
        foto("28-registar")
        tocarDescricao("Voltar")
        tocarDescricao("Definições")
        esperar("Ligação à folha")
        foto("29-definicoes")
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
        esperar("Quando comprar e vender")
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
