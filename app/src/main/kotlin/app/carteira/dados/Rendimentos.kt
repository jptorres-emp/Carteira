package app.carteira.dados

import java.time.YearMonth

/** Uma parte de um tipo de rendimento: "Certificados de Aforro 654 €", "ETFs 250 €". */
data class ParteRendimento(
    val nome: String,
    val valorEur: Double,
    /** true: conta pelo que pagou nos últimos 12 meses (e não pela estimativa da folha). */
    val pagoUltimos12m: Boolean = false,
)

/** Dividendos, juros, P2P…: o total do tipo e as partes de que é feito. */
data class TipoRendimento(val tipo: String, val partes: List<ParteRendimento>) {
    val valorEur: Double get() = partes.sumOf { it.valorEur }
}

/**
 * O que o património rende num ano, por tipo: dividendos, juros, P2P e o resto. A base é a tab Investimentos
 * (coluna Rendimento Anual: o valor de cada classe × a taxa que lá puseste). Os ETFs que a folha deixa a 0 mas
 * pagaram distribuições contam pelo que pagaram nos últimos 12 meses (tab Dividendos Mensais, ponte v5).
 * Tudo bruto, antes de impostos.
 */
data class Rendimentos(
    /** Só os tipos que rendem, pela ordem de TIPOS. */
    val tipos: List<TipoRendimento>,
    val patrimonioEur: Double?,
    /** Dividendos recebidos (ações e ETFs, brutos, em euros) nos últimos 12 meses e desde janeiro. */
    val dividendos12mEur: Double?,
    val dividendosAnoEur: Double?,
    val ano: Int?,
) {
    val totalEur: Double get() = tipos.sumOf { it.valorEur }
    val porMesEur: Double get() = totalEur / 12
    /** O que rende em % do património todo. */
    val taxa: Double? get() = patrimonioEur?.takeIf { it > 0 }?.let { totalEur / it }
    /** Depois dos 28% de IRS sobre dividendos e juros (taxa liberatória e autónoma em Portugal). */
    val liquidoEur: Double get() = totalEur * (1 - IRS)
    val temPagosUltimos12m: Boolean get() = tipos.any { t -> t.partes.any { it.pagoUltimos12m } }

    companion object {
        const val IRS = 0.28
        const val DIVIDENDOS = "Dividendos"
        const val JUROS = "Juros"
        const val P2P = "P2P"
        const val RENDAS = "Rendas"
        const val OUTROS = "Outros"

        /** A ordem em que os tipos aparecem. */
        val TIPOS = listOf(DIVIDENDOS, JUROS, P2P, RENDAS, OUTROS)

        private const val ETFS = "ETFs"

        /** O tipo de rendimento e o nome da parte de cada classe da tab Investimentos. */
        fun tipoDe(classe: String): Pair<String, String> {
            val n = classe.trim().lowercase()
            return when {
                n.startsWith("ações") || n.startsWith("acoes") || n.startsWith("ação") || n.startsWith("acao") -> DIVIDENDOS to "Ações"
                n.startsWith("etf") -> DIVIDENDOS to ETFS
                "aforro" in n -> JUROS to "Certificados de Aforro"
                "tesouro" in n -> JUROS to "Certificados do Tesouro"
                "obriga" in n -> JUROS to "Obrigações"
                "p2p" in n || "crowdlending" in n -> P2P to "P2P"
                "depósito" in n || "deposito" in n || "poupança" in n || "poupanca" in n -> JUROS to "Depósitos"
                "saldo" in n || "cash" in n || "revolut" in n || "conta" in n || "liquidez" in n -> JUROS to "Saldos nas contas"
                "renda" in n || "imóve" in n || "imove" in n || "imobili" in n -> RENDAS to classe.trim()
                "ppr" in n -> OUTROS to "PPR"
                "cripto" in n -> OUTROS to "Cripto"
                else -> OUTROS to classe.trim()
            }
        }

        fun de(d: Dados): Rendimentos? {
            val p = d.patrimonio ?: return null
            return de(
                classes = p.classes,
                patrimonioEur = p.totalEur,
                etfs = d.etfs.map { it.ticker }.toSet(),
                dividendosPorMes = d.dividendosPorMes,
                anos = d.dividendos.anos,
                cambioMensal = d.cambioMensal,
                usdEur = d.cambio.usdEur,
                hoje = d.meta.hoje,
            )
        }

        /**
         * classes: a tab Investimentos; etfs: os tickers dos ETFs da carteira; dividendosPorMes e anos: os
         * dividendos recebidos (em dólares brutos), por empresa e mês e por ano; cambioMensal: euros por dólar
         * de cada mês ("2026-03"); usdEur: o câmbio de hoje; hoje: "aaaa-mm-dd".
         */
        fun de(
            classes: List<ClassePatrimonio>,
            patrimonioEur: Double?,
            etfs: Set<String>,
            dividendosPorMes: List<DividendoMes>,
            anos: List<AnoDividendos>,
            cambioMensal: Map<String, Double>,
            usdEur: Double?,
            hoje: String,
        ): Rendimentos? {
            if (classes.isEmpty()) return null
            val mesHoje = runCatching { YearMonth.parse(hoje.take(7)) }.getOrNull()
            // Os 12 meses que acabam no mês de hoje ("2025-12" a "2026-11").
            val janela = mesHoje?.let { m -> (11 downTo 0).map { m.minusMonths(it.toLong()).toString() } }.orEmpty()
            fun emEur(mes: String, usd: Double): Double? = (cambioMensal[mes] ?: usdEur)?.let { usd * it }

            // As partes de cada tipo, pela ordem em que aparecem na folha (as do mesmo nome somam-se).
            val partes = linkedMapOf<Pair<String, String>, Double>()
            var etfsDaFolha = 0.0
            classes.forEach { c ->
                val (tipo, parte) = tipoDe(c.classe)
                val v = c.rendimentoAnualEur ?: 0.0
                if (tipo == DIVIDENDOS && parte == ETFS) etfsDaFolha += v
                if (v > 0) partes[tipo to parte] = (partes[tipo to parte] ?: 0.0) + v
            }

            // ETFs de distribuição que a folha deixa a 0: o que pagaram nos últimos 12 meses.
            var etfsPagos = false
            if (etfsDaFolha <= 0 && etfs.isNotEmpty() && janela.isNotEmpty()) {
                val meses = janela.toSet()
                var soma = 0.0
                var algum = false
                dividendosPorMes.filter { it.ticker in etfs && it.mes in meses && it.usd > 0 }.forEach { m ->
                    emEur(m.mes, m.usd)?.let { soma += it; algum = true }
                }
                if (algum && soma > 0) {
                    partes[DIVIDENDOS to ETFS] = soma
                    etfsPagos = true
                }
            }

            val tipos = TIPOS.mapNotNull { tipo ->
                val doTipo = partes.filterKeys { it.first == tipo }
                    .map { (chave, v) -> ParteRendimento(chave.second, v, pagoUltimos12m = etfsPagos && tipo == DIVIDENDOS && chave.second == ETFS) }
                    .sortedByDescending { it.valorEur }
                if (doTipo.isEmpty()) null else TipoRendimento(tipo, doTipo)
            }

            // Os dividendos recebidos de facto (a tab Dividendos Anuais, mês a mês em dólares).
            val porAno = anos.associateBy { it.ano }
            fun recebido(meses: List<String>): Double? {
                if (anos.isEmpty() || meses.isEmpty()) return null
                var soma = 0.0
                meses.forEach { mes ->
                    val ym = YearMonth.parse(mes)
                    val usd = porAno[ym.year]?.meses?.getOrNull(ym.monthValue - 1) ?: return@forEach
                    if (usd != 0.0) soma += emEur(mes, usd) ?: return null
                }
                return soma
            }
            val anoHoje = mesHoje?.year
            val desdeJaneiro = mesHoje?.let { m -> (1..m.monthValue).map { YearMonth.of(m.year, it).toString() } }.orEmpty()

            return Rendimentos(
                tipos = tipos,
                patrimonioEur = patrimonioEur,
                dividendos12mEur = recebido(janela),
                dividendosAnoEur = recebido(desdeJaneiro),
                ano = anoHoje,
            )
        }
    }
}
