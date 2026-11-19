package app.carteira.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.carteira.dados.AnalisePatrimonio
import app.carteira.dados.AnoPatrimonio
import app.carteira.dados.ClassePatrimonio
import app.carteira.dados.Patrimonio
import app.carteira.dados.PosicaoPlano
import kotlin.math.max
import kotlin.math.min

/**
 * Separador Património: como evoluiu o teu património total (tab Evolução Patrimonial), onde estás
 * face ao plano da folha, os marcos, o ano a ano e a composição de hoje.
 */
@Composable
fun PatrimonioEcra(estado: Estado, vm: CarteiraViewModel) {
    val d = estado.dados ?: return SemDados(estado, vm)
    val a = remember(d) { AnalisePatrimonio.de(d) }
    ListaAtualizavel(estado, vm) {
        if (a == null) {
            item {
                Cartao("Património") {
                    Text(
                        "A folha ainda não tem valores de património (tab Evolução Patrimonial, coluna Net Worth).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            item { TopoPatrimonio(a) }
            if (a.serie.size >= 2) item { Cartao("Evolução") { GraficoPatrimonio(a) } }
            if (a.metaHoje != null || a.fimDoPlano != null) item { CartaoPlano(a) }
            if (a.proximoMarcoEur != null) item { CartaoMarcos(a) }
            if (a.anos.isNotEmpty()) item { CartaoAnoAAno(a) }
        }
        val p = d.patrimonio
        if (p != null && p.classes.isNotEmpty()) item { CartaoComposicao(p) }
        if (d.versaoPonte < 3) {
            item {
                Cartao("Há mais para ver") {
                    Text(
                        "Atualiza a ponte da folha (versão 3) para veres o plano até 2030: as metas de referência, base e " +
                            "alargada, mês a mês.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { vm.irPara(Ecra.Definicoes) }) { Text("Como atualizar") }
                }
            }
        }
        item {
            TextoPequeno(
                "Da tab Evolução Patrimonial: cada mês é o património no fim do mês; o deste mês é o valor de agora " +
                    "(tab Investimentos).",
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

private fun textoPosicao(p: PosicaoPlano): Pair<String, Color> = when (p) {
    PosicaoPlano.ACIMA_BASE -> "Acima da meta base" to Paleta.verde
    PosicaoPlano.ENTRE_REFERENCIA_E_BASE -> "No limite" to Paleta.ambar
    PosicaoPlano.ABAIXO_REFERENCIA -> "Abaixo da referência" to Paleta.vermelho
}

// ───────────────────────────── Topo ─────────────────────────────

@Composable
private fun TopoPatrimonio(a: AnalisePatrimonio) {
    val branco = Color.White
    val ano = a.mesAtual.take(4)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Paleta.marinho, Paleta.marinhoClaro))),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Património total",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = branco.copy(alpha = 0.75f),
                )
                a.posicaoPlano?.let { p ->
                    val (texto, cor) = textoPosicao(p)
                    Surface(color = cor.copy(alpha = 0.45f), shape = RoundedCornerShape(50)) {
                        Text(
                            texto,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = branco,
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                Formatos.eur(a.atualEur, 0),
                style = MaterialTheme.typography.displaySmall.numeros(),
                fontWeight = FontWeight.SemiBold,
                color = branco,
            )
            a.vsMesAnteriorEur?.let { v ->
                Surface(color = (if (v >= 0) Paleta.verde else Paleta.vermelho).copy(alpha = 0.45f), shape = RoundedCornerShape(50)) {
                    Text(
                        "${if (v >= 0) "▲" else "▼"} ${Formatos.eurSinal(v)} este mês",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge.numeros(),
                        color = branco,
                    )
                }
            }
            a.esteAnoEur?.let { v ->
                Text(
                    "Em $ano: ${Formatos.eurSinal(v)}" + (a.esteAnoPct?.let { " (${Formatos.pctSinal(it)})" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium.numeros(),
                    color = if (v >= 0) Paleta.verdeClaro else Paleta.vermelhoClaro,
                )
            }
            HorizontalDivider(color = branco.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ColunaTopo(
                    "Desde ${mesCurto(a.inicio.mes)}",
                    Formatos.eurSinal(a.desdeInicioEur),
                    if (a.inicio.valorEur > 0) "×${Formatos.numero(a.atualEur / a.inicio.valorEur, 1)}" else null,
                    Modifier.weight(1f),
                )
                ColunaTopo(
                    "Ritmo (12 meses)",
                    a.ritmoMensal12mEur?.let { Formatos.eurSinal(it) + "/mês" } ?: "—",
                    "reforços incluídos",
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ColunaTopo(rotulo: String, valor: String, detalhe: String?, modifier: Modifier) {
    Column(modifier) {
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f), maxLines = 1)
        Text(
            valor,
            style = MaterialTheme.typography.titleSmall.numeros(),
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
        )
        if (detalhe != null) {
            Text(detalhe, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f), maxLines = 1)
        }
    }
}

// ───────────────────────────── Plano ─────────────────────────────

@Composable
private fun CartaoPlano(a: AnalisePatrimonio) {
    val plano = a.plano
    Cartao("Face ao plano") {
        val hoje = a.metaHoje
        val base = hoje?.baseEur
        if (hoje != null && base != null) {
            ReguaPlano(a.atualEur, hoje.referenciaEur, base, hoje.alargadaEur)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legenda(MaterialTheme.colorScheme.primary, "Hoje", Formatos.eur(a.atualEur, 0))
                Legenda(Paleta.dourado, "Meta base", Formatos.eur(base, 0))
                val r = hoje.referenciaEur
                val al = hoje.alargadaEur
                if (r != null && al != null) {
                    Legenda(Paleta.dourado.copy(alpha = 0.45f), "Intervalo", Formatos.compacto(r) + "–" + Formatos.compacto(al) + " €")
                }
            }
            val dif = a.atualEur - base
            Text(
                if (dif >= 0) {
                    "Estás ${Formatos.eur(dif, 0)} acima da meta base para hoje."
                } else {
                    "Estás ${Formatos.eur(-dif, 0)} abaixo da meta base para hoje."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            TextoPequeno("A meta de hoje fica entre a do fim do mês passado e a do fim deste mês, pelo dia do mês.")
        }
        val doMes = a.metaDoMes?.baseEur
        if (doMes != null) {
            HorizontalDivider(color = corBordaCartao())
            val falta = doMes - a.atualEur
            LinhaValor("Meta base para o fim de ${mesLongo(a.mesAtual).substringBefore(" de")}", Formatos.eur(doMes, 0))
            Text(
                if (falta > 0) "Faltam ${Formatos.eur(falta, 0)} até ao fim do mês." else "Já passaste a meta do fim do mês.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (falta > 0) MaterialTheme.colorScheme.onSurface else Paleta.verde,
            )
        }
        val reforcoPlano = plano?.reforcoMensalEur
        val reforcoReal = a.reforcoMedio12mEur
        if (reforcoReal != null) {
            HorizontalDivider(color = corBordaCartao())
            LinhaValor(
                "Reforço médio (12 meses)",
                Formatos.eur(reforcoReal, 0) + "/mês",
                cor = if (reforcoPlano != null && reforcoReal < reforcoPlano * 0.9) Paleta.ambar else Color.Unspecified,
            )
            if (reforcoPlano != null) TextoPequeno("O plano assume ${Formatos.eur(reforcoPlano, 0)} por mês.")
        }
        val fim = a.fimDoPlano
        if (fim != null && fim.mes > a.mesAtual) {
            HorizontalDivider(color = corBordaCartao())
            Text("Onde o plano te leva", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val r = fim.referenciaEur
            val al = fim.alargadaEur
            val textoFim = buildString {
                append("Em ").append(mesCurto(fim.mes)).append(": ")
                if (r != null && al != null) {
                    append("entre ").append(Formatos.eur(r, 0)).append(" e ").append(Formatos.eur(al, 0))
                    fim.baseEur?.let { append(" (base ").append(Formatos.eur(it, 0)).append(")") }
                } else {
                    append(Formatos.eur(fim.baseEur ?: r ?: al, 0)).append(" pela meta base")
                }
                append(".")
            }
            Text(textoFim, style = MaterialTheme.typography.bodyMedium)
            fim.historicaEur?.let {
                TextoPequeno("Ao ritmo histórico da carteira (TTWROR real): ${Formatos.eur(it, 0)}.")
            }
            if (plano?.taxaBase != null) {
                val taxas = listOfNotNull(plano.taxaReferencia, plano.taxaBase, plano.taxaAlargada).joinToString(" / ") { Formatos.pct(it) }
                TextoPequeno(
                    "Pressupostos (linha 3 da tab Evolução Patrimonial): " +
                        (reforcoPlano?.let { "${Formatos.eur(it, 0)} por mês e " } ?: "") + "$taxas ao ano.",
                )
            }
        }
    }
}

/** Régua com a faixa do plano (referência → alargada), o traço da meta base e um ponto em hoje. */
@Composable
private fun ReguaPlano(atual: Double, referencia: Double?, base: Double, alargada: Double?) {
    val corAtual = MaterialTheme.colorScheme.primary
    val trilho = MaterialTheme.colorScheme.surfaceVariant
    val fundo = corCartao()
    val lo0 = min(min(referencia ?: base, base), atual)
    val hi0 = max(max(alargada ?: base, base), atual)
    val folga = max((hi0 - lo0) * 0.25, hi0 * 0.002)
    val lo = lo0 - folga
    val hi = hi0 + folga
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val x = { v: Double -> ((v - lo) / (hi - lo) * size.width).toFloat() }
        val meio = size.height / 2
        val alt = 10.dp.toPx()
        drawRoundRect(trilho, Offset(0f, meio - alt / 2), Size(size.width, alt), CornerRadius(alt / 2))
        if (referencia != null && alargada != null) {
            drawRoundRect(
                Paleta.dourado.copy(alpha = 0.3f), Offset(x(referencia), meio - alt / 2),
                Size(x(alargada) - x(referencia), alt), CornerRadius(alt / 2),
            )
        }
        drawLine(Paleta.dourado, Offset(x(base), meio - alt), Offset(x(base), meio + alt), strokeWidth = 3.dp.toPx())
        drawCircle(corAtual, 7.dp.toPx(), Offset(x(atual), meio))
        drawCircle(fundo, 2.5.dp.toPx(), Offset(x(atual), meio))
    }
}

// ───────────────────────────── Marcos ─────────────────────────────

@Composable
private fun CartaoMarcos(a: AnalisePatrimonio) {
    val proximo = a.proximoMarcoEur ?: return
    val anterior = a.marcoAnteriorEur ?: 0.0
    Cartao("Marcos") {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                TextoPequeno("Próximo marco")
                Text(Formatos.eur(proximo, 0), style = MaterialTheme.typography.headlineSmall.numeros(), fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.End) {
                TextoPequeno("Faltam")
                Text(Formatos.eur(proximo - a.atualEur, 0), style = MaterialTheme.typography.titleMedium.numeros(), fontWeight = FontWeight.SemiBold)
            }
        }
        BarraComMarca(a.atualEur - anterior, null, proximo - anterior, MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth()) {
            TextoPequeno(Formatos.eur(anterior, 0), modifier = Modifier.weight(1f))
            TextoPequeno(Formatos.pct((a.atualEur - anterior) / (proximo - anterior), 0) + " do caminho")
        }
        a.marcoAoRitmo?.let { m ->
            LinhaValor("Ao ritmo dos últimos 12 meses", mesCurto(m), destaque = true)
        }
        a.marcoPeloPlano?.let { m ->
            LinhaValor("Pela meta base da folha", mesCurto(m))
        }
        if (a.marcoAoRitmo != null) {
            TextoPequeno("O ritmo inclui reforços e mercado; um ano mau no mercado atrasa a data.")
        }
        if (a.marcosAtingidos.isNotEmpty()) {
            HorizontalDivider(color = corBordaCartao())
            TextoPequeno("Já passaste")
            val cronologico = a.marcosAtingidos.reversed()
            a.marcosAtingidos.forEach { m ->
                val k = cronologico.indexOf(m)
                val desde = if (k > 0) cronologico[k - 1].atingidoEm else a.inicio.mes
                val meses = AnalisePatrimonio.mesesEntre(desde, m.atingidoEm)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Paleta.verde))
                    Spacer(Modifier.width(8.dp))
                    Text(Formatos.eur(m.valorEur, 0), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.numeros())
                    Text(mesCurto(m.atingidoEm), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (meses > 0) "$meses meses" else "",
                        modifier = Modifier.width(78.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ───────────────────────────── Ano a ano ─────────────────────────────

@Composable
private fun CartaoAnoAAno(a: AnalisePatrimonio) {
    Cartao("Ano a ano") {
        BarrasAnuais(
            a.anos.map { y ->
                BarraAno(
                    rotulo = y.ano.toString(),
                    valor = y.fimEur,
                    nota = y.crescimentoPct?.let { Formatos.pctSinal(it, 0) },
                    corNota = corDoSinal(y.crescimentoEur),
                )
            },
            formatar = { Formatos.compacto(it) },
        )
        TextoPequeno("Património no fim de cada ano (o deste ano é o de agora), em euros.")
        a.anos.reversed().forEach { y ->
            HorizontalDivider(color = corBordaCartao())
            LinhaAno(y)
        }
        TextoPequeno(
            "Reforços: compras − vendas de ações e ETFs (tab Transações). O resto é a valorização e o que juntaste " +
                "fora da carteira (contas, certificados...).",
        )
    }
}

@Composable
private fun LinhaAno(y: AnoPatrimonio) {
    val periodo = when {
        y.parcial && y.mesInicio.endsWith("-01").not() -> "${mesCurto(y.mesInicio).substringBefore('/')}–${mesCurto(y.mesFim).substringBefore('/')}"
        y.parcial -> "até ${mesCurto(y.mesFim).substringBefore('/')}"
        else -> null
    }
    val resto = y.crescimentoEur - y.reforcosEur
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(y.ano.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (periodo != null) {
                Spacer(Modifier.width(6.dp))
                TextoPequeno(periodo)
            }
            Spacer(Modifier.weight(1f))
            Text(
                Formatos.eurSinal(y.crescimentoEur) + (y.crescimentoPct?.let { " (${Formatos.pctSinal(it)})" } ?: ""),
                style = MaterialTheme.typography.bodyMedium.numeros(),
                fontWeight = FontWeight.Medium,
                color = corDoSinal(y.crescimentoEur),
            )
        }
        if (y.crescimentoEur > 0 && y.reforcosEur >= 0 && resto >= 0) {
            BarraEmpilhada(
                listOf(y.reforcosEur to MaterialTheme.colorScheme.primary, resto to Paleta.dourado),
                altura = 8.dp,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legenda(MaterialTheme.colorScheme.primary, "Reforços", Formatos.eurSinal(y.reforcosEur))
            Legenda(Paleta.dourado, "Mercado e outras poupanças", Formatos.eurSinal(resto))
        }
    }
}

// ───────────────────────────── Composição ─────────────────────────────

/** Junta as classes da tab Investimentos em grupos legíveis. */
internal fun agruparClasses(classes: List<ClassePatrimonio>): List<Pair<String, Double>> {
    val grupos = linkedMapOf<String, Double>()
    classes.forEach { c ->
        val n = c.classe.lowercase()
        val g = when {
            n.startsWith("ações") || n.startsWith("acoes") -> "Ações"
            n.startsWith("etf") -> "ETFs"
            "certificado" in n -> "Certificados de Aforro"
            "ppr" in n -> "PPR"
            "p2p" in n -> "P2P"
            "cripto" in n -> "Cripto"
            // Os saldos das corretoras podem ter mais do que dinheiro (a Trading Republic tem cripto e ETFs).
            "saldo" in n || "cash" in n || "revolut" in n || "depósito" in n || "deposito" in n -> "Corretoras e liquidez"
            else -> c.classe
        }
        grupos[g] = (grupos[g] ?: 0.0) + c.valorEur
    }
    val total = grupos.values.sum()
    return grupos.entries
        .filter { total > 0 && it.value / total >= 0.002 }
        .sortedByDescending { it.value }
        .map { it.key to it.value }
}

@Composable
private fun CartaoComposicao(p: Patrimonio) {
    val grupos = agruparClasses(p.classes)
    if (grupos.isEmpty()) return
    val total = p.totalEur ?: grupos.sumOf { it.second }
    Cartao("Composição hoje") {
        BarraEmpilhada(grupos.mapIndexed { k, (_, v) -> v to Paleta.categoria(k) })
        grupos.forEachIndexed { k, (nome, valor) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Paleta.categoria(k)))
                Spacer(Modifier.width(8.dp))
                Text(nome, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(Formatos.eur(valor, 0), style = MaterialTheme.typography.bodyMedium.numeros())
                Text(
                    Formatos.pct(if (total > 0) valor / total else null, 0),
                    modifier = Modifier.width(48.dp),
                    style = MaterialTheme.typography.bodySmall.numeros(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
        TextoPequeno("Da tab Investimentos: ações e ETFs ao vivo; o resto é o último saldo que lá puseste.")
    }
}

// ───────────────────────────── Cartão do Início ─────────────────────────────

/** Resumo do património no Início: valor, este ano, a linha dos últimos 2 anos e o próximo marco. */
@Composable
fun CartaoPatrimonioResumo(a: AnalisePatrimonio, vm: CarteiraViewModel) {
    val abrir = { vm.irPara(Ecra.Patrimonio) }
    Cartao(
        "Património",
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = abrir),
        acao = "Ver evolução",
        aoAcao = abrir,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(Formatos.eur(a.atualEur, 0), style = MaterialTheme.typography.headlineSmall.numeros(), fontWeight = FontWeight.SemiBold)
                a.esteAnoEur?.let { v ->
                    Text(
                        Formatos.eurSinal(v) + (a.esteAnoPct?.let { " (${Formatos.pctSinal(it)})" } ?: "") + " em ${a.mesAtual.take(4)}",
                        style = MaterialTheme.typography.bodySmall.numeros(),
                        color = corDoSinal(v),
                    )
                }
            }
            a.posicaoPlano?.let { p ->
                val (texto, cor) = textoPosicao(p)
                Spacer(Modifier.width(8.dp))
                SeloEstado(texto, cor)
            }
        }
        val meses = remember(a) {
            val inicio = AnalisePatrimonio.somarMeses(a.mesAtual, -24).let { if (it < a.inicio.mes) a.inicio.mes else it }
            (0..AnalisePatrimonio.mesesEntre(inicio, a.mesAtual)).map { AnalisePatrimonio.somarMeses(inicio, it) }
        }
        val porMes = remember(a) { a.serie.associateBy { it.mes } }
        MiniGrafico(meses.map { porMes[it]?.valorEur }, meses.map { a.metaDe(it)?.baseEur })
        val proximo = a.proximoMarcoEur
        if (proximo != null) {
            TextoPequeno(
                "Próximo marco: ${Formatos.eur(proximo, 0)} · faltam ${Formatos.eur(proximo - a.atualEur, 0)}" +
                    (a.marcoAoRitmo?.let { " · ${mesCurto(it)} ao ritmo atual" } ?: ""),
            )
        }
    }
}
