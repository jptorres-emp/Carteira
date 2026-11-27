package app.carteira.dados

/**
 * Sobre que total se calcula o peso de cada posição na vista Pesos:
 * - ACOES: só as ações (os ETFs ficam de fora), para ver o portefólio de ações sozinho;
 * - CARTEIRA: ações + ETFs, a base das tuas regras (os alvos e máximos da folha são desta base);
 * - TUDO: o património todo, com o resto (certificados, PPR, P2P, saldos…) em linhas à parte.
 */
enum class BasePesos(val rotulo: String, val nome: String) {
    ACOES("Ações", "das ações"),
    CARTEIRA("Ações + ETFs", "da carteira"),
    TUDO("Tudo", "do património");

    companion object {
        val ROTULOS: List<String> = entries.map { it.rotulo }

        /** A base pelo rótulo guardado nas preferências; a carteira se não houver ou não se reconhecer. */
        fun de(rotulo: String?): BasePesos = entries.firstOrNull { it.rotulo == rotulo } ?: CARTEIRA
    }
}

/**
 * O total de uma base e como converter para ela.
 * fatorRegras: os pesos, alvos e máximos da folha são % da carteira (ações + ETFs); multiplicados por este fator
 * passam a % desta base com o mesmo valor em euros, por isso o âmbar e o vermelho caem nas mesmas posições.
 * outras: o resto do património (só na base TUDO), por grupos e do maior para o menor.
 */
data class BaseDosPesos(
    val base: BasePesos,
    val totalEur: Double,
    val acoesEur: Double,
    val etfsEur: Double,
    val fatorRegras: Double,
    val outras: List<ParteDoPatrimonio>,
    /** Na base TUDO sem o património da folha (ponte antiga): fica igual à carteira. */
    val semPatrimonio: Boolean = false,
) {
    val incluiEtfs: Boolean get() = base != BasePesos.ACOES

    /** Um peso, alvo ou máximo da folha (em % da carteira) convertido para esta base. */
    fun naBase(pesoNaCarteira: Double?): Double? = pesoNaCarteira?.let { it * fatorRegras }

    /** O peso de um valor em euros nesta base. */
    fun peso(valorEur: Double): Double = valorEur / totalEur

    val outrasEur: Double get() = outras.sumOf { it.valorEur }
}

data class ParteDoPatrimonio(val nome: String, val valorEur: Double)

/** Os grupos da tab Investimentos, com os nomes do cartão Composição. */
object GruposPatrimonio {
    const val ACOES = "Ações"
    const val ETFS = "ETFs"

    fun de(classe: String): String {
        val n = classe.trim().lowercase()
        return when {
            n.startsWith("ações") || n.startsWith("acoes") -> ACOES
            n.startsWith("etf") -> ETFS
            "certificado" in n -> "Certificados de Aforro"
            "ppr" in n -> "PPR"
            "p2p" in n -> "P2P"
            "cripto" in n -> "Cripto"
            // Os saldos das corretoras podem ter mais do que dinheiro (a Trading Republic tem cripto e ETFs).
            "saldo" in n || "cash" in n || "revolut" in n || "depósito" in n || "deposito" in n -> "Corretoras e liquidez"
            else -> classe.trim()
        }
    }
}

object Pesos {
    /**
     * acoesEur e etfsEur: o valor de hoje das ações e dos ETFs (da Carteira, ao vivo); classes: o património
     * da folha (null se a ponte não o trouxer). As ações e os ETFs do património não entram no resto: já são
     * as linhas das posições. Devolve null se o total da base não for positivo.
     */
    fun base(base: BasePesos, acoesEur: Double, etfsEur: Double, classes: List<ClassePatrimonio>?): BaseDosPesos? {
        val carteira = acoesEur + etfsEur
        if (carteira <= 0) return null
        return when (base) {
            BasePesos.ACOES ->
                if (acoesEur > 0) BaseDosPesos(base, acoesEur, acoesEur, etfsEur, carteira / acoesEur, emptyList()) else null
            BasePesos.CARTEIRA -> BaseDosPesos(base, carteira, acoesEur, etfsEur, 1.0, emptyList())
            BasePesos.TUDO -> {
                val outras = outrasPartes(classes.orEmpty())
                val total = carteira + outras.sumOf { it.valorEur }
                BaseDosPesos(base, total, acoesEur, etfsEur, carteira / total, outras, semPatrimonio = classes == null)
            }
        }
    }

    /** O património sem as ações e os ETFs, junto por grupos (os de valor zero ficam de fora), do maior para o menor. */
    fun outrasPartes(classes: List<ClassePatrimonio>): List<ParteDoPatrimonio> =
        classes
            .filter { it.valorEur > 0 }
            .groupBy({ GruposPatrimonio.de(it.classe) }, { it.valorEur })
            .filterKeys { it != GruposPatrimonio.ACOES && it != GruposPatrimonio.ETFS }
            .map { (nome, valores) -> ParteDoPatrimonio(nome, valores.sum()) }
            .sortedByDescending { it.valorEur }
}
