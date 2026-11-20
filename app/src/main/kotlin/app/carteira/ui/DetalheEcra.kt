package app.carteira.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.carteira.dados.Historico
import app.carteira.dados.Posicao
import app.carteira.dados.Rentabilidade
import app.carteira.dados.Venda

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
        d.historico.de(p.ticker)?.let { r ->
            item { CartaoRentabilidade(r, d.historico) { vm.irPara(Ecra.Indice) } }
            if (r.aberta && r.lotes.isNotEmpty()) item { CartaoSimularVenda(r, d.historico, p) }
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
        d.historico.de(e.ticker)?.let { r ->
            item { CartaoRentabilidade(r, d.historico) { vm.irPara(Ecra.Indice) } }
            if (r.aberta && r.lotes.isNotEmpty()) item { CartaoSimularVenda(r, d.historico, null) }
        }
    }
}

// ───────────────────────────── Rentabilidade total ─────────────────────────────

/**
 * Tudo o que a posição deu desde a primeira compra: o que vale hoje, o que recebeste nas vendas e os
 * dividendos, contra o que pagaste. Inclui, só a título informativo, quanto das ações que tens o lucro já
 * pagou (de borla), a rentabilidade por ano (TIR) e o mesmo dinheiro no S&P 500 (VUAA), nas mesmas datas.
 */
@Composable
fun CartaoRentabilidade(r: Rentabilidade, h: Historico, verTodas: (() -> Unit)? = null) {
    Cartao("Rentabilidade total") {
        LinhaValor("Pagaste nas compras", "${Formatos.eur(r.compradoEur, 0)} · ${plural(r.compras, "compra", "compras")}")
        if (r.vendas > 0) {
            LinhaValor("Recebeste nas vendas", "${Formatos.eur(r.vendidoEur, 0)} · ${plural(r.vendas, "venda", "vendas")}")
        }
        if (r.dividendosEur >= 0.5) LinhaValor("Dividendos (brutos)", Formatos.eur(r.dividendosEur, 0))
        if (r.aberta) LinhaValor("Vale hoje", Formatos.eur(r.valorEur, 0))
        LinhaValor(
            "Lucro total",
            "${Formatos.eurSinal(r.lucroEur)} (${Formatos.pctSinal(r.pctEur)})",
            cor = corDoSinal(r.lucroEur),
            destaque = true,
        )
        if (!r.etf) {
            LinhaValor(
                "Em dólares",
                "${Formatos.usdSinal(r.lucroMoeda)} (${Formatos.pctSinal(r.pctMoeda)})",
                cor = corDoSinal(r.lucroMoeda),
            )
        }
        r.maisValiasEur?.let { LinhaValor("Mais-valias das vendas", Formatos.eurSinal(it), cor = corDoSinal(it)) }
        // Informativo (não entra nas regras): o lucro já tirado paga as ações que tens? Ao tocar, de onde vem.
        if (r.aberta && (r.pctDeBorla ?: 0.0) >= 0.005) {
            var aberto by rememberSaveable(r.ticker) { mutableStateOf(false) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = if (aberto) "Esconder de onde vem o lucro" else "Ver de onde vem o lucro") { aberto = !aberto },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinhaValor(
                    "Pago pelo lucro ${if (aberto) "▴" else "▾"}",
                    if (r.deBorla) {
                        "🎁 de borla (+${Formatos.eur(r.excedenteBorlaEur, 0)})"
                    } else {
                        "${Formatos.pct(r.pctDeBorla, 0)} · faltam ${Formatos.eur(r.faltaParaBorlaEur, 0)}"
                    },
                    cor = if (r.deBorla) Paleta.verde else Color.Unspecified,
                )
                if (aberto) DiscriminacaoBorla(r, h.vendasDe(r.ticker))
            }
        }
        if (h.v5) {
            LinhaValor(
                "Por ano (TIR)",
                r.tir?.let { Formatos.pctSinal(it) } ?: "menos de 1 ano",
                cor = r.tir?.let { corDoSinal(it) } ?: Color.Unspecified,
            )
        }
        ContraIndice(r, h, verTodas)
        TextoPequeno(
            "Desde a primeira compra (${Formatos.data(r.primeiraCompra)}): " +
                (if (r.aberta) "o que vale hoje + " else "") +
                "o que recebeste nas vendas + dividendos − o que pagaste, com as comissões. " +
                "Em euros, cada compra e venda conta com o câmbio do seu dia; em dólares, sem o câmbio." +
                (
                    if (h.dividendosDesde != null) {
                        " Dividendos desde ${h.dividendosDesde}, " + (if (h.v5) "ao câmbio de cada mês." else "ao câmbio de hoje.")
                    } else {
                        ""
                    }
                    ) +
                (if (r.maisValiasEur != null) " Mais-valias pelo FIFO, como no IRS." else "") +
                (
                    if (r.aberta && (r.pctDeBorla ?: 0.0) >= 0.005) {
                        " Pago pelo lucro: quanto do que custaram as ações que tens (${Formatos.eur(r.custoAtualEur, 0)}) já está " +
                            "pago pelas mais-valias e pelos dividendos (${Formatos.eur(r.lucroTiradoEur ?: 0.0, 0)}); a 100% ficam de borla."
                    } else {
                        ""
                    }
                    ) +
                (if (h.v5) " TIR: o que a posição rendeu por ano, contando com a data de cada compra, venda e dividendo; só a partir de um ano." else ""),
        )
    }
}

