package app.carteira.dados

import org.json.JSONArray
import org.json.JSONObject

/** Converte o JSON da ponte nos modelos da app. Tolera campos em falta ou com texto ("—"). */
object Leitor {

    /** Lê a resposta de `acao=dados`. Lança um erro legível se a ponte respondeu com erro. */
    fun lerResposta(texto: String): Dados {
        val raiz = objeto(texto)
        if (!raiz.optBoolean("ok", false)) throw ErroPonte(raiz.texto("erro") ?: "A ponte não devolveu dados")
        val d = raiz.optJSONObject("dados") ?: throw ErroPonte("A resposta da ponte não tem dados")
        return lerDados(d, raiz.optInt("versaoPonte", 1))
    }

    /**
     * Preços e sinais para os alertas. Aceita a resposta leve de `acao=alertas` (ponte v2) ou a
     * resposta completa de `acao=dados`.
     */
    fun lerMercado(texto: String): Mercado {
        val raiz = objeto(texto)
        if (!raiz.optBoolean("ok", false)) throw ErroPonte(raiz.texto("erro") ?: "A ponte respondeu com erro")
        raiz.optJSONObject("dados")?.let { return Mercado.de(lerDados(it, raiz.optInt("versaoPonte", 1))) }
        val a = raiz.optJSONObject("alertas") ?: throw ErroPonte("A resposta da ponte não tem preços")
        val meta = a.optJSONObject("meta") ?: JSONObject()
        return Mercado(
            hoje = meta.texto("hoje") ?: "",
            atualizadoEm = meta.texto("atualizadoEm") ?: "",
            cotacoes = a.lista("posicoes") {
                Cotacao(
                    TipoCotacao.POSICAO, it.texto("ticker") ?: "?", it.texto("empresa") ?: it.texto("ticker") ?: "?",
                    it.num("precoUsd"), "USD", it.num("varDiaPct"), it.texto("zona"), it.texto("sinal"), it.texto("camada"),
                )
            } + a.lista("etfs") {
                Cotacao(
                    TipoCotacao.ETF, it.texto("ticker") ?: "?", it.texto("nome") ?: it.texto("ticker") ?: "?",
                    it.num("precoEur"), "EUR", it.num("varDiaPct"), null, null, "ETFs",
                )
            } + a.lista("watchlist") {
                Cotacao(
                    TipoCotacao.WATCHLIST, it.texto("ticker") ?: "?", it.texto("empresa") ?: it.texto("ticker") ?: "?",
                    it.num("preco"), null, null, null, it.texto("sinal"), it.texto("tabela"),
                    it.num("valorJusto"), it.num("margem"), it.texto("testeTroca"),
                )
            },
        )
    }

    fun lerRegisto(texto: String): RespostaRegisto {
        val raiz = objeto(texto)
        val ok = raiz.optBoolean("ok", false)
        return RespostaRegisto(
            ok = ok,
            mensagem = if (ok) raiz.texto("mensagem") ?: "Registado." else raiz.texto("erro") ?: "Erro desconhecido",
            aviso = raiz.texto("aviso"),
        )
    }

    /** Resposta de `acao=ping`: nome da folha. */
    fun lerPing(texto: String): String {
        val raiz = objeto(texto)
        if (!raiz.optBoolean("ok", false)) throw ErroPonte(raiz.texto("erro") ?: "A ponte respondeu com erro")
        return raiz.texto("folha") ?: "folha"
    }

    private fun objeto(texto: String): JSONObject {
        val t = texto.trim()
        if (t.startsWith("<")) {
            throw ErroPonte(
                "A ponte respondeu com uma página em vez de dados. Na implementação do Apps Script, " +
                    "confirma \"Quem tem acesso: Qualquer pessoa\" e usa o URL que acaba em /exec."
            )
        }
        return try {
            JSONObject(t)
        } catch (e: Exception) {
            throw ErroPonte("Resposta da ponte ilegível: ${t.take(80)}")
        }
    }

