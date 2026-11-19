package app.carteira.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.carteira.dados.AnalisePatrimonio
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

/** Legendas (ou selos) lado a lado; as que não cabem passam para a linha de baixo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinhaLegendas(espaco: Dp = 14.dp, conteudo: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(espaco),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { conteudo() }
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
    val maximo = escalaRedonda(visiveis.maxOf { maxOf(it.carteiraUsd, it.sp500Usd ?: 0.0, it.investidoUsd ?: 0.0) } * 1.04)

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
        LinhaLegendas {
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
                    }
                    // Tocar e arrastar não chegam a quem usa o TalkBack: as ações mudam de mês.
                    .semantics {
                        contentDescription = "Gráfico, ${mesLongo(p.mes)}"
                        customActions = listOf(
                            CustomAccessibilityAction("Mês anterior") { escolhido = (i - 1).coerceAtLeast(0); true },
                            CustomAccessibilityAction("Mês seguinte") { escolhido = (i + 1).coerceAtMost(visiveis.lastIndex); true },
                        )
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

/** Topo do eixo com três intervalos "redondos" (0, 8 k, 16 k, 24 k em vez de 0, 7,9 k, 15,8 k...). */
private fun escalaRedonda(maximo: Double): Double {
    if (maximo <= 0) return 1.0
    val bruto = maximo / 3
    val potencia = Math.pow(10.0, Math.floor(Math.log10(bruto)))
    val passo = listOf(1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0).first { it * potencia >= bruto } * potencia
    return passo * 3
}

private fun indiceEm(px: Float, largura: Float, n: Int): Int =
    if (largura <= 0 || n < 2) 0 else (px / largura * (n - 1)).roundToInt().coerceIn(0, n - 1)