/**
 * O mesmo dinheiro no VUAA: cada compra põe os mesmos euros no VUAA nesse dia e cada venda tira os mesmos
 * euros, como a tab Validação S&P 500. A diferença é o que ganhaste (ou perdeste) por escolher esta ação.
 */
@Composable
private fun ContraIndice(r: Rentabilidade, h: Historico, verTodas: (() -> Unit)?) {
    if (!h.temIndice) {
        if (h.temDados) TextoPequeno("Atualiza a ponte da folha para a versão 5 para comparar com o S&P 500 e veres a TIR.")
        return
    }
    val e = r.espelho
    Text(
        "Contra o S&P 500 (VUAA)",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp),
    )
    if (e == null) {
        TextoPequeno(
            if (r.semPrecoIndice > 0) {
                "Falta o preço do S&P 500 de ${plural(r.semPrecoIndice, "transação", "transações")}: a ponte fixa-o no dia " +
                    "seguinte, ou podes colá-lo na tab Transações (colunas R e S)."
            } else {
                "Sem dados para comparar."
            },
        )
        return
    }
    LinhaValor(
        "O mesmo dinheiro no VUAA",
        Formatos.eurSinal(e.lucroEur) + (e.tir?.let { " · ${Formatos.pctSinal(it)}/ano" } ?: ""),
        cor = corDoSinal(e.lucroEur),
    )
    val dif = r.diferencaIndiceEur ?: 0.0
    LinhaValor(
        "Diferença",
        Formatos.eurSinal(dif) + if (dif >= 0) " · bateste o índice" else " · o índice fez melhor",
        cor = corDoSinal(dif),
        destaque = true,
    )
    TextoPequeno(
        "Cada compra põe os mesmos euros no VUAA nesse dia e cada venda tira os mesmos euros, com as mesmas " +
            "comissões (como a tab Validação S&P 500). O VUAA reinveste os dividendos dele." +
            if (e.unidades < -1e-6) " Vendeste mais do que o VUAA chegava a valer: a diferença conta o que faltava, ao preço de hoje." else "",
    )
    if (verTodas != null) {
        TextButton(onClick = verTodas, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
            Text("Ver todas as ações contra o S&P 500")
        }
    }
}

/**
 * De onde vem o dinheiro do "de borla": cada venda (o que recebeste, o que essas ações custaram pelo FIFO e o
 * lucro que ficou) e os dividendos de cada ano; e o que esse lucro tem de pagar, compra a compra das que tens.
 */
