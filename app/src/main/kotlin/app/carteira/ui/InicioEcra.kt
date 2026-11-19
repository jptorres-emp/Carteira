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
import app.carteira.alertas.Alerta
import app.carteira.alertas.TipoAlerta
import app.carteira.dados.AnalisePatrimonio
import app.carteira.dados.Dados
import app.carteira.dados.ItemWatchlist
import app.carteira.dados.Objetivo
import app.carteira.dados.Sinais
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * Ecrã inicial: o que interessa ao abrir a app, do mais importante para o resto.
 * Valor e dia → alertas recentes → património → evolução → indicadores → objetivos → o que precisa
 * de atenção → hoje → alocação → dividendos → oportunidades → desempenho → movimentos.
 */
@Composable
fun InicioEcra(estado: Estado, vm: CarteiraViewModel) {
    val d = estado.dados ?: return SemDados(estado, vm)
    val atencao = itensAtencao(d)
    // Primeiro as que passam o teste de troca da folha (pelo ganho por ano); depois pela margem.
    val oportunidades = d.watchlist.filter { Sinais.compra(it.sinal) }
        .sortedWith(compareByDescending<ItemWatchlist> { ganhoDaTroca(it) ?: -1.0 }.thenByDescending { it.margem ?: 0.0 })
    val recentes = estado.alertas.filter { recente(it.quando, 3) }.take(3)
    val patrimonio = remember(d) { AnalisePatrimonio.de(d) }

    ListaAtualizavel(estado, vm) {
        item { CartaoPrincipal(d) }
        if (estado.conviteAlertas) item { ConviteAlertas(estado, vm) }
        if (recentes.isNotEmpty()) item { CartaoAlertasRecentes(recentes, estado.alertasNaoVistos, vm) }
        if (patrimonio != null) item { CartaoPatrimonioResumo(patrimonio, vm) }
        if (d.evolucao.size >= 2) {
            item { Cartao("Evolução") { GraficoEvolucao(d.evolucao) } }
        } else if (d.versaoPonte < 2) {
            item { AvisoPonteAntiga(vm) }
        }
        item { GrelhaIndicadores(d) }
        if (d.objetivos.isNotEmpty()) item { CartaoObjetivos(d.objetivos) }
        if (atencao.isNotEmpty()) item { CartaoAtencao(atencao, vm) }
        item { CartaoHoje(d, vm) }
        item { CartaoAlocacao(d) }
        item { CartaoDividendosResumo(d, vm) }
        if (oportunidades.isNotEmpty()) item { CartaoOportunidades(oportunidades, vm) }
        item { CartaoDesempenho(d) }
        if (d.transacoes.isNotEmpty()) item { CartaoMovimentos(d) }
        item {
            TextoPequeno(
                "Dados da folha de ${Formatos.momento(d.meta.atualizadoEm)}. Puxa para baixo para atualizar.",
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/** "✓ Avaliar troca com DUNA: +15,3%/ano" → 15,3. Só conta se o teste passou (✓). */
private fun ganhoDaTroca(w: ItemWatchlist): Double? {
    val t = w.testeTroca ?: return null
    if (!t.startsWith("✓")) return null
    return Regex("""\+(\d+(?:,\d+)?)%/ano""").find(t)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
}

private fun recente(quando: String, dias: Long): Boolean {
    val dia = runCatching { LocalDate.parse(quando.take(10)) }.getOrNull() ?: return false
    return !dia.isBefore(LocalDate.now().minusDays(dias))
}

/** Variação de hoje em euros e em %, a partir da variação diária de cada posição e ETF. */
private fun variacaoHojeEur(d: Dados): Pair<Double, Double>? {
    var mudanca = 0.0
    var total = 0.0
    var algum = false
    val valores = d.posicoes.map { it.valorEur to it.varDiaPct } + d.etfs.map { it.valorEur to it.varDiaPct }
    valores.forEach { (v, r) ->
        if (v == null) return@forEach
        total += v
        if (r == null) return@forEach
        mudanca += v * r / (1 + r)
        algum = true
    }
    if (!algum || total <= 0) return null
    return mudanca to mudanca / (total - mudanca)
}

// ───────────────────────────── Cartão principal ─────────────────────────────

@Composable
private fun CartaoPrincipal(d: Dados) {
    val acoes = d.totaisAcoes?.valorEur
    val etfs = d.totaisEtfs?.valorEur
    val total = d.indicador("VALOR TOTAL (EUR)")?.valor ?: ((acoes ?: 0.0) + (etfs ?: 0.0))
    val ganho = d.indicador("GANHO/PERDA (EUR)")?.valor
    val ganhoPct = d.indicador("GANHO/PERDA (%)")?.valor
    val patrimonio = d.patrimonio?.totalEur ?: d.indicador("PATRIMÓNIO TOTAL (NET WORTH)")?.valor
    val hoje = variacaoHojeEur(d)
    val branco = Color.White

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Paleta.marinho, Paleta.marinhoClaro))),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Valor da carteira",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = branco.copy(alpha = 0.75f),
                )
                Text(Formatos.momento(d.meta.atualizadoEm), style = MaterialTheme.typography.labelSmall, color = branco.copy(alpha = 0.6f))
            }
            Text(
                Formatos.eur(total, 0),
                style = MaterialTheme.typography.displaySmall.numeros(),
                fontWeight = FontWeight.SemiBold,
                color = branco,
            )
            if (hoje != null) {
                val positivo = hoje.first >= 0
                Surface(
                    color = (if (positivo) Paleta.verde else Paleta.vermelho).copy(alpha = 0.45f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        "${if (positivo) "▲" else "▼"} ${Formatos.eurSinal(hoje.first)} (${Formatos.pctSinal(hoje.second, 2)}) hoje",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge.numeros(),
                        color = branco,
                    )
                }
            }
            if (ganho != null) {
                Text(
                    "Ganho total ${Formatos.eurSinal(ganho)} (${Formatos.pctSinal(ganhoPct)})",
                    style = MaterialTheme.typography.bodyMedium.numeros(),
                    color = if (ganho >= 0) Paleta.verdeClaro else Paleta.vermelhoClaro,
                )
            }
            HorizontalDivider(color = branco.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth()) {
                ColunaHero("Património", Formatos.eur(patrimonio, 0), Modifier.weight(1f))
                ColunaHero("Ações", Formatos.eur(acoes, 0), Modifier.weight(1f))
                ColunaHero("ETFs", Formatos.eur(etfs, 0), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ColunaHero(rotulo: String, valor: String, modifier: Modifier) {
    Column(modifier) {
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f))
        Text(
            valor,
            style = MaterialTheme.typography.titleSmall.numeros(),
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

// ───────────────────────────── Alertas ─────────────────────────────

@Composable
private fun ConviteAlertas(estado: Estado, vm: CarteiraViewModel) {
    Cartao("Alertas no telemóvel") {
        Text(
            "Aviso-te quando uma posição tem sinal de compra pelas regras da tua folha, quando uma empresa da " +
                "watchlist fica abaixo do valor justo e quando há uma variação grande de preço.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BotaoAtivarAlertas(estado, vm)
            TextButton(onClick = { vm.dispensarConviteAlertas() }) { Text("Agora não") }
        }
    }
}

@Composable
private fun CartaoAlertasRecentes(recentes: List<Alerta>, naoVistos: Int, vm: CarteiraViewModel) {
    Cartao(
        if (naoVistos > 0) "Alertas novos ($naoVistos)" else "Alertas recentes",
        acao = "Ver todos",
        aoAcao = { vm.irPara(Ecra.Alertas) },
    ) {
        recentes.forEachIndexed { k, a ->
            if (k > 0) HorizontalDivider(color = corBordaCartao())
            LinhaAlerta(a) { vm.abrir(a.chave) }
        }
    }
}

/** Uma linha de alerta (usada aqui e no ecrã de alertas). */
@Composable
fun LinhaAlerta(a: Alerta, aoTocar: () -> Unit) {
    val cor = when (a.tipo) {
        TipoAlerta.COMPRA, TipoAlerta.WATCHLIST -> Paleta.verde
        TipoAlerta.VENDA -> Paleta.ambar
        TipoAlerta.VARIACAO -> if (a.positivo) Paleta.verde else Paleta.vermelho
    }
    val simbolo = when (a.tipo) {
        TipoAlerta.COMPRA -> "🟢"
        TipoAlerta.WATCHLIST -> "👀"
        TipoAlerta.VENDA -> "🟠"
        TipoAlerta.VARIACAO -> if (a.positivo) "▲" else "▼"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = aoTocar).padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(cor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(simbolo, style = MaterialTheme.typography.labelMedium, color = cor)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(a.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            TextoPequeno(a.texto)
            Text(
                Formatos.quando(a.quando),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AvisoPonteAntiga(vm: CarteiraViewModel) {
    Cartao("Há mais para ver") {
        Text(
            "Atualiza a ponte da folha para veres a evolução da carteira face ao S&P 500, os teus objetivos, " +
                "o património total e os setores.",
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

// ───────────────────────────── Precisa de atenção ─────────────────────────────

private enum class Nivel { COMPRA, REDUZIR, SAIR, SETOR, REGRA }

private data class ItemAtencao(val nivel: Nivel, val titulo: String, val detalhe: String?, val chave: String?)

/** Pelos SINAIS da folha (as tuas regras), não pelas zonas: é o que manda agir. */
private fun itensAtencao(d: Dados): List<ItemAtencao> {
    val itens = mutableListOf<ItemAtencao>()
    d.posicoes.filter { Sinais.compra(it.sinal) }.sortedByDescending { Sinais.compraForte(it.sinal) }.forEach { p ->
        itens += ItemAtencao(
            Nivel.COMPRA,
            "${p.ticker} · ${Sinais.semEmoji(p.sinal)}",
            listOfNotNull(p.nome, p.precoUsd?.let { Formatos.usd(it) }, p.zona?.let { "zona ${Sinais.semEmoji(it)}" }).joinToString(" · "),
            "P:${p.ticker}",
        )
    }
    d.posicoes.filter { Sinais.reduzir(it.sinal) }.forEach { p ->
        itens += ItemAtencao(
            Nivel.REDUZIR,
            "${p.ticker} · ${Sinais.semEmoji(p.sinal)}",
            listOfNotNull(
                p.nome,
                p.pesoTotal?.let { "peso ${Formatos.pct(it)}" + (p.pesoMaximo?.let { m -> " (máx. ${Formatos.pct(m)})" } ?: "") },
            ).joinToString(" · "),
            "P:${p.ticker}",
        )
    }
    // Saídas planeadas, juntas pelo prazo: "Sair até 31/12/2026: AAA, BBB, CCC".
    d.posicoes.filter { Sinais.sair(it.sinal) }.groupBy { Sinais.semEmoji(it.sinal) }.forEach { (prazo, ps) ->
        val dias = Regex("""(\d{2})/(\d{2})/(\d{4})""").find(prazo)?.destructured?.let { (dd, mm, aa) ->
            runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.of(aa.toInt(), mm.toInt(), dd.toInt())) }.getOrNull()
        }
        val falta = dias?.let { if (it >= 0) " · faltam $it dias" else " · prazo passou" } ?: ""
        itens += ItemAtencao(
            Nivel.SAIR,
            prazo,
            ps.joinToString(", ") { it.ticker } + falta,
            if (ps.size == 1) "P:${ps[0].ticker}" else null,
        )
    }
    d.setores.filter { it.acima }.forEach { s ->
        itens += ItemAtencao(
            Nivel.SETOR,
            "Setor acima do limite: ${nomeSetor(s.setor)}",
            "${Formatos.pct(s.pesoAcoes)} das ações · limite ${Formatos.pct(s.limite, 0)}",
            null,
        )
    }
    d.regras.filter { it.alerta }.forEach { r ->
        val detalhe = listOfNotNull(
            "hoje ${r.hoje}".takeIf { r.hoje.isNotBlank() },
            "limite ${r.limite}".takeIf { r.limite.isNotBlank() && r.limite != "—" },
        ).joinToString(" · ")
        itens += ItemAtencao(Nivel.REGRA, r.regra, detalhe.ifBlank { null }, null)
    }
    return itens
}

@Composable
private fun CartaoAtencao(itens: List<ItemAtencao>, vm: CarteiraViewModel) {
    var tudo by rememberSaveable { mutableStateOf(false) }
    val visiveis = if (tudo) itens else itens.take(6)
    Cartao("Precisa de atenção") {
        visiveis.forEachIndexed { k, item ->
            if (k > 0) HorizontalDivider(color = corBordaCartao())
            val cor = when (item.nivel) {
                Nivel.COMPRA -> Paleta.verde
                Nivel.REDUZIR, Nivel.REGRA -> Paleta.ambar
                Nivel.SAIR, Nivel.SETOR -> Paleta.vermelho
            }
            val rotulo = when (item.nivel) {
                Nivel.COMPRA -> "Comprar"
                Nivel.REDUZIR -> "Reduzir"
                Nivel.SAIR -> "Sair"
                Nivel.SETOR -> "Setor"
                Nivel.REGRA -> "Regra"
            }
            val chave = item.chave
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (chave != null) Modifier.clickable { vm.abrir(chave) } else Modifier)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    item.detalhe?.let { TextoPequeno(it) }
                }
                Spacer(Modifier.width(8.dp))
                SeloEstado(rotulo, cor)
            }
        }
        if (itens.size > 6) {
            TextButton(onClick = { tudo = !tudo }) { Text(if (tudo) "Mostrar menos" else "Ver todos (${itens.size})") }
        }
    }
}

// ───────────────────────────── Hoje ─────────────────────────────

private data class Movimento(val ticker: String, val variacao: Double, val impactoEur: Double?, val chave: String)

@Composable
private fun CartaoHoje(d: Dados, vm: CarteiraViewModel) {
    val todos = d.posicoes.mapNotNull { p ->
        p.varDiaPct?.let { r -> Movimento(p.ticker, r, p.valorEur?.let { it * r / (1 + r) }, "P:${p.ticker}") }
    } + d.etfs.mapNotNull { e ->
        e.varDiaPct?.let { r -> Movimento(e.ticker, r, e.valorEur?.let { it * r / (1 + r) }, "E:${e.ticker}") }
    }
    if (todos.isEmpty()) return
    val sobem = todos.filter { it.variacao > 0 }.sortedByDescending { it.variacao }.take(3)
    val descem = todos.filter { it.variacao < 0 }.sortedBy { it.variacao }.take(3)
    Cartao("Hoje") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ColunaMovimentos("Sobem mais", sobem, Modifier.weight(1f), vm)
            ColunaMovimentos("Descem mais", descem, Modifier.weight(1f), vm)
        }
    }
}

@Composable
private fun ColunaMovimentos(titulo: String, lista: List<Movimento>, modifier: Modifier, vm: CarteiraViewModel) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextoPequeno(titulo)
        if (lista.isEmpty()) Text("—", style = MaterialTheme.typography.bodyMedium)
        lista.forEach { m ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.abrir(m.chave) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(m.ticker, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                    m.impactoEur?.let {
                        Text(
                            Formatos.eurSinal(it),
                            style = MaterialTheme.typography.labelSmall.numeros(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    Formatos.pctSinal(m.variacao, 2),
                    style = MaterialTheme.typography.bodyMedium.numeros(),
                    color = corDoSinal(m.variacao),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ───────────────────────────── Alocação ─────────────────────────────

@Composable
private fun CartaoAlocacao(d: Dados) {
    val temSetores = d.setores.any { it.valorEur > 0 }
    var vista by rememberSaveable { mutableStateOf("Camadas") }
    if (d.camadas.isEmpty() && !temSetores) return
    Cartao("Alocação") {
        if (temSetores && d.camadas.isNotEmpty()) {
            Filtros(listOf("Camadas", "Setores"), vista, compacto = true) { vista = it }
        }
        if (vista == "Camadas" && d.camadas.isNotEmpty()) {
            val camadas = d.camadas.filter { (it.pesoHoje ?: 0.0) > 0 || (it.pesoIdeal ?: 0.0) > 0 }
            BarraEmpilhada(camadas.mapIndexed { k, c -> (c.pesoHoje ?: 0.0) to Paleta.categoria(k) })
            val escala = camadas.maxOf { max(it.pesoHoje ?: 0.0, it.pesoIdeal ?: 0.0) }.coerceAtLeast(0.01) * 1.05
            camadas.forEachIndexed { k, c ->
                val hoje = c.pesoHoje ?: 0.0
                // Saídas e residuais têm ideal 0 (é para irem saindo): aí não há alvo a mostrar.
                val ideal = c.pesoIdeal?.takeIf { it > 0 }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Paleta.categoria(k)))
                        Spacer(Modifier.width(6.dp))
                        val n = c.numero?.toInt()?.toString() ?: "—"
                        val nIdeal = c.numeroIdeal?.toInt()?.takeIf { it > 0 }?.let { "/$it" } ?: ""
                        Text("${c.camada} · $n$nIdeal", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Formatos.pct(hoje) + if (ideal != null) " · ideal ${Formatos.pct(ideal)}" else "",
                            style = MaterialTheme.typography.bodySmall.numeros(),
                        )
                    }
                    BarraComMarca(hoje, ideal, escala, Paleta.categoria(k))
                    val dif = c.diferencaEur
                    if (dif != null && abs(dif) >= 50 && ideal != null) {
                        TextoPequeno(if (dif > 0) "${Formatos.eur(dif, 0)} acima do ideal" else "faltam ${Formatos.eur(-dif, 0)} para o ideal")
                    }
                }
            }
        } else {
            val setores = d.setores.filter { it.valorEur > 0 }.sortedByDescending { it.pesoAcoes ?: 0.0 }
            val escala = setores.maxOf { max(it.pesoAcoes ?: 0.0, it.limite ?: 0.0) }.coerceAtLeast(0.01) * 1.05
            TextoPequeno("Peso de cada setor nas ações; o traço é o limite da tab Distribuição.")
            setores.forEach { s ->
                val cor = if (s.acima) Paleta.vermelho else MaterialTheme.colorScheme.primary
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            nomeSetor(s.setor),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            Formatos.pct(s.pesoAcoes) + (s.limite?.let { " · limite ${Formatos.pct(it, 0)}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall.numeros(),
                            color = if (s.acima) Paleta.vermelho else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    BarraComMarca(s.pesoAcoes ?: 0.0, s.limite, escala, cor)
                }
            }
        }
    }
}

/** Nomes dos setores (GICS, em inglês na folha) em português. */
fun nomeSetor(setor: String): String = when (setor.trim().lowercase()) {
    "information technology", "technology" -> "Tecnologia"
    "healthcare", "health care" -> "Saúde"
    "financials", "financial services" -> "Financeiro"
    "consumer discretionary", "consumer cyclical" -> "Consumo discricionário"
    "communication services" -> "Comunicações"
    "consumer staples", "consumer defensive" -> "Consumo básico"
    "industrial", "industrials" -> "Indústria"
    "real estate" -> "Imobiliário"
    "energy" -> "Energia"
    "materials", "basic materials" -> "Materiais"
    "utilities" -> "Utilities"
    else -> setor
}

// ───────────────────────────── Dividendos ─────────────────────────────

@Composable
private fun CartaoDividendosResumo(d: Dados, vm: CarteiraViewModel) {
    val div = d.dividendos
    val ano = div.previsaoAno
    val anoAtual = d.meta.hoje.take(4).toIntOrNull()
    val porAno = div.anos.associateBy { it.ano }
    val esteAno = anoAtual?.let { porAno[it]?.totalUsd }
    val anoPassado = anoAtual?.let { porAno[it - 1]?.totalUsd }
    val bruto = ano?.let { div.indicador("BRUTO $it ($)")?.valor }
    val liquido = ano?.let { div.indicador("LÍQUIDO $it (€)")?.valor }
    val mes = d.meta.hoje.takeIf { it.length >= 7 }?.substring(5, 7)?.toIntOrNull()?.minus(1)?.takeIf { it in 0..11 }
    if (esteAno == null && bruto == null && div.calendario.isEmpty()) return

    Cartao("Dividendos", acao = "Ver tudo", aoAcao = { vm.irPara(Ecra.Dividendos) }) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                TextoPequeno("Recebido em ${anoAtual ?: ""}")
                Text(Formatos.usd(esteAno, 0), style = MaterialTheme.typography.titleMedium.numeros(), fontWeight = FontWeight.SemiBold)
                if (anoAtual != null && anoPassado != null) TextoPequeno("${anoAtual - 1}: ${Formatos.usd(anoPassado, 0)}")
            }
            if (ano != null && (bruto != null || liquido != null)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    TextoPequeno("Previsão $ano")
                    Text(
                        Formatos.eur(liquido, 0) + " líq.",
                        style = MaterialTheme.typography.titleMedium.numeros(),
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextoPequeno("${Formatos.usd(bruto, 0)} brutos")
                }
            }
        }
        if (div.calendario.size == 12) {
            BarrasMensais(
                previsto = div.calendario.map { it.previstoBrutoUsd ?: 0.0 },
                recebido = div.calendario.map { it.recebidoAnoAnteriorUsd },
                destacado = mes,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Legenda(MaterialTheme.colorScheme.primary, "Previsão ${ano ?: ""}")
                Legenda(Paleta.dourado, "Recebido em ${(ano ?: 1) - 1}")
            }
            val quem = mes?.let { div.calendario.getOrNull(it)?.quemPaga }
            if (mes != null && !quem.isNullOrBlank()) TextoPequeno("Costumam pagar em ${div.calendario[mes].mes.lowercase()}: $quem")
        }
    }
}

// ───────────────────────────── Oportunidades ─────────────────────────────

@Composable
private fun CartaoOportunidades(lista: List<ItemWatchlist>, vm: CarteiraViewModel) {
    Cartao("Oportunidades na watchlist", acao = "Ver todas", aoAcao = { vm.irPara(Ecra.Watchlist) }) {
        TextoPequeno(
            "${lista.size} abaixo do valor justo (🟢 Subvalorizada). Primeiro as que passam o teste de troca da folha, " +
                "pelo ganho por ano.",
        )
        lista.take(3).forEachIndexed { k, w ->
            if (k > 0) HorizontalDivider(color = corBordaCartao())
            Row(
                Modifier.fillMaxWidth().clickable { vm.abrir("W:${w.ticker}") }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.ticker, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        TextoPequeno(w.empresa ?: "", modifier = Modifier.weight(1f, fill = false))
                    }
                    w.testeTroca?.takeIf { it.startsWith("✓") }?.let { TextoPequeno(it, cor = Paleta.verde) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${Formatos.numero(w.preco)} → ${Formatos.numero(w.valorJusto)}",
                        style = MaterialTheme.typography.bodySmall.numeros(),
                    )
                    Text(
                        "margem ${Formatos.pctSinal(w.margem)}",
                        style = MaterialTheme.typography.labelMedium.numeros(),
                        color = corDoSinal(w.margem),
                    )
                }
            }
        }
    }
}