/** Área por baixo do primeiro troço contínuo de uma série (até ao fundo do gráfico). */
private fun areaSob(valores: List<Double?>, x: (Int) -> Float, y: (Double) -> Float, fundo: Float): Path? {
    val primeiro = valores.indexOfFirst { it != null }
    if (primeiro < 0) return null
    var ultimo = primeiro
    while (ultimo + 1 < valores.size && valores[ultimo + 1] != null) ultimo++
    if (ultimo == primeiro) return null
    return Path().apply {
        moveTo(x(primeiro), fundo)
        for (k in primeiro..ultimo) lineTo(x(k), y(valores[k] ?: 0.0))
        lineTo(x(ultimo), fundo)
        close()
    }
}

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
fun BarraComMarca(
    valor: Double,
    marca: Double?,
    escala: Double,
    cor: Color,
    modifier: Modifier = Modifier,
    altura: Dp = 10.dp,
) {
    val trilho = MaterialTheme.colorScheme.surfaceVariant
    val corMarca = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(altura)) {
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

/**
 * Doze barras (previsão) com um traço por cima (o que já foi recebido no mesmo mês). Com aoTocar, cada
 * mês (a barra e a letra) é um botão: fica marcado e diz aos leitores de ecrã o que tem (descricao).
 */
@Composable
fun BarrasMensais(
    previsto: List<Double>,
    recebido: List<Double?>,
    destacado: Int?,
    modifier: Modifier = Modifier,
    aoTocar: ((Int) -> Unit)? = null,
    descricao: ((Int) -> String)? = null,
) {
    val cor = MaterialTheme.colorScheme.primary
    val maximo = (previsto + recebido.filterNotNull()).maxOrNull()?.takeIf { it > 0 } ?: return
    Row(modifier.fillMaxWidth()) {
        for (k in 0 until 12) {
            val v = previsto.getOrNull(k) ?: 0.0
            val r = recebido.getOrNull(k)
            val selecionado = k == destacado
            Column(
                Modifier
                    .weight(1f)
                    .then(
                        if (aoTocar != null) {
                            Modifier.selectable(selected = selecionado, role = Role.Tab, onClick = { aoTocar(k) })
                        } else {
                            Modifier
                        },
                    )
                    .semantics { if (descricao != null) contentDescription = descricao(k) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .drawBehind {
                            val largura = size.width * 0.56f
                            val x0 = (size.width - largura) / 2
                            val alt = (v / maximo * size.height).toFloat()
                            drawRoundRect(
                                cor.copy(alpha = if (selecionado) 1f else 0.45f),
                                topLeft = Offset(x0, size.height - alt),
                                size = Size(largura, alt),
                                cornerRadius = CornerRadius(3.dp.toPx()),
                            )
                            if (r != null && r > 0) {
                                val ry = size.height - (r / maximo * size.height).toFloat()
                                drawLine(
                                    Paleta.dourado, Offset(x0 - 2.dp.toPx(), ry), Offset(x0 + largura + 2.dp.toPx(), ry),
                                    strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
                                )
                            }
                        },
                )
                Text(
                    "JFMAMJJASOND"[k].toString(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
                    color = if (selecionado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Uma coluna do gráfico por ano. */
data class BarraAno(val rotulo: String, val valor: Double, val previsao: Boolean = false, val nota: String? = null, val corNota: Color = Color.Unspecified)

/** Colunas por ano com o valor por cima e o ano (e uma nota, como o crescimento) por baixo. */
@Composable
fun BarrasAnuais(itens: List<BarraAno>, modifier: Modifier = Modifier, altura: Dp = 110.dp, formatar: (Double) -> String) {
    val maximo = itens.maxOfOrNull { it.valor }?.takeIf { it > 0 } ?: return
    val cor = MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(altura + 20.dp), verticalAlignment = Alignment.Bottom) {
            itens.forEach { b ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatar(b.valor),
                        style = MaterialTheme.typography.labelSmall.numeros(),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    val fracao = (b.valor / maximo).coerceIn(0.0, 1.0).toFloat()
                    Box(
                        Modifier
                            .fillMaxWidth(0.62f)
                            .height(altura * fracao.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                            .background(if (b.previsao) cor.copy(alpha = 0.18f) else cor)
                            .then(
                                if (b.previsao) {
                                    Modifier.border(1.5.dp, cor.copy(alpha = 0.7f), RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            itens.forEach { b ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        b.rotulo,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (b.previsao) FontWeight.Normal else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (b.nota != null) {
                        Text(
                            b.nota,
                            style = MaterialTheme.typography.labelSmall.numeros(),
                            color = if (b.corNota == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else b.corNota,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * O património mês a mês (real) com o plano da folha: a faixa entre a meta de referência e a
 * alargada e, tracejada, a meta base. "Até 2030" mostra também o caminho do plano para a frente.
 * Toca ou arrasta para ver um mês.
 */
@Composable
fun GraficoPatrimonio(a: AnalisePatrimonio) {
    val fimPlano = a.fimDoPlano?.mes?.takeIf { it > a.mesAtual }
    val ate = fimPlano?.let { "Até ${it.take(4)}" }
    val opcoes = listOfNotNull("1A", "3A", "Tudo", ate)
    var escolha by rememberSaveable { mutableStateOf("Tudo") }
    val periodo = if (escolha in opcoes) escolha else "Tudo"
    val meses = remember(a, periodo) {
        val ultimo = if (periodo == ate && fimPlano != null) fimPlano else a.mesAtual
        val primeiro = when (periodo) {
            "1A" -> AnalisePatrimonio.somarMeses(a.mesAtual, -12)
            "3A" -> AnalisePatrimonio.somarMeses(a.mesAtual, -36)
            else -> a.inicio.mes
        }.let { if (it < a.inicio.mes) a.inicio.mes else it }
        val n = AnalisePatrimonio.mesesEntre(primeiro, ultimo)
        (0..n).map { AnalisePatrimonio.somarMeses(primeiro, it) }
    }
    if (meses.size < 2) return
    val porMes = remember(a) { a.serie.associateBy { it.mes } }
    val real = meses.map { porMes[it]?.valorEur }
    val base = meses.map { a.metaDe(it)?.baseEur }
    val referencia = meses.map { a.metaDe(it)?.referenciaEur }
    val alargada = meses.map { a.metaDe(it)?.alargadaEur }
    val hoje = meses.indexOf(a.mesAtual)
    var escolhido by remember(meses) { mutableStateOf<Int?>(null) }
    val i = (escolhido ?: hoje.takeIf { it >= 0 } ?: meses.lastIndex).coerceIn(0, meses.lastIndex)

    val corReal = MaterialTheme.colorScheme.primary
    val corGrelha = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val corFundo = corCartao()
    val corFaixa = Paleta.dourado.copy(alpha = 0.16f)
    val maximo = escalaRedonda((real + alargada + base).filterNotNull().maxOrNull()?.times(1.04) ?: 1.0)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            mesLongo(meses[i]) + if (real[i] == null) " (plano)" else "",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Filtros(opcoes, periodo, compacto = true) { escolha = it }
        LinhaLegendas {
            if (real[i] != null) Legenda(corReal, "Real", Formatos.eur(real[i], 0))
            if (base[i] != null) Legenda(Paleta.dourado, "Meta base", Formatos.eur(base[i], 0), tracejado = true)
            val r = referencia[i]
            val al = alargada[i]
            if (r != null && al != null) {
                Legenda(Paleta.dourado.copy(alpha = 0.45f), "Intervalo", Formatos.intervaloEur(r, al))
            }
        }
        Row {
            Canvas(
                Modifier
                    .weight(1f)
                    .height(180.dp)
                    .pointerInput(meses) {
                        detectTapGestures { pos -> escolhido = indiceEm(pos.x, size.width.toFloat(), meses.size) }
                    }
                    .pointerInput(meses) {
                        detectHorizontalDragGestures { mudanca, _ ->
                            escolhido = indiceEm(mudanca.position.x, size.width.toFloat(), meses.size)
                        }
                    }
                    .semantics {
                        contentDescription = "Gráfico, ${mesLongo(meses[i])}"
                        customActions = listOf(
                            CustomAccessibilityAction("Mês anterior") { escolhido = (i - 1).coerceAtLeast(0); true },
                            CustomAccessibilityAction("Mês seguinte") { escolhido = (i + 1).coerceAtMost(meses.lastIndex); true },
                        )
                    },
            ) {
                val w = size.width
                val h = size.height
                val n = meses.size
                val x = { k: Int -> w * k / (n - 1) }
                val y = { v: Double -> (h - v / maximo * h).toFloat() }
                for (g in 0..3) {
                    val yy = h * g / 3
                    drawLine(corGrelha, Offset(0f, yy), Offset(w, yy), strokeWidth = 1f)
                }
                // Faixa do plano: de cada troço contínuo com as duas metas.
                var k = 0
                while (k < n) {
                    if (referencia[k] == null || alargada[k] == null) {
                        k++
                        continue
                    }
                    val inicio = k
                    while (k < n && referencia[k] != null && alargada[k] != null) k++
                    val fim = k - 1
                    if (fim > inicio) {
                        val faixa = Path()
                        for (j in inicio..fim) {
                            if (j == inicio) faixa.moveTo(x(j), y(alargada[j]!!)) else faixa.lineTo(x(j), y(alargada[j]!!))
                        }
                        for (j in fim downTo inicio) faixa.lineTo(x(j), y(referencia[j]!!))
                        faixa.close()
                        drawPath(faixa, corFaixa)
                    }
                }
                drawPath(
                    caminho(base, x, y), Paleta.dourado,
                    style = Stroke(width = 1.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))),
                )
                // Real: área e linha.
                areaSob(real, x, y, h)?.let {
                    drawPath(it, Brush.verticalGradient(listOf(corReal.copy(alpha = 0.25f), corReal.copy(alpha = 0.02f))))
                }
                drawPath(caminho(real, x, y), corReal, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round))
                // Hoje, quando o gráfico continua para o futuro.
                if (hoje in 0 until n - 1) {
                    drawLine(
                        corGrelha.copy(alpha = 1f), Offset(x(hoje), 0f), Offset(x(hoje), h), strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                    )
                }
                // Mês escolhido.
                val px = x(i)
                drawLine(corGrelha.copy(alpha = 1f), Offset(px, 0f), Offset(px, h), strokeWidth = 1.5f)
                base[i]?.let { drawCircle(Paleta.dourado, 4.dp.toPx(), Offset(px, y(it))) }
                real[i]?.let {
                    drawCircle(corReal, 5.dp.toPx(), Offset(px, y(it)))
                    drawCircle(corFundo, 2.dp.toPx(), Offset(px, y(it)))
                }
            }
            Column(
                Modifier.height(180.dp).padding(start = 6.dp),
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
            TextoPequeno(mesCurto(meses.first()), modifier = Modifier.weight(1f))
            if (hoje in 1 until meses.lastIndex) TextoPequeno("hoje: ${mesCurto(meses[hoje])}", modifier = Modifier.weight(1f))
            TextoPequeno(mesCurto(meses.last()))
        }
    }
}