@Composable
internal fun DiscriminacaoBorla(r: Rentabilidade, vendas: List<Venda>) {
    val lucro = r.lucroTiradoEur ?: 0.0
    val cor = corBordaCartao()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("De onde vem o lucro · ${Formatos.eur(lucro, 0)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        vendas.forEach { v ->
            val m = v.movimento
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LinhaValor(
                    "Venda de ${Formatos.data(v.data)}",
                    v.maisValiaEur?.let { Formatos.eurSinal(it) } ?: "—",
                    cor = v.maisValiaEur?.let { corDoSinal(it) } ?: Color.Unspecified,
                )
                TextoPequeno(
                    "${Formatos.quantidade(m.unidades)} un. a ${Formatos.preco(m.preco, m.moeda)}: recebeste ${Formatos.eur(v.recebidoEur, 0)}" +
                        (
                            v.custoEur?.let { " − custaram ${Formatos.eur(it, 0)} (o teu dinheiro a voltar)" }
                                ?: "; faltam compras na folha para saber o custo"
                            ),
                )
            }
        }
        if (r.dividendosEur >= 0.5) {
            LinhaValor("Dividendos (brutos)", Formatos.eurSinal(r.dividendosEur), cor = corDoSinal(r.dividendosEur))
            if (r.dividendosPorAno.size > 1) {
                TextoPequeno(r.dividendosPorAno.joinToString(" · ") { (ano, eur) -> "$ano: ${Formatos.eur(eur, 0)}" })
            }
        }
        HorizontalDivider(color = cor)
        Text(
            "O que tem de pagar · ${Formatos.eur(r.custoAtualEur, 0)}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        TextoPequeno("O que custaram as que ainda tens, compra a compra (as mais antigas já vendidas saem primeiro).")
        val lotes = r.lotes
        lotes.take(6).forEach { l ->
            LinhaValor("Compra de ${Formatos.data(l.data)} · ${Formatos.quantidade(l.unidades)} un.", Formatos.eur(l.unidades * l.custoEurPorUnidade, 0))
        }
        if (lotes.size > 6) {
            val resto = lotes.drop(6)
            LinhaValor("Mais ${plural(resto.size, "compra", "compras")}", Formatos.eur(resto.sumOf { it.unidades * it.custoEurPorUnidade }, 0))
        }
        HorizontalDivider(color = cor)
        val saldo = lucro - r.custoAtualEur
        LinhaValor(
            if (saldo >= -0.005) "🎁 Já paga tudo e sobra" else "Ainda falta",
            Formatos.eur(kotlin.math.abs(saldo), 0),
            cor = if (saldo >= -0.005) Paleta.verde else Color.Unspecified,
            destaque = true,
        )
        TextoPequeno(
            "Pelo FIFO e com as comissões; cada compra e venda ao câmbio do seu dia" +
                (if (r.dividendosPorAno.isNotEmpty()) " e os dividendos ao câmbio de cada mês." else "."),
        )
    }
}

/** Barra do que o lucro já pagou das ações que tens: cheia (e verde) quando a posição está de borla. */
@Composable
internal fun BarraRecuperado(pct: Double, deBorla: Boolean, modifier: Modifier = Modifier) {
    val cor = if (deBorla) Paleta.verde else MaterialTheme.colorScheme.primary
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = "Pago pelo lucro ${Formatos.pct(pct, 0)}" },
    ) {
        Box(
            Modifier
                .fillMaxWidth(pct.coerceIn(0.0, 1.0).toFloat())
                .fillMaxHeight()
                .background(cor),
        )
    }
}

/** "1 compra", "3 compras". */
internal fun plural(n: Int, um: String, varios: String): String = if (n == 1) "1 $um" else "$n $varios"

// ───────────────────────────── Simular uma venda ─────────────────────────────

/**
 * Ações a vender sugeridas pela folha quando diz para reduzir ou sair (o "Ajuste sugerido" em euros, ao
 * preço de hoje), arredondadas a ações inteiras; null se a folha não sugere vender.
 */
internal fun sugestaoDeVenda(r: Rentabilidade, p: Posicao?): Double? {
    val ajuste = p?.ajusteSugeridoEur?.takeIf { it < -0.5 } ?: return null
    if (!r.aberta || r.valorEur <= 0) return null
    if (-ajuste >= r.valorEur - 0.5) return r.unidadesHoje
    val porAcao = r.valorEur / r.unidadesHoje
    val inteiras = kotlin.math.floor(r.unidadesHoje + 1e-9)
    return kotlin.math.round(-ajuste / porAcao).coerceIn(1.0, inteiras.coerceAtLeast(1.0)).coerceAtMost(r.unidadesHoje)
}

/** Mais uma ação (a última fração, se tiveres ações fracionadas). */
internal fun maisUma(n: Double, total: Double): Double = minOf(kotlin.math.floor(n + 1e-9) + 1, total)

/** Menos uma ação; nunca abaixo de uma (ou de tudo, se tiveres menos de uma). */
internal fun menosUma(n: Double, total: Double): Double {
    val inteiro = kotlin.math.floor(n + 1e-9)
    val menos = if (n - inteiro > 1e-9) inteiro else n - 1
    return menos.coerceAtLeast(minOf(1.0, total))
}

/**
 * Vender agora parte desta posição: o que recebes ao preço de hoje, o custo das ações pelo FIFO, a mais-valia
 * e o IRS estimado sobre o saldo do ano. Começa na sugestão da folha quando ela diz para reduzir ou sair.
 */
