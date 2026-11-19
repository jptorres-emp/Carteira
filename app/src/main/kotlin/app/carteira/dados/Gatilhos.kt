package app.carteira.dados

/**
 * Os preços que contam para comprar e vender uma posição, pelas regras da camada (as mesmas do Sinal da tab
 * Avaliação e Zonas, que vêm da tab Regras da Carteira):
 * - Core A e Core B compram abaixo do «Comprar abaixo de» (até ao peso alvo) e reforçam mais abaixo; não vendem
 *   pela avaliação (só aparam acima do peso máximo ou em valorização extrema).
 * - Satélite só compra abaixo do «Reforçar forte abaixo de» (zona 🟢🟢), até ao peso alvo; acima do «Manter até /
 *   reduzir acima de» reduz até ao alvo e acima do «Vender acima de» vende tudo.
 * - Aposta não se reforça; reduz e vende como o satélite.
 * - As que estão a sair saem pela data, não pelo preço; as residuais (e os ETFs) não têm preços.
 * Os preços estão na moeda da cotação (a coluna Moeda da tab Avaliação e Zonas).
 */
data class Gatilhos(
    val moeda: String,
    val preco: Double?,
    /** Comprar abaixo deste preço (null: a camada não compra). */
    val comprar: Double?,
    /** Só no Core: níveis mais fundos, onde reforça até ao peso máximo. */
    val reforcar: Double?,
    val reforcarForte: Double?,
    val reduzir: Double?,
    val vender: Double?,
    val notaCompra: String,
    val notaVenda: String,
) {
    /** Quanto o preço tem de mexer até ao nível: −0,18 = cair 18%; +0,26 = subir 26%. */
    fun distancia(nivel: Double?): Double? =
        if (nivel == null || preco == null || preco <= 0) null else nivel / preco - 1

    val naZonaDeCompra: Boolean get() = comprar != null && preco != null && preco <= comprar
    val acimaDeReduzir: Boolean get() = reduzir != null && preco != null && preco >= reduzir
    val acimaDeVender: Boolean get() = vender != null && preco != null && preco >= vender
    val temPrecos: Boolean get() = comprar != null || reduzir != null || vender != null

    companion object {
        private const val VENDA_SATELITE = "Acima do primeiro preço, reduz até ao peso alvo; acima do segundo, vende tudo."

        fun de(camada: String?, a: Avaliacao?): Gatilhos? {
            if (a == null) return null
            val c = camada?.trim().orEmpty()
            val moeda = a.moeda ?: "USD"
            val p = a.precoLocal
            return when {
                c == "Core A" || c == "Core B" -> Gatilhos(
                    moeda, p, a.comprarAbaixo, a.reforcarAbaixo, a.reforcarForteAbaixo, null, null,
                    "$c: compra abaixo deste preço até ao peso alvo; mais abaixo, reforça até ao máximo.",
                    "$c: não vende pela avaliação; só apara acima do peso máximo ou em valorização extrema.",
                )
                c == "Satélite" -> Gatilhos(
                    moeda, p, a.reforcarForteAbaixo, null, null, a.reduzirAcima, a.venderAcima,
                    "Satélite: só compra na zona 🟢🟢 (reforçar forte), até ao peso alvo.",
                    VENDA_SATELITE,
                )
                c == "Aposta" -> Gatilhos(
                    moeda, p, null, null, null, a.reduzirAcima, a.venderAcima,
                    "Aposta: não se reforça.",
                    VENDA_SATELITE,
                )
                c.startsWith("Sair") -> Gatilhos(
                    moeda, p, null, null, null, null, null,
                    "A sair: não se compra.",
                    "Sai pela data da camada ($c), não pelo preço.",
                )
                else -> null
            }
        }
    }
}
