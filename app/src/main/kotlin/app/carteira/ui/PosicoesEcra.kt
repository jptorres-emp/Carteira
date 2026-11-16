@file:OptIn(ExperimentalMaterial3Api::class)

package app.carteira.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Linha da lista: uma ação ou um ETF, já com os números em euros. */
private data class Linha(
    val ticker: String,
    val nome: String,
    val camada: String,
    val valorEur: Double?,
    val ganhoPct: Double?,
    val varDia: Double?,
    val peso: Double?,
    val zona: String?,
    val etf: Boolean,
)

private val ordens = listOf("Valor", "Ganho", "Hoje", "Peso vs alvo")

@Composable
fun PosicoesEcra(estado: Estado, vm: CarteiraViewModel) {
    val d = estado.dados ?: return SemDados(estado, vm)
    var procura by rememberSaveable { mutableStateOf("") }
    var filtro by rememberSaveable { mutableStateOf("Todas") }
    var ordem by rememberSaveable { mutableStateOf("Valor") }

    val totalCarteira = (d.totaisAcoes?.valorEur ?: 0.0) + (d.totaisEtfs?.valorEur ?: 0.0)
    val linhas = d.posicoes.map {
        Linha(it.ticker, it.nome, it.camada ?: "—", it.valorEur, it.ganhoPct, it.varDiaPct, it.pesoTotal, it.zona, false)
    } + d.etfs.map {
        Linha(
            it.ticker, it.nome ?: it.ticker, "ETFs", it.valorEur, it.ganhoPct, it.varDiaPct,
            if (totalCarteira > 0 && it.valorEur != null) it.valorEur / totalCarteira else null, null, true,
        )
    }
    val alvos = d.posicoes.associate { it.ticker to it.pesoAlvo }

    val camadas = listOf("Core A", "Core B", "Satélite", "Aposta").filter { c -> linhas.any { it.camada == c } } +
        linhas.map { it.camada }.distinct().filter { it !in listOf("Core A", "Core B", "Satélite", "Aposta", "ETFs", "—") }.sorted()
    val opcoes = listOf("Todas", "🟢 Comprar", "🟠🔴 Reduzir/vender") + camadas + listOf("ETFs")

    val termo = procura.trim().lowercase()
    val visiveis = linhas.filter { l ->
        (termo.isEmpty() || l.ticker.lowercase().contains(termo) || l.nome.lowercase().contains(termo)) &&
            when (filtro) {
                "Todas" -> true
                "🟢 Comprar" -> l.zona?.startsWith("🟢") == true
                "🟠🔴 Reduzir/vender" -> l.zona?.startsWith("🟠") == true || l.zona?.startsWith("🔴") == true
                else -> l.camada == filtro
            }
    }.let { lista ->
        when (ordem) {
            "Ganho" -> lista.sortedByDescending { it.ganhoPct ?: Double.NEGATIVE_INFINITY }
            "Hoje" -> lista.sortedByDescending { it.varDia ?: Double.NEGATIVE_INFINITY }
            "Peso vs alvo" -> lista.sortedByDescending { l ->
                val alvo = alvos[l.ticker]
                if (alvo != null && alvo > 0 && l.peso != null) l.peso / alvo else Double.NEGATIVE_INFINITY
            }
            else -> lista.sortedByDescending { it.valorEur ?: 0.0 }
        }
    }

    ListaAtualizavel(estado, vm) {
        item {
            OutlinedTextField(
                value = procura,
                onValueChange = { procura = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Procurar empresa ou ticker") },
            )
        }
        item { Filtros(opcoes, filtro) { filtro = it } }
        item {
            val soma = visiveis.sumOf { it.valorEur ?: 0.0 }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextoPequeno(
                    "${visiveis.size} posições · ${Formatos.eur(soma, 0)}" +
                        if (totalCarteira > 0) " · ${Formatos.pct(soma / totalCarteira)} da carteira" else "",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { ordem = ordens[(ordens.indexOf(ordem) + 1) % ordens.size] }) {
                    Text("Ordenar: $ordem")
                }
            }
        }
        items(visiveis, key = { it.ticker }) { l ->
            CartaoLinha(l, alvos[l.ticker]) {
                vm.irPara(if (l.etf) Ecra.Etf(l.ticker) else Ecra.Posicao(l.ticker))
            }
        }
    }
}

@Composable
private fun CartaoLinha(l: Linha, alvo: Double?, aoTocar: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = aoTocar)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(l.ticker, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    TextoPequeno(l.camada)
                }
                Text(
                    l.nome,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SeloZona(l.zona)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Formatos.eur(l.valorEur, 0), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    Formatos.pctSinal(l.ganhoPct),
                    color = corDoSinal(l.ganhoPct),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "hoje ${Formatos.pctSinal(l.varDia, 2)}",
                    color = corDoSinal(l.varDia),
                    style = MaterialTheme.typography.labelSmall,
                )
                val peso = "peso ${Formatos.pct(l.peso)}" + if (alvo != null && alvo > 0) " / ${Formatos.pct(alvo)}" else ""
                Text(peso, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
            }
        }
    }
}
