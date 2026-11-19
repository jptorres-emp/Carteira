package app.carteira.alertas

import app.carteira.dados.Cotacao
import app.carteira.dados.Mercado
import app.carteira.dados.Sinais
import app.carteira.dados.TipoCotacao
import app.carteira.ui.Formatos
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

enum class TipoAlerta { COMPRA, VENDA, WATCHLIST, VARIACAO }

data class Alerta(
    val tipo: TipoAlerta,
    /** "P:ALFA" (posição), "E:MUND" (ETF) ou "W:KAPA" (watchlist): diz o que abrir ao tocar. */
    val chave: String,
    val ticker: String,
    val titulo: String,
    val texto: String,
    /** Hora de Lisboa, "aaaa-mm-ddThh:mm". */
    val quando: String,
    /** Compra ou subida (verde); venda ou descida (vermelho). */
    val positivo: Boolean,
)

data class PreferenciasAlertas(
    /** Sinal de compra nas posições, segundo as regras da folha. */
    val compras: Boolean = true,
    /** Empresa da watchlist que passa a 🟢 Subvalorizada. */
    val watchlist: Boolean = true,
    /** Sinal de reduzir ou sair nas posições. */
    val vendas: Boolean = false,
    /** Variação grande de preço num dia. */
    val variacao: Boolean = true,
    /** 0,05 = 5%. A partir do dobro há um segundo aviso no mesmo dia. */
    val limiar: Double = 0.05,
)

/** O que se viu de cada empresa na última verificação. */
data class EstadoItem(
    val sinal: String?,
    val preco: Double?,
    /** Dia (aaaa-mm-dd) em que se viu o preço. */
    val diaPreco: String?,
    /** Último preço de um dia anterior: a base da variação da watchlist, que a folha não tem. */
    val precoRef: Double?,
    val diaRef: String?,
    /** Nível de variação já avisado (1 = limiar, 2 = dobro) e o dia desse aviso. */
    val nivelAvisado: Int = 0,
    val diaAvisado: String? = null,
)

data class EstadoAlertas(
    val itens: Map<String, EstadoItem>,
    /** "COMPRA:P:ALFA" → quando foi o último aviso desse tipo para essa empresa. */
    val ultimos: Map<String, String>,
)

/**
 * Compara o que a folha mostra agora com o que se viu da última vez e decide o que avisar.
 *
 * Só avisa mudanças: um sinal que passa a compra, uma empresa da watchlist que passa a
 * subvalorizada, uma variação que passa o limiar. Na primeira verificação só regista o estado.
 */
object Detetor {
    /** Depois de avisar uma entrada em zona, não repete o mesmo aviso durante 3 dias (o preço pode oscilar no limite). */
    private const val PAUSA_DIAS = 3L

    /** A variação da watchlist compara com o último preço visto até 4 dias antes (cobre o fim de semana). */
    private const val REFERENCIA_MAX_DIAS = 4L

