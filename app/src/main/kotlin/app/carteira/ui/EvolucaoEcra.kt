package app.carteira.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.carteira.dados.AnalisePatrimonio
import app.carteira.dados.Dados
import app.carteira.dados.Objetivo
import app.carteira.dados.Rendimentos
import kotlin.math.abs
import kotlin.math.max

/**
 * Separador Evolução. Património: o net worth mês a mês face ao plano da folha, o registo do mês, os
 * marcos, o ano a ano e a composição. Carteira: ações e ETFs face ao S&P 500, os indicadores, os
 * objetivos, o risco e os últimos movimentos.
 */
@Composable
fun EvolucaoEcra(estado: Estado, vm: CarteiraViewModel) {
    val d = estado.dados ?: return SemDados(estado, vm)
    val a = remember(d) { AnalisePatrimonio.de(d) }
    val vista = estado.vistaEvolucao
    ListaAtualizavel(estado, vm) {
        item {
            Filtros(VistaEvolucao.entries.map { it.rotulo }, vista.rotulo) { rotulo ->
                VistaEvolucao.entries.firstOrNull { it.rotulo == rotulo }?.let { vm.escolherVistaEvolucao(it) }
            }
        }
        when (vista) {
            VistaEvolucao.PATRIMONIO -> itensPatrimonio(d, a, vm)
            VistaEvolucao.CARTEIRA -> itensCarteira(d, vm)
        }
    }
}

// ───────────────────────────── Património ─────────────────────────────

