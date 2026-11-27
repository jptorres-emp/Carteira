package app.carteira.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.carteira.dados.AnalisePatrimonio
import app.carteira.dados.AnoPatrimonio
import app.carteira.dados.ClassePatrimonio
import app.carteira.dados.Faixa
import app.carteira.dados.GruposPatrimonio
import app.carteira.dados.OpcaoReforco
import app.carteira.dados.Patrimonio
import app.carteira.dados.PosicaoPlano
import app.carteira.dados.ProjecaoRendimentos
import app.carteira.dados.Rendimentos
import kotlin.math.max
import kotlin.math.min

/*
 * Peças da vista Património do separador Evolução (EvolucaoEcra): o topo, o gráfico com o plano,
 * "Face ao plano", os marcos, o ano a ano e a composição de hoje.
 */

internal fun textoPosicao(p: PosicaoPlano): Pair<String, Color> = when (p) {
    PosicaoPlano.ACIMA_BASE -> "Acima da meta base" to Paleta.verde
    PosicaoPlano.ENTRE_REFERENCIA_E_BASE -> "No limite" to Paleta.ambar
    PosicaoPlano.ABAIXO_REFERENCIA -> "Abaixo da referência" to Paleta.vermelho
}

// ───────────────────────────── Topo ─────────────────────────────