    fun comparar(
        antes: EstadoAlertas?,
        mercado: Mercado,
        prefs: PreferenciasAlertas,
        agora: LocalDateTime,
    ): Pair<List<Alerta>, EstadoAlertas> {
        val quando = agora.truncatedTo(ChronoUnit.MINUTES).toString().take(16)
        val hoje = mercado.hoje.takeIf { it.length == 10 } ?: agora.toLocalDate().toString()
        val novos = mutableListOf<Alerta>()
        val itens = mutableMapOf<String, EstadoItem>()
        val ultimos = antes?.ultimos.orEmpty().filterValues { dentroDe(it, agora, PAUSA_DIAS * 2) }.toMutableMap()

        fun emitir(tipo: TipoAlerta, c: Cotacao, titulo: String, texto: String, positivo: Boolean, comPausa: Boolean, sufixo: String = "") {
            val id = "${tipo.name}$sufixo:${c.chave}"
            if (comPausa && ultimos[id]?.let { dentroDe(it, agora, PAUSA_DIAS) } == true) return
            ultimos[id] = quando
            novos += Alerta(tipo, c.chave, c.ticker, titulo, texto, quando, positivo)
        }

        for (c in mercado.cotacoes.distinctBy { it.chave }) {
            val ant = antes?.itens?.get(c.chave)

            // Base da variação da watchlist: o último preço visto num dia anterior (≈ fecho).
            var precoRef = ant?.precoRef
            var diaRef = ant?.diaRef
            if (ant?.preco != null && ant.diaPreco != null && ant.diaPreco < hoje) {
                precoRef = ant.preco
                diaRef = ant.diaPreco
            }

            if (ant != null) {
                when (c.tipo) {
                    TipoCotacao.POSICAO -> {
                        val entrouEmCompra = Sinais.compra(c.sinal) && !Sinais.compra(ant.sinal)
                        val ficouForte = Sinais.compraForte(c.sinal) && !Sinais.compraForte(ant.sinal)
                        if (prefs.compras && (entrouEmCompra || ficouForte)) {
                            // Passar de 🟢 a 🟢🟢 é um sinal mais forte: tem a sua própria pausa.
                            emitir(
                                TipoAlerta.COMPRA, c, "${c.ticker} · ${Sinais.semEmoji(c.sinal)}", textoPosicao(c), true, true,
                                sufixo = if (ficouForte) "+" else "",
                            )
                        }
                        val venda = Sinais.reduzir(c.sinal) || Sinais.sair(c.sinal)
                        val jaEraVenda = Sinais.reduzir(ant.sinal) || Sinais.sair(ant.sinal)
                        if (prefs.vendas && venda && !jaEraVenda) {
                            emitir(TipoAlerta.VENDA, c, "${c.ticker} · ${Sinais.semEmoji(c.sinal)}", textoPosicao(c), false, true)
                        }
                    }
                    TipoCotacao.WATCHLIST -> {
                        if (prefs.watchlist && Sinais.compra(c.sinal) && !Sinais.compra(ant.sinal)) {
                            val texto = buildString {
                                append(c.nome).append(" · ").append(Formatos.numero(c.preco))
                                append(" → justo ").append(Formatos.numero(c.valorJusto))
                                c.margem?.let { append(" (margem ").append(Formatos.pctSinal(it)).append(")") }
                                c.testeTroca?.let { append(" · ").append(it) }
                            }
                            emitir(TipoAlerta.WATCHLIST, c, "${c.ticker} · abaixo do valor justo", texto, true, true)
                        }
                    }
                    TipoCotacao.ETF -> Unit
                }
            }

            // Variação grande: a da folha (posições e ETFs) ou a calculada (watchlist).
            var nivel = if (ant?.diaAvisado == hoje) ant.nivelAvisado else 0
            var diaAvisado = if (ant?.diaAvisado == hoje) hoje else null
            val variacao = c.varDiaPct ?: variacaoDesde(c.preco, precoRef, diaRef, hoje)
            if (prefs.variacao && variacao != null && prefs.limiar > 0) {
                val n = when {
                    abs(variacao) >= 2 * prefs.limiar -> 2
                    abs(variacao) >= prefs.limiar -> 1
                    else -> 0
                }
                if (n > nivel) {
                    // Na primeira vez que se vê a empresa só se regista (não se sabe se já foi avisado).
                    if (ant != null) {
                        val quandoTexto = if (c.varDiaPct != null) "hoje" else "desde ${Formatos.data(diaRef).take(5)}"
                        val texto = buildString {
                            append(c.nome).append(" · ").append(precoTexto(c))
                            when (c.tipo) {
                                TipoCotacao.POSICAO -> {
                                    c.grupo?.let { append(" · ").append(it) }
                                    if (!c.sinal.isNullOrBlank()) append(" · sinal: ").append(Sinais.semEmoji(c.sinal))
                                }
                                TipoCotacao.ETF -> append(" · ETF")
                                TipoCotacao.WATCHLIST -> append(" · watchlist")
                            }
                        }
                        emitir(
                            TipoAlerta.VARIACAO, c, "${c.ticker} ${Formatos.pctSinal(variacao)} $quandoTexto", texto,
                            variacao > 0, false,
                        )
                    }
                    nivel = n
                    diaAvisado = hoje
                }
            }

            itens[c.chave] = EstadoItem(
                sinal = c.sinal,
                preco = c.preco ?: ant?.preco,
                diaPreco = if (c.preco != null) hoje else ant?.diaPreco,
                precoRef = precoRef,
                diaRef = diaRef,
                nivelAvisado = nivel,
                diaAvisado = diaAvisado,
            )
        }
        return novos to EstadoAlertas(itens, ultimos)
    }

    private fun textoPosicao(c: Cotacao): String = buildString {
        append(c.nome).append(" · ").append(precoTexto(c))
        c.varDiaPct?.let { append(" (hoje ").append(Formatos.pctSinal(it)).append(")") }
        c.zona?.let { append(" · zona ").append(it) }
    }

