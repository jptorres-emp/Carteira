package app.carteira.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.carteira.dados.AnaliseVenda
import app.carteira.dados.Historico
import app.carteira.dados.Rentabilidade
import app.carteira.dados.VendasDoTicker

private const val TODAS = "Todas"
private const val VISTA_VENDAS = "Vendas"
private const val VISTA_BORLA = "🎁 De borla"

/**
 * As vendas da tab Transações com a mais-valia de cada uma (FIFO), por ano, e as posições já fechadas. A
 * opção "De borla" mostra, só a título informativo, que posições já se pagaram com o lucro (mais-valias das
 * vendas e dividendos).
 */
@Composable
fun VendasEcra(estado: Estado, vm: CarteiraViewModel) {
    val d = estado.dados ?: return SemDados(estado, vm)
    val h = d.historico
    var ano by rememberSaveable { mutableStateOf(TODAS) }
    var vista by rememberSaveable { mutableStateOf(VISTA_VENDAS) }
    // O ticker com as vendas abertas, uma a uma (só nos que têm mais do que uma).
    var aberto by rememberSaveable { mutableStateOf<String?>(null) }

    ListaAtualizavel(estado, vm) {
        if (!h.temDados) {
            item {
                Cartao("Vendas") {
                    Text(
                        if (d.versaoPonte < 4) {
                            "Para veres as vendas, as mais-valias e a rentabilidade total de cada ação, atualiza a ponte " +
                                "da folha para a versão 4: na folha, Extensões > Apps Script, cola o código novo, guarda e " +
                                "faz Implementar > Gerir implementações > lápis > Versão: Nova versão. O URL mantém-se."
                        } else {
                            "Ainda não há compras nem vendas na tab Transações."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            return@ListaAtualizavel
        }
        val anoAtual = d.meta.hoje.take(4).toIntOrNull()
        item { Filtros(listOf(VISTA_VENDAS, VISTA_BORLA), vista) { vista = it } }
        if (vista == VISTA_BORLA) {
            item { CartaoDeBorla(h) { r -> vm.irPara(if (r.etf) Ecra.Etf(r.ticker) else Ecra.Posicao(r.ticker)) } }
            return@ListaAtualizavel
        }
        item { MosaicosMaisValias(h, anoAtual) }
        if (h.anos.size > 1) item { CartaoPorAno(h) }
        item { Filtros(listOf(TODAS) + h.anos.map { it.ano.toString() }, ano) { ano = it } }
        // Uma linha por ticker: as vendas do mesmo ticker juntas, com o preço médio (sem repetidos).
        val grupos = h.vendasPorTicker(ano.toIntOrNull())
        item {
            TextoPequeno(
                "Uma linha por ticker: as vendas do mesmo ticker juntam-se, com o preço médio. À direita: o que " +
                    "recebeste e a mais-valia. O selo compara com o que as ações valeriam hoje (tab Vendas).",
            )
        }
        items(grupos, key = { "g${it.ticker}" }) { g ->
            val r = h.de(g.ticker)
            val abrir: (() -> Unit)? = if (r != null && r.aberta) {
                { vm.irPara(if (r.etf) Ecra.Etf(g.ticker) else Ecra.Posicao(g.ticker)) }
            } else {
                null
            }
            CartaoVendasTicker(g, aberto == g.ticker, { aberto = if (aberto == g.ticker) null else g.ticker }, abrir)
        }
        if (ano == TODAS && h.fechadas.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Posições fechadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextoPequeno("O que cada uma deu, da primeira compra à última venda, com os dividendos.")
                }
            }
            items(h.fechadas, key = { "f${it.ticker}" }) { r -> CartaoFechada(r) }
        }
        item {
            TextoPequeno(
                "Mais-valia = o que recebeste (menos a comissão) − o custo em euros das ações vendidas, com as comissões " +
                    "das compras. O custo segue o FIFO (as primeiras compradas são as primeiras vendidas), como no IRS, " +
                    "e cada compra e venda conta com o câmbio do seu dia. Valores antes de impostos.",
            )
        }
    }
}

/** As posições que tens, da mais perto de ficar de borla à mais longe (as 8 primeiras, ou todas). */
@Composable
private fun CartaoDeBorla(h: Historico, abrir: (Rentabilidade) -> Unit) {
    var todas by rememberSaveable { mutableStateOf(false) }
    // A posição aberta (uma de cada vez): de onde vem o lucro e o que ele tem de pagar.
    var aberta by rememberSaveable { mutableStateOf<String?>(null) }
    val lista = h.recuperacao
    val livres = h.deBorla
    Cartao("De borla?") {
        Text(
            when {
                livres.isNotEmpty() -> "🎁 " + plural(livres.size, "posição já está de borla", "posições já estão de borla") +
                    ": " + livres.joinToString(", ") { it.ticker }
                lista.isNotEmpty() -> "Ainda nenhuma. A mais perto é a ${lista.first().ticker}: o lucro que já tiraste paga " +
                    "${Formatos.pct(lista.first().pctDeBorla, 0)} do que custaram as ações que tens."
                else -> "Ainda nenhuma: nenhuma posição deu lucro em vendas ou dividendos."
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        TextoPequeno(
            "Uma posição fica de borla quando o lucro que já tiraste dela (as mais-valias das vendas e os dividendos) " +
                "paga o que custaram as ações que ainda tens: essas passam a ser lucro. Só conta o lucro: numa venda, " +
                "o custo das ações vendidas é o teu dinheiro a voltar. É só informativo: o que conta para comprar ou " +
                "vender continua a ser a zona e o peso de cada posição. Toca numa posição para veres de onde vem o lucro.",
        )
        (if (todas) lista else lista.take(8)).forEach { r ->
            val estaAberta = aberta == r.ticker
            LinhaRecuperado(r, estaAberta) { aberta = if (estaAberta) null else r.ticker }
            if (estaAberta) {
                DiscriminacaoBorla(r, h.vendasDe(r.ticker))
                TextButton(onClick = { abrir(r) }) { Text("Abrir a ${r.ticker}") }
            }
        }
        if (lista.size > 8) {
            TextButton(onClick = { todas = !todas }) {
                Text(if (todas) "Mostrar menos" else "Mostrar todas (${lista.size})")
            }
        }
    }
}

@Composable
private fun LinhaRecuperado(r: Rentabilidade, aberta: Boolean, aoTocar: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (aberta) "Esconder de onde vem o lucro" else "Ver de onde vem o lucro", onClick = aoTocar)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                r.ticker + if (aberta) " ▴" else " ▾",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                (if (r.deBorla) "🎁 " else "") + Formatos.pct(r.pctDeBorla, 0),
                style = MaterialTheme.typography.titleSmall.numeros(),
                fontWeight = FontWeight.SemiBold,
                color = if (r.deBorla) legivel(Paleta.verde) else MaterialTheme.colorScheme.onSurface,
            )
        }
        BarraRecuperado(r.pctDeBorla ?: 0.0, r.deBorla)
        val lucro = Formatos.eur(r.lucroTiradoEur ?: 0.0, 0)
        val custo = Formatos.eur(r.custoAtualEur, 0)
        TextoPequeno(
            if (r.deBorla) {
                "lucro $lucro · as que tens custaram $custo (+${Formatos.eur(r.excedenteBorlaEur, 0)}) e valem ${Formatos.eur(r.valorEur, 0)}"
            } else {
                "lucro $lucro de $custo · faltam ${Formatos.eur(r.faltaParaBorlaEur, 0)}" +
                    (r.acoesParaFicarDeBorla?.let { " · cerca de ${plural(it, "ação vendida", "ações vendidas")} ao preço de hoje" } ?: "")
            },
        )
    }
}