private fun LazyListScope.itensPatrimonio(d: Dados, a: AnalisePatrimonio?, vm: CarteiraViewModel) {
    val rendimentos = Rendimentos.de(d)?.takeIf { it.tipos.isNotEmpty() }
    if (a == null) {
        item {
            Cartao("Património") {
                Text(
                    "A folha ainda não tem valores de património (tab Evolução Patrimonial, coluna Net Worth (€)).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    } else {
        item { TopoPatrimonio(a, rendimentos) }
    }
    if (d.versaoPonte >= 3) item { CartaoRegistoMensal(d, a, vm) }
    if (a != null) {
        if (a.serie.size >= 2) item { Cartao("Mês a mês") { GraficoPatrimonio(a) } }
        if (a.metaHoje != null || a.fimDoPlano != null) item { CartaoPlano(a) }
        if (a.proximoMarcoEur != null) item { CartaoMarcos(a) }
        if (a.anos.isNotEmpty()) item { CartaoAnoAAno(a) }
    }
    val p = d.patrimonio
    if (p != null && p.classes.isNotEmpty()) item { CartaoComposicao(p) }
    if (rendimentos != null) item { CartaoRendimentos(rendimentos) }
    if (d.versaoPonte < 3) {
        item {
            Cartao("Há mais para ver") {
                Text(
                    "Atualiza a ponte da folha (versão 3) para veres o plano até 2030 — as metas de referência, base e " +
                        "alargada de cada mês — e para gravares o património do mês a partir daqui.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { vm.irPara(Ecra.Definicoes) }) { Text("Como atualizar") }
            }
        }
    }
    item {
        TextoPequeno(
            "Da tab Evolução Patrimonial: cada mês é o património no fim do mês; o deste mês é o valor de agora " +
                "(tab Investimentos).",
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/**
 * O que está gravado na folha: o mês corrente e, se faltar, o anterior. O botão abre o formulário com o
 * mês que faz sentido gravar (no início do mês, o que acabou de fechar).
 */
@Composable
private fun CartaoRegistoMensal(d: Dados, a: AnalisePatrimonio?, vm: CarteiraViewModel) {
    val hoje = d.meta.hoje.take(7).takeIf { it.length == 7 } ?: return
    val anterior = AnalisePatrimonio.somarMeses(hoje, -1)
    val porMes = d.evolucao.associateBy { it.mes }
    val gravado = porMes[hoje]?.netWorthFolhaEur
    val linhaAnterior = porMes[anterior]
    val faltaAnterior = linhaAnterior != null && linhaAnterior.netWorthFolhaEur == null
    val dia = d.meta.hoje.drop(8).take(2).toIntOrNull() ?: 15
    val sugerido = if (faltaAnterior && dia <= 10) anterior else hoje
    Cartao("Registo na folha") {
        if (faltaAnterior) {
            Text(
                "${maiuscula(mesLongo(anterior))} ainda não tem valor na folha.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = legivel(Paleta.ambar),
            )
        }
        LinhaValor(maiuscula(mesLongo(hoje)), if (gravado != null) Formatos.eur(gravado, 0) else "por gravar")
        if (a != null) {
            TextoPequeno(
                "Agora: ${Formatos.eur(a.atualEur, 0)} (tab Investimentos). Grava no fim do mês, como fazias com o " +
                    "Preenchimento rápido.",
            )
        }
        Button(onClick = { vm.irPara(Ecra.AtualizarPatrimonio(sugerido)) }, modifier = Modifier.fillMaxWidth()) {
            Text("Atualizar património")
        }
    }
}

/** "novembro de 2026" → "Novembro de 2026". */
internal fun maiuscula(texto: String): String = texto.replaceFirstChar { it.uppercase() }

// ───────────────────────────── Carteira ─────────────────────────────

private fun LazyListScope.itensCarteira(d: Dados, vm: CarteiraViewModel) {
    if (d.evolucao.size >= 2) {
        item { Cartao("Carteira vs S&P 500") { GraficoEvolucao(d.evolucao) } }
    } else if (d.versaoPonte < 2) {
        item { AvisoPonteAntiga(vm) }
    }
    if (d.historico.temDados) item { EntradaContraIndice(d.historico) { vm.irPara(Ecra.Indice) } }
    item { GrelhaIndicadores(d) }
    if (d.objetivos.isNotEmpty()) item { CartaoObjetivos(d.objetivos) }
    item { CartaoDesempenho(d) }
    if (d.transacoes.isNotEmpty()) item { CartaoMovimentos(d) }
    item {
        TextoPequeno(
            "Ações e ETFs, como no Dashboard e na tab Evolução Patrimonial da folha.",
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun AvisoPonteAntiga(vm: CarteiraViewModel) {
    Cartao("Há mais para ver") {
        Text(
            "Atualiza a ponte da folha para veres a evolução da carteira face ao S&P 500 e os teus objetivos.",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = { vm.irPara(Ecra.Definicoes) }) { Text("Como atualizar") }
    }
}

// ───────────────────────────── Indicadores ─────────────────────────────

@Composable
private fun GrelhaIndicadores(d: Dados) {
    val doze = d.indicador("Retorno Rolling 12 Meses")?.valor
    val anual = d.indicador("TTWROR Anualizado c/ Dividendos")?.valor ?: d.indicador("TTWROR Anualizado")?.valor
    val comparacao = d.comparacaoPrincipal
    val vsSp = comparacao?.diferencaEur ?: d.indicador("vs S&P 500 (VUAA, c/ dividendos)")?.valor
    val vsPct = comparacao?.diferencaPct
    val ano = d.dividendos.previsaoAno
    val liquido = ano?.let { d.dividendos.indicador("LÍQUIDO $it (€)")?.valor }
    val dividendosAno = d.indicador("DIVIDENDOS/ANO — AÇÕES (EUR)")?.valor
    val queda = d.indicador("Máximo Drawdown")?.valor
    val volatilidade = d.indicador("Volatilidade Anualizada")?.valor

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LinhaDeMosaicos {
            Mosaico(
                "Últimos 12 meses",
                Formatos.pctSinal(doze),
                Modifier.weight(1f).fillMaxHeight(),
                detalhe = anual?.let { "${Formatos.pct(it)}/ano desde o início" },
            )
            Mosaico(
                "vs S&P 500",
                Formatos.eurSinal(vsSp),
                Modifier.weight(1f).fillMaxHeight(),
                detalhe = when {
                    vsPct != null -> (if (vsPct >= 0) "à frente " else "atrás ") + Formatos.pct(abs(vsPct))
                    vsSp != null -> if (vsSp >= 0) "à frente, c/ dividendos" else "atrás, c/ dividendos"
                    else -> null
                },
                corDetalhe = corDoSinal(vsSp),
            )
        }
        LinhaDeMosaicos {
            if (liquido != null) {
                Mosaico(
                    "Dividendos $ano (líquidos)",
                    Formatos.eur(liquido, 0),
                    Modifier.weight(1f).fillMaxHeight(),
                    detalhe = "≈ ${Formatos.eur(liquido / 12, 0)} por mês",
                )
            } else {
                Mosaico("Dividendos/ano", Formatos.eur(dividendosAno, 0), Modifier.weight(1f).fillMaxHeight(), detalhe = "estimativa bruta")
            }
            Mosaico(
                "Maior queda",
                Formatos.pctSinal(queda),
                Modifier.weight(1f).fillMaxHeight(),
                detalhe = volatilidade?.let { "volatilidade ${Formatos.pct(it)}" },
            )
        }
    }
}

// ───────────────────────────── Objetivos ─────────────────────────────

@Composable
private fun CartaoObjetivos(objetivos: List<Objetivo>) {
    Cartao("Estou no caminho?") {
        objetivos.forEachIndexed { k, o ->
            if (k > 0) HorizontalDivider(color = corBordaCartao())
            // Rentabilidades vêm em fração (0,139); património e reforço em euros.
            val emPercentagem = abs(o.real) < 2 && (o.meta == null || abs(o.meta) < 2)
            val formatar: (Double?) -> String = { x -> if (emPercentagem) Formatos.pct(x) else Formatos.eur(x, 0) }
            val cor = corDoEstado(o.estado)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(o.objetivo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        val diferenca = o.meta?.let { m -> if (emPercentagem) Formatos.pp(o.real - m) else Formatos.eurSinal(o.real - m) }
                        TextoPequeno(
                            "${formatar(o.real)} · meta ${formatar(o.meta)}" + (diferenca?.let { " ($it)" } ?: ""),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    SeloEstado(o.estado, cor)
                }
                val meta = o.meta
                if (meta != null && meta > 0 && o.real >= 0) {
                    BarraComMarca(valor = o.real, marca = meta, escala = max(o.real, meta) * 1.1, cor = cor)
                }
            }
        }
    }
}

// ───────────────────────────── Desempenho ─────────────────────────────

@Composable
private fun CartaoDesempenho(d: Dados) {
    // A TIR (ponte v5) vem à frente: é o que o teu dinheiro rendeu de facto, ao lado do mesmo dinheiro no VUAA.
    val carteira = d.historico.carteira
    val tir = listOfNotNull(
        carteira?.tir?.let { "O teu dinheiro (TIR)" to Formatos.pct(it) },
        carteira?.tirIndice?.let { "No VUAA (TIR)" to Formatos.pct(it) },
    )
    // (rótulo da folha, nome na app, é percentagem?)
    val linhas = tir + listOf(
        Triple("TTWROR Anualizado", "Rendibilidade anual (TWR)", true),
        Triple("TTWROR Anualizado c/ Dividendos", "Anual com dividendos", true),
        Triple("TTWROR (acumulado, desde Set/22)", "Acumulada desde o início", true),
        Triple("Volatilidade Anualizada", "Volatilidade anual", true),
        Triple("Sharpe Ratio", "Sharpe", false),
        Triple("Beta vs S&P500", "Beta vs S&P 500", false),
        Triple("Melhor Mês", "Melhor mês", true),
        Triple("Pior Mês", "Pior mês", true),
        Triple("% Meses Positivos", "Meses positivos", true),
    ).mapNotNull { (rotulo, nome, pct) ->
        d.indicador(rotulo)?.let { i -> nome to if (pct) Formatos.pct(i.valor) else Formatos.numero(i.valor, 2) }
    }
    if (linhas.isEmpty()) return
    Cartao("Desempenho e risco") {
        linhas.chunked(2).forEach { par ->
            // Valores alinhados por baixo: com letra grande, um rótulo pode ocupar duas linhas e o do lado uma.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                par.forEach { (nome, valor) ->
                    Column(Modifier.weight(1f)) {
                        TextoPequeno(nome)
                        Text(valor, style = MaterialTheme.typography.titleSmall.numeros(), fontWeight = FontWeight.SemiBold)
                    }
                }
                if (par.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (carteira?.tir != null) {
            TextoPequeno(
                "TIR: o que o teu dinheiro rendeu por ano, em euros, com as datas de cada compra, venda e dividendo " +
                    "desde ${Formatos.data(carteira.desde.toString())}; no VUAA, o mesmo dinheiro nas mesmas datas. A TWR " +
                    "da folha mede só as escolhas, sem o efeito de quando entrou o dinheiro, em dólares e desde set/22.",
            )
        }
    }
}

// ───────────────────────────── Movimentos ─────────────────────────────

@Composable
private fun CartaoMovimentos(d: Dados) {
    Cartao("Últimos movimentos") {
        d.transacoes.take(4).forEachIndexed { k, t ->
            if (k > 0) HorizontalDivider(color = corBordaCartao())
            val moeda = t.moeda ?: "USD"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${t.tipo ?: ""} ${Formatos.quantidade(t.unidades)} ${t.ticker}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    TextoPequeno("${Formatos.data(t.data)} · a ${Formatos.preco(t.preco, moeda)}")
                }
                Text(
                    Formatos.moeda(t.valor, moeda, 0),
                    style = MaterialTheme.typography.bodyMedium.numeros(),
                    color = if (t.tipo == "Venda") legivel(Paleta.verde) else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