@Composable
internal fun CartaoSimularVenda(r: Rentabilidade, h: Historico, p: Posicao?) {
    val sugestao = sugestaoDeVenda(r, p)
    var n by rememberSaveable(r.ticker) { mutableStateOf(sugestao ?: minOf(1.0, r.unidadesHoje)) }
    val s = h.simularVenda(r, n) ?: return
    val nome = if (r.etf) "unidades" else "ações"
    Cartao("Simular uma venda") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { n = menosUma(n, r.unidadesHoje) }, enabled = n > minOf(1.0, r.unidadesHoje) + 1e-9) { Text("−") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Formatos.quantidade(s.unidades),
                    style = MaterialTheme.typography.titleLarge.numeros(),
                    fontWeight = FontWeight.SemiBold,
                )
                TextoPequeno("de ${Formatos.quantidade(r.unidadesHoje)} $nome")
            }
            OutlinedButton(onClick = { n = maisUma(n, r.unidadesHoje) }, enabled = n < r.unidadesHoje - 1e-9) { Text("+") }
        }
        val opcoes = buildList {
            if (sugestao != null) add("Sugestão da folha" to sugestao)
            if (r.unidadesHoje >= 2) add("Metade" to kotlin.math.round(r.unidadesHoje / 2).coerceAtLeast(1.0))
            add("Tudo" to r.unidadesHoje)
        }.distinctBy { it.second }
        Filtros(opcoes.map { it.first }, opcoes.firstOrNull { kotlin.math.abs(it.second - n) < 1e-9 }?.first ?: "", compacto = true) { o ->
            opcoes.firstOrNull { it.first == o }?.let { n = it.second }
        }
        LinhaValor("Recebes, ao preço de agora", Formatos.eur(s.recebidoEur, 0))
        LinhaValor("Custo dessas $nome (FIFO)", Formatos.eur(s.custoEur, 0))
        val mv = s.maisValiaEur
        LinhaValor("Mais-valia", Formatos.eurSinal(mv), cor = corDoSinal(mv))
        val imposto = s.impostoEur ?: 0.0
        LinhaValor(
            "IRS estimado (28%)",
            when {
                imposto > 0.5 -> Formatos.eur(imposto, 0)
                imposto < -0.5 -> "baixa ${Formatos.eur(-imposto, 0)}"
                else -> Formatos.eur(0.0, 0)
            },
            cor = if (imposto < -0.5) Paleta.verde else Color.Unspecified,
        )
        LinhaValor("Fica para ti", Formatos.eur(s.recebidoEur - imposto.coerceAtLeast(0.0), 0), destaque = true)
        if (p?.pesoTotal != null && r.valorEur > 0) {
            val depois = p.pesoTotal * (r.valorEur - s.recebidoEur) / r.valorEur
            LinhaValor(
                "Peso na carteira",
                "${Formatos.pct(p.pesoTotal)} → ${Formatos.pct(depois)}" +
                    (p.pesoMaximo?.let { " · máx. ${Formatos.pct(it)}" } ?: p.pesoAlvo?.let { " · alvo ${Formatos.pct(it)}" } ?: ""),
            )
        }
        TextoPequeno(
            "Saldo de mais-valias de ${s.ano}: ${Formatos.eurSinal(s.saldoAnoAntesEur)} → ${Formatos.eurSinal(s.saldoAnoDepoisEur)}. " +
                "O IRS é 28% do saldo do ano (mais-valias − menos-valias), se não englobares; aqui conta só a diferença " +
                "que esta venda faz. Sem comissão de venda; o preço muda até venderes." +
                if (s.unidadesMenosDeUmAno > 1e-9) {
                    " ${Formatos.quantidade(s.unidadesMenosDeUmAno)} destas $nome têm menos de um ano: se o teu rendimento " +
                        "estiver no último escalão do IRS, essa mais-valia tem de ser englobada."
                } else {
                    ""
                },
        )
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
            LinhaValor(
                "Reduzir acima de",
                nivelComDistancia(it, g, if (g.acimaDeReduzir) "já passou" else null),
                cor = if (g.acimaDeReduzir) Paleta.ambar else Color.Unspecified,
            )
        }
        g.reforcar?.let { LinhaValor("Reforçar abaixo de", nivelComDistancia(it, g, if (preco != null && preco <= it) "já lá está" else null)) }
        g.reforcarForte?.let {
            LinhaValor("Reforçar forte abaixo de", nivelComDistancia(it, g, if (preco != null && preco <= it) "já lá está" else null))
        }
        TextoPequeno(g.notaCompra)
        TextoPequeno(g.notaVenda)
    }
}

/** "120,75 $ (+6%)", ou "120,75 $ · já passou" quando o preço de hoje já lá chegou. */
private fun nivelComDistancia(nivel: Double, g: Gatilhos, atingido: String?): String =
    Formatos.preco(nivel, g.moeda) + when {
        atingido != null -> " · $atingido"
        else -> g.distancia(nivel)?.let { " (${Formatos.pctSinal(it, 0)})" } ?: ""
    }

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
