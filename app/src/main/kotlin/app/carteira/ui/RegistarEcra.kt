@file:OptIn(ExperimentalMaterial3Api::class)

package app.carteira.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.carteira.dados.Dados
import app.carteira.dados.TickerInfo
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun dataPt(diasAtras: Long = 0): String = LocalDate.now().minusDays(diasAtras).format(formatoData)

@Composable
fun RegistarEcra(estado: Estado, vm: CarteiraViewModel, ecra: Ecra.Registar) {
    val d = estado.dados ?: return SemDados(estado, vm)
    var dividendo by rememberSaveable { mutableStateOf(ecra.modo == ModoRegisto.DIVIDENDO) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Filtros(listOf("Compra ou venda", "Dividendo"), if (dividendo) "Dividendo" else "Compra ou venda") {
            dividendo = it == "Dividendo"
        }
        if (dividendo) {
            FormDividendo(d, estado, vm, ecra.ticker)
        } else {
            FormTransacao(d, estado, vm, ecra)
        }
        Spacer(Modifier.size(80.dp))
    }
}

/** Campo do ticker com sugestões da carteira por baixo. */
@Composable
private fun CampoTicker(valor: String, sugestoes: List<TickerInfo>, aoMudar: (String) -> Unit, aoEscolher: (TickerInfo) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = { aoMudar(it.uppercase()) },
        label = { Text("Ticker") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
    )
    val termo = valor.trim().lowercase()
    val exato = sugestoes.any { it.ticker.equals(valor.trim(), ignoreCase = true) }
    if (termo.isNotEmpty() && !exato) {
        val lista = sugestoes.filter {
            it.ticker.lowercase().contains(termo) || (it.nome?.lowercase()?.contains(termo) == true)
        }.take(6)
        if (lista.isNotEmpty()) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                lista.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable { aoEscolher(t) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t.ticker, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        TextoPequeno(t.nome ?: "", Modifier.weight(1f))
                        TextoPequeno(t.classe)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CampoNumero(valor: String, rotulo: String, aoMudar: (String) -> Unit, erro: String? = null, sufixo: String? = null) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = erro != null,
        supportingText = if (erro != null) { { Text(erro) } } else null,
        suffix = if (sufixo != null) { { Text(sufixo) } } else null,
    )
}

@Composable
private fun CampoData(valor: String, aoMudar: (String) -> Unit) {
    val valido = Formatos.dataIso(valor) != null
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text("Data (dd/mm/aaaa)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = !valido,
        supportingText = if (valido) null else { { Text("Escreve a data como 16/11/2026") } },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = { aoMudar(dataPt()) }, label = { Text("Hoje") })
        AssistChip(onClick = { aoMudar(dataPt(1)) }, label = { Text("Ontem") })
    }
}

@Composable
internal fun BotaoEnviar(texto: String, ativo: Boolean, aEnviar: Boolean, aoCarregar: () -> Unit) {
    Button(onClick = aoCarregar, enabled = ativo && !aEnviar, modifier = Modifier.fillMaxWidth()) {
        if (aEnviar) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("A registar na folha…")
        } else {
            Text(texto)
        }
    }
}