    fun lerDados(d: JSONObject, versaoPonte: Int = 1): Dados {
        val meta = d.optJSONObject("meta") ?: JSONObject()
        val cambio = d.optJSONObject("cambio") ?: JSONObject()
        return Dados(
            meta = Meta(
                folha = meta.texto("folha") ?: "",
                atualizadoEm = meta.texto("atualizadoEm") ?: "",
                hoje = meta.texto("hoje") ?: "",
                avisos = meta.textos("avisos"),
            ),
            versaoPonte = versaoPonte,
            evolucao = d.lista("evolucao") { o ->
                o.num("carteiraUsd")?.let { carteira ->
                    PontoEvolucao(
                        mes = o.texto("mes") ?: "",
                        carteiraUsd = carteira,
                        investidoUsd = o.num("investidoUsd"),
                        sp500Usd = o.num("sp500Usd"),
                        netWorthEur = o.num("netWorthEur"),
                        reforcoEur = o.num("reforcoEur"),
                        metaBaseEur = o.num("metaBaseEur"),
                    )
                }
            }.filterNotNull().filter { it.mes.length >= 7 },
            objetivos = d.lista("objetivos") { o ->
                o.num("real")?.let { real ->
                    Objetivo(
                        objetivo = o.texto("objetivo") ?: "",
                        meta = o.num("meta"),
                        real = real,
                        metaTexto = o.texto("metaTexto"),
                        realTexto = o.texto("realTexto"),
                        diferencaTexto = o.texto("diferencaTexto"),
                        estado = o.texto("estado") ?: "",
                    )
                }
            }.filterNotNull(),
            patrimonio = d.optJSONObject("patrimonio")?.let { p ->
                Patrimonio(
                    totalEur = p.num("totalEur"),
                    classes = p.lista("classes") { o ->
                        o.num("valorEur")?.let { valor ->
                            ClassePatrimonio(
                                classe = o.texto("classe") ?: "?",
                                investidoEur = o.num("investidoEur"),
                                valorEur = valor,
                                valorizacao = o.num("valorizacao"),
                                rendimentoAnualEur = o.num("rendimentoAnualEur"),
                            )
                        }
                    }.filterNotNull(),
                )
            },
            comparacoes = d.lista("comparacoes") { o ->
                o.num("diferencaEur")?.let { dif ->
                    Comparacao(
                        comparacao = o.texto("comparacao") ?: "",
                        carteiraEur = o.num("carteiraEur"),
                        indiceEur = o.num("indiceEur"),
                        diferencaEur = dif,
                        leitura = o.texto("leitura"),
                        principal = o.optBoolean("principal", false),
                    )
                }
            }.filterNotNull(),
            setores = d.lista("setores") { o ->
                o.num("valorEur")?.let { valor ->
                    Setor(
                        setor = o.texto("setor") ?: "?",
                        valorEur = valor,
                        pesoAcoes = o.num("pesoAcoes"),
                        numero = o.num("numero"),
                        limite = o.num("limite"),
                        estado = o.texto("estado"),
                    )
                }
            }.filterNotNull(),
            cambio = Cambio(cambio.num("usdEur"), cambio.num("gbpUsd")),
            totaisAcoes = d.optJSONObject("totaisAcoes")?.let {
                TotaisAcoes(
                    custoUsd = it.num("custoUsd"),
                    valorUsd = it.num("valorUsd"),
                    ganhoUsd = it.num("ganhoUsd"),
                    ganhoPct = it.num("ganhoPct"),
                    dividendosAnoUsd = it.num("dividendosAnoUsd"),
                    custoEur = it.num("custoEur"),
                    valorEur = it.num("valorEur"),
                    ajusteSugeridoEur = it.num("ajusteSugeridoEur"),
                )
            },
            totaisEtfs = d.optJSONObject("totaisEtfs")?.let {
                TotaisEtfs(it.num("custoEur"), it.num("valorEur"), it.num("ganhoEur"), it.num("ganhoPct"))
            },
            camadas = d.lista("camadas") {
                Camada(
                    camada = it.texto("camada") ?: "",
                    numero = it.num("numero"),
                    numeroIdeal = it.num("numeroIdeal"),
                    pesoHoje = it.num("pesoHoje"),
                    pesoIdeal = it.num("pesoIdeal"),
                    valorEur = it.num("valorEur"),
                    diferencaEur = it.num("diferencaEur"),
                )
            },
            indicadores = d.lista("indicadores", ::lerIndicador),
            regras = d.lista("regras") {
                Regra(
                    regra = it.texto("regra") ?: "",
                    hoje = it.texto("hoje") ?: "",
                    limite = it.texto("limite") ?: "",
                    estado = it.texto("estado") ?: "",
                    nota = it.texto("nota"),
                )
            },
            posicoes = d.lista("posicoes", ::lerPosicao),
            etfs = d.lista("etfs") {
                Etf(
                    ticker = it.texto("ticker") ?: "?",
                    nome = it.texto("nome"),
                    quantidade = it.num("quantidade") ?: 0.0,
                    precoCompraEur = it.num("precoCompraEur"),
                    precoEur = it.num("precoEur"),
                    varDiaPct = it.num("varDiaPct"),
                    custoEur = it.num("custoEur"),
                    valorEur = it.num("valorEur"),
                    ganhoEur = it.num("ganhoEur"),
                    ganhoPct = it.num("ganhoPct"),
                    pesoEtfs = it.num("pesoEtfs"),
                )
            },
            tickers = d.lista("tickers") {
                TickerInfo(
                    ticker = it.texto("ticker") ?: "?",
                    nome = it.texto("nome"),
                    classe = it.texto("classe") ?: "Ação",
                    quantidade = it.num("quantidade") ?: 0.0,
                )
            },
            dividendos = lerDividendos(d.optJSONObject("dividendos") ?: JSONObject()),
            watchlist = d.lista("watchlist") {
                ItemWatchlist(
                    tabela = it.texto("tabela") ?: "",
                    ticker = it.texto("ticker") ?: "?",
                    empresa = it.texto("empresa"),
                    nota = it.texto("nota"),
                    preco = it.num("preco"),
                    intervalo52s = it.texto("intervalo52s"),
                    pe = it.num("pe"),
                    peJusto = it.num("peJusto"),
                    valorJusto = it.num("valorJusto"),
                    margem = it.num("margem"),
                    sinal = it.texto("sinal"),
                    prioridade = it.texto("prioridade"),
                    moat = it.texto("moat"),
                    camadaPotencial = it.texto("camadaPotencial"),
                    crescimento = it.num("crescimento"),
                    fcfYield = it.num("fcfYield"),
                    testeTroca = it.texto("testeTroca"),
                )
            },
            transacoes = d.lista("transacoes") {
                Transacao(
                    linha = it.num("linha")?.toInt() ?: 0,
                    data = it.texto("data"),
                    tipo = it.texto("tipo"),
                    classe = it.texto("classe"),
                    ticker = it.texto("ticker") ?: "?",
                    unidades = it.num("unidades"),
                    preco = it.num("preco"),
                    moeda = it.texto("moeda"),
                    valor = it.num("valor"),
                    comissao = it.num("comissao"),
                )
            },
        )
    }

