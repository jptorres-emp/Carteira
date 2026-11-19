package app.carteira.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.carteira.dados.Dados
import app.carteira.dados.EsteAno

/**
 * Dividendos: o que já recebeste este ano, a previsão da folha, mês a mês (toca num mês para ver quem
 * paga), por ano e por empresa. Sem tabelas de colunas fixas, para não partir em ecrãs estreitos.
 */
@Composable
fun DividendosEcra(estado: Estado, vm: CarteiraViewModel) {
    val d = estado.dados ?: return SemDados(estado, vm)
    val div = d.dividendos
    ListaAtualizavel(estado, vm) {
        item { CartaoRecebidoEsteAno(d) }
        item { GrelhaDividendos(d) }
        if (div.calendario.size == 12) item { CartaoMesAMes(d) }
        if (div.anos.isNotEmpty()) item { CartaoPorAno(d) }
        val esteAno = div.esteAno
        if (esteAno != null && esteAno.empresas.isNotEmpty()) item { CartaoPorEmpresa(esteAno) }
        item {
            TextoPequeno(
                "Brutos em dólares, como na folha. Os líquidos são em euros, depois da retenção na fonte e do IRS.",
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

private fun mesDeHoje(d: Dados): Int? =
    d.meta.hoje.takeIf { it.length >= 7 }?.substring(5, 7)?.toIntOrNull()?.minus(1)?.takeIf { it in 0..11 }

private val MESES_CURTOS = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

// ───────────────────────────── Este ano ─────────────────────────────

@Composable
private fun CartaoRecebidoEsteAno(d: Dados) {
    val div = d.dividendos
    val anoAtual = d.meta.hoje.take(4).toIntOrNull() ?: return
    val mes = mesDeHoje(d) ?: 11
    val porAno = div.anos.associateBy { it.ano }
    val este = porAno[anoAtual]
    val passado = porAno[anoAtual - 1]
    val recebido = este?.totalUsd ?: este?.meses?.sum() ?: 0.0
    val ateHoje = este?.meses?.take(mes + 1)?.sum()
    val passadoAteHoje = passado?.meses?.take(mes + 1)?.sum()
    val variacao = if (ateHoje != null && passadoAteHoje != null && passadoAteHoje > 0) ateHoje / passadoAteHoje - 1 else null
    val usdEur = d.cambio.usdEur
    Cartao("Recebido em $anoAtual") {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(Formatos.usd(recebido, 0), style = MaterialTheme.typography.headlineMedium.numeros(), fontWeight = FontWeight.SemiBold)
                if (usdEur != null) TextoPequeno("≈ ${Formatos.eur(recebido * usdEur, 0)} brutos")
            }
            if (variacao != null && passadoAteHoje != null) {
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Formatos.pctSinal(variacao, 0),
                        style = MaterialTheme.typography.titleLarge.numeros(),
                        fontWeight = FontWeight.SemiBold,
                        color = corDoSinal(variacao),
                    )
                    TextoPequeno("vs jan–${MESES_CURTOS[mes]} de ${anoAtual - 1}")
                    TextoPequeno(Formatos.usd(passadoAteHoje, 0))
                }
            }
        }
        val total = passado?.totalUsd
        if (total != null && total > 0 && recebido > 0) {
            BarraComMarca(recebido, total, maxOf(recebido, total) * 1.05, MaterialTheme.colorScheme.primary)
            TextoPequeno(
                if (recebido >= total) {
                    "Já passaste o total de ${anoAtual - 1} (${Formatos.usd(total, 0)})."
                } else {
                    "O traço é o total de ${anoAtual - 1}: ${Formatos.usd(total, 0)}."
                },
            )
        }
    }
}

