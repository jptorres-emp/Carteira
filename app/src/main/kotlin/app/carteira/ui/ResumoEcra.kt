@file:OptIn(ExperimentalMaterial3Api::class)

package app.carteira.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.carteira.dados.Camada
import app.carteira.dados.Dados
import app.carteira.dados.Regra
import kotlin.math.max

/** Lista que se atualiza ao puxar para baixo, comum aos quatro separadores. */
@Composable
fun ListaAtualizavel(estado: Estado, vm: CarteiraViewModel, conteudo: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    PullToRefreshBox(
        isRefreshing = estado.aCarregar,
        onRefresh = { vm.atualizar() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val erro = estado.erro
            if (erro != null) {
                item { AvisoErro(erro, estado.dados != null) { vm.atualizar() } }
            }
            conteudo()
        }
    }
}

/** Ecrã para quando ainda não há dados (primeira vez ou sem ligação). */
@Composable
fun SemDados(estado: Estado, vm: CarteiraViewModel) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (estado.aCarregar) {
            Text("A ler a folha…", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                estado.erro ?: "Ainda não há dados. Liga a app à folha nas Definições.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            if (estado.configurado) {
                Button(onClick = { vm.atualizar() }) { Text("Tentar outra vez") }
            } else {
                Button(onClick = { vm.irPara(Ecra.Definicoes) }) { Text("Ligar à folha") }
            }
        }
    }
}