    private fun lerIndicador(o: JSONObject) = Indicador(
        rotulo = o.texto("rotulo") ?: "",
        valor = o.num("valor") ?: 0.0,
        texto = o.texto("texto") ?: "",
    )

    private fun lerPosicao(o: JSONObject) = Posicao(
        ticker = o.texto("ticker") ?: "?",
        empresa = o.texto("empresa"),
        setor = o.texto("setor"),
        camada = o.texto("camada"),
        quantidade = o.num("quantidade") ?: 0.0,
        precoCompraUsd = o.num("precoCompraUsd"),
        precoUsd = o.num("precoUsd"),
        varDiaPct = o.num("varDiaPct"),
        pe = o.num("pe"),
        custoUsd = o.num("custoUsd"),
        valorUsd = o.num("valorUsd"),
        ganhoUsd = o.num("ganhoUsd"),
        ganhoPct = o.num("ganhoPct"),
        pesoAcoes = o.num("pesoAcoes"),
        divAnualPorAcaoUsd = o.num("divAnualPorAcaoUsd"),
        yieldPct = o.num("yieldPct"),
        yieldCustoPct = o.num("yieldCustoPct"),
        frequencia = o.texto("frequencia"),
        risco = o.num("risco"),
        rating = o.texto("rating"),
        custoEur = o.num("custoEur"),
        valorEur = o.num("valorEur"),
        ganhoEur = o.num("ganhoEur"),
        pesoTotal = o.num("pesoTotal"),
        pesoAlvo = o.num("pesoAlvo"),
        pesoMaximo = o.num("pesoMaximo"),
        zona = o.texto("zona"),
        sinal = o.texto("sinal"),
        ajusteSugeridoEur = o.num("ajusteSugeridoEur"),
        avaliacao = o.optJSONObject("avaliacao")?.let { a ->
            Avaliacao(
                moeda = a.texto("moeda"),
                precoLocal = a.num("precoLocal"),
                rendImplicita = a.num("rendImplicita"),
                hurdle = a.num("hurdle"),
                valorIntrinseco = a.num("valorIntrinseco"),
                potencial = a.num("potencial"),
                reforcarForteAbaixo = a.num("reforcarForteAbaixo"),
                reforcarAbaixo = a.num("reforcarAbaixo"),
                comprarAbaixo = a.num("comprarAbaixo"),
                reduzirAcima = a.num("reduzirAcima"),
                venderAcima = a.num("venderAcima"),
                pesoVsMaximo = a.texto("pesoVsMaximo"),
                temaIa = a.optBoolean("temaIa", false),
                tese = a.texto("tese"),
                alarme = a.texto("alarme"),
                fiscalidade = a.texto("fiscalidade"),
                fonte = a.texto("fonte"),
            )
        },
    )