    private fun precoTexto(c: Cotacao): String = when (c.moeda) {
        "USD" -> Formatos.preco(c.preco, "USD")
        "EUR" -> Formatos.preco(c.preco, "EUR")
        else -> Formatos.numero(c.preco)
    }

    private fun variacaoDesde(preco: Double?, ref: Double?, diaRef: String?, hoje: String): Double? {
        if (preco == null || ref == null || ref <= 0 || diaRef == null || diaRef >= hoje) return null
        val dias = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(diaRef), LocalDate.parse(hoje)) }.getOrNull()
        if (dias == null || dias > REFERENCIA_MAX_DIAS) return null
        return preco / ref - 1
    }

    private fun dentroDe(quando: String, agora: LocalDateTime, dias: Long): Boolean = try {
        !LocalDateTime.parse(quando).isBefore(agora.minusDays(dias))
    } catch (e: DateTimeParseException) {
        false
    }
}

/** Guarda o estado e o histórico dos alertas em JSON (ficheiros da app). */
object ArquivoAlertas {

    fun estadoParaJson(e: EstadoAlertas): String {
        val itens = JSONObject()
        e.itens.forEach { (k, i) ->
            itens.put(
                k,
                JSONObject()
                    .put("s", i.sinal ?: JSONObject.NULL)
                    .put("p", i.preco ?: JSONObject.NULL)
                    .put("dp", i.diaPreco ?: JSONObject.NULL)
                    .put("pr", i.precoRef ?: JSONObject.NULL)
                    .put("dr", i.diaRef ?: JSONObject.NULL)
                    .put("n", i.nivelAvisado)
                    .put("da", i.diaAvisado ?: JSONObject.NULL),
            )
        }
        val ultimos = JSONObject()
        e.ultimos.forEach { (k, v) -> ultimos.put(k, v) }
        return JSONObject().put("itens", itens).put("ultimos", ultimos).toString()
    }

    fun estadoDeJson(texto: String?): EstadoAlertas? {
        if (texto.isNullOrBlank()) return null
        return runCatching {
            val raiz = JSONObject(texto)
            val itens = mutableMapOf<String, EstadoItem>()
            raiz.optJSONObject("itens")?.let { o ->
                o.keys().forEach { k ->
                    val i = o.getJSONObject(k)
                    itens[k] = EstadoItem(
                        sinal = i.textoOuNulo("s"),
                        preco = i.numeroOuNulo("p"),
                        diaPreco = i.textoOuNulo("dp"),
                        precoRef = i.numeroOuNulo("pr"),
                        diaRef = i.textoOuNulo("dr"),
                        nivelAvisado = i.optInt("n", 0),
                        diaAvisado = i.textoOuNulo("da"),
                    )
                }
            }
            val ultimos = mutableMapOf<String, String>()
            raiz.optJSONObject("ultimos")?.let { o -> o.keys().forEach { k -> ultimos[k] = o.getString(k) } }
            EstadoAlertas(itens, ultimos)
        }.getOrNull()
    }

    fun historicoParaJson(lista: List<Alerta>): String {
        val a = JSONArray()
        lista.forEach {
            a.put(
                JSONObject()
                    .put("tipo", it.tipo.name)
                    .put("chave", it.chave)
                    .put("ticker", it.ticker)
                    .put("titulo", it.titulo)
                    .put("texto", it.texto)
                    .put("quando", it.quando)
                    .put("positivo", it.positivo),
            )
        }
        return a.toString()
    }

    fun historicoDeJson(texto: String?): List<Alerta> {
        if (texto.isNullOrBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(texto)
            (0 until a.length()).mapNotNull { k ->
                val o = a.optJSONObject(k) ?: return@mapNotNull null
                val tipo = runCatching { TipoAlerta.valueOf(o.getString("tipo")) }.getOrNull() ?: return@mapNotNull null
                Alerta(
                    tipo = tipo,
                    chave = o.optString("chave"),
                    ticker = o.optString("ticker"),
                    titulo = o.optString("titulo"),
                    texto = o.optString("texto"),
                    quando = o.optString("quando"),
                    positivo = o.optBoolean("positivo", true),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.textoOuNulo(k: String): String? =
        if (!has(k) || isNull(k)) null else optString(k).ifEmpty { null }

    private fun JSONObject.numeroOuNulo(k: String): Double? =
        if (!has(k) || isNull(k)) null else (opt(k) as? Number)?.toDouble()?.takeIf { it.isFinite() }
}
