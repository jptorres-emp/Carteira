package app.carteira.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.carteira.dados.ItemWatchlist

@Composable
fun WatchlistEcra(estado: Estado, vm: CarteiraViewModel) {
    val d = estado.dados ?: return SemDados(estado, vm)
    var filtro by rememberSaveable { mutableStateOf("Todas") }
    var aberto by rememberSaveable { mutableStateOf("") }

    val tabelas = d.watchlist.map { it.tabela }.distinct()
    val nomes = tabelas.associateWith { nomeCurto(it) }
    val opcoes = listOf("Todas", FILTRO_JUSTO) + tabelas.map { nomes.getValue(it) }
    val visiveis = d.watchlist.filter { w ->
        when (filtro) {
            "Todas" -> true
            // O mesmo critério da folha: 🟢 Subvalorizada ou 🟡 Perto do justo (sem sinal, usa a margem).
            FILTRO_JUSTO -> w.sinal?.let { it.startsWith("🟢") || it.startsWith("🟡") } ?: ((w.margem ?: -1.0) >= -0.20)
            else -> nomes[w.tabela] == filtro
        }
    }.sortedByDescending { it.margem ?: Double.NEGATIVE_INFINITY }

    // Vindo de um alerta: mostra a empresa aberta e desliza até ela.
    val lista = rememberLazyListState()
    val destaque = estado.destaqueWatchlist
    LaunchedEffect(destaque) {
        if (destaque == null) return@LaunchedEffect
        filtro = "Todas"
        val todas = d.watchlist.sortedByDescending { it.margem ?: Double.NEGATIVE_INFINITY }
        val k = todas.indexOfFirst { it.ticker == destaque }
        if (k >= 0) {
            aberto = todas[k].tabela + todas[k].ticker
            val cabecalho = (if (estado.erro != null) 1 else 0) + 2
            runCatching { lista.animateScrollToItem(k + cabecalho) }
        }
        vm.destaqueMostrado()
    }

    ListaAtualizavel(estado, vm, lista) {
        item { Filtros(opcoes, filtro) { filtro = it } }
        item {
            TextoPequeno(
                "${visiveis.size} empresas, das mais baratas para as mais caras face ao valor justo da folha. " +
                    "Toca numa para ver a nota.",
            )
        }
        items(visiveis, key = { it.tabela + it.ticker }) { w ->
            val chave = w.tabela + w.ticker
            CartaoWatchlist(w, aberto == chave) { aberto = if (aberto == chave) "" else chave }
        }
    }
}

private const val FILTRO_JUSTO = "🟢🟡 Abaixo ou perto do justo"

/** "POTENCIAL CORE A" → "Core A". */
private fun nomeCurto(tabela: String): String {
    val t = tabela.removePrefix("POTENCIAL").trim()
    return when (t.uppercase()) {
        "CORE A" -> "Core A"
        "CORE B" -> "Core B"
        "SATÉLITE" -> "Satélite"
        else -> t.lowercase().replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun CartaoWatchlist(w: ItemWatchlist, aberto: Boolean, aoTocar: () -> Unit) {
    CartaoClicavel(aoTocar) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.ticker, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        TextoPequeno(nomeCurto(w.tabela))
                    }
                    Text(
                        w.empresa ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${Formatos.numero(w.preco)} → ${Formatos.numero(w.valorJusto)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "margem ${Formatos.pctSinal(w.margem)}",
                        color = corDoSinal(w.margem),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SeloZona(w.sinal)
                w.camadaPotencial?.let { TextoPequeno("camada: $it") }
            }
            if (aberto) {
                w.nota?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                w.testeTroca?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (it.startsWith("✓")) Paleta.verde else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                w.prioridade?.let { LinhaValor("Prioridade", it) }
                LinhaValor("P/E atual / justo", "${Formatos.numero(w.pe, 1)} / ${Formatos.numero(w.peJusto, 1)}")
                w.moat?.let { LinhaValor("Moat", it) }
                w.crescimento?.let { LinhaValor("Crescimento esperado", Formatos.pct(it)) }
                w.fcfYield?.let { LinhaValor("FCF yield", Formatos.pct(it, 2)) }
                w.intervalo52s?.let { LinhaValor("Intervalo de 52 semanas", it) }
            }
        }
    }
}