@Composable
private fun GrelhaDividendos(d: Dados) {
    val div = d.dividendos
    val ano = div.previsaoAno
    val bruto = ano?.let { div.indicador("BRUTO $it ($)")?.valor }
    val brutoEur = ano?.let { div.indicador("BRUTO $it (€)")?.valor }
    val liquido = ano?.let { div.indicador("LÍQUIDO $it (€)")?.valor }
    val estimativa = div.indicador("DIVIDENDO ANUAL ESTIMADO ($)")?.valor
    val yieldCarteira = div.indicador("YIELD MÉDIO DA CARTEIRA")?.valor
    val total = div.indicador("TOTAL RECEBIDO ATÉ HOJE ($)")?.valor ?: div.anos.sumOf { it.totalUsd ?: 0.0 }
    val primeiroAno = div.anos.minOfOrNull { it.ano }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LinhaDeMosaicos {
            if (ano != null && liquido != null) {
                Mosaico("Previsão $ano (líquido)", Formatos.eur(liquido, 0), Modifier.weight(1f).fillMaxHeight(), detalhe = "≈ ${Formatos.eur(liquido / 12, 0)} por mês")
            } else {
                Mosaico("Estimativa anual", Formatos.usd(estimativa, 0), Modifier.weight(1f).fillMaxHeight(), detalhe = "bruto")
            }
            if (ano != null && bruto != null) {
                Mosaico("Previsão $ano (bruto)", Formatos.usd(bruto, 0), Modifier.weight(1f).fillMaxHeight(), detalhe = brutoEur?.let { "≈ ${Formatos.eur(it, 0)}" })
            } else {
                Mosaico("Yield da carteira", Formatos.pct(yieldCarteira), Modifier.weight(1f).fillMaxHeight())
            }
        }
        LinhaDeMosaicos {
            Mosaico(
                "Yield da carteira",
                Formatos.pct(yieldCarteira),
                Modifier.weight(1f).fillMaxHeight(),
                detalhe = estimativa?.let { "${Formatos.usd(it, 0)} por ano" },
            )
            Mosaico(
                "Total recebido",
                Formatos.usd(total, 0),
                Modifier.weight(1f).fillMaxHeight(),
                detalhe = primeiroAno?.let { "desde $it" },
            )
        }
    }
}

// ───────────────────────────── Mês a mês ─────────────────────────────

