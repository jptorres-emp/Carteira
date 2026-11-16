package app.carteira.ui

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/** Números à portuguesa: espaço nos milhares e vírgula nas décimas ("1 234,56 €"). */
object Formatos {
    private val simbolos = DecimalFormatSymbols(Locale.forLanguageTag("pt-PT")).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }

    private fun padrao(casas: Int): DecimalFormat {
        val decimais = if (casas > 0) "." + "0".repeat(casas) else ""
        return DecimalFormat("#,##0$decimais", simbolos).apply { roundingMode = RoundingMode.HALF_UP }
    }

    fun numero(x: Double?, casas: Int = 2): String = if (x == null) "—" else padrao(casas).format(x)

    /** Até `max` casas, sem zeros a mais (0,4245 · 1,5 · 12). */
    fun numeroLivre(x: Double?, max: Int = 4): String {
        if (x == null) return "—"
        val f = DecimalFormat("#,##0." + "#".repeat(max), simbolos).apply { roundingMode = RoundingMode.HALF_UP }
        return f.format(x)
    }

    fun eur(x: Double?, casas: Int = 2): String = if (x == null) "—" else numero(x, casas) + " €"

    fun usd(x: Double?, casas: Int = 2): String = if (x == null) "—" else numero(x, casas) + " $"

    /** Valor com a moeda dada ("USD", "EUR", "GBP", "DKK", ...). */
    fun moeda(x: Double?, moeda: String?, casas: Int = 2): String = when (moeda) {
        null, "USD" -> usd(x, casas)
        "EUR" -> eur(x, casas)
        "GBP" -> if (x == null) "—" else numero(x, casas) + " £"
        else -> if (x == null) "—" else numero(x, casas) + " " + moeda
    }

    /** Percentagem guardada como fração (0,2096 → "20,96%"). */
    fun pct(x: Double?, casas: Int = 1): String = if (x == null) "—" else numero(x * 100, casas) + "%"

    /** Percentagem com sinal (+1,2% / −0,8%), para variações e ganhos. */
    fun pctSinal(x: Double?, casas: Int = 1): String {
        if (x == null) return "—"
        val sinal = if (x > 0) "+" else if (x < 0) "−" else ""
        return sinal + numero(abs(x) * 100, casas) + "%"
    }

    /** Valor com sinal (+1 234 € / −56 €). */
    fun eurSinal(x: Double?, casas: Int = 0): String {
        if (x == null) return "—"
        val sinal = if (x > 0) "+" else if (x < 0) "−" else ""
        return sinal + numero(abs(x), casas) + " €"
    }

    fun usdSinal(x: Double?, casas: Int = 0): String {
        if (x == null) return "—"
        val sinal = if (x > 0) "+" else if (x < 0) "−" else ""
        return sinal + numero(abs(x), casas) + " $"
    }

    /** Números grandes em curto, para eixos: 60 k, 1,2 M. */
    fun compacto(x: Double?): String {
        if (x == null) return "—"
        val a = abs(x)
        return when {
            a >= 1_000_000 -> numeroLivre(x / 1_000_000, 1) + " M"
            a >= 10_000 -> numero(x / 1_000, 0) + " k"
            a >= 1_000 -> numeroLivre(x / 1_000, 1) + " k"
            else -> numero(x, 0)
        }
    }

    /** Diferença de duas percentagens em pontos percentuais (+7,4 pp). */
    fun pp(x: Double?, casas: Int = 1): String {
        if (x == null) return "—"
        val sinal = if (x > 0) "+" else if (x < 0) "−" else ""
        return sinal + numero(abs(x) * 100, casas) + " pp"
    }

    /** "2026-11-16T15:42" → "hoje às 15:42", "ontem às 15:42" ou "13/11 às 15:42". */
    fun quando(iso: String?, hoje: java.time.LocalDate = java.time.LocalDate.now()): String {
        if (iso == null || iso.length < 16) return iso ?: "—"
        val dia = runCatching { java.time.LocalDate.parse(iso.substring(0, 10)) }.getOrNull() ?: return iso
        val hora = iso.substring(11, 16)
        return when (dia) {
            hoje -> "hoje às $hora"
            hoje.minusDays(1) -> "ontem às $hora"
            else -> iso.substring(8, 10) + "/" + iso.substring(5, 7) + " às " + hora
        }
    }

    /** "2026-11-16" → "16/11/2026". */
    fun data(iso: String?): String {
        if (iso == null || iso.length < 10) return iso ?: "—"
        return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4)
    }

    /** "2026-11-16T09:40:35+00:00" → "16/11 às 09:40". */
    fun momento(iso: String?): String {
        if (iso == null || iso.length < 16) return iso ?: "—"
        return iso.substring(8, 10) + "/" + iso.substring(5, 7) + " às " + iso.substring(11, 16)
    }

    /** "16/11/2026" ou "16-11-2026" → "2026-11-16"; null se não for uma data válida. */
    fun dataIso(texto: String): String? {
        val m = Regex("""^\s*(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})\s*$""").find(texto) ?: return null
        val (d, mes, a) = m.destructured
        val dia = d.toInt()
        val mm = mes.toInt()
        val ano = a.toInt()
        if (mm !in 1..12 || dia !in 1..31) return null
        val diasNoMes = java.time.YearMonth.of(ano, mm).lengthOfMonth()
        if (dia > diasNoMes) return null
        return "%04d-%02d-%02d".format(ano, mm, dia)
    }

    /** Lê "1 234,5" ou "1234.5"; null se não for número. */
    fun lerNumero(texto: String): Double? {
        val t = texto.trim().replace(" ", "").replace(" ", "").replace(" ", "")
        if (t.isEmpty()) return null
        val normal = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
        return normal.toDoubleOrNull()
    }
}
