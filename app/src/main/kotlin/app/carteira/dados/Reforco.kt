package app.carteira.dados

import kotlin.math.floor

/** Uma compra sugerida com o dinheiro novo do mês. */
data class CompraSugerida(
    val ticker: String,
    val nome: String?,
    val etf: Boolean,
    /** Porquê esta compra: "Core A · Comprar até ao alvo" ou "ETF global (regra 10)". */
    val motivo: String,
    val unidades: Double,
    /** Preço de uma unidade na moeda em que a compra se regista (dólares nas ações, euros nos ETF). */
    val preco: Double,
    val moeda: String,
    val precoEur: Double,
    val custoEur: Double,
    /** Até quanto a folha deixa comprar (o "Ajuste sugerido"), nas ações. */
    val limiteEur: Double?,
)

/** Uma compra do reforço já registada na folha: quantas unidades, quanto custou e quantas o ticker tinha antes. */
data class RegistoReforco(val ticker: String, val unidades: Double, val custoEur: Double, val quantidadeAntes: Double)

/**
 * O reforço do mês em curso ("aaaa-mm"): o valor escolhido para este mês (null = o habitual) e as compras
 * já registadas a partir dele. Fica guardado no telemóvel até mudar o mês ou recomeçares.
 */
data class MesReforco(val mes: String = "", val montanteEur: Double? = null, val registados: List<RegistoReforco> = emptyList()) {
    val registadoEur: Double get() = registados.sumOf { it.custoEur }

    /** "2026-11|1500.0|AAA;2.0;400.5;10.0|BBB;..." (sem vírgulas decimais, para não depender da língua). */
    fun paraTexto(): String = (listOf(mes, montanteEur?.toString() ?: "") +
        registados.map { "${it.ticker};${it.unidades};${it.custoEur};${it.quantidadeAntes}" }).joinToString("|")

    companion object {
        fun deTexto(texto: String?): MesReforco? {
            val partes = texto?.split('|') ?: return null
            if (partes.size < 2 || !Regex("\\d{4}-\\d{2}").matches(partes[0])) return null
            val registados = partes.drop(2).mapNotNull { r ->
                val c = r.split(';')
                if (c.size != 4 || c[0].isBlank()) return@mapNotNull null
                val n = c.drop(1).map { it.toDoubleOrNull() ?: return@mapNotNull null }
                RegistoReforco(c[0], n[0], n[1], n[2])
            }
            return MesReforco(partes[0], partes[1].toDoubleOrNull(), registados)
        }
    }
}

/** Um core em 🟢 que não leva nada: o dinheiro que sobra não chega para uma ação, ou a folha deixa menos do que isso. */
data class ForaDoReforco(val ticker: String, val precoEur: Double, val limiteEur: Double, val faltouDinheiro: Boolean)

/** Onde pôr o dinheiro novo, pelas regras da folha. */
data class PlanoReforco(
    val montanteEur: Double,
    val compras: List<CompraSugerida>,
    /** O que não chega para mais uma unidade (fica para o próximo mês). */
    val sobraEur: Double,
    /** Regra 10: as ações pesam mais do que a soma dos alvos + 5 pp, por isso vai tudo para o ETF. */
    val soEtf: Boolean,
    val pesoAcoes: Double?,
    val limiteAcoes: Double?,
    /** O ETF global que recebe o resto; null se a carteira não tiver nenhum. */
    val etfGlobal: String?,
    /** Core em 🟢 que ficaram de fora (por ex. uma ação custa mais do que o que sobra). */
    val deFora: List<ForaDoReforco>,
) {
    val investidoEur: Double get() = compras.sumOf { it.custoEur }
}