@Composable
fun ResumoEcra(estado: Estado, vm: CarteiraViewModel) {
    val d = estado.dados ?: return SemDados(estado, vm)
    ListaAtualizavel(estado, vm) {
        item { CartaoValor(d) }
        item { LinhaMosaicos(d) }
        val alertas = d.regras.filter { it.alerta }
        if (alertas.isNotEmpty()) item { CartaoAlertas(alertas) }
        if (d.camadas.isNotEmpty()) item { CartaoCamadas(d.camadas) }
        item { CartaoMovimentos(d, vm) }
        item { CartaoDesempenho(d) }
        item {
            TextoPequeno(
                "Dados da folha de ${Formatos.momento(d.meta.atualizadoEm)}. Puxa para baixo para atualizar.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Variação de hoje em euros, a partir da variação diária de cada posição. */
private fun variacaoHojeEur(d: Dados): Pair<Double, Double>? {
    var mudanca = 0.0
    var total = 0.0
    var algum = false
    d.posicoes.forEach { p ->
        val v = p.valorEur ?: return@forEach
        total += v
        val r = p.varDiaPct ?: return@forEach
        mudanca += v * r / (1 + r)
        algum = true
    }
    d.etfs.forEach { e ->
        val v = e.valorEur ?: return@forEach
        total += v
        val r = e.varDiaPct ?: return@forEach
        mudanca += v * r / (1 + r)
        algum = true
    }
    if (!algum || total <= 0) return null
    return mudanca to mudanca / (total - mudanca)
}

@Composable
private fun CartaoValor(d: Dados) {
    val totalEur = d.indicador("VALOR TOTAL (EUR)")?.valor
        ?: ((d.totaisAcoes?.valorEur ?: 0.0) + (d.totaisEtfs?.valorEur ?: 0.0))
    val ganhoEur = d.indicador("GANHO/PERDA (EUR)")?.valor
    val ganhoPct = d.indicador("GANHO/PERDA (%)")?.valor
    val totalUsd = d.indicador("VALOR TOTAL (USD)")?.valor
    val hoje = variacaoHojeEur(d)
    Cartao {
        Text("Valor da carteira", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Formatos.eur(totalEur, 0), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Ganho ${Formatos.eurSinal(ganhoEur)} (${Formatos.pctSinal(ganhoPct)})",
                color = corDoSinal(ganhoEur),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (hoje != null) {
            Text(
                "Hoje ${Formatos.eurSinal(hoje.first)} (${Formatos.pctSinal(hoje.second, 2)})",
                color = corDoSinal(hoje.first),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TextoPequeno(
            "Em dólares: ${Formatos.usd(totalUsd, 0)} · câmbio ${Formatos.numero(d.cambio.usdEur, 4)} €/$",
        )
    }
}

@Composable
private fun LinhaMosaicos(d: Dados) {
    val dividendos = d.indicador("DIVIDENDOS/ANO — AÇÕES (EUR)")?.valor
    val alerta = d.indicador("POSIÇÕES EM ALERTA")?.valor
    val vsSp = d.indicador("vs S&P 500 (VUAA, c/ dividendos)")?.valor
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Mosaico("Dividendos/ano", Formatos.eur(dividendos, 0), Modifier.weight(1f), detalhe = "estimativa")
        Mosaico(
            "vs S&P 500",
            Formatos.eurSinal(vsSp),
            Modifier.weight(1f),
            detalhe = if (vsSp == null) null else if (vsSp >= 0) "à frente" else "atrás",
            corDetalhe = corDoSinal(vsSp),
        )
        Mosaico(
            "Posições",
            Formatos.numero((d.posicoes.size + d.etfs.size).toDouble(), 0),
            Modifier.weight(1f),
            detalhe = if (alerta != null && alerta > 0) "${alerta.toInt()} em alerta" else null,
        )
    }
}

@Composable
private fun CartaoAlertas(alertas: List<Regra>) {
    Cartao("Regras a rever") {
        alertas.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider()
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(r.regra, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    val nota = listOfNotNull(
                        "hoje ${r.hoje}".takeIf { r.hoje.isNotBlank() },
                        "limite ${r.limite}".takeIf { r.limite.isNotBlank() && r.limite != "—" },
                    ).joinToString(" · ")
                    if (nota.isNotBlank()) TextoPequeno(nota)
                    if (!r.nota.isNullOrBlank()) TextoPequeno(r.nota)
                }
                Spacer(Modifier.width(8.dp))
                SeloZona("🟠 " + r.estado.removePrefix("⚠").trim())
            }
        }
    }
}

@Composable
private fun CartaoCamadas(camadas: List<Camada>) {
    val escala = camadas.maxOf { max(it.pesoHoje ?: 0.0, it.pesoIdeal ?: 0.0) }.coerceAtLeast(0.01)
    Cartao("Peso por camada (hoje vs ideal)") {
        camadas.forEach { c ->
            val hoje = c.pesoHoje ?: 0.0
            val ideal = c.pesoIdeal
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    val n = c.numero?.toInt()?.toString() ?: "—"
                    val nIdeal = c.numeroIdeal?.toInt()?.let { "/$it" } ?: ""
                    Text("${c.camada} · $n$nIdeal", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        Formatos.pct(hoje) + if (ideal != null) " (ideal ${Formatos.pct(ideal)})" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                LinearProgressIndicator(
                    progress = { (hoje / escala).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val dif = c.diferencaEur
                if (dif != null && kotlin.math.abs(dif) >= 1) {
                    TextoPequeno(
                        if (dif > 0) "${Formatos.eur(dif, 0)} acima do ideal" else "${Formatos.eur(-dif, 0)} abaixo do ideal",
                    )
                }
            }
        }
    }
}

@Composable
private fun CartaoMovimentos(d: Dados, vm: CarteiraViewModel) {
    val todos = d.posicoes.mapNotNull { p -> p.varDiaPct?.let { Triple(p.ticker, it, true) } } +
        d.etfs.mapNotNull { e -> e.varDiaPct?.let { Triple(e.ticker, it, false) } }
    if (todos.isEmpty()) return
    val sobem = todos.filter { it.second > 0 }.sortedByDescending { it.second }.take(3)
    val descem = todos.filter { it.second < 0 }.sortedBy { it.second }.take(3)
    Cartao("Hoje") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                TextoPequeno("Sobem mais")
                if (sobem.isEmpty()) Text("—")
                sobem.forEach { (t, v, acao) ->
                    LinhaMovimento(t, v) { vm.irPara(if (acao) Ecra.Posicao(t) else Ecra.Etf(t)) }
                }
            }
            Column(Modifier.weight(1f)) {
                TextoPequeno("Descem mais")
                if (descem.isEmpty()) Text("—")
                descem.forEach { (t, v, acao) ->
                    LinhaMovimento(t, v) { vm.irPara(if (acao) Ecra.Posicao(t) else Ecra.Etf(t)) }
                }
            }
        }
    }
}

@Composable
private fun LinhaMovimento(ticker: String, variacao: Double, aoTocar: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = aoTocar,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(ticker, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(Formatos.pctSinal(variacao, 2), color = corDoSinal(variacao), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CartaoDesempenho(d: Dados) {
    val linhas = listOf(
        "TTWROR Anualizado" to "Rendibilidade anual (TTWROR)",
        "TTWROR (acumulado, desde Set/22)" to "Rendibilidade acumulada",
        "TTWROR Anualizado c/ Dividendos" to "Anual com dividendos",
        "Retorno Rolling 12 Meses" to "Últimos 12 meses",
        "Volatilidade Anualizada" to "Volatilidade anual",
        "Máximo Drawdown" to "Maior queda",
        "Sharpe Ratio" to "Sharpe",
        "Beta vs S&P500" to "Beta vs S&P 500",
        "PATRIMÓNIO TOTAL (NET WORTH)" to "Património total",
    ).mapNotNull { (rotulo, nome) -> d.indicador(rotulo)?.let { nome to it.texto } }
    if (linhas.isEmpty()) return
    Cartao("Desempenho") {
        linhas.forEach { (nome, texto) -> LinhaValor(nome, texto) }
    }
}