@Composable
private fun MosaicosMaisValias(h: Historico, anoAtual: Int?) {
    val esteAno = h.anos.firstOrNull { it.ano == anoAtual }
    LinhaDeMosaicos {
        Mosaico(
            "Mais-valias em ${anoAtual ?: "este ano"}",
            Formatos.eurSinal(esteAno?.maisValiasEur ?: 0.0),
            Modifier.weight(1f).fillMaxHeight(),
            detalhe = plural(esteAno?.vendas ?: 0, "venda", "vendas"),
        )
        Mosaico(
            "Desde o início",
            Formatos.eurSinal(h.maisValiasTotalEur),
            Modifier.weight(1f).fillMaxHeight(),
            detalhe = plural(h.vendas.size, "venda", "vendas"),
        )
    }
}

@Composable
private fun CartaoPorAno(h: Historico) {
    Cartao("Mais-valias por ano") {
        h.anos.forEach { a ->
            LinhaValor(
                "${a.ano} · ${plural(a.vendas, "venda", "vendas")}",
                Formatos.eurSinal(a.maisValiasEur),
                cor = corDoSinal(a.maisValiasEur),
            )
        }
        if (h.anos.any { it.semCusto > 0 }) {
            TextoPequeno("Há vendas sem compras registadas que as cubram: ficam fora da soma.")
        }
    }
}

