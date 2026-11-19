package app.carteira.dados

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil

/** Património real de um mês (fim do mês; o mês corrente usa o valor de agora). */
data class PontoPatrimonio(val mes: String, val valorEur: Double, val reforcoEur: Double?)

data class AnoPatrimonio(
    val ano: Int,
    /** Património no fim do ano (ou no último mês com dados, se o ano ainda não acabou). */
    val fimEur: Double,
    val mesInicio: String,
    val mesFim: String,
    /** Crescimento face ao fim do ano anterior (no primeiro ano, face ao primeiro mês). */
    val crescimentoEur: Double,
    val crescimentoPct: Double?,
    /** Reforços na carteira nesse ano (compras − vendas de ações e ETFs, tab Transações). */
    val reforcosEur: Double,
    /** O ano ainda não acabou, ou é o primeiro e começou a meio. */
    val parcial: Boolean,
)

data class Marco(val valorEur: Double, val atingidoEm: String)

/** Onde está o património face às metas do mês (a mesma leitura do "Estou no caminho?" da folha). */
enum class PosicaoPlano { ACIMA_BASE, ENTRE_REFERENCIA_E_BASE, ABAIXO_REFERENCIA }

/**
 * Tudo o que o separador Património mostra, calculado a partir da tab Evolução Patrimonial.
 * Não tem nada de Android: é testado no computador (AnalisePatrimonioTest).
 */
