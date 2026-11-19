package app.carteira.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.carteira.dados.Gatilhos
import app.carteira.dados.Posicao

@Composable
fun PosicaoDetalheEcra(estado: Estado, vm: CarteiraViewModel, ticker: String) {
    val d = estado.dados ?: return SemDados(estado, vm)
    val p = d.posicoes.firstOrNull { it.ticker == ticker }
    if (p == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$ticker já não está na carteira.")
        }
        return
    }
    val a = p.avaliacao
    val moedaLocal = a?.moeda ?: "USD"
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { CabecalhoPosicao(p, moedaLocal) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.irPara(Ecra.Registar(ModoRegisto.TRANSACAO, p.ticker, "Compra")) },
                    modifier = Modifier.weight(1f),
                ) { Text("Registar compra") }
                OutlinedButton(
                    onClick = { vm.irPara(Ecra.Registar(ModoRegisto.TRANSACAO, p.ticker, "Venda")) },
                    modifier = Modifier.weight(1f),
                ) { Text("Registar venda") }
            }
        }
        // Os preços de comprar e vender que contam para a camada, logo a seguir aos botões.
        Gatilhos.de(p.camada, a)?.let { g -> item { CartaoGatilhos(g) } }
        item {
            Cartao("A tua posição") {
                LinhaValor("Ações", Formatos.quantidade(p.quantidade))
                LinhaValor("Preço médio", Formatos.preco(p.precoCompraUsd, "USD"))
                LinhaValor("Custo", "${Formatos.eur(p.custoEur, 0)} · ${Formatos.usd(p.custoUsd, 0)}")
                LinhaValor("Valor", "${Formatos.eur(p.valorEur, 0)} · ${Formatos.usd(p.valorUsd, 0)}", destaque = true)
                // Cada valor com a sua percentagem: em euros conta o câmbio, em dólares não.
                LinhaValor(
                    "Ganho em euros",
                    "${Formatos.eurSinal(p.ganhoEur)} (${Formatos.pctSinal(p.ganhoPctEur)})",
                    cor = corDoSinal(p.ganhoEur ?: p.ganhoUsd),
                )
                LinhaValor(
                    "Ganho em dólares",
                    "${Formatos.usdSinal(p.ganhoUsd)} (${Formatos.pctSinal(p.ganhoPct)})",
                    cor = corDoSinal(p.ganhoUsd),
                )
                LinhaValor(
                    "Peso na carteira",
                    Formatos.pct(p.pesoTotal) +
                        (p.pesoAlvo?.let { " · alvo ${Formatos.pct(it)}" } ?: "") +
                        (p.pesoMaximo?.let { " · máx. ${Formatos.pct(it)}" } ?: ""),
                )
                if (p.ajusteSugeridoEur != null) {
                    LinhaValor("Ajuste sugerido", Formatos.eurSinal(p.ajusteSugeridoEur), cor = corDoSinal(p.ajusteSugeridoEur))
                }
            }
        }
        if (a != null) {
            item {
                Cartao("Avaliação (${moedaLocal})") {
                    LinhaValor("Valor intrínseco", Formatos.preco(a.valorIntrinseco, moedaLocal))
                    LinhaValor("Potencial", Formatos.pctSinal(a.potencial), cor = corDoSinal(a.potencial))
                    LinhaValor("Rendibilidade implícita", "${Formatos.pct(a.rendImplicita)} (hurdle ${Formatos.pct(a.hurdle)})")
                    a.pesoVsMaximo?.let { LinhaValor("Peso vs máximo", it) }
                    if (a.temaIa) LinhaValor("Tema IA-capex", "Sim (conta para o teto)")
                }
            }
        }
        item {
            Cartao("Dividendos") {
                val anual = p.divAnualPorAcaoUsd?.let { it * p.quantidade }
                LinhaValor("Por ação, por ano", Formatos.preco(p.divAnualPorAcaoUsd, "USD"))
                LinhaValor("Total por ano", Formatos.usd(anual))
                LinhaValor("Yield / yield on cost", "${Formatos.pct(p.yieldPct, 2)} / ${Formatos.pct(p.yieldCustoPct, 2)}")
                p.frequencia?.let { LinhaValor("Pagamento", it) }
            }
        }
        if (a != null && (a.tese != null || a.alarme != null)) {
            item {
                Cartao("Tese") {
                    a.tese?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    a.alarme?.let {
                        TextoPequeno("A vigiar")
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    a.fiscalidade?.let { TextoPequeno(it) }
                    a.fonte?.let { TextoPequeno("Fonte: $it") }
                }
            }
        }
        item {
            Cartao("Outros dados") {
                p.setor?.let { LinhaValor("Setor", it) }
                p.pe?.let { LinhaValor("P/E", Formatos.numero(it, 1)) }
                p.risco?.let { LinhaValor("Risco (1-7)", Formatos.numero(it, 0)) }
                p.rating?.let { LinhaValor("Rating", it) }
            }
        }
    }
}