@Composable
private fun FormTransacao(d: Dados, estado: Estado, vm: CarteiraViewModel, ecra: Ecra.Registar) {
    val tickerInicial = ecra.ticker
    val classeInicial = d.tickers.firstOrNull { it.ticker == tickerInicial }?.classe
        ?: if (d.etfs.any { it.ticker == tickerInicial }) "ETF" else "Ação"
    var tipo by rememberSaveable { mutableStateOf(ecra.tipo ?: "Compra") }
    var ticker by rememberSaveable { mutableStateOf(tickerInicial ?: "") }
    var classe by rememberSaveable { mutableStateOf(classeInicial) }
    var data by rememberSaveable { mutableStateOf(dataPt()) }
    // Do reforço do mês vêm as unidades e o preço de agora: confirma-os com o que a corretora te deu.
    var unidades by rememberSaveable { mutableStateOf(ecra.unidades?.let { Formatos.numeroLivre(it, 4).replace(Formatos.NB.toString(), "") } ?: "") }
    var preco by rememberSaveable { mutableStateOf(ecra.preco?.let { Formatos.numeroLivre(it, 4).replace(Formatos.NB.toString(), "") } ?: "") }
    var comissao by rememberSaveable { mutableStateOf("0") }
    var cambio by rememberSaveable { mutableStateOf(d.cambio.usdEur?.let { Formatos.numeroLivre(it, 5) } ?: "") }

    val info = d.tickers.firstOrNull { it.ticker.equals(ticker.trim(), ignoreCase = true) }
    val acao = classe == "Ação"
    val moeda = if (acao) "$" else "€"
    val precoAtual = if (acao) d.posicoes.firstOrNull { it.ticker == info?.ticker }?.precoUsd
    else d.etfs.firstOrNull { it.ticker == info?.ticker }?.precoEur

    val u = Formatos.lerNumero(unidades)
    val p = Formatos.lerNumero(preco)
    val c = Formatos.lerNumero(comissao) ?: if (comissao.isBlank()) 0.0 else null
    val fx = Formatos.lerNumero(cambio)
    val dataIso = Formatos.dataIso(data)
    val tem = info?.quantidade ?: 0.0

    val erroUnidades = when {
        unidades.isNotBlank() && (u == null || u <= 0) -> "Número de unidades inválido"
        tipo == "Venda" && u != null && u > tem + 1e-9 -> "Só tens ${Formatos.numeroLivre(tem)}"
        else -> null
    }
    val erroPreco = if (preco.isNotBlank() && (p == null || p <= 0)) "Preço inválido" else null
    val erroComissao = if (c == null || c < 0) "Comissão inválida" else null
    val erroCambio = if (acao && (fx == null || fx <= 0.3 || fx >= 3)) "Câmbio inválido (euros por dólar, ex. 0,9078)" else null
    val valido = ticker.isNotBlank() && dataIso != null && u != null && u > 0 && p != null && p > 0 &&
        erroUnidades == null && erroComissao == null && erroCambio == null

    Filtros(listOf("Compra", "Venda"), tipo) { tipo = it }
    CampoTicker(ticker, d.tickers, aoMudar = { ticker = it }) { t ->
        ticker = t.ticker
        classe = t.classe
    }
    if (info != null) {
        TextoPequeno(
            "${info.nome ?: info.ticker} · tens ${Formatos.quantidade(info.quantidade)}" +
                (precoAtual?.let { " · preço atual ${Formatos.numero(it)} $moeda" } ?: ""),
        )
    } else if (ticker.isNotBlank()) {
        TextoPequeno("Não está na Carteira: a compra fica registada, mas tens de o juntar à tab Carteira para entrar nos totais.")
    }
    Filtros(listOf("Ação", "ETF"), classe) { classe = it }
    CampoData(data) { data = it }
    CampoNumero(unidades, "Unidades", { unidades = it }, erroUnidades)
    CampoNumero(preco, "Preço por unidade", { preco = it }, erroPreco, moeda)
    if (precoAtual != null && preco.isBlank()) {
        AssistChip(
            onClick = { preco = Formatos.numeroLivre(precoAtual, 4).replace(Formatos.NB.toString(), "") },
            label = { Text("Usar o preço atual (${Formatos.numero(precoAtual)} $moeda)") },
        )
    }
    CampoNumero(comissao, "Comissão", { comissao = it }, erroComissao, moeda)
    if (acao) {
        CampoNumero(cambio, "Câmbio USD/EUR (euros por dólar)", { cambio = it }, erroCambio)
    }
    if (u != null && p != null && u > 0 && p > 0) {
        val total = u * p
        val emEur = if (acao && fx != null) " (≈ ${Formatos.eur(total * fx)})" else ""
        Text(
            "Total: ${Formatos.numeroLivre(u)} × ${Formatos.numero(p)} $moeda = ${Formatos.numero(total)} $moeda$emEur",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
    if (ecra.doReforco) {
        TextoPequeno("Do reforço do mês: confirma as unidades e o preço com o que a corretora te deu.")
    }
    BotaoEnviar(
        texto = if (tipo == "Compra") "Registar compra" else "Registar venda",
        ativo = valido,
        aEnviar = estado.aEnviar,
    ) {
        val t = ticker.trim().uppercase()
        val qtd = u!!
        val precoUn = p!!
        val com = c ?: 0.0
        // Ações em dólares (ao câmbio escrito), ETF em euros.
        val custoEur = (qtd * precoUn + com) * (if (acao) fx ?: 1.0 else 1.0)
        val compra = tipo == "Compra"
        vm.registarTransacao(
            data = dataIso!!,
            tipo = tipo,
            classe = classe,
            ticker = t,
            unidades = qtd,
            preco = precoUn,
            comissao = com,
            cambio = if (acao) fx else null,
        ) { ok ->
            if (ok) {
                if (ecra.doReforco && compra) vm.reforcoRegistado(t, qtd, custoEur)
                vm.voltar()
            }
        }
    }
    TextoPequeno("Fica escrita na tab Transações; a Carteira e o resto da folha atualizam-se sozinhos.")
}

@Composable
private fun FormDividendo(d: Dados, estado: Estado, vm: CarteiraViewModel, tickerInicial: String?) {
    var ticker by rememberSaveable { mutableStateOf(tickerInicial ?: "") }
    var data by rememberSaveable { mutableStateOf(dataPt()) }
    var valor by rememberSaveable { mutableStateOf("") }
    var moeda by rememberSaveable { mutableStateOf("USD") }

    val pagadoras = buildList {
        d.posicoes.filter { (it.divAnualPorAcaoUsd ?: 0.0) > 0 }.forEach { add(TickerInfo(it.ticker, it.nome, "Ação", it.quantidade)) }
        d.etfs.forEach { add(TickerInfo(it.ticker, it.nome, "ETF", it.quantidade)) }
        d.dividendos.esteAno?.empresas?.forEach { e ->
            if (none { it.ticker == e.ticker }) add(TickerInfo(e.ticker, e.empresa, "", 0.0))
        }
    }
    val v = Formatos.lerNumero(valor)
    val dataIso = Formatos.dataIso(data)
    val erroValor = if (valor.isNotBlank() && (v == null || v <= 0)) "Valor inválido" else null
    val valido = ticker.isNotBlank() && dataIso != null && v != null && v > 0

    CampoTicker(ticker, pagadoras, aoMudar = { ticker = it }) { t -> ticker = t.ticker }
    val posicao = d.posicoes.firstOrNull { it.ticker == ticker.trim() }
    if (posicao?.divAnualPorAcaoUsd != null) {
        TextoPequeno(
            "${posicao.nome}: ${Formatos.preco(posicao.divAnualPorAcaoUsd, "USD")} por ação por ano · ${posicao.frequencia ?: ""}",
        )
    }
    CampoData(data) { data = it }
    CampoNumero(valor, "Valor bruto recebido", { valor = it }, erroValor)
    Filtros(listOf("USD", "EUR", "GBP"), moeda) { moeda = it }
    TextoComDica(
        "Valor bruto, antes de impostos.",
        dica = "Como na folha. Em euros ou libras, a ponte converte para dólares ao câmbio da Carteira e soma ao mês " +
            "da data em Dividendos Mensais.",
    )
    BotaoEnviar("Registar dividendo", valido, estado.aEnviar) {
        vm.registarDividendo(dataIso!!, ticker.trim().uppercase(), v!!, moeda) { ok -> if (ok) vm.voltar() }
    }
}
