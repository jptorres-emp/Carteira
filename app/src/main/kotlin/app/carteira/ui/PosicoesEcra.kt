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
import app.carteira.dados.Dados
import app.carteira.dados.Gatilhos
import app.carteira.dados.Historico
import app.carteira.dados.Sinais

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
    /** O sinal da folha: a zona já com as tuas regras (camada, peso, saídas planeadas). */
    val sinal: String?,
    val etf: Boolean,
    /** Os preços de comprar e vender pela camada (só nas ações com avaliação). */
    val gatilhos: Gatilhos? = null,
)

private val ordens = listOf("Valor", "Ganho", "Hoje", "Peso vs alvo")

@Composable
fun PosicoesEcra(estado: Estado, vm: CarteiraViewModel) {
    val d = estado.dados ?: return SemDados(estado, vm)
    var procura by rememberSaveable { mutableStateOf("") }
    var filtro by rememberSaveable { mutableStateOf("Todas") }
    var ordem by rememberSaveable { mutableStateOf("Valor") }

    val totalCarteira = (d.totaisAcoes?.valorEur ?: 0.0) + (d.totaisEtfs?.valorEur ?: 0.0)
    val historico = d.historico
    val linhas = d.posicoes.map {
        // O valor da lista está em euros, por isso o ganho também.
        Linha(
            it.ticker, it.nome, it.camada ?: "—", it.valorEur, it.ganhoPctEur ?: it.ganhoPct, it.varDiaPct, it.pesoTotal, it.zona, it.sinal, false,
            gatilhos = Gatilhos.de(it.camada, it.avaliacao),
        )
    } + d.etfs.map {
        Linha(
            it.ticker, it.nome ?: it.ticker, "ETFs", it.valorEur, it.ganhoPct, it.varDiaPct,
            if (totalCarteira > 0 && it.valorEur != null) it.valorEur / totalCarteira else null, null, null, true,
        )
    }
    val alvos = d.posicoes.associate { it.ticker to it.pesoAlvo }

    val camadas = listOf("Core A", "Core B", "Satélite", "Aposta").filter { c -> linhas.any { it.camada == c } } +
        linhas.map { it.camada }.distinct().filter { it !in listOf("Core A", "Core B", "Satélite", "Aposta", "ETFs", "—") }.sorted()
    // Os filtros seguem o SINAL da folha (as tuas regras), não só a zona do preço.
    val opcoes = listOf("Todas", FILTRO_COMPRA, FILTRO_REDUZIR, FILTRO_SAIR) + camadas + listOf("ETFs")

    val termo = procura.trim().lowercase()
    val visiveis = linhas.filter { l ->
        (termo.isEmpty() || l.ticker.lowercase().contains(termo) || l.nome.lowercase().contains(termo)) &&
            when (filtro) {
                "Todas" -> true
                FILTRO_COMPRA -> Sinais.compra(l.sinal)
                FILTRO_REDUZIR -> Sinais.reduzir(l.sinal)
                FILTRO_SAIR -> Sinais.sair(l.sinal)
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
        item { EntradaVendas(d, historico) { vm.irPara(Ecra.Vendas) } }
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

private const val FILTRO_COMPRA = "🟢 Sinal de compra"
private const val FILTRO_REDUZIR = "🟠 Reduzir"
private const val FILTRO_SAIR = "🔴 Sair"

@Composable
private fun CartaoLinha(l: Linha, alvo: Double?, aoTocar: () -> Unit) {
    CartaoClicavel(aoTocar) {
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
                // O sinal (o que fazer pelas tuas regras); a zona do preço fica no detalhe.
                SeloZona(l.sinal ?: l.zona)
                l.gatilhos?.let { LinhaGatilhos(it) }
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

/** Atalho para as Vendas: quantas há e as mais-valias do ano (o "de borla" é uma opção desse ecrã). */
@Composable
private fun EntradaVendas(d: Dados, h: Historico, aoTocar: () -> Unit) {
    CartaoClicavel(aoTocar) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Vendas e posições fechadas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (!h.temDados) {
                    TextoPequeno("Precisa da ponte da folha na versão 4")
                } else {
                    val ano = d.meta.hoje.take(4).toIntOrNull()
                    val esteAno = h.anos.firstOrNull { it.ano == ano }
                    TextoPequeno(
                        if (esteAno != null) {
                            "${h.vendas.size} vendas · mais-valias em ${esteAno.ano}: ${Formatos.eurSinal(esteAno.maisValiasEur)}"
                        } else {
                            "${h.vendas.size} vendas · ${h.fechadas.size} posições fechadas"
                        },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("Ver", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** "compra ≤ 7,28 $ · vende ≥ 12,71 $": os preços que contam pela camada, a verde e a vermelho (✓ = já lá chegou). */
@Composable
private fun LinhaGatilhos(g: Gatilhos) {
    if (g.comprar == null && g.vender == null) return
    LinhaLegendas(espaco = 10.dp) {
        g.comprar?.let {
            Text(
                "compra ≤ ${Formatos.preco(it, g.moeda)}" + if (g.naZonaDeCompra) " ✓" else "",
                style = MaterialTheme.typography.labelSmall.numeros(),
                color = legivel(Paleta.verde),
                fontWeight = if (g.naZonaDeCompra) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
        g.vender?.let {
            Text(
                "vende ≥ ${Formatos.preco(it, g.moeda)}" + if (g.acimaDeVender) " ✓" else "",
                style = MaterialTheme.typography.labelSmall.numeros(),
                color = legivel(Paleta.vermelho),
                fontWeight = if (g.acimaDeVender) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
