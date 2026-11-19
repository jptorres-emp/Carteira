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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.carteira.dados.AnaliseVenda
import app.carteira.dados.Historico
import app.carteira.dados.Rentabilidade
import app.carteira.dados.Venda

private const val TODAS = "Todas"

/**
 * De borla e vendas: que posições já se pagaram com as vendas e os dividendos (e quanto falta às outras),
 * as vendas da tab Transações com a mais-valia de cada uma (FIFO), por ano, e as posições já fechadas.
 */
@Composable
fun VendasEcra(estado: Estado, vm: CarteiraViewModel) {
    val d = estado.dados ?: return SemDados(estado, vm)
    val h = d.historico
    var ano by rememberSaveable { mutableStateOf(TODAS) }

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
        item { CartaoDeBorla(h) { r -> vm.irPara(if (r.etf) Ecra.Etf(r.ticker) else Ecra.Posicao(r.ticker)) } }
        item {
            Text(
                "Vendas",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item { MosaicosMaisValias(h, anoAtual) }
        if (h.anos.size > 1) item { CartaoPorAno(h) }
        item { Filtros(listOf(TODAS) + h.anos.map { it.ano.toString() }, ano) { ano = it } }
        val lista = h.vendas.filter { ano == TODAS || it.ano?.toString() == ano }
        item { TextoPequeno("À direita: o que recebeste e a mais-valia de cada venda. O selo compara com o que as ações valeriam hoje (tab Vendas).") }
        items(lista, key = { "v${it.movimento.linha}" }) { v ->
            val r = h.de(v.ticker)
            val abrir: (() -> Unit)? = if (r != null && r.aberta) {
                { vm.irPara(if (r.etf) Ecra.Etf(v.ticker) else Ecra.Posicao(v.ticker)) }
            } else {
                null
            }
            CartaoVenda(v, abrir)
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
    val lista = h.recuperacao
    val livres = h.deBorla
    Cartao("De borla?") {
        Text(
            when {
                livres.isNotEmpty() -> "🎁 " + plural(livres.size, "posição já está de borla", "posições já estão de borla") +
                    ": " + livres.joinToString(", ") { it.ticker }
                lista.isNotEmpty() -> "Ainda nenhuma. A mais perto é a ${lista.first().ticker}, com ${Formatos.pct(lista.first().pctRecuperado, 0)} do custo recuperado."
                else -> "Ainda nenhuma: nenhuma posição teve vendas ou dividendos."
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        TextoPequeno(
            "Uma posição fica de borla quando o que recebeste nas vendas e em dividendos já cobre tudo o que pagaste " +
                "por ela: as ações que ainda tens são lucro.",
        )
        (if (todas) lista else lista.take(8)).forEach { r -> LinhaRecuperado(r) { abrir(r) } }
        if (lista.size > 8) {
            TextButton(onClick = { todas = !todas }) {
                Text(if (todas) "Mostrar menos" else "Mostrar todas (${lista.size})")
            }
        }
    }
}

@Composable
private fun LinhaRecuperado(r: Rentabilidade, aoTocar: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = aoTocar).padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.ticker, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                (if (r.deBorla) "🎁 " else "") + Formatos.pct(r.pctRecuperado, 0),
                style = MaterialTheme.typography.titleSmall.numeros(),
                fontWeight = FontWeight.SemiBold,
                color = if (r.deBorla) legivel(Paleta.verde) else MaterialTheme.colorScheme.onSurface,
            )
        }
        BarraRecuperado(r.pctRecuperado ?: 0.0, r.deBorla)
        TextoPequeno(
            if (r.deBorla) {
                "${Formatos.eur(r.recuperadoEur - r.compradoEur, 0)} a mais do que pagaste · as que tens valem ${Formatos.eur(r.valorEur, 0)}"
            } else {
                "faltam ${Formatos.eur(r.faltaRecuperarEur, 0)}" +
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
private fun CartaoLista(aoTocar: (() -> Unit)?, conteudo: @Composable ColumnScope.() -> Unit) {
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

@Composable
private fun CartaoVenda(v: Venda, aoTocar: (() -> Unit)?) {
    CartaoLista(aoTocar) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(v.ticker, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    TextoPequeno(Formatos.data(v.data))
                }
                v.nome?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                val m = v.movimento
                TextoPequeno("${Formatos.quantidade(m.unidades)} × ${Formatos.preco(m.preco, m.moeda)}")
                v.analise?.let { SeloVeredito(it) }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Formatos.eur(v.recebidoEur, 0), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val mv = v.maisValiaEur
                if (mv != null) {
                    Text(Formatos.eurSinal(mv), color = corDoSinal(mv), style = MaterialTheme.typography.bodySmall)
                    Text(Formatos.pctSinal(v.maisValiaPct), color = corDoSinal(mv), style = MaterialTheme.typography.labelSmall)
                } else {
                    TextoPequeno("sem custo")
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
