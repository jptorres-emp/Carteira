package app.carteira.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.carteira.dados.Dados

@Composable
fun DividendosEcra(estado: Estado, vm: CarteiraViewModel) {
    val d = estado.dados ?: return SemDados(estado, vm)
    val div = d.dividendos
    val anoAtual = d.meta.hoje.take(4).toIntOrNull()
    val recebidoAno = div.anos.firstOrNull { it.ano == anoAtual }?.totalUsd
    val estimativa = div.indicador("DIVIDENDO ANUAL ESTIMADO ($)")?.valor
    val total = div.indicador("TOTAL RECEBIDO ATÉ HOJE ($)")?.valor
    val usdEur = d.cambio.usdEur

    ListaAtualizavel(estado, vm) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mosaico(
                    "Estimativa anual",
                    Formatos.usd(estimativa, 0),
                    Modifier.weight(1f),
                    detalhe = usdEur?.let { u -> estimativa?.let { "≈ ${Formatos.eur(it * u, 0)}" } },
                )
                Mosaico("Recebido em ${anoAtual ?: ""}", Formatos.usd(recebidoAno, 0), Modifier.weight(1f))
                Mosaico("Total recebido", Formatos.usd(total, 0), Modifier.weight(1f), detalhe = "desde 2022")
            }
        }
        if (div.previsaoAno != null) item { CartaoPrevisao(d) }
        if (div.calendario.isNotEmpty()) item { CartaoCalendario(d) }
        val esteAno = div.esteAno
        if (esteAno != null && esteAno.empresas.isNotEmpty()) {
            item {
                Cartao("Recebido em ${esteAno.ano}, por empresa") {
                    val maximo = esteAno.empresas.maxOf { it.totalUsd }.coerceAtLeast(0.01)
                    esteAno.empresas.forEach { e ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(e.ticker, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(Formatos.usd(e.totalUsd), style = MaterialTheme.typography.bodyMedium)
                            }
                            LinearProgressIndicator(
                                progress = { (e.totalUsd / maximo).toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        if (div.anos.isNotEmpty()) {
            item {
                Cartao("Por ano") {
                    div.anos.forEach { a ->
                        LinhaValor(a.ano.toString(), Formatos.usd(a.totalUsd))
                    }
                    TextoPequeno("Valores brutos em dólares, como na folha.")
                }
            }
        }
    }
}

@Composable
private fun CartaoPrevisao(d: Dados) {
    val div = d.dividendos
    val ano = div.previsaoAno ?: return
    val bruto = div.indicador("BRUTO $ano ($)")?.valor
    val brutoEur = div.indicador("BRUTO $ano (€)")?.valor
    val liquido = div.indicador("LÍQUIDO $ano (€)")?.valor
    val semVendas = div.indicador("SEM AS VENDAS DO PLANO ($)")?.valor
    Cartao("Previsão para $ano") {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                TextoPequeno("Bruto")
                Text(Formatos.usd(bruto, 0), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                TextoPequeno("≈ ${Formatos.eur(brutoEur, 0)}")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                TextoPequeno("Depois de impostos")
                Text(Formatos.eur(liquido, 0), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                TextoPequeno("≈ ${Formatos.eur(liquido?.let { it / 12 }, 0)} por mês")
            }
        }
        if (semVendas != null && bruto != null) {
            TextoPequeno(
                "Sem as vendas do plano seriam ${Formatos.usd(semVendas, 0)} (${Formatos.usdSinal(semVendas - bruto)}).",
            )
        }
    }
}

@Composable
private fun CartaoCalendario(d: Dados) {
    val div = d.dividendos
    val ano = div.previsaoAno
    Cartao("Mês a mês em ${ano ?: ""}") {
        Row(Modifier.fillMaxWidth()) {
            TextoPequeno("Mês", Modifier.weight(1.2f))
            TextoPequeno("Previsto", Modifier.weight(1f))
            TextoPequeno("Líquido", Modifier.weight(1f))
            TextoPequeno(if (ano != null) "Em ${ano - 1}" else "Ano anterior", Modifier.weight(1f))
        }
        HorizontalDivider()
        div.calendario.forEach { m ->
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(m.mes, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(Formatos.usd(m.previstoBrutoUsd, 0), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(Formatos.eur(m.previstoLiquidoEur, 0), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(Formatos.usd(m.recebidoAnoAnteriorUsd, 0), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
                m.quemPaga?.let { TextoPequeno(it) }
                val recebido = m.recebidoAnoUsd
                if (recebido != null && recebido > 0) {
                    TextoPequeno("Já recebido: ${Formatos.usd(recebido)}", cor = corDoSinal(recebido))
                }
            }
        }
    }
}
