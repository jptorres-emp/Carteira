package app.carteira.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.carteira.dados.Dados
import app.carteira.dados.Historico
import app.carteira.dados.Rentabilidade

private const val FILTRO_ACOES = "Ações"
private const val FILTRO_TENS = "Só as que tens"
private const val FILTRO_ETFS = "ETFs"

/**
 * Cada ação contra o mesmo dinheiro no S&P 500 (VUAA), nas mesmas datas, da que mais o bateu para a que
 * menos: o teste do método ação a ação. A tab Validação S&P 500 faz o mesmo para a carteira toda.
 */
@Composable
fun IndiceEcra(estado: Estado, vm: CarteiraViewModel) {
    val d = estado.dados ?: return SemDados(estado, vm)
    val h = d.historico
    var filtro by rememberSaveable { mutableStateOf(FILTRO_ACOES) }

    ListaAtualizavel(estado, vm) {
        if (!h.temIndice) {
            item {
                Cartao("Contra o S&P 500") {
                    Text(
                        "Para comparares cada ação com o mesmo dinheiro no S&P 500 e veres a rentabilidade por ano, " +
                            "atualiza a ponte da folha para a versão 5: na folha, Extensões > Apps Script, cola o código " +
                            "novo, guarda e faz Implementar > Gerir implementações > lápis > Versão: Nova versão. O URL mantém-se.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            return@ListaAtualizavel
        }
        val lista = when (filtro) {
            FILTRO_TENS -> h.contraIndice.filter { !it.etf && it.aberta }
            FILTRO_ETFS -> h.contraIndice.filter { it.etf }
            else -> h.contraIndice.filter { !it.etf }
        }
        item { Filtros(listOf(FILTRO_ACOES, FILTRO_TENS, FILTRO_ETFS), filtro) { filtro = it } }
        item { ResumoContraIndice(d, h, lista, filtro) }
        items(lista, key = { "i${it.ticker}" }) { r ->
            val abrir: (() -> Unit)? = if (r.aberta) {
                { vm.irPara(if (r.etf) Ecra.Etf(r.ticker) else Ecra.Posicao(r.ticker)) }
            } else {
                null
            }
            CartaoContraIndice(r, abrir)
        }
        val semPreco = h.porTicker.values.filter { it.semPrecoIndice > 0 }.map { it.ticker }.sorted()
        if (semPreco.isNotEmpty()) {
            item {
                TextoPequeno(
                    "Sem comparação por faltar o preço do S&P 500 de alguma transação: ${semPreco.joinToString(", ")}. " +
                        "A ponte fixa-o no dia seguinte; também o podes colar na tab Transações (colunas R e S).",
                )
            }
        }
        item {
            TextoPequeno(
                "Cada compra põe os mesmos euros no VUAA nesse dia e cada venda tira os mesmos euros, com as mesmas " +
                    "comissões, como a tab Validação S&P 500. Diferença = o lucro da ação (com os dividendos) − o lucro " +
                    "do VUAA, que reinveste os dele. TIR: o que cada uma rendeu por ano, contando com as datas; só " +
                    "aparece quando o dinheiro está lá, em média, há mais de um ano.",
            )
        }
    }
}

@Composable
private fun ResumoContraIndice(d: Dados, h: Historico, lista: List<Rentabilidade>, filtro: String) {
    val frente = lista.count { (it.diferencaIndiceEur ?: 0.0) >= 0 }
    val soma = lista.sumOf { it.diferencaIndiceEur ?: 0.0 }
    val nome = if (filtro == FILTRO_ETFS) "ETF" else "ações"
    Cartao("Contra o S&P 500") {
        Text(
            if (lista.isEmpty()) "Sem $nome para comparar." else "$frente de ${lista.size} $nome à frente do VUAA",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (lista.isNotEmpty()) {
            LinhaValor(
                if (filtro == FILTRO_TENS) "Soma das que tens" else "Soma destas $nome",
                Formatos.eurSinal(soma),
                cor = corDoSinal(soma),
                destaque = true,
            )
        }
        if (filtro == FILTRO_ACOES) {
            d.comparacoes.firstOrNull { it.comparacao.startsWith("Só ações vs VUAA") }?.let { c ->
                LinhaValor("Tab Validação S&P 500", Formatos.eurSinal(c.diferencaEur), cor = corDoSinal(c.diferencaEur))
                TextoPequeno(
                    "A folha soma também os dividendos que não estão ligados a nenhuma destas ações" +
                        (h.dividendosDesde?.let { " (os de antes de $it, por exemplo)" } ?: "") + ".",
                )
            }
        }
        if (filtro == FILTRO_ETFS) {
            TextoPequeno("Num ETF de obrigações, ficar atrás do S&P 500 é normal: está na carteira por outra razão.")
        }
    }
}

@Composable
private fun CartaoContraIndice(r: Rentabilidade, aoTocar: (() -> Unit)?) {
    val dif = r.diferencaIndiceEur ?: 0.0
    CartaoLista(aoTocar) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.ticker, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (!r.aberta) {
                        Spacer(Modifier.width(8.dp))
                        TextoPequeno("vendida")
                    }
                }
                r.nome?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                TextoPequeno(
                    if (r.tir != null) {
                        "TIR ${Formatos.pctSinal(r.tir)}/ano · VUAA " + (r.espelho?.tir?.let { "${Formatos.pctSinal(it)}/ano" } ?: "—")
                    } else if (r.aberta) {
                        "desde ${Formatos.data(r.primeiraCompra)} · menos de 1 ano"
                    } else {
                        "${Formatos.data(r.primeiraCompra)} a ${Formatos.data(r.ultimaVenda)}"
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    Formatos.eurSinal(dif),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = corDoSinal(dif),
                )
                Text(Formatos.pctSinal(r.diferencaIndicePct), color = corDoSinal(dif), style = MaterialTheme.typography.bodySmall)
                TextoPequeno("pagaste ${Formatos.eur(r.compradoEur, 0)}")
            }
        }
    }
}

/** Atalho na Evolução: quantas ações estão à frente do S&P 500. */
@Composable
internal fun EntradaContraIndice(h: Historico, aoTocar: () -> Unit) {
    CartaoClicavel(aoTocar) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Cada ação contra o S&P 500", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val acoes = h.contraIndice.filter { !it.etf }
                TextoPequeno(
                    when {
                        !h.temIndice -> "Precisa da ponte da folha na versão 5"
                        acoes.isEmpty() -> "Ainda sem ações para comparar"
                        else -> "${acoes.count { (it.diferencaIndiceEur ?: 0.0) >= 0 }} de ${acoes.size} à frente do VUAA, com o mesmo dinheiro"
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("Ver", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