@Composable
private fun CabecalhoPosicao(p: Posicao, moedaLocal: String) {
    Cartao {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.ticker, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                TextoPequeno(listOfNotNull(p.camada, p.setor).joinToString(" · "))
            }
            Column(horizontalAlignment = Alignment.End) {
                val precoLocal = p.avaliacao?.precoLocal
                Text(
                    if (precoLocal != null && moedaLocal != "USD") Formatos.preco(precoLocal, moedaLocal) else Formatos.preco(p.precoUsd, "USD"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "hoje ${Formatos.pctSinal(p.varDiaPct, 2)}",
                    color = corDoSinal(p.varDiaPct),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SeloZona(p.zona)
            p.sinal?.let { SeloZona(it) }
        }
    }
}

@Composable
fun EtfDetalheEcra(estado: Estado, vm: CarteiraViewModel, ticker: String) {
    val d = estado.dados ?: return SemDados(estado, vm)
    val e = d.etfs.firstOrNull { it.ticker == ticker }
    if (e == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$ticker já não está na carteira.")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Cartao {
                Text(e.ticker, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                e.nome?.let { TextoPequeno(it) }
                Text(
                    "${Formatos.preco(e.precoEur, "EUR")} · hoje ${Formatos.pctSinal(e.varDiaPct, 2)}",
                    color = corDoSinal(e.varDiaPct),
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.irPara(Ecra.Registar(ModoRegisto.TRANSACAO, e.ticker, "Compra")) },
                    modifier = Modifier.weight(1f),
                ) { Text("Registar compra") }
                OutlinedButton(
                    onClick = { vm.irPara(Ecra.Registar(ModoRegisto.TRANSACAO, e.ticker, "Venda")) },
                    modifier = Modifier.weight(1f),
                ) { Text("Registar venda") }
            }
        }
        item {
            Cartao("A tua posição") {
                LinhaValor("Unidades", Formatos.quantidade(e.quantidade))
                LinhaValor("Preço médio", Formatos.preco(e.precoCompraEur, "EUR"))
                LinhaValor("Custo", Formatos.eur(e.custoEur, 0))
                LinhaValor("Valor", Formatos.eur(e.valorEur, 0), destaque = true)
                LinhaValor("Ganho", "${Formatos.eurSinal(e.ganhoEur)} (${Formatos.pctSinal(e.ganhoPct)})", cor = corDoSinal(e.ganhoEur))
                LinhaValor("Peso nos ETF", Formatos.pct(e.pesoEtfs))
            }
        }
    }
}

// ───────────────────────────── Quando comprar e vender ─────────────────────────────

/** Os preços que mandam comprar e vender pela camada, com a régua do preço e a distância ao preço de hoje. */
@Composable
private fun CartaoGatilhos(g: Gatilhos) {
    Cartao("Quando comprar e vender") {
        val preco = g.preco
        if (g.temPrecos && preco != null) ReguaPrecos(g, preco)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuadroGatilho(
                titulo = "Comprar abaixo de",
                valor = g.comprar,
                moeda = g.moeda,
                distancia = g.distancia(g.comprar),
                atingido = g.naZonaDeCompra,
                cor = Paleta.verde,
                semPreco = "Não compra",
                modifier = Modifier.weight(1f),
            )
            QuadroGatilho(
                titulo = "Vender acima de",
                valor = g.vender,
                moeda = g.moeda,
                distancia = g.distancia(g.vender),
                atingido = g.acimaDeVender,
                cor = Paleta.vermelho,
                semPreco = "Não vende pelo preço",
                modifier = Modifier.weight(1f),
            )
        }
        g.reduzir?.let {
            LinhaValor("Reduzir acima de", nivelComDistancia(it, g), cor = if (g.acimaDeReduzir) Paleta.ambar else Color.Unspecified)
        }
        g.reforcar?.let { LinhaValor("Reforçar abaixo de", nivelComDistancia(it, g)) }
        g.reforcarForte?.let { LinhaValor("Reforçar forte abaixo de", nivelComDistancia(it, g)) }
        TextoPequeno(g.notaCompra)
        TextoPequeno(g.notaVenda)
    }
}

