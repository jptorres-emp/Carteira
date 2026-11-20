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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.carteira.R
import app.carteira.alertas.Alerta
import app.carteira.alertas.TipoAlerta
import app.carteira.dados.Dados
import app.carteira.dados.Sinais
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * Ecrã inicial: só o estado atual da carteira — valor e o dia de hoje, alertas, o que precisa de
 * atenção, quem mais mexeu hoje e a alocação. A evolução e o desempenho estão no separador Evolução.
 */
@Composable
fun InicioEcra(estado: Estado, vm: CarteiraViewModel) {
    val d = estado.dados ?: return SemDados(estado, vm)
    val atencao = itensAtencao(d)
    val recentes = estado.alertas.filter { recente(it.quando, 3) }.take(3)

    ListaAtualizavel(estado, vm) {
        item { CartaoPrincipal(d) }
        item { LinhaAtalhos(vm) }
        if (estado.conviteAlertas) item { ConviteAlertas(estado, vm) }
        if (recentes.isNotEmpty()) item { CartaoAlertasRecentes(recentes, estado.alertasNaoVistos, vm) }
        if (atencao.isNotEmpty()) item { CartaoAtencao(atencao, vm) }
        item { CartaoHoje(d, vm) }
        item { CartaoAlocacao(d) }
        item {
            TextoPequeno(
                "Dados da folha de ${Formatos.momento(d.meta.atualizadoEm)}. Puxa para baixo para atualizar. " +
                    "A evolução, o desempenho e os objetivos estão no separador Evolução.",
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
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

/** Um atalho do Início: ícone num círculo e o nome por baixo. */
private data class Atalho(val icone: Int, val rotulo: String, val descricao: String, val destino: Ecra)

private val ATALHOS = listOf(
    Atalho(R.drawable.ic_reforco, "Reforço", "Reforço do mês", Ecra.Reforco),
    Atalho(R.drawable.ic_adicionar, "Registar", "Registar compra ou venda", Ecra.Registar(ModoRegisto.TRANSACAO)),
    Atalho(R.drawable.ic_dividendos, "Dividendo", "Registar dividendo", Ecra.Registar(ModoRegisto.DIVIDENDO)),
    Atalho(R.drawable.ic_vendas, "Vendas", "Vendas e mais-valias", Ecra.Vendas),
    Atalho(R.drawable.ic_indice, "S&P 500", "Cada ação contra o S&P 500", Ecra.Indice),
)

/**
 * O que se faz todos os meses, a um toque: o reforço, registar uma compra ou um dividendo, as vendas e a
 * comparação com o S&P 500. Os mesmos atalhos aparecem ao carregar sem largar no ícone da app.
 */
@Composable
private fun LinhaAtalhos(vm: CarteiraViewModel) {
    val estilo = estiloQueCabe(ATALHOS.map { it.rotulo })
    Row(Modifier.fillMaxWidth()) {
        ATALHOS.forEach { a ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = a.descricao) { vm.irPara(a.destino) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(a.icone),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Text(a.rotulo, style = estilo, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
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
            Text(simbolo, style = MaterialTheme.typography.labelMedium, color = legivel(cor))
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
            listOfNotNull(p.nome, p.precoUsd?.let { Formatos.preco(it, "USD") }, p.zona?.let { "zona ${Sinais.semEmoji(it)}" }).joinToString(" · "),
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
                            color = if (s.acima) legivel(Paleta.vermelho) else MaterialTheme.colorScheme.onSurface,
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

/** Botão que liga os alertas (e pede a autorização das notificações no Android 13+). */
@Composable
fun BotaoAtivarAlertas(estado: Estado, vm: CarteiraViewModel, texto: String = "Ativar alertas") {
    val pedir = lembrarPedidoNotificacoes { concedida ->
        vm.definirAlertas(estado.defAlertas.copy(ativo = true))
        if (!concedida) vm.mostrar("Sem autorização para notificações: os alertas ficam só na app (no sino).")
    }
    Button(onClick = pedir) { Text(texto) }
}
