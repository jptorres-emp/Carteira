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
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
import app.carteira.ui.Ecra
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
    override suspend fun ping(): String = """{"ok":true,"versaoPonte":6,"folha":"Carteira de teste"}"""
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
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement(ticker)
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
        // Itens: 0 principal, 1 atalhos, 2 convite dos alertas, 3 atenção, 4 hoje, 5 alocação, 6 rodapé.
        // (Desliza por índice: o deslizar "até um texto" anima e, com o relógio parado, não acaba.)
        for (k in 0..6) {
            deslizarPara(k)
            registar(marcas, vistas)
            when (k) {
                3 -> foto("01b-inicio-atencao")
                4 -> foto("01c-inicio-hoje")
                5 -> {
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
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("ALFA")
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
        // A pesquisa ainda tem "ALFA" (cada ecrã guarda o seu estado): substitui-se.
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("BORE")
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
            "Onde o plano te leva (dez/30)", "Marcos", "Próximo marco", "Ano a ano", "Composição hoje", "Rendimentos por ano",
            "Rendimentos no futuro")
        val vistas = mutableSetOf<String>()
        registar(marcas, vistas)
        // O topo diz quanto o património rende por ano.
        esperar("por ano (")
        esperar("dividendos e juros")
        foto("11-evolucao")
        // Itens: 0 filtros, 1 topo, 2 registo, 3 gráfico, 4 plano, 5 marcos, 6 ano a ano, 7 composição,
        // 8 rendimentos, 9 rendimentos no futuro, 10 rodapé.
        for (k in 2..10) {
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
                8 -> {
                    foto("11i-evolucao-rendimentos")
                    // Os ETFs estão a 0% na folha: contam pelo que o EMRG pagou nos últimos 12 meses.
                    esperar("ETFs 61")
                    esperar("pagos em 12 meses")
                    esperar("Certificados de Aforro 180")
                    esperar("351")
                    esperar("depois dos 28% de IRS")
                }
                9 -> {
                    foto("11j-evolucao-futuro")
                    // Cenário base do plano de exemplo (400 €/mês, 6,5%): de 577 € por ano hoje a 10 405 € em 2056.
                    esperar("Base 6,5%")
                    esperar("Daqui a")
                    esperar(Formatos.eur(577.0, 0))
                    esperar(Formatos.eur(2_138.0, 0))
                    esperar(Formatos.eur(10_405.0, 0))
                    esperar(Formatos.eur(867.0, 0))
                    esperar("2056")
                    esperar("levantar 4% ao ano daria ${Formatos.eur(22_797.0, 0)}")
                    esperar("${Formatos.eur(400.0, 0)} por mês para ações e ETFs")
                    // O cenário alargado (8%) dá mais.
                    tocar("Alargada 8,0%")
                    esperar(Formatos.eur(14_007.0, 0))
                    foto("11k-evolucao-futuro-alargado")
                    tocar("Base 6,5%")
                    esperar(Formatos.eur(10_405.0, 0))
                }
            }
        }
        assertEquals(marcas.toSet(), vistas)

        // A outra vista: a carteira face ao S&P 500, indicadores, objetivos, risco e movimentos.
        deslizarPara(0)
        tocar("Carteira")
        esperar("Carteira vs S&P 500")
        val marcasCarteira = listOf("Carteira vs S&P 500", "Cada ação contra o S&P 500", "Últimos 12 meses", "Estou no caminho?",
            "Desempenho e risco", "O teu dinheiro (TIR)", "No VUAA (TIR)", "Últimos movimentos")
        val vistasCarteira = mutableSetOf<String>()
        for (k in 0..7) {
            deslizarPara(k)
            registar(marcasCarteira, vistasCarteira)
            when (k) {
                0 -> foto("12-evolucao-carteira")
                2 -> foto("12b-evolucao-indicadores")
                4 -> foto("12c-evolucao-risco")
            }
        }
        assertEquals(marcasCarteira.toSet(), vistasCarteira)

        // YTD, como no Google Finance: este ano a partir de 0%, sem contar o dinheiro que entrou ou saiu.
        deslizarPara(1)
        tocar("YTD")
        esperar("novembro de 2026 (até agora)")
        esperar("Desde o fim de 2025: a carteira está")
        esperar("atrás do S&P 500")
        foto("12d-evolucao-ytd")
        tocar("Tudo")
        esperar("Sobre o que investiste")
    }

    /** Posições, vista Pesos: o peso de cada uma numa barra, o alvo ao lado; a escolha fica guardada. */
    @Test
    fun pesosDasPosicoes() {
        abrir()
        esperar("Valor da carteira")
        tocar("Posições")
        esperar("ALFA")
        tocar("Pesos")
        // A concentração: o ETF MUND e as quatro maiores ações; as dez maiores.
        esperar("As 5 maiores: 55,0%")
        esperar("as 10 maiores: 89,1%")
        // A JADE (10,0%, acima do máximo de 10%) e a ALFA com o alvo ao lado.
        esperar("10,0%")
        esperar("alvo 9,0%")
        foto("02c-posicoes-pesos")
        assertEquals("Pesos", app.getSharedPreferences("carteira", Context.MODE_PRIVATE).getString("vista_posicoes", null))
        // Fica escolhida ao voltar ao separador.
        tocarSeparador("Início")
        tocarSeparador("Posições")
        esperar("As 5 maiores")
        // Tocar numa linha abre a posição.
        tocar("BORE")
        esperar("Quando comprar e vender")
        tocarDescricao("Voltar")
        esperar("As 5 maiores")
        tocar("Lista")
        esperar("peso 18,3%")
        assertEquals("Lista", app.getSharedPreferences("carteira", Context.MODE_PRIVATE).getString("vista_posicoes", null))
    }

    /**
     * Vista Pesos nas três bases: só as ações (os ETFs saem; os pesos, alvos e máximos sobem pelo mesmo fator),
     * ações + ETFs (a base das regras) e o património todo, com o resto no fim. A base fica guardada.
     */
    @Test
    fun pesosNasTresBases() {
        abrir()
        esperar("Valor da carteira")
        tocar("Posições")
        esperar("ALFA")
        tocar("Pesos")
        // Por omissão, a base das regras: ações + ETFs, como antes.
        esperar("Ações 70,1% · ETFs 29,9%")
        esperar("As 5 maiores: 55,0% · as 10 maiores: 89,1% da carteira")
        // Só as ações: o alvo de 9% da carteira da ALFA passa a 12,8% das ações.
        tocar("Ações")
        esperar("Core A 41,0% · Core B 32,6% · Satélite 24,9% · Residual 1,5%")
        esperar("As 5 maiores: 63,3% das ações")
        esperar("10 posições")
        esperar("100,0% das ações")
        esperar("alvo 12,8%")
        assertTrue("os ETFs saem da base Ações", compose.onAllNodes(hasText("MUND")).fetchSemanticsNodes().isEmpty())
        foto("02d-posicoes-pesos-acoes")
        val preferencias = app.getSharedPreferences("carteira", Context.MODE_PRIVATE)
        assertEquals("Ações", preferencias.getString("base_pesos", null))
        // Tudo: o património inteiro; o resto (certificados, PPR, depósito) no fim, sem barra.
        tocar("Tudo")
        esperar("Ações 39,4% · ETFs 16,8% · resto do património 43,9%")
        esperar("As 5 maiores: 30,9% · as 10 maiores: 50,0% do património")
        esperar("Resto do património")
        esperar("Certificados de Aforro")
        esperar("23,0%")
        deslizarPara(4)
        foto("02e-posicoes-pesos-tudo")
        assertEquals("Tudo", preferencias.getString("base_pesos", null))
        // Fica escolhida ao voltar ao separador.
        tocarSeparador("Início")
        tocarSeparador("Posições")
        esperar("Resto do património")
        // A Lista continua com o peso na carteira (o MUND, um ETF, pesa 18,3%). Volta ao topo antes de tocar:
        // com a lista deslizada, o seletor fica por baixo da barra de cima e o toque não lhe chega.
        deslizarPara(0)
        tocar("Lista")
        esperar("peso 18,3%")
        tocar("Pesos")
        tocar("Ações + ETFs")
        esperar("As 5 maiores: 55,0%")
        assertEquals("Ações + ETFs", preferencias.getString("base_pesos", null))
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
        esperar("Vendas e posições fechadas")
        foto("07-posicoes")
        // A JADE já está de borla: aparece, só informativo, no detalhe e na opção das Vendas.
        abrirPosicao("JADE")
        esperar("Quando comprar e vender")
        // Itens do detalhe: 0 cabeçalho, 1 botões, 2 comprar e vender, 3 a tua posição, 4 rentabilidade total.
        deslizarPara(4)
        esperar("Rentabilidade total")
        esperar("🎁 de borla")
        foto("07b-posicao-de-borla")
        tocarDescricao("Voltar")
        abrirPosicao("ALFA")
        esperar("Quando comprar e vender")
        deslizarPara(4)
        esperar("Pago pelo lucro")
        esperar("Recebeste nas vendas")
        esperar("Mais-valias das vendas")
        foto("07b2-posicao-rentabilidade")
        tocarDescricao("Voltar")
        tocar("Vendas e posições fechadas")
        esperar("Mais-valias em 2026")
        foto("07c-vendas")
        // Itens: 0 opções (Vendas / De borla), 1 mais-valias, 2 por ano, 3 filtros dos anos.
        deslizarPara(3)
        tocar("2025")
        esperar("MARE")
        foto("07d-vendas-2025")
        tocar("Todas")
        val marcas = listOf(
            "Mais-valias por ano", "Posições fechadas", "ZETA", "MARE", "NORA", "Vendeste bem", "Devias ter mantido",
            "hoje davam lucro", "continuavam em prejuízo", "Vendeste por", "Hoje valeriam",
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
        // Separadas pelo que dariam hoje se as tivesses mantido (em euros, face ao custo). Itens: 4 texto de ajuda,
        // 5 "davam lucro" (5 empresas: JADE, ALFA, ZETA, BORE e NORA, da que mais dava para a que menos),
        // 11 "continuavam em prejuízo" (2: MARE e OBRG).
        deslizarPara(5)
        esperar("Se as tivesses mantido, hoje davam lucro")
        esperar("5 empresas, somadas:")
        esperar("JADE")
        foto("07e3-vendas-davam-lucro")
        deslizarPara(11)
        esperar("Se as tivesses mantido, continuavam em prejuízo")
        esperar("2 empresas, somadas:")
        esperar("MARE")
        foto("07e4-vendas-em-prejuizo")
        // Sem tickers repetidos: a NORA, vendida duas vezes, é uma linha só, com o preço médio e as duas juntas.
        deslizarPara(10)
        esperar("2 vendas · última 12/08/2026")
        esperar("(preço médio)")
        assertEquals(1, compose.onAllNodes(hasText("▾ Ver as 2 vendas")).fetchSemanticsNodes().size)
        tocar("▾ Ver as 2 vendas")
        esperar("12/08/2026 · 3 × ")
        esperar("30/06/2025 · 3 × ")
        foto("07e2-vendas-nora-juntas")
        tocar("▴ Esconder as vendas")
        // Num ano só, a NORA tem uma venda: sem "Ver as vendas".
        deslizarPara(3)
        tocar("2025")
        esperar("NORA")
        assertEquals(0, compose.onAllNodes(hasText("▾ Ver as", substring = true)).fetchSemanticsNodes().size)
        tocar("Todas")
        // A opção "De borla": que posições já se pagaram e quanto falta às outras.
        deslizarPara(0)
        tocar("🎁 De borla")
        esperar("1 posição já está de borla")
        // Só conta o lucro: a ALFA vendeu 997 €, mas o lucro (mais-valias e dividendos) paga só uma parte das que tem.
        esperar("Só conta o lucro")
        esperar("faltam ")
        foto("07f-de-borla")
        // Ao tocar numa posição: de onde vem o lucro (vendas e dividendos) e o que ele tem de pagar.
        tocar("ALFA ▾")
        esperar("De onde vem o lucro")
        esperar("Venda de ")
        esperar("o teu dinheiro a voltar")
        esperar("O que tem de pagar")
        esperar("Compra de ")
        esperar("Ainda falta")
        foto("07g-de-borla-alfa")
        // O botão fica abaixo do detalhe aberto, fora do ecrã: toca-se pela ação (deslizar até ele anima e, com o
        // relógio parado, não acaba).
        compose.onAllNodes(hasText("Abrir a ALFA") and hasClickAction()).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        assentar()
        esperar("Quando comprar e vender")
        deslizarPara(4)
        tocar("Pago pelo lucro ▾")
        esperar("De onde vem o lucro")
        esperar("Ainda falta")
        foto("07h-posicao-de-onde-vem")
    }

    @Test
    fun contraOIndiceESimularVenda() {
        abrir()
        esperar("Valor da carteira")
        tocar("Posições")
        abrirPosicao("ALFA")
        esperar("Quando comprar e vender")
        // Itens do detalhe: 0 cabeçalho, 1 botões, 2 comprar e vender, 3 a tua posição, 4 rentabilidade total,
        // 5 simular uma venda.
        deslizarPara(4)
        esperar("Por ano (TIR)")
        esperar("+26,2%")
        esperar("Contra o S&P 500 (VUAA)")
        esperar("O mesmo dinheiro no VUAA")
        esperar("bateste o índice")
        foto("30-rentabilidade-indice")
        deslizarPara(5)
        esperar("Simular uma venda")
        esperar("Recebes, ao preço de agora")
        esperar("Custo dessas ações (FIFO)")
        esperar("IRS estimado (28%)")
        esperar("Fica para ti")
        foto("30b-simular-1")
        tocar("+")
        esperar("Saldo de mais-valias de 2026")
        foto("30c-simular-2")
        tocar("Tudo")
        foto("30d-simular-tudo")
        tocarDescricao("Voltar")
        // A BORE tem sinal de reduzir: o simulador começa na sugestão da folha (470 € ≈ 4 ações).
        abrirPosicao("BORE")
        esperar("Quando comprar e vender")
        deslizarPara(5)
        esperar("Sugestão da folha")
        esperar("de 13 ações")
        esperar("Peso na carteira")
        foto("30e-simular-sugestao")
        tocarDescricao("Voltar")
        // A lista de todas, a partir da Evolução.
        tocarSeparador("Evolução")
        deslizarPara(0)
        tocar("Carteira")
        // Itens da vista Carteira: 0 filtros, 1 gráfico, 2 o atalho para cada ação contra o S&P 500.
        deslizarPara(2)
        tocar("Cada ação contra o S&P 500")
        esperar("4 de 13 ações à frente do VUAA")
        esperar("Tab Validação S&P 500")
        foto("31-indice")
        val marcas = listOf("JADE", "ALFA", "ILHA", "HORT", "MARE", "vendida")
        val vistas = mutableSetOf<String>()
        for (k in 0 until 20) {
            try {
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(k)
            } catch (e: IllegalArgumentException) {
                break // passou do último item
            }
            assentar()
            marcas.filterTo(vistas) { compose.onAllNodes(hasText(it, substring = true)).fetchSemanticsNodes().isNotEmpty() }
            foto("31b-indice-${k.toString().padStart(2, '0')}")
        }
        assertEquals(marcas.toSet(), vistas)
        deslizarPara(0)
        tocar("Só as que tens")
        esperar("4 de 10 ações à frente do VUAA")
        foto("31c-indice-tens")
        tocar("ETFs")
        esperar("0 de 3 ETF à frente do VUAA")
        foto("31d-indice-etfs")
    }

    /** Tema escuro (a app segue o do telemóvel): os ecrãs principais, para rever cores e contraste. */
    @Test
    @Config(qualifiers = "+night")
    fun temaEscuro() {
        abrir()
        esperar("Valor da carteira")
        percorrer("30-escuro-inicio", maximo = 7)
        deslizarPara(0)
        tocar("Reforço")
        esperar("Quanto investes este mês")
        percorrer("35-escuro-reforco", maximo = 6)
        tocarDescricao("Voltar")
        tocarSeparador("Evolução")
        esperar("Património total")
        percorrer("31-escuro-evolucao", maximo = 4)
        deslizarPara(8)
        esperar("Rendimentos por ano")
        foto("31b-escuro-rendimentos")
        tocar("Posições")
        esperar("ALFA")
        foto("32-escuro-posicoes")
        tocar("Pesos")
        esperar("As 5 maiores")
        foto("32b-escuro-pesos")
        tocar("Lista")
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
        deslizarPara(1)
        tocar("YTD")
        esperar("(até agora)")
        foto("22b-carteira-ytd")
        tocar("Posições")
        esperar("Vendas e posições fechadas")
        foto("23-posicoes")
        tocar("Pesos")
        esperar("As 5 maiores")
        percorrer("23b-posicoes-pesos", maximo = 6)
        deslizarPara(3)
        tocar("Lista")
        deslizarPara(0)
        // Com letra grande, a ALFA fica abaixo do ecrã: procura-se pela caixa de pesquisa.
        abrirPosicao("ALFA")
        esperar("Quando comprar e vender")
        percorrer("24-posicao", maximo = 8)
        tocarDescricao("Voltar")
        tocar("Vendas e posições fechadas")
        esperar("Mais-valias em")
        percorrer("24b-vendas", maximo = 30)
        deslizarPara(0)
        tocar("🎁 De borla")
        esperar("De borla?")
        percorrer("24c-de-borla", maximo = 3)
        tocarDescricao("Voltar")
        tocarSeparador("Evolução")
        deslizarPara(0)
        tocar("Carteira")
        deslizarPara(2)
        tocar("Cada ação contra o S&P 500")
        esperar("à frente do VUAA")
        percorrer("24d-indice", maximo = 20)
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
        tocarDescricao("Voltar")
        deslizarPara(0)
        tocar("Reforço")
        esperar("Quanto investes este mês")
        percorrer("29b-reforco", maximo = 8)
        tocarDescricao("Voltar")
        tocar("Posições")
        tocar("Ordenar: Valor ▾")
        foto("29c-posicoes-ordem")
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

    /** Reforço do mês (regra 10): o plano com a amostra, registar uma compra a partir dele e as frações. */
    @Test
    fun reforcoDoMes() {
        val fonte = abrir()
        esperar("Valor da carteira")
        tocar("Reforço")
        esperar("Quanto investes este mês")
        // 400 € do plano da folha: 2 GIRA (Core B em 🟢, até ao Ajuste sugerido) e 2 MUND (o ETF global).
        esperar("Investe 391,56")
        esperar("o reforço mensal do plano da folha")
        esperar("Sobram 8,44")
        foto("40-reforco")
        val marcas = listOf("GIRA", "MUND", "ETF global (regra 10)", "Em 🟢, mas ficaram de fora", "ILHA: 1 ação custa",
            "Como a app escolhe", "Peso das ações")
        val vistas = mutableSetOf<String>()
        for (k in 0..5) {
            deslizarPara(k)
            marcas.filterTo(vistas) { compose.onAllNodes(hasText(it, substring = true)).fetchSemanticsNodes().isNotEmpty() }
            foto("40b-reforco-${k.toString().padStart(2, '0')}")
        }
        assertEquals(marcas.toSet(), vistas)

        // Registar a 1.ª compra: o formulário vem preenchido com as unidades e o preço de agora.
        deslizarPara(2)
        tocar("Registar")
        esperar("Do reforço do mês")
        assertTrue(compose.onAllNodes(hasText("GIRA")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("74,6")).fetchSemanticsNodes().isNotEmpty())
        foto("40c-reforco-registar")
        tocar("Registar compra")
        val pedido = fonte.ultimoPedido!!
        assertEquals("GIRA", pedido.getString("ticker"))
        assertEquals("Compra", pedido.getString("tipo"))
        assertEquals(2.0, pedido.getDouble("unidades"), 1e-9)
        assertEquals(74.6, pedido.getDouble("preco"), 1e-9)
        // De volta ao reforço (a lista volta onde estava): a compra sai do que falta e o GIRA não volta a aparecer.
        esperar("Reforço do mês")
        deslizarPara(0)
        esperar("Já registaste em")
        esperar("✓ GIRA")
        foto("40d-reforco-registado")
        deslizarPara(2)
        esperar("Investe 262,50")
        foto("40e-reforco-falta")
        assertTrue(app.getSharedPreferences("carteira", Context.MODE_PRIVATE).getString("reforco_mes", "")!!.contains("|GIRA;2.0;"))
        deslizarPara(1)
        tocar("Recomeçar")
        deslizarPara(0)
        esperar("Investe 391,56")

        // Com frações, o dinheiro vai quase todo: até a ILHA leva uma parte (o ajuste dela é menor do que uma ação).
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        assentar()
        esperar("Investe 399,99")
        esperar("0,2348")
        foto("40f-reforco-fracoes")
    }

    /** Atalhos do Início e do ícone, a ordem das Posições e as preferências (guardadas no telemóvel). */
    @Test
    fun atalhosEPreferencias() {
        abrir()
        esperar("Valor da carteira")
        foto("41-inicio-atalhos")
        val preferencias = app.getSharedPreferences("carteira", Context.MODE_PRIVATE)

        // O atalho "Dividendo" do ícone da app abre o formulário por cima do Início.
        compose.runOnIdle { vm.abrir("ECRA:Dividendo") }
        assentar()
        esperar("Valor bruto recebido")
        tocarDescricao("Voltar")
        esperar("Valor da carteira")

        // Posições: a ordem escolha-se numa lista e fica guardada.
        tocar("Posições")
        tocar("Ordenar: Valor ▾")
        foto("42-posicoes-ordem")
        tocar("A–Z")
        esperar("Ordenar: A–Z")
        assertEquals("A–Z", preferencias.getString("ordem_posicoes", null))
        val topo = { t: String -> compose.onAllNodes(hasText(t) and hasClickAction()).onFirst().fetchSemanticsNode().boundsInRoot.top }
        assertTrue("ALFA antes da BORE", topo("ALFA") < topo("BORE"))
        foto("42b-posicoes-a-z")
        // A pesquisa (e o filtro) ficam ao voltar de uma empresa.
        abrirPosicao("GIRA")
        esperar("Quando comprar e vender")
        tocarDescricao("Voltar")
        esperar("Ordenar: A–Z")
        assertTrue(compose.onAllNodes(hasText("GIRA") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())

        // Definições: com a ponte ligada, as preferências vêm primeiro.
        tocarSeparador("Início")
        tocarDescricao("Definições")
        esperar("Preferências")
        foto("43-definicoes-preferencias")
        tocar("Posições")
        assertEquals("Posicoes", preferencias.getString("ecra_inicial", null))
        assertEquals(Ecra.Posicoes, CarteiraViewModel(app) { _, _ -> FonteFalsa(amostra) }.estado.value.ecra)
        // O valor habitual do reforço: o primeiro campo do ecrã.
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("1000")
        assentar()
        assertEquals(1000f, preferencias.getFloat("reforco_valor", 0f))
        tocarDescricao("Voltar")
        tocar("Reforço")
        esperar("Investe 916,56")
        esperar("o teu valor habitual")
        foto("44-reforco-1000")
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