data class AnalisePatrimonio(
    val serie: List<PontoPatrimonio>,
    val atualEur: Double,
    val mesAtual: String,
    val vsMesAnteriorEur: Double?,
    val fimAnoAnteriorEur: Double?,
    val esteAnoEur: Double?,
    val esteAnoPct: Double?,
    val reforcosEsteAnoEur: Double?,
    val inicio: PontoPatrimonio,
    val desdeInicioEur: Double,
    val reforcosDesdeInicioEur: Double,
    /** Quanto cresceu, em média, por mês nos últimos 12 meses (reforços incluídos). */
    val ritmoMensal12mEur: Double?,
    /** Média dos reforços nos últimos 12 meses fechados (como o objetivo da folha). */
    val reforcoMedio12mEur: Double?,
    val anos: List<AnoPatrimonio>,
    /** Do mais recente para o mais antigo. */
    val marcosAtingidos: List<Marco>,
    val marcoAnteriorEur: Double?,
    val proximoMarcoEur: Double?,
    /** Mês em que se chega ao próximo marco se o ritmo dos últimos 12 meses se mantiver. */
    val marcoAoRitmo: String?,
    /** Primeiro mês em que a meta base passa o próximo marco. */
    val marcoPeloPlano: String?,
    /** Metas para o fim do mês corrente. */
    val metaDoMes: MesPlano?,
    /**
     * Metas para hoje: entre a do fim do mês passado e a do fim deste, pelo dia do mês. A meio do mês
     * ainda faltam reforços, por isso comparar com a meta do fim do mês dava "abaixo" sem razão.
     */
    val metaHoje: MesPlano?,
    val posicaoPlano: PosicaoPlano?,
    val plano: Plano?,
) {
    val faltaParaMarcoEur: Double? get() = proximoMarcoEur?.let { it - atualEur }

    /** Última linha do plano (dez/2030 na folha). */
    val fimDoPlano: MesPlano? get() = plano?.meses?.lastOrNull { it.baseEur != null || it.referenciaEur != null }

    /** Metas de um mês: do plano (ponte v3) ou, se não houver, das colunas da evolução (ponte v2). */
    fun metaDe(mes: String): MesPlano? = metas[mes]

    private val metas: Map<String, MesPlano> by lazy { mapaDeMetas(plano, emptyList()) }

    companion object {
        val MARCOS = listOf(
            10_000.0, 25_000.0, 50_000.0, 75_000.0, 100_000.0, 150_000.0, 200_000.0, 250_000.0,
            300_000.0, 400_000.0, 500_000.0, 750_000.0, 1_000_000.0, 1_500_000.0, 2_000_000.0,
        )

        fun somarMeses(mes: String, n: Long): String = YearMonth.parse(mes).plusMonths(n).toString()

        fun mesesEntre(de: String, ate: String): Long =
            java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.parse(de), YearMonth.parse(ate))

        private fun mapaDeMetas(plano: Plano?, evolucao: List<PontoEvolucao>): Map<String, MesPlano> {
            val m = linkedMapOf<String, MesPlano>()
            evolucao.forEach { e ->
                if (e.metaBaseEur != null || e.metaReferenciaEur != null || e.metaAlargadaEur != null) {
                    m[e.mes] = MesPlano(e.mes, e.metaReferenciaEur, e.metaBaseEur, e.metaAlargadaEur, null)
                }
            }
            plano?.meses?.forEach { m[it.mes] = it }
            return m
        }

        /** null se a folha não tiver nenhum valor de património. */
        fun de(d: Dados): AnalisePatrimonio? = de(
            evolucao = d.evolucao,
            plano = d.plano,
            agoraEur = d.patrimonio?.totalEur ?: d.indicador("PATRIMÓNIO TOTAL (NET WORTH)")?.valor,
            hoje = d.meta.hoje,
        )

        /**
         * evolucao: a tab Evolução Patrimonial; plano: as metas (ponte v3); agoraEur: o património de agora
         * (tab Investimentos), para quando a tabela ainda não tem a linha do mês; hoje: "aaaa-mm-dd".
         */
        fun de(evolucao: List<PontoEvolucao>, plano: Plano?, agoraEur: Double?, hoje: String): AnalisePatrimonio? {
            val mesHoje = hoje.take(7).takeIf { it.length == 7 && runCatching { YearMonth.parse(it) }.isSuccess }
            val serie = evolucao
                .filter { runCatching { YearMonth.parse(it.mes) }.isSuccess }
                .mapNotNull { e -> e.netWorthEur?.let { PontoPatrimonio(e.mes, it, e.reforcoEur) } }
                .distinctBy { it.mes }
                .sortedBy { it.mes }
                .toMutableList()
            // O valor de agora (tab Investimentos), se a tabela ainda não tiver a linha do mês corrente.
            val agora = agoraEur
            if (agora != null && mesHoje != null && (serie.isEmpty() || serie.last().mes < mesHoje)) {
                serie += PontoPatrimonio(mesHoje, agora, null)
            }
            if (serie.isEmpty()) return null

            val atual = serie.last()
            val mesAtual = atual.mes
            val porMes = serie.associateBy { it.mes }
            val anterior = porMes[somarMeses(mesAtual, -1)]
            val anoAtual = mesAtual.take(4).toInt()
            val fimAnoAnterior = porMes["${anoAtual - 1}-12"] ?: serie.lastOrNull { it.mes.take(4).toInt() < anoAtual }
            val esteAno = fimAnoAnterior?.let { atual.valorEur - it.valorEur }
            val reforcosEsteAno = serie.filter { it.mes.startsWith("$anoAtual-") }.mapNotNull { it.reforcoEur }
                .takeIf { it.isNotEmpty() }?.sum()

            // Ritmo: crescimento médio por mês nos últimos 12 meses (ou desde o início, se houver menos).
            val ha12 = porMes[somarMeses(mesAtual, -12)]
            val ritmo = when {
                ha12 != null -> (atual.valorEur - ha12.valorEur) / 12
                serie.size >= 2 -> (atual.valorEur - serie.first().valorEur) /
                    mesesEntre(serie.first().mes, mesAtual).coerceAtLeast(1)
                else -> null
            }
            val fechados = serie.filter { it.mes < mesAtual }.takeLast(12).mapNotNull { it.reforcoEur }
            val reforcoMedio = fechados.takeIf { it.isNotEmpty() }?.average()

            val anos = serie.groupBy { it.mes.take(4).toInt() }.toSortedMap().map { (ano, pontos) ->
                val fim = pontos.last()
                val antes = serie.lastOrNull { it.mes.take(4).toInt() < ano }
                val base = antes ?: pontos.first()
                val crescimento = fim.valorEur - base.valorEur
                AnoPatrimonio(
                    ano = ano,
                    fimEur = fim.valorEur,
                    mesInicio = pontos.first().mes,
                    mesFim = fim.mes,
                    crescimentoEur = crescimento,
                    crescimentoPct = if (base.valorEur > 0) crescimento / base.valorEur else null,
                    // No primeiro ano, o reforço do primeiro mês já está no valor de partida.
                    reforcosEur = (if (antes == null) pontos.drop(1) else pontos).mapNotNull { it.reforcoEur }.sum(),
                    parcial = ano == anoAtual || (antes == null && !pontos.first().mes.endsWith("-01")),
                )
            }

            // Marcos atravessados desde o início do registo (os que já estavam para trás não contam).
            val inicio = serie.first()
            val atingidos = MARCOS.filter { it > inicio.valorEur }.mapNotNull { nivel ->
                serie.firstOrNull { it.valorEur >= nivel }?.let { Marco(nivel, it.mes) }
            }.reversed()
            val proximo = MARCOS.firstOrNull { it > atual.valorEur }
            val marcoAnterior = MARCOS.lastOrNull { it <= atual.valorEur }
            val aoRitmo = if (proximo != null && ritmo != null && ritmo > 0) {
                somarMeses(mesAtual, ceil((proximo - atual.valorEur) / ritmo).toLong().coerceAtLeast(1))
            } else {
                null
            }
            val metas = mapaDeMetas(plano, evolucao)
            val pelaBase = proximo?.let { alvo ->
                metas.values.sortedBy { it.mes }.firstOrNull { it.mes >= mesAtual && (it.baseEur ?: 0.0) >= alvo }?.mes
            }
            val metaDoMes = metas[mesAtual]
            val dia = runCatching { LocalDate.parse(hoje.take(10)) }.getOrNull()
            val metaHoje = if (metaDoMes != null && dia != null && YearMonth.from(dia).toString() == mesAtual) {
                val antes = metas[somarMeses(mesAtual, -1)]
                val f = dia.dayOfMonth.toDouble() / dia.lengthOfMonth()
                fun entre(a: Double?, b: Double?) = if (a != null && b != null) a + (b - a) * f else b
                MesPlano(
                    mesAtual,
                    entre(antes?.referenciaEur, metaDoMes.referenciaEur),
                    entre(antes?.baseEur, metaDoMes.baseEur),
                    entre(antes?.alargadaEur, metaDoMes.alargadaEur),
                    entre(antes?.historicaEur, metaDoMes.historicaEur),
                )
            } else {
                metaDoMes
            }
            val posicao = metaHoje?.baseEur?.let { base ->
                val referencia = metaHoje.referenciaEur
                when {
                    atual.valorEur >= base -> PosicaoPlano.ACIMA_BASE
                    referencia == null || atual.valorEur >= referencia -> PosicaoPlano.ENTRE_REFERENCIA_E_BASE
                    else -> PosicaoPlano.ABAIXO_REFERENCIA
                }
            }

            return AnalisePatrimonio(
                serie = serie,
                atualEur = atual.valorEur,
                mesAtual = mesAtual,
                vsMesAnteriorEur = anterior?.let { atual.valorEur - it.valorEur },
                fimAnoAnteriorEur = fimAnoAnterior?.valorEur,
                esteAnoEur = esteAno,
                esteAnoPct = if (esteAno != null && fimAnoAnterior != null && fimAnoAnterior.valorEur > 0) {
                    esteAno / fimAnoAnterior.valorEur
                } else {
                    null
                },
                reforcosEsteAnoEur = reforcosEsteAno,
                inicio = inicio,
                desdeInicioEur = atual.valorEur - inicio.valorEur,
                reforcosDesdeInicioEur = serie.drop(1).mapNotNull { it.reforcoEur }.sum(),
                ritmoMensal12mEur = ritmo,
                reforcoMedio12mEur = reforcoMedio,
                anos = anos,
                marcosAtingidos = atingidos,
                marcoAnteriorEur = marcoAnterior,
                proximoMarcoEur = proximo,
                marcoAoRitmo = aoRitmo,
                marcoPeloPlano = pelaBase,
                metaDoMes = metaDoMes,
                metaHoje = metaHoje,
                posicaoPlano = posicao,
                plano = plano ?: metas.values.toList().takeIf { it.isNotEmpty() }?.let {
                    Plano(null, null, null, null, it.sortedBy { m -> m.mes })
                },
            )
        }
    }
}