@Composable
internal fun TopoPatrimonio(a: AnalisePatrimonio, rendimentos: Rendimentos? = null) {
    val branco = Color.White
    val ano = a.mesAtual.take(4)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Paleta.marinho, Paleta.marinhoClaro))),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Património total", style = MaterialTheme.typography.labelLarge, color = branco.copy(alpha = 0.75f))
            Text(
                Formatos.eur(a.atualEur, 0),
                style = MaterialTheme.typography.displaySmall.numeros(),
                fontWeight = FontWeight.SemiBold,
                color = branco,
            )
            // Os selos passam para baixo um do outro se não couberem lado a lado.
            LinhaLegendas(espaco = 8.dp) {
                a.vsMesAnteriorEur?.let { v ->
                    SeloTopo("${if (v >= 0) "▲" else "▼"} ${Formatos.eurSinal(v)} este mês", if (v >= 0) Paleta.verde else Paleta.vermelho)
                }
                a.posicaoPlano?.let { p ->
                    val (texto, cor) = textoPosicao(p)
                    SeloTopo(texto, cor)
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
            if (rendimentos != null && rendimentos.totalEur > 0) {
                Text(
                    "Rende ${Formatos.eur(rendimentos.totalEur, 0)} por ano (${Formatos.eur(rendimentos.porMesEur, 0)}/mês): " +
                        listaDeTipos(rendimentos),
                    style = MaterialTheme.typography.bodySmall.numeros(),
                    color = branco.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** "dividendos, juros e P2P". */
private fun listaDeTipos(r: Rendimentos): String {
    val nomes = r.tipos.map { if (it.tipo == Rendimentos.P2P) it.tipo else it.tipo.lowercase() }
    return if (nomes.size <= 1) nomes.joinToString() else nomes.dropLast(1).joinToString(", ") + " e " + nomes.last()
}

@Composable
private fun SeloTopo(texto: String, cor: Color) {
    Surface(color = cor.copy(alpha = 0.45f), shape = RoundedCornerShape(50)) {
        Text(
            texto,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge.numeros(),
            color = Color.White,
        )
    }
}

@Composable
private fun ColunaTopo(rotulo: String, valor: String, detalhe: String?, modifier: Modifier) {
    Column(modifier) {
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f))
        Text(
            valor,
            style = MaterialTheme.typography.titleSmall.numeros(),
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        if (detalhe != null) {
            Text(detalhe, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f))
        }
    }
}

// ───────────────────────────── Plano ─────────────────────────────

@Composable
internal fun CartaoPlano(a: AnalisePatrimonio) {
    val plano = a.plano
    val dicaPlano = buildString {
        append("A meta de hoje fica entre a do fim do mês passado e a do fim deste mês, pelo dia do mês.")
        if (plano?.taxaBase != null) {
            val taxas = listOfNotNull(plano.taxaReferencia, plano.taxaBase, plano.taxaAlargada).joinToString(" / ") { Formatos.pct(it) }
            append("\n\nPressupostos (linha 3 da tab Evolução Patrimonial): ")
            plano.reforcoMensalEur?.let { append("${Formatos.eur(it, 0)} por mês e ") }
            append("$taxas ao ano (referência / base / alargada).")
        }
    }
    Cartao("Face ao plano", dica = dicaPlano) {
        val hoje = a.metaHoje
        val base = hoje?.baseEur
        if (hoje != null && base != null) {
            ReguaPlano(a.atualEur, hoje.referenciaEur, base, hoje.alargadaEur)
            LinhaLegendas {
                Legenda(MaterialTheme.colorScheme.primary, "Hoje", Formatos.eur(a.atualEur, 0))
                Legenda(Paleta.dourado, "Meta base", Formatos.eur(base, 0))
                val r = hoje.referenciaEur
                val al = hoje.alargadaEur
                if (r != null && al != null) {
                    Legenda(Paleta.dourado.copy(alpha = 0.45f), "Intervalo", Formatos.intervaloEur(r, al))
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
        }
        val doMes = a.metaDoMes?.baseEur
        if (doMes != null) {
            HorizontalDivider(color = corBordaCartao())
            val falta = doMes - a.atualEur
            LinhaValor("Meta base para o fim de ${mesLongo(a.mesAtual).substringBefore(" de")}", Formatos.eur(doMes, 0))
            Text(
                if (falta > 0) "Faltam ${Formatos.eur(falta, 0)} até ao fim do mês." else "Já passaste a meta do fim do mês.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (falta > 0) MaterialTheme.colorScheme.onSurface else legivel(Paleta.verde),
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
            Text("Onde o plano te leva (${mesCurto(fim.mes)})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            fim.baseEur?.let { LinhaValor("Meta base", Formatos.eur(it, 0), destaque = true) }
            val r = fim.referenciaEur
            val al = fim.alargadaEur
            if (r != null && al != null) LinhaValor("Intervalo", Formatos.intervaloEur(r, al))
            fim.historicaEur?.let { LinhaValor("Ao ritmo histórico (TTWROR real)", Formatos.eur(it, 0)) }
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
internal fun CartaoMarcos(a: AnalisePatrimonio) {
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
            TextoPequeno("Já passaste (e quantos meses levou cada um)")
            val cronologico = a.marcosAtingidos.reversed()
            a.marcosAtingidos.forEach { m ->
                val k = cronologico.indexOf(m)
                val desde = if (k > 0) cronologico[k - 1].atingidoEm else a.inicio.mes
                val meses = AnalisePatrimonio.mesesEntre(desde, m.atingidoEm)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Paleta.verde))
                    Spacer(Modifier.width(8.dp))
                    Text(Formatos.eur(m.valorEur, 0), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.numeros())
                    Spacer(Modifier.width(8.dp))
                    Text(
                        mesCurto(m.atingidoEm) + if (meses > 0) " · $meses meses" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

// ───────────────────────────── Ano a ano ─────────────────────────────

@Composable
internal fun CartaoAnoAAno(a: AnalisePatrimonio) {
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
        TextoPequeno("Património no fim de cada ano (o deste ano é o de agora).")
        HorizontalDivider(color = corBordaCartao())
        LinhaLegendas {
            Legenda(MaterialTheme.colorScheme.primary, "Reforços")
            Legenda(Paleta.dourado, "Mercado e outras poupanças")
        }
        a.anos.reversed().forEach { y -> LinhaAno(y) }
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
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        TextoPequeno("Reforços ${Formatos.eurSinal(y.reforcosEur)} · mercado e outras ${Formatos.eurSinal(resto)}")
    }
}

// ───────────────────────────── Composição ─────────────────────────────

/** Junta as classes da tab Investimentos em grupos legíveis. */
internal fun agruparClasses(classes: List<ClassePatrimonio>): List<Pair<String, Double>> {
    val grupos = linkedMapOf<String, Double>()
    classes.forEach { c ->
        val g = GruposPatrimonio.de(c.classe)
        grupos[g] = (grupos[g] ?: 0.0) + c.valorEur
    }
    val total = grupos.values.sum()
    return grupos.entries
        .filter { total > 0 && it.value / total >= 0.002 }
        .sortedByDescending { it.value }
        .map { it.key to it.value }
}

@Composable
internal fun CartaoComposicao(p: Patrimonio) {
    val grupos = agruparClasses(p.classes)
    if (grupos.isEmpty()) return
    val total = p.totalEur ?: grupos.sumOf { it.second }
    Cartao("Composição hoje", dica = "Da tab Investimentos: ações e ETFs ao vivo; o resto é o último saldo que lá puseste.") {
        BarraEmpilhada(grupos.mapIndexed { k, (_, v) -> v to Paleta.categoria(k) })
        grupos.forEachIndexed { k, (nome, valor) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Paleta.categoria(k)))
                Spacer(Modifier.width(8.dp))
                Text(nome, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
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
    }
}

// ───────────────────────────── Rendimentos ─────────────────────────────

/**
 * Dividendos, juros, P2P…: quanto o património rende por ano, de onde vem e o que já recebeste. Os valores são brutos,
 * como na folha; com [comIrs], mostra também o que fica depois dos 28% de IRS.
 */
@Composable
internal fun CartaoRendimentos(r: Rendimentos, comIrs: Boolean = true) {
    if (r.tipos.isEmpty()) return
    val total = r.totalEur
    val dicaRendimentos = "Valores brutos, por ano, ao ritmo de hoje: o valor de cada classe × a taxa da tab Investimentos" +
        (if (r.temPagosUltimos12m) "; os ETFs que lá estão a 0%, pelo que pagaram nos últimos 12 meses" else "") +
        if (comIrs) {
            ". Os 28% de IRS são a taxa liberatória sobre dividendos e juros."
        } else {
            ". O IRS está desligado (escolhe-se em Rendimentos no futuro)."
        }
    Cartao("Rendimentos por ano", dica = dicaRendimentos) {
        Text(
            Formatos.eur(total, 0),
            style = MaterialTheme.typography.headlineSmall.numeros(),
            fontWeight = FontWeight.SemiBold,
        )
        TextoPequeno(
            listOfNotNull(
                "${Formatos.eur(r.porMesEur, 0)} por mês",
                r.taxa?.let { "${Formatos.pct(it)} do património" },
                if (comIrs) "≈ ${Formatos.eur(r.liquidoEur, 0)} depois dos 28% de IRS" else null,
            ).joinToString(" · "),
        )
        BarraEmpilhada(r.tipos.mapIndexed { k, t -> t.valorEur to Paleta.categoria(k) })
        r.tipos.forEachIndexed { k, t ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Paleta.categoria(k)))
                    Spacer(Modifier.width(8.dp))
                    Text(t.tipo, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(Formatos.eur(t.valorEur, 0), style = MaterialTheme.typography.bodyMedium.numeros())
                    Text(
                        Formatos.pct(if (total > 0) t.valorEur / total else null, 0),
                        modifier = Modifier.width(48.dp),
                        style = MaterialTheme.typography.bodySmall.numeros(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
                // As partes ("Ações 865 € · ETFs 236 €"), se o tipo tiver mais do que a que lhe dá o nome.
                if (t.partes.size > 1 || t.partes.first().nome != t.tipo) {
                    TextoPequeno(
                        t.partes.joinToString(" · ") { p ->
                            "${p.nome} ${Formatos.eur(p.valorEur, 0)}" + if (p.pagoUltimos12m) " (pagos em 12 meses)" else ""
                        },
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = corBordaCartao())
        r.dividendos12mEur?.let { v ->
            TextoPequeno(
                "Dividendos recebidos: ${Formatos.eur(v, 0)} nos últimos 12 meses" +
                    (r.dividendosAnoEur?.let { a -> " · ${Formatos.eur(a, 0)} em ${r.ano}" } ?: "") + ".",
            )
        }
    }
}

// ───────────────────────────── Rendimentos no futuro ─────────────────────────────

/**
 * O que o património vai render em dinheiro daqui a 1 a 30 anos, como nos simuladores de reforma: Monte Carlo com a
 * mediana e o intervalo dos percentis 10 a 90, impostos e euros de hoje. Ajusta-se sozinho à tua evolução: parte
 * dos valores e taxas de hoje, usa o teu reforço real, o que as tuas metas pagam e a volatilidade da tua carteira.
 */
@Composable
internal fun CartaoProjecao(p: ProjecaoRendimentos, comIrs: Boolean = true, aoMudarIrs: (Boolean) -> Unit = {}) {
    if (p.reforcos.isEmpty()) return
    var reforcoEscolhido by rememberSaveable { mutableIntStateOf(p.indiceReforcoPadrao) }
    var nominais by rememberSaveable { mutableStateOf(false) }
    val reforco = p.reforcos[reforcoEscolhido.coerceIn(0, p.reforcos.lastIndex)]
    val rotulosReforcos = p.reforcos.map { "${it.nome} ${Formatos.eur(it.mensalEur, 0)}" }
    val rotulosValores = listOf(VALORES_DE_HOJE, VALORES_NOMINAIS)
    // Com IRS, os dividendos reinvestem-se líquidos e o rendimento mostra-se depois dos 28%; sem IRS, tudo bruto.
    val projecao = remember(p, comIrs) { p.comIrs(comIrs) }
    val pontos = remember(projecao, reforco.mensalEur) { projecao.simular(reforco.mensalEur) }
    if (pontos.isEmpty()) return
    val emValores = { f: Faixa, anos: Int -> if (nominais) f else f.map { ProjecaoRendimentos.emEurosDeHoje(it, anos) } }
    val liquido = { f: Faixa -> f.map { it * projecao.fatorLiquido } }
    Cartao("Rendimentos no futuro", dica = dicaProjecao(projecao, reforco, nominais)) {
        LinhaEscolha("Reforço", rotulosReforcos, rotulosReforcos[p.reforcos.indexOf(reforco)]) { rotulo ->
            reforcoEscolhido = rotulosReforcos.indexOf(rotulo).coerceAtLeast(0)
        }
        LinhaEscolha("Valores", rotulosValores, if (nominais) VALORES_NOMINAIS else VALORES_DE_HOJE) { rotulo ->
            nominais = rotulo == VALORES_NOMINAIS
        }
        LinhaEscolha("IRS", listOf(COM_IRS, SEM_IRS), if (comIrs) COM_IRS else SEM_IRS) { rotulo ->
            aoMudarIrs(rotulo == COM_IRS)
        }
        // A tabela de 4 colunas só cabe com largura (medida com a letra do telemóvel); num ecrã estreito ou com letra
        // grande, cada horizonte ocupa duas linhas, para nada ficar cortado (o ano, os intervalos).
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tabela = maxWidth >= LARGURA_TABELA * LocalDensity.current.fontScale
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CabecalhoProjecao(tabela)
                LinhaProjecao("Hoje", null, p.rendimentoHojeEur * projecao.fatorLiquido, null, tabela)
                pontos.forEach { pt ->
                    val r = liquido(emValores(pt.rendimento, pt.anos))
                    LinhaProjecao(if (pt.anos == 1) "1 ano" else "${pt.anos} anos", pt.ano.toString(), r.p50, r, tabela)
                }
            }
        }
        HorizontalDivider(color = corBordaCartao())
        val ultimo = pontos.last()
        val patrimonio = emValores(ultimo.patrimonio, ultimo.anos).p50
        val prudente = patrimonio * ProjecaoRendimentos.LEVANTAMENTO_PRUDENTE
        val classico = patrimonio * ProjecaoRendimentos.LEVANTAMENTO
        TextoComDica(
            "Património em ${ultimo.ano}: ${Formatos.eur(patrimonio, 0)} · levantar 3,5–4%: " +
                "${Formatos.eur(prudente / 12, 0)}–${Formatos.eur(classico / 12, 0)}/mês",
            dica = "Quanto podias levantar por ano do património mediano sem o gastar: ${Formatos.eur(prudente, 0)} a " +
                "${Formatos.eur(classico, 0)} por ano. A regra dos 4% vem dos EUA; na Europa, 3,5% é mais prudente.\n\n" +
                "Numa carteira de acumulação é este o número que conta: os ETFs de acumulação não pagam dividendos, mas crescem." +
                if (comIrs) " Antes do IRS das mais-valias (28% do lucro de cada venda)." else "",
            tituloDica = "Levantar do património",
        )
        resumoEvolucao(p)?.let { (resumo, dica) -> TextoComDica(resumo, dica, tituloDica = "A tua evolução") }
    }
}

/** Como se calcula: o método, o intervalo, os pressupostos e o IRS (o que antes enchia o cartão). */
private fun dicaProjecao(p: ProjecaoRendimentos, reforco: OpcaoReforco, nominais: Boolean): String {
    val comIrs = p.irs > 0
    val deOnde = if (reforco.nome == ProjecaoRendimentos.TEU_REFORCO) "a tua média dos últimos 12 meses" else "o reforço do plano"
    val volatilidade = Formatos.pct(p.volatilidade) + if (p.volatilidadeReal) ", a da tua carteira" else ", de referência"
    val metas = p.parteAcoesNovo?.let { " (as tuas metas: ${Formatos.pct(it, 0)} em ações e o resto no ETF de acumulação, que não paga)" } ?: ""
    return "Mediana de ${Formatos.numero(ProjecaoRendimentos.SIMULACOES.toDouble(), 0)} simulações de mercado, mês a mês, " +
        "como nos simuladores de reforma. O intervalo vai do percentil 10 (mercados maus) ao 90 (mercados bons): 8 em cada " +
        "10 simulações ficam lá dentro.\n\n" +
        "Pressupostos: ${Formatos.eur(reforco.mensalEur, 0)} por mês para ações e ETFs ($deOnde); a carteira a crescer " +
        "${Formatos.pct(p.taxaMediana)} ao ano na mediana (o cenário base do teu plano), com volatilidade de $volatilidade, " +
        (if (comIrs) "e os dividendos reinvestidos depois dos 28% de IRS. " else "e os dividendos reinvestidos por inteiro. ") +
        "O que já tens rende ${Formatos.pct(p.taxaExistente)} " +
        "(a previsão de dividendos, já sem as vendas do plano); o dinheiro novo rende ${Formatos.pct(p.taxaNovo)}$metas. " +
        "Certificados, P2P e saldos às taxas da tab Investimentos, sem reforços.\n\n" +
        (
            if (comIrs) {
                "IRS: com 28%, a taxa liberatória sobre dividendos e juros, os valores são o que te fica depois do imposto. " +
                    "Sem IRS, tudo bruto. Vale também para os Rendimentos por ano.\n\n"
            } else {
                "IRS: desligado, os valores são brutos e os dividendos reinvestem-se por inteiro. Com 28%, ficam depois da " +
                    "taxa liberatória. Vale também para os Rendimentos por ano.\n\n"
            }
        ) +
        "Tudo se recalcula com os dados da folha. " +
        if (nominais) "Valores nominais." else "Valores em euros de hoje (${Formatos.pct(ProjecaoRendimentos.INFLACAO, 0)} de inflação ao ano)."
}

/**
 * "Investes 772 €/mês (plano 800 €) · a carteira rendeu 12,7%/ano (plano 6,5%)", com a explicação no ⓘ.
 * Null se a folha não tiver nem o teu reforço nem a tua rendibilidade.
 */
private fun resumoEvolucao(p: ProjecaoRendimentos): Pair<String, String>? {
    val partes = mutableListOf<String>()
    val dica = mutableListOf<String>()
    p.teuReforco?.let { teu ->
        partes += "Investes ${Formatos.eur(teu.mensalEur, 0)}/mês" + (p.reforcoDoPlano?.let { " (plano ${Formatos.eur(it.mensalEur, 0)})" } ?: "")
        dica += "Investes: a média dos teus reforços nos últimos 12 meses fechados (tab Evolução Patrimonial)."
    }
    p.twrReal?.let { twr ->
        partes += "a carteira rendeu ${Formatos.pct(twr)}/ano (plano ${Formatos.pct(p.taxaMediana)})"
        dica += "Rendeu: o TWR anualizado com dividendos desde o início" +
            (p.ritmoUltimos12m?.let { " (${Formatos.pct(it)} nos últimos 12 meses)" } ?: "") + ". A projeção usa os " +
            "${Formatos.pct(p.taxaMediana)} do plano e não o teu passado: poucos anos, de mercado em alta e medidos em dólares, " +
            "não chegam para projetar trinta."
    }
    if (partes.isEmpty()) return null
    return partes.joinToString(" · ") to dica.joinToString("\n\n")
}

private const val VALORES_DE_HOJE = "Euros de hoje"
private const val VALORES_NOMINAIS = "Nominais"
private const val COM_IRS = "Com 28%"
private const val SEM_IRS = "Sem"

/** "Reforço  [O teu 772 €] [Plano 800 €]": uma linha de escolhas com o nome à esquerda. */
@Composable
private fun LinhaEscolha(nome: String, opcoes: List<String>, escolhida: String, aoEscolher: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextoPequeno(nome, modifier = Modifier.width(58.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)))
        Filtros(opcoes, escolhida, compacto = true, quebrar = true, aoEscolher = aoEscolher)
    }
}

/** "1,6–2,9 mil €" ou "540–720 €". */
private fun intervalo(f: Faixa): String =
    if (f.p90 < 1_000) {
        "${Formatos.numero(f.p10, 0)}–${Formatos.eur(f.p90, 0)}"
    } else {
        fun mil(x: Double) = Formatos.numero(x / 1_000, if (x < 100_000) 1 else 0)
        "${mil(f.p10)}–${mil(f.p90)} mil${Formatos.NB}€"
    }

/** Abaixo desta largura (com letra normal; cresce com a letra), a projeção passa de tabela a duas linhas por horizonte. */
private val LARGURA_TABELA = 330.dp

@Composable
private fun CabecalhoProjecao(tabela: Boolean) {
    val estilo = MaterialTheme.typography.labelSmall
    val cor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth()) {
        if (tabela) {
            Text("Daqui a", Modifier.weight(1.25f), style = estilo, color = cor)
            Text("Por ano", Modifier.weight(1f), style = estilo, color = cor, textAlign = TextAlign.End)
            Text("Por mês", Modifier.weight(0.85f), style = estilo, color = cor, textAlign = TextAlign.End)
            Text("Intervalo/ano", Modifier.weight(1.4f), style = estilo, color = cor, textAlign = TextAlign.End)
        } else {
            Text("Daqui a", style = estilo, color = cor)
            Text(
                "Por ano · por mês · intervalo/ano",
                Modifier.weight(1f).padding(start = 12.dp),
                style = estilo,
                color = cor,
                textAlign = TextAlign.End,
            )
        }
    }
}

/**
 * Um horizonte da projeção. Em tabela: "10 anos 2036 | 1 124 € | 94 € | 0,8–1,7 mil €". Em duas linhas (ecrã
 * estreito): "10 anos" com o ano por baixo, e à direita o valor por ano com o por mês e o intervalo por baixo.
 */
@Composable
private fun LinhaProjecao(rotulo: String, ano: String?, porAnoEur: Double, faixa: Faixa?, tabela: Boolean) {
    if (!tabela) {
        Row(Modifier.fillMaxWidth()) {
            Column {
                Text(rotulo, style = MaterialTheme.typography.bodyMedium)
                if (ano != null) {
                    Text(ano, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), horizontalAlignment = Alignment.End) {
                Text(
                    Formatos.eur(porAnoEur, 0),
                    style = MaterialTheme.typography.bodyMedium.numeros(),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                )
                Text(
                    listOfNotNull("${Formatos.eur(porAnoEur / 12, 0)}/mês", faixa?.let { intervalo(it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.numeros(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1.25f), verticalAlignment = Alignment.CenterVertically) {
            Text(rotulo, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (ano != null) {
                Spacer(Modifier.width(4.dp))
                Text(ano, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Text(
            Formatos.eur(porAnoEur, 0),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.numeros(),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Text(
            Formatos.eur(porAnoEur / 12, 0),
            Modifier.weight(0.85f),
            style = MaterialTheme.typography.bodyMedium.numeros(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        Text(
            faixa?.let { intervalo(it) } ?: "",
            Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodySmall.numeros(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}
