package app.carteira.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle

// Azuis da folha (quadros e cabeçalhos).
private val Marinho = Color(0xFF1F2A44)
private val Azul = Color(0xFF2D5497)

private val Claro = lightColorScheme(
    primary = Azul,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6DCE5),
    onPrimaryContainer = Marinho,
    secondary = Color(0xFF55607A),
    tertiary = Color(0xFFB7950B),
    background = Color(0xFFF2F4F8),
    surface = Color(0xFFF2F4F8),
    surfaceVariant = Color(0xFFE6E9EF),
    outlineVariant = Color(0xFFD5DAE3),
)

private val Escuro = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Marinho,
    primaryContainer = Color(0xFF2D4470),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFBDC6DC),
    tertiary = Color(0xFFF2C14E),
)

/**
 * Cores da folha (azuis) por omissão; com coresDoSistema = true, usa as cores do papel de parede
 * (Android 12+).
 */
@Composable
fun TemaCarteira(coresDoSistema: Boolean = false, conteudo: @Composable () -> Unit) {
    val escuro = isSystemInDarkTheme()
    val contexto = LocalContext.current
    val esquema: ColorScheme = when {
        coresDoSistema && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (escuro) dynamicDarkColorScheme(contexto) else dynamicLightColorScheme(contexto)
        escuro -> Escuro
        else -> Claro
    }
    MaterialTheme(colorScheme = esquema, content = conteudo)
}

/** Cores dos ganhos e perdas, legíveis em claro e em escuro. */
object Cores {
    val ganho = Color(0xFF1B8A3A)
    val perda = Color(0xFFC62828)
    val ganhoEscuro = Color(0xFF6FD08C)
    val perdaEscuro = Color(0xFFFF8A80)
}

@Composable
fun corDoSinal(x: Double?): Color {
    val escuro = isSystemInDarkTheme()
    return when {
        x == null || x == 0.0 -> MaterialTheme.colorScheme.onSurfaceVariant
        x > 0 -> if (escuro) Cores.ganhoEscuro else Cores.ganho
        else -> if (escuro) Cores.perdaEscuro else Cores.perda
    }
}

/** Zonas da folha ("🟢 Comprar", "🟠 Reduzir", ...): cor de fundo e de texto, e o texto sem o emoji. */
data class EstiloZona(val fundo: Color, val texto: Color, val rotulo: String)

fun estiloZona(zona: String?): EstiloZona {
    val z = zona?.trim().orEmpty()
    val rotulo = z.replace(Regex("^[\\p{So}\\p{Sk}\\uFE0F\\u200D\\s]+"), "").ifEmpty { z.ifEmpty { "—" } }
    return when {
        z.startsWith("🟢🟢") -> EstiloZona(Color(0xFF0F6B2F), Color.White, rotulo)
        z.startsWith("🟢") -> EstiloZona(Color(0xFFCDEFD6), Color(0xFF0F5226), rotulo)
        z.startsWith("🟡") -> EstiloZona(Color(0xFFFFF1C2), Color(0xFF6B5200), rotulo)
        z.startsWith("🟠") -> EstiloZona(Color(0xFFFFDDB8), Color(0xFF7A3B00), rotulo)
        z.startsWith("🔴") -> EstiloZona(Color(0xFFFFD2CF), Color(0xFF8C1D18), rotulo)
        z.startsWith("⚪") -> EstiloZona(Color(0xFFE4E6EB), Color(0xFF3B4252), rotulo)
        else -> EstiloZona(Color(0xFFE4E6EB), Color(0xFF3B4252), rotulo)
    }
}

/** Cores do cartão principal, dos gráficos e das categorias. */
object Paleta {
    val marinho = Color(0xFF1F2A44)
    val marinhoClaro = Color(0xFF34497A)
    val dourado = Color(0xFFC9A227)
    val cinzento = Color(0xFF8A94A6)
    val verde = Color(0xFF1B8A5A)
    val ambar = Color(0xFFC98A10)
    val vermelho = Color(0xFFC8463D)
    /** No fundo escuro do cartão principal. */
    val verdeClaro = Color(0xFF8FE3B0)
    val vermelhoClaro = Color(0xFFFFA59E)

    /** Categorias (camadas, classes de ativos), sempre pela mesma ordem. */
    val categorias = listOf(
        Color(0xFF2D5497), Color(0xFF5B8DD6), Color(0xFF9DBDEB), Color(0xFFC9A227),
        Color(0xFF7A5C99), Color(0xFF3E9C8F), Color(0xFFD98C5F), Color(0xFFB0B7C3),
    )

    fun categoria(i: Int): Color = categorias[i % categorias.size]
}

/** Fundo dos cartões: branco no claro, um cinzento acima do fundo no escuro. */
@Composable
fun corCartao(): Color = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White

@Composable
fun corBordaCartao(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)

/** Algarismos da mesma largura, para os números alinharem (1 111 e 8 888 ocupam o mesmo). */
fun TextStyle.numeros(): TextStyle = copy(fontFeatureSettings = "tnum")

/** Verde, âmbar ou vermelho a partir de um estado da folha ("🟢 OK", "No limite", "Acima do plano", ...). */
fun corDoEstado(estado: String?): Color {
    val e = estado?.trim().orEmpty()
    val minusculas = e.lowercase()
    return when {
        e.startsWith("🟢") || e.startsWith("✓") || "acima do plano" in minusculas || "no caminho" in minusculas -> Paleta.verde
        e.startsWith("🔴") || "abaixo" in minusculas -> Paleta.vermelho
        e.startsWith("🟡") || e.startsWith("🟠") || e.startsWith("⚠") || "limite" in minusculas -> Paleta.ambar
        else -> Paleta.cinzento
    }
}
