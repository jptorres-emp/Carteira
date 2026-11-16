package app.carteira.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.carteira.dados.PontoEvolucao
import kotlin.math.roundToInt

private val MESES_CURTOS = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
private val MESES_LONGOS = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
)

/** "2026-11" → "nov/26". */
fun mesCurto(mes: String): String {
    val m = mes.substring(5, 7).toIntOrNull()?.takeIf { it in 1..12 } ?: return mes
    return MESES_CURTOS[m - 1] + "/" + mes.substring(2, 4)
}

/** "2026-11" → "novembro de 2026". */
fun mesLongo(mes: String): String {
    val m = mes.substring(5, 7).toIntOrNull()?.takeIf { it in 1..12 } ?: return mes
    return MESES_LONGOS[m - 1] + " de " + mes.substring(0, 4)
}

/** Ponto colorido + rótulo + valor, para legendas. */
@Composable
fun Legenda(cor: Color, rotulo: String, valor: String? = null, modifier: Modifier = Modifier, tracejado: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (tracejado) {
            Canvas(Modifier.size(width = 12.dp, height = 8.dp)) {
                drawLine(
                    cor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)),
                )
            }
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape).background(cor))
        }
        Spacer(Modifier.width(5.dp))
        Column {
            Text(rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (valor != null) {
                Text(valor, style = MaterialTheme.typography.labelLarge.numeros(), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * A carteira contra o que investiste e contra o mesmo dinheiro no S&P 500, mês a mês (tab Evolução
 * Patrimonial). Toca ou arrasta no gráfico para ver um mês.
 */
@Composable
fun GraficoEvolucao(pontos: List<PontoEvolucao>) {
    var periodo by rememberSaveable { mutableStateOf("Tudo") }
    val visiveis = remember(pontos, periodo) {
        when (periodo) {
            "1A" -> pontos.takeLast(13)
            "3A" -> pontos.takeLast(37)
            else -> pontos
        }
    }
    var escolhido by remember(visiveis) { mutableStateOf<Int?>(null) }
    if (visiveis.size < 2) return
    val i = (escolhido ?: visiveis.lastIndex).coerceIn(0, visiveis.lastIndex)
    val p = visiveis[i]
    val corCarteira = MaterialTheme.colorScheme.primary
    val corGrelha = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val corFundo = corCartao()
    val maximo = visiveis.maxOf { maxOf(it.carteiraUsd, it.sp500Usd ?: 0.0, it.investidoUsd ?: 0.0) } * 1.08

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                mesLongo(p.mes),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Filtros(listOf("1A", "3A", "Tudo"), periodo, compacto = true) { periodo = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legenda(corCarteira, "Carteira", Formatos.usd(p.carteiraUsd, 0))
            Legenda(Paleta.dourado, "S&P 500", Formatos.usd(p.sp500Usd, 0))
            Legenda(Paleta.cinzento, "Investido", Formatos.usd(p.investidoUsd, 0), tracejado = true)
        }
        Row {
            Canvas(
                Modifier
                    .weight(1f)
                    .height(170.dp)
                    .pointerInput(visiveis) {
                        detectTapGestures { pos -> escolhido = indiceEm(pos.x, size.width.toFloat(), visiveis.size) }
                    }
                    .pointerInput(visiveis) {
                        detectHorizontalDragGestures { mudanca, _ ->
                            escolhido = indiceEm(mudanca.position.x, size.width.toFloat(), visiveis.size)
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                val n = visiveis.size
                val x = { k: Int -> w * k / (n - 1) }
                val y = { v: Double -> (h - v / maximo * h).toFloat() }
                for (g in 0..3) {
                    val yy = h * g / 3
                    drawLine(corGrelha, Offset(0f, yy), Offset(w, yy), strokeWidth = 1f)
                }
                // Área e linha da carteira.
                val linha = Path()
                val area = Path()
                visiveis.forEachIndexed { k, pt ->
                    val px = x(k)
                    val py = y(pt.carteiraUsd)
                    if (k == 0) {
                        linha.moveTo(px, py)
                        area.moveTo(px, h)
                        area.lineTo(px, py)
                    } else {
                        linha.lineTo(px, py)
                        area.lineTo(px, py)
                    }
                }
                area.lineTo(x(n - 1), h)
                area.close()
                drawPath(area, Brush.verticalGradient(listOf(corCarteira.copy(alpha = 0.25f), corCarteira.copy(alpha = 0.02f))))
                drawPath(
                    caminho(visiveis.map { it.investidoUsd }, x, y), Paleta.cinzento,
                    style = Stroke(width = 1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
                drawPath(
                    caminho(visiveis.map { it.sp500Usd }, x, y), Paleta.dourado,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
                drawPath(linha, corCarteira, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round))
                // Mês escolhido.
                val px = x(i)
                drawLine(corGrelha.copy(alpha = 1f), Offset(px, 0f), Offset(px, h), strokeWidth = 1.5f)
                p.investidoUsd?.let { drawCircle(Paleta.cinzento, 3.5.dp.toPx(), Offset(px, y(it))) }
                p.sp500Usd?.let { drawCircle(Paleta.dourado, 4.dp.toPx(), Offset(px, y(it))) }
                drawCircle(corCarteira, 5.dp.toPx(), Offset(px, y(p.carteiraUsd)))
                drawCircle(corFundo, 2.dp.toPx(), Offset(px, y(p.carteiraUsd)))
            }
            Column(
                Modifier.height(170.dp).padding(start = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                for (g in 3 downTo 0) {
                    Text(
                        Formatos.compacto(maximo * g / 3),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            TextoPequeno(mesCurto(visiveis.first().mes), modifier = Modifier.weight(1f))
            TextoPequeno(mesCurto(visiveis.last().mes))
        }
        val investido = p.investidoUsd?.takeIf { it > 0 }
        if (investido != null) {
            val carteira = p.carteiraUsd / investido - 1
            val indice = p.sp500Usd?.let { it / investido - 1 }
            Text(
                buildString {
                    append("Sobre o que investiste: carteira ").append(Formatos.pctSinal(carteira))
                    if (indice != null) append(" · o mesmo dinheiro no S&P 500 ").append(Formatos.pctSinal(indice))
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextoPequeno("Em dólares, como na tab Evolução Patrimonial. O último mês usa os valores de agora.")
    }
}

private fun indiceEm(px: Float, largura: Float, n: Int): Int =
    if (largura <= 0 || n < 2) 0 else (px / largura * (n - 1)).roundToInt().coerceIn(0, n - 1)

/** Linha por uma série com falhas (null): interrompe onde falta o valor. */
private fun caminho(valores: List<Double?>, x: (Int) -> Float, y: (Double) -> Float): Path {
    val p = Path()
    var aberto = false
    valores.forEachIndexed { k, v ->
        if (v == null) {
            aberto = false
        } else if (!aberto) {
            p.moveTo(x(k), y(v))
            aberto = true
        } else {
            p.lineTo(x(k), y(v))
        }
    }
    return p
}

/** Barra horizontal com o valor de hoje e um traço no alvo (ideal, limite ou meta). */
@Composable
fun BarraComMarca(valor: Double, marca: Double?, escala: Double, cor: Color, modifier: Modifier = Modifier) {
    val trilho = MaterialTheme.colorScheme.surfaceVariant
    val corMarca = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val raio = CornerRadius(size.height / 2)
        drawRoundRect(trilho, cornerRadius = raio)
        if (escala > 0) {
            val w = ((valor / escala).coerceIn(0.0, 1.0) * size.width).toFloat()
            if (w > 0f) drawRoundRect(cor, size = Size(maxOf(w, size.height), size.height), cornerRadius = raio)
            if (marca != null && marca > 0) {
                val mx = ((marca / escala).coerceIn(0.0, 1.0) * size.width).toFloat()
                drawLine(corMarca, Offset(mx, -3.dp.toPx()), Offset(mx, size.height + 3.dp.toPx()), strokeWidth = 2.dp.toPx())
            }
        }
    }
}

/** Barra dividida em partes (alocação). */
@Composable
fun BarraEmpilhada(partes: List<Pair<Double, Color>>, modifier: Modifier = Modifier, altura: Dp = 14.dp) {
    val total = partes.sumOf { it.first }
    if (total <= 0) return
    val separador = corCartao()
    Canvas(modifier.fillMaxWidth().height(altura).clip(androidx.compose.foundation.shape.RoundedCornerShape(50))) {
        var x = 0f
        partes.forEachIndexed { k, (v, cor) ->
            val w = (v / total * size.width).toFloat()
            drawRect(cor, Offset(x, 0f), Size(w, size.height))
            x += w
            if (k < partes.lastIndex) drawRect(separador, Offset(x - 1f, 0f), Size(2f, size.height))
        }
    }
}

/** Doze barras (previsão) com um traço por cima (o que já foi recebido no mesmo mês). */
@Composable
fun BarrasMensais(previsto: List<Double>, recebido: List<Double?>, mesAtual: Int?, modifier: Modifier = Modifier) {
    val cor = MaterialTheme.colorScheme.primary
    val maximo = (previsto + recebido.filterNotNull()).maxOrNull()?.takeIf { it > 0 } ?: return
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            val passo = size.width / 12
            val largura = passo * 0.56f
            for (k in 0 until 12) {
                val v = previsto.getOrNull(k) ?: 0.0
                val alt = (v / maximo * size.height).toFloat()
                val x0 = k * passo + (passo - largura) / 2
                drawRoundRect(
                    cor.copy(alpha = if (k == mesAtual) 1f else 0.5f),
                    topLeft = Offset(x0, size.height - alt),
                    size = Size(largura, alt),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
                val r = recebido.getOrNull(k)
                if (r != null && r > 0) {
                    val ry = size.height - (r / maximo * size.height).toFloat()
                    drawLine(
                        Paleta.dourado, Offset(x0 - 2.dp.toPx(), ry), Offset(x0 + largura + 2.dp.toPx(), ry),
                        strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            "JFMAMJJASOND".forEachIndexed { k, letra ->
                Text(
                    letra.toString(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (k == mesAtual) FontWeight.Bold else FontWeight.Normal,
                    color = if (k == mesAtual) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