/**
 * O reforço do mês pela regra 10 da folha ("Dinheiro novo"): 1.º os core (Core A e Core B) em 🟢 e abaixo do
 * alvo, cada um até ao "Ajuste sugerido" da folha (que já traz os limites da regra 13); 2.º o ETF global
 * (ações do mundo todo). Se as ações pesarem mais do que a soma dos alvos + 5 pp, vai tudo para o ETF. Satélites e apostas
 * não recebem dinheiro novo (regras 5 e 10). Primeiro os 🟢🟢, depois o Core A e, a seguir, quem está mais
 * longe do alvo. Sem frações, compra ações inteiras e o que sobra fica para o mês seguinte.
 */
object Reforco {

    /**
     * [jaComprado]: por ticker, o que já compraste neste reforço e os dados ainda não mostram (ver [porRefletir]);
     * sai do "Ajuste sugerido" para a mesma ação não ser sugerida duas vezes.
     */
    fun planear(
        d: Dados,
        montanteEur: Double,
        fracoes: Boolean,
        etfEscolhido: String? = null,
        jaComprado: Map<String, Double> = emptyMap(),
    ): PlanoReforco {
        val usdEur = d.cambio.usdEur ?: 1.0
        val regra = d.regras.firstOrNull { it.regra.trim().startsWith("Peso das ações na carteira") }
        val peso = regra?.let { numero(it.hoje) }
        val limite = regra?.let { numero(it.limite) }
        val soEtf = peso != null && limite != null && peso > limite + 1e-9
        var resta = montanteEur.coerceAtLeast(0.0)
        val compras = ArrayList<CompraSugerida>()
        val deFora = ArrayList<ForaDoReforco>()

        if (!soEtf) {
            val candidatos = d.posicoes.filter { p ->
                (p.camada ?: "").startsWith("Core") && Sinais.compra(p.sinal) && (p.ajusteSugeridoEur ?: 0.0) > 0.5 &&
                    (p.precoUsd ?: 0.0) > 0 && (p.pesoAlvo == null || p.pesoTotal == null || p.pesoTotal < p.pesoAlvo)
            }.sortedWith(
                compareByDescending<Posicao> { Sinais.compraForte(it.sinal) }
                    .thenBy { if (it.camada == "Core A") 0 else 1 }
                    .thenByDescending { it.ajusteSugeridoEur ?: 0.0 },
            )
            for (p in candidatos) {
                val precoEur = p.precoUsd!! * usdEur
                val limiteEur = p.ajusteSugeridoEur!! - (jaComprado[p.ticker] ?: 0.0)
                if (limiteEur <= 0.5) continue
                val dinheiro = minOf(limiteEur, resta)
                val unidades = unidades(dinheiro, precoEur, fracoes)
                if (unidades <= 0) {
                    deFora += ForaDoReforco(p.ticker, precoEur, limiteEur, faltouDinheiro = resta < limiteEur)
                    continue
                }
                val custo = unidades * precoEur
                resta -= custo
                compras += CompraSugerida(
                    p.ticker, p.nome, false, "${p.camada} · ${Sinais.semEmoji(p.sinal)}", unidades, p.precoUsd, "USD",
                    precoEur, custo, limiteEur,
                )
            }
        }

        val etf = etfGlobal(d, etfEscolhido)
        val precoEtf = etf?.precoEur ?: 0.0
        if (etf != null && precoEtf > 0) {
            val unidades = unidades(resta, precoEtf, fracoes)
            if (unidades > 0) {
                val custo = unidades * precoEtf
                resta -= custo
                compras += CompraSugerida(
                    etf.ticker, etf.nome, true, if (soEtf) "ETF global (regra 10: ações acima do alvo)" else "ETF global (regra 10)",
                    unidades, precoEtf, "EUR", precoEtf, custo, null,
                )
            }
        }
        return PlanoReforco(montanteEur, compras, resta.coerceAtLeast(0.0), soEtf, peso, limite, etf?.ticker, deFora)
    }

    /** Quantas unidades a carteira tem do ticker (ação ou ETF), pelos dados de agora. */
    fun quantidade(d: Dados, ticker: String): Double =
        d.posicoes.firstOrNull { it.ticker == ticker }?.quantidade ?: d.etfs.firstOrNull { it.ticker == ticker }?.quantidade ?: 0.0