// ───────────────────────────── Desempenho ─────────────────────────────

@Composable
private fun CartaoDesempenho(d: Dados) {
    // (rótulo da folha, nome na app, é percentagem?)
    val linhas = listOf(
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                par.forEach { (nome, valor) ->
                    Column(Modifier.weight(1f)) {
                        TextoPequeno(nome)
                        Text(valor, style = MaterialTheme.typography.titleSmall.numeros(), fontWeight = FontWeight.SemiBold)
                    }
                }
                if (par.size == 1) Spacer(Modifier.weight(1f))
            }
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
                        "${t.tipo ?: ""} ${Formatos.numeroLivre(t.unidades)} ${t.ticker}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    TextoPequeno("${Formatos.data(t.data)} · a ${Formatos.moeda(t.preco, moeda)}")
                }
                Text(
                    Formatos.moeda(t.valor, moeda, 0),
                    style = MaterialTheme.typography.bodyMedium.numeros(),
                    color = if (t.tipo == "Venda") Paleta.verde else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Botão que liga os alertas (e pede a autorização das notificações no Android 13+). */
@Composable
fun BotaoAtivarAlertas(estado: Estado, vm: CarteiraViewModel, texto: String = "Ativar alertas") {
    val pedir = lembrarPedidoNotificacoes { concedida ->
        vm.definirAlertas(estado.defAlertas.copy(ativo = true))
        if (!concedida) vm.mostrar("Sem autorização para notificações: os alertas ficam só na app (no sino).")
    }
    Button(onClick = pedir) { Text(texto) }
}