/** Cartão de lista; só se pode tocar quando há para onde ir (posições que ainda tens). */
@Composable
internal fun CartaoLista(aoTocar: (() -> Unit)?, conteudo: @Composable ColumnScope.() -> Unit) {
    if (aoTocar != null) {
        CartaoClicavel(aoTocar, conteudo = conteudo)
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = corCartao()),
            border = BorderStroke(1.dp, corBordaCartao()),
            content = conteudo,
        )
    }
}

/**
 * As vendas de um ticker, juntas: unidades × preço médio, o que recebeste e a mais-valia de todas. Com mais do
 * que uma venda, "Ver as N vendas" mostra cada uma (data, unidades × preço, recebido e mais-valia).
 */
@Composable
private fun CartaoVendasTicker(g: VendasDoTicker, aberto: Boolean, aoAlternar: () -> Unit, aoTocar: (() -> Unit)?) {
    val n = g.vendas.size
    CartaoLista(aoTocar) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = if (n > 1) 2.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.ticker, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    TextoPequeno(if (n == 1) Formatos.data(g.ultima) else "${plural(n, "venda", "vendas")} · última ${Formatos.data(g.ultima)}")
                }
                g.nome?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                val preco = g.precoMedio
                TextoPequeno(
                    if (preco != null) {
                        "${Formatos.quantidade(g.unidades)} × ${Formatos.preco(preco, g.moeda)}" + if (n > 1) " (preço médio)" else ""
                    } else {
                        "${Formatos.quantidade(g.unidades)} un."
                    },
                )
                g.analise?.let { SeloVeredito(it) }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Formatos.eur(g.recebidoEur, 0), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val mv = g.maisValiaEur
                if (mv != null) {
                    Text(Formatos.eurSinal(mv), color = corDoSinal(mv), style = MaterialTheme.typography.bodySmall)
                    Text(Formatos.pctSinal(g.maisValiaPct), color = corDoSinal(mv), style = MaterialTheme.typography.labelSmall)
                }
                if (g.semCusto > 0) TextoPequeno(if (g.semCusto == n) "sem custo" else "${g.semCusto} sem custo")
            }
        }
        if (n > 1) {
            Text(
                if (aberto) "▴ Esconder as vendas" else "▾ Ver as $n vendas",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = if (aberto) "Esconder as vendas de ${g.ticker}" else "Ver as vendas de ${g.ticker}", onClick = aoAlternar)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            if (aberto) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    g.vendas.forEach { v ->
                        val m = v.movimento
                        val mv = v.maisValiaEur
                        LinhaValor(
                            "${Formatos.data(v.data)} · ${Formatos.quantidade(m.unidades)} × ${Formatos.preco(m.preco, m.moeda)}",
                            Formatos.eur(v.recebidoEur, 0) + (mv?.let { " · " + Formatos.eurSinal(it) } ?: " · sem custo"),
                            cor = mv?.let { corDoSinal(it) } ?: Color.Unspecified,
                        )
                    }
                }
            }
        }
    }
}

/** "Vendeste bem · hoje valeriam −1%" ou "Devias ter mantido · hoje valeriam +57%" (da tab Vendas). */
@Composable
private fun SeloVeredito(a: AnaliseVenda) {
    val veredito = a.veredito?.takeIf { it.contains("🟢") || it.contains("🔴") } ?: return
    val recebido = a.valorRecebido
    val hoje = a.valorSeMantivesse
    val texto = if (recebido != null && hoje != null && recebido > 0) {
        "$veredito · hoje valeriam ${Formatos.pctSinal(hoje / recebido - 1, 0)}"
    } else {
        veredito
    }
    SeloZona(texto)
}

@Composable
private fun CartaoFechada(r: Rentabilidade) {
    CartaoLista(null) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.ticker, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (r.etf) {
                        Spacer(Modifier.width(8.dp))
                        TextoPequeno("ETF")
                    }
                }
                r.nome?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                TextoPequeno("${Formatos.data(r.primeiraCompra)} a ${Formatos.data(r.ultimaVenda)}")
                TextoPequeno(
                    "${plural(r.compras, "compra", "compras")}, ${plural(r.vendas, "venda", "vendas")}" +
                        if (r.dividendosEur >= 0.5) " · ${Formatos.eur(r.dividendosEur, 0)} de dividendos" else "",
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    Formatos.eurSinal(r.lucroEur),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = corDoSinal(r.lucroEur),
                )
                Text(Formatos.pctSinal(r.pctEur), color = corDoSinal(r.lucroEur), style = MaterialTheme.typography.bodySmall)
                TextoPequeno("pagaste ${Formatos.eur(r.compradoEur, 0)}")
            }
        }
    }
}