    /**
     * Das compras registadas, o que os dados ainda não mostram, em euros por ticker. Logo a seguir a registar, a
     * app ainda tem a quantidade de antes; quando a folha a atualiza, a compra passa a contar no "Ajuste
     * sugerido" e deixa de ser preciso descontá-la.
     */
    fun porRefletir(d: Dados, registados: List<RegistoReforco>): Map<String, Double> =
        registados.groupBy { it.ticker }.mapNotNull { (ticker, lista) ->
            val unidades = lista.sumOf { it.unidades }
            if (unidades <= 0) return@mapNotNull null
            val faltam = (lista.first().quantidadeAntes + unidades - quantidade(d, ticker)).coerceIn(0.0, unidades)
            if (faltam < 1e-6) null else ticker to lista.sumOf { it.custoEur } * faltam / unidades
        }.toMap()

    /**
     * O ETF que recebe o dinheiro novo: o escolhido nas Definições; senão o maior ETF de ações do mundo todo (pelo
     * nome: All-World, ACWI, World, Global, Mundo…, sem os de um setor); senão o ETF de ações com mais valor.
     * Os de obrigações ficam sempre de fora (pelo nome).
     */
    fun etfGlobal(d: Dados, escolhido: String? = null): Etf? {
        d.etfs.firstOrNull { it.ticker == escolhido }?.let { return it }
        val acoes = d.etfs.filter { !obrigacoes(it.nome) }
        return acoes.filter { global(it.nome) }.maxByOrNull { it.valorEur ?: 0.0 }
            ?: acoes.maxByOrNull { it.valorEur ?: 0.0 }
    }

    private val PALAVRAS_GLOBAL = listOf("all-world", "all world", "acwi", "world", "global", "mundo", "mundial")
    private val PALAVRAS_SETOR = listOf(
        "technology", "tecnologia", "information", "health", "saúde", "energy", "energia", "financ", "semicondu",
        "consumer", "consumo", "industr", "real estate", "imobili", "utilities", "materials", "momentum", "small",
    )

    /** Um ETF de ações do mundo todo (e não de um setor), pelo nome. */
    internal fun global(nome: String?): Boolean {
        val n = nome?.lowercase() ?: return false
        return PALAVRAS_GLOBAL.any { n.contains(it) } && PALAVRAS_SETOR.none { n.contains(it) }
    }

    private val PALAVRAS_OBRIGACOES = listOf("obriga", "bond", "treasury", "tesouro", "govt", "government", "aggregate")

    internal fun obrigacoes(nome: String?): Boolean {
        val n = nome?.lowercase() ?: return false
        return PALAVRAS_OBRIGACOES.any { n.contains(it) }
    }

    /** Unidades que o dinheiro compra: inteiras, ou com 4 casas se houver frações (mas nunca compras de menos de 1 €). */
    internal fun unidades(dinheiro: Double, preco: Double, fracoes: Boolean): Double {
        if (dinheiro <= 0 || preco <= 0) return 0.0
        val u = dinheiro / preco + 1e-9
        if (!fracoes) return floor(u)
        val f = floor(u * 10_000) / 10_000
        return if (f * preco < MINIMO_FRACAO_EUR) 0.0 else f
    }

    /** Abaixo disto, uma compra fracionada não vale a pena (fica na sobra). */
    private const val MINIMO_FRACAO_EUR = 1.0

    /** "0,7924" → 0.7924; "70,1%" → 0.701; "≤ 80%" → 0.8. */
    internal fun numero(texto: String?): Double? {
        if (texto == null) return null
        val t = texto.replace(" ", "").replace(" ", "")
        val m = Regex("-?\\d+(?:[.,]\\d+)?").find(t) ?: return null
        val x = m.value.replace(',', '.').toDoubleOrNull() ?: return null
        return if (t.contains('%')) x / 100 else x
    }

}