@Composable
private fun CartaoMesAMes(d: Dados) {
    val div = d.dividendos
    val cal = div.calendario
    val ano = div.previsaoAno
    val anoAtual = d.meta.hoje.take(4).toIntOrNull()
    val mesAtual = mesDeHoje(d)
    var escolhido by rememberSaveable { mutableIntStateOf(mesAtual ?: 0) }
    val anterior = ano?.minus(1)
    Cartao(if (ano != null) "Mês a mês em $ano" else "Mês a mês") {
        TextoPequeno("Toca num mês para ver quanto e quem paga.")
        BarrasMensais(
            previsto = cal.map { it.previstoBrutoUsd ?: 0.0 },
            recebido = cal.map { it.recebidoAnoAnteriorUsd },
            destacado = escolhido,
            aoTocar = { escolhido = it },
            descricao = { k -> "${cal[k].mes}: ${Formatos.usd(cal[k].previstoBrutoUsd, 0)} previstos" },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legenda(MaterialTheme.colorScheme.primary, if (ano != null) "Previsão $ano" else "Previsão")
            if (anterior != null) Legenda(Paleta.dourado, "Recebido em $anterior")
        }
        HorizontalDivider(color = corBordaCartao())
        val m = cal[escolhido.coerceIn(0, 11)]
        Text(m.mes, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        LinhaValor("Previsto (bruto)", Formatos.usd(m.previstoBrutoUsd, 0), destaque = true)
        LinhaValor("Líquido", Formatos.eur(m.previstoLiquidoEur, 0))
        if (anterior != null) {
            // O mês ainda não chegou no ano anterior (ou está a meio): diz isso em vez de mostrar 0 $.
            val aindaNao = anterior == anoAtual && mesAtual != null && escolhido > mesAtual
            val aMeio = anterior == anoAtual && escolhido == mesAtual
            LinhaValor(
                "Em $anterior" + if (aMeio) " (até agora)" else "",
                if (aindaNao) "ainda não chegou" else Formatos.usd(m.recebidoAnoAnteriorUsd, 0),
            )
        }
        val ja = m.recebidoAnoUsd
        if (ja != null && ja > 0 && ano != null) LinhaValor("Já recebido em $ano", Formatos.usd(ja), cor = Paleta.verde)
        m.quemPaga?.takeIf { it.isNotBlank() }?.let { quem ->
            Text("Quem paga", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(quem.split(",").joinToString(" · ") { it.trim() }, style = MaterialTheme.typography.bodyMedium)
        }
        val semVendas = div.indicador("SEM AS VENDAS DO PLANO ($)")?.valor
        val bruto = ano?.let { div.indicador("BRUTO $it ($)")?.valor }
        if (semVendas != null && bruto != null) {
            HorizontalDivider(color = corBordaCartao())
            TextoPequeno(
                "Sem as vendas do plano, $ano daria ${Formatos.usd(semVendas, 0)} brutos (${Formatos.usdSinal(semVendas - bruto)}).",
            )
        }
    }
}

// ───────────────────────────── Por ano ─────────────────────────────

@Composable
private fun CartaoPorAno(d: Dados) {
    val div = d.dividendos
    val anoAtual = d.meta.hoje.take(4).toIntOrNull()
    val anos = div.anos.sortedBy { it.ano }
    val previsao = div.previsaoAno
    val brutoPrevisto = previsao?.let { div.indicador("BRUTO $it ($)")?.valor }
    val itens = anos.mapIndexed { k, a ->
        val valor = a.totalUsd ?: a.meses.sum()
        val antes = anos.getOrNull(k - 1)?.let { it.totalUsd ?: it.meses.sum() }
        val crescimento = if (antes != null && antes > 0) valor / antes - 1 else null
        BarraAno(
            rotulo = a.ano.toString(),
            valor = valor,
            nota = crescimento?.let { Formatos.pctSinal(it, 0) },
            corNota = corDoSinal(crescimento),
        )
    } + listOfNotNull(
        if (previsao != null && brutoPrevisto != null && anos.none { it.ano == previsao }) {
            BarraAno(previsao.toString(), brutoPrevisto, previsao = true, nota = "previsão")
        } else {
            null
        },
    )
    Cartao("Por ano") {
        BarrasAnuais(itens, formatar = { Formatos.compacto(it) })
        TextoPequeno(
            "Brutos, em dólares." + (anoAtual?.let { " $it vai até hoje." } ?: "") +
                (previsao?.let { " $it é a previsão da folha." } ?: ""),
        )
    }
}

// ───────────────────────────── Por empresa ─────────────────────────────

@Composable
private fun CartaoPorEmpresa(esteAno: EsteAno) {
    var todas by rememberSaveable { mutableStateOf(false) }
    val lista = esteAno.empresas.sortedByDescending { it.totalUsd }
    val maximo = lista.first().totalUsd.coerceAtLeast(0.01)
    val total = lista.sumOf { it.totalUsd }
    val visiveis = if (todas) lista else lista.take(10)
    Cartao("Recebido em ${esteAno.ano}, por empresa") {
        TextoPequeno("${lista.size} empresas e ETFs · ${Formatos.usd(total, 0)} no total")
        visiveis.forEach { e ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.ticker, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        e.empresa ?: "",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(Formatos.usd(e.totalUsd), style = MaterialTheme.typography.bodyMedium.numeros(), maxLines = 1)
                }
                BarraComMarca(e.totalUsd, null, maximo, MaterialTheme.colorScheme.primary, altura = 6.dp)
            }
        }
        if (lista.size > 10) {
            TextButton(onClick = { todas = !todas }) { Text(if (todas) "Mostrar menos" else "Ver todas (${lista.size})") }
        }
    }
}
