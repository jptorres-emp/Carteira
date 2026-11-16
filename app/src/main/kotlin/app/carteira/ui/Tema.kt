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
    background = Color(0xFFF8F9FC),
    surface = Color(0xFFF8F9FC),
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