    private fun lerDividendos(o: JSONObject) = Dividendos(
        indicadores = o.lista("indicadores", ::lerIndicador),
        previsaoAno = o.num("previsaoAno")?.toInt(),
        calendario = o.lista("calendario") {
            MesCalendario(
                mes = it.texto("mes") ?: "",
                previstoBrutoUsd = it.num("previstoBrutoUsd"),
                previstoLiquidoEur = it.num("previstoLiquidoEur"),
                recebidoAnoAnteriorUsd = it.num("recebidoAnoAnteriorUsd"),
                recebidoAnoUsd = it.num("recebidoAnoUsd"),
                quemPaga = it.texto("quemPaga"),
            )
        },
        anos = o.lista("anos") {
            AnoDividendos(
                ano = it.num("ano")?.toInt() ?: 0,
                meses = it.numeros("meses"),
                totalUsd = it.num("totalUsd"),
            )
        },
        esteAno = o.optJSONObject("esteAno")?.let { e ->
            EsteAno(
                ano = e.num("ano")?.toInt() ?: 0,
                empresas = e.lista("empresas") {
                    DividendoEmpresa(
                        ticker = it.texto("ticker") ?: "?",
                        empresa = it.texto("empresa"),
                        meses = it.numeros("meses"),
                        totalUsd = it.num("totalUsd") ?: 0.0,
                    )
                },
            )
        },
    )

    // ── Ajudas: org.json devolve "null" em texto e NaN em números; aqui ficam null. ──

    private fun JSONObject.texto(k: String): String? {
        if (!has(k) || isNull(k)) return null
        val v = opt(k)
        return if (v is String) v.trim().ifEmpty { null } else v?.toString()
    }

    private fun JSONObject.num(k: String): Double? {
        if (!has(k) || isNull(k)) return null
        val v = opt(k)
        return if (v is Number) v.toDouble().takeIf { it.isFinite() } else null
    }

    private fun JSONObject.numeros(k: String): List<Double> {
        val a = optJSONArray(k) ?: return emptyList()
        return (0 until a.length()).map { i -> (a.opt(i) as? Number)?.toDouble() ?: 0.0 }
    }

    private fun JSONObject.textos(k: String): List<String> {
        val a = optJSONArray(k) ?: return emptyList()
        return (0 until a.length()).mapNotNull { i -> (a.opt(i) as? String)?.trim()?.ifEmpty { null } }
    }

    private fun <T> JSONObject.lista(k: String, ler: (JSONObject) -> T): List<T> {
        val a: JSONArray = optJSONArray(k) ?: return emptyList()
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(ler) }
    }
}

/** Erro com uma mensagem que se pode mostrar tal e qual. */
class ErroPonte(mensagem: String) : Exception(mensagem)