private fun nivelComDistancia(nivel: Double, g: Gatilhos): String =
    Formatos.preco(nivel, g.moeda) + (g.distancia(nivel)?.let { " (${Formatos.pctSinal(it, 0)})" } ?: "")

/** Um preço de decisão em destaque; com contorno quando o preço de hoje já lá chegou. */
@Composable
private fun QuadroGatilho(
    titulo: String,
    valor: Double?,
    moeda: String,
    distancia: Double?,
    atingido: Boolean,
    cor: Color,
    semPreco: String,
    modifier: Modifier = Modifier,
) {
    val texto = legivel(cor)
    Surface(
        color = if (valor == null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else cor.copy(alpha = if (atingido) 0.16f else 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = if (atingido) BorderStroke(1.5.dp, texto) else null,
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (valor == null) {
                Text(semPreco, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    Formatos.preco(valor, moeda),
                    style = MaterialTheme.typography.titleMedium.numeros(),
                    fontWeight = FontWeight.SemiBold,
                    color = texto,
                )
                val nota = when {
                    atingido -> "✓ o preço já lá está"
                    distancia != null -> "${Formatos.pctSinal(distancia, 0)} do preço de hoje"
                    else -> null
                }
                nota?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (atingido) texto else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Régua do preço: verde onde compra, cinzento onde mantém, âmbar onde reduz e vermelho onde vende; o ponto é hoje. */
@Composable
private fun ReguaPrecos(g: Gatilhos, preco: Double) {
    val niveis = listOfNotNull(g.comprar, g.reforcar, g.reforcarForte, g.reduzir, g.vender) + preco
    val minimo = niveis.min() * 0.9
    val maximo = niveis.max() * 1.1
    val verde = Paleta.verde
    val ambar = Paleta.ambar
    val vermelho = Paleta.vermelho
    val neutro = MaterialTheme.colorScheme.outlineVariant
    val marca = MaterialTheme.colorScheme.onSurface
    val fundo = corCartao()
    val descricao = buildString {
        append("Preço de hoje ${Formatos.preco(preco, g.moeda)}")
        g.comprar?.let { append(", comprar abaixo de ${Formatos.preco(it, g.moeda)}") }
        g.reduzir?.let { append(", reduzir acima de ${Formatos.preco(it, g.moeda)}") }
        g.vender?.let { append(", vender acima de ${Formatos.preco(it, g.moeda)}") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .semantics { contentDescription = descricao },
        ) {
            val w = size.width
            val altura = 10.dp.toPx()
            val topo = (size.height - altura) / 2
            fun x(v: Double): Float = ((v - minimo) / (maximo - minimo) * w).toFloat().coerceIn(0f, w)
            val barra = Path().apply { addRoundRect(RoundRect(0f, topo, w, topo + altura, CornerRadius(altura / 2))) }
            clipPath(barra) {
                drawRect(neutro, Offset(0f, topo), Size(w, altura))
                g.comprar?.let { drawRect(verde, Offset(0f, topo), Size(x(it), altura)) }
                g.reduzir?.let { r ->
                    val fim = g.vender?.let { x(it) } ?: w
                    drawRect(ambar, Offset(x(r), topo), Size((fim - x(r)).coerceAtLeast(0f), altura))
                }
                g.vender?.let { v -> drawRect(vermelho, Offset(x(v), topo), Size(w - x(v), altura)) }
            }
            val centro = Offset(x(preco), size.height / 2)
            drawCircle(marca, radius = 9.dp.toPx(), center = centro)
            drawCircle(fundo, radius = 5.dp.toPx(), center = centro)
        }
        LinhaLegendas(espaco = 12.dp) {
            Legenda(marca, "Hoje", Formatos.preco(preco, g.moeda))
            if (g.comprar != null) Legenda(verde, "Comprar")
            Legenda(neutro, "Manter")
            if (g.reduzir != null) Legenda(ambar, "Reduzir")
            if (g.vender != null) Legenda(vermelho, "Vender")
        }
    }
}
