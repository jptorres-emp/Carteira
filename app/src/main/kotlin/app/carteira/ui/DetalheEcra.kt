package app.carteira.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                    a.reforcarForteAbaixo?.let { LinhaValor("Reforçar forte abaixo de", Formatos.preco(it, moedaLocal)) }
                    a.reforcarAbaixo?.let { LinhaValor("Reforçar abaixo de", Formatos.preco(it, moedaLocal)) }
                    a.comprarAbaixo?.let { LinhaValor("Comprar abaixo de", Formatos.preco(it, moedaLocal)) }
                    a.reduzirAcima?.let { LinhaValor("Reduzir acima de", Formatos.preco(it, moedaLocal)) }
                    a.venderAcima?.let { LinhaValor("Vender acima de", Formatos.preco(it, moedaLocal)) }
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
