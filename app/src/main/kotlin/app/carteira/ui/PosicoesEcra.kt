@file:OptIn(ExperimentalMaterial3Api::class)

package app.carteira.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.carteira.dados.BaseDosPesos
import app.carteira.dados.BasePesos
import app.carteira.dados.Dados
import app.carteira.dados.Gatilhos
import app.carteira.dados.Historico
import app.carteira.dados.ParteDoPatrimonio
import app.carteira.dados.Pesos
import app.carteira.dados.Sinais
import kotlin.math.max

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

/** As ordens da lista; a escolhida fica guardada para a próxima vez. */
internal val ORDENS_POSICOES = listOf("Valor", "Ganho", "Hoje", "Peso vs alvo", "Sinal", "A–Z")

/** Cartões com tudo, ou só o peso de cada posição numa barra (também fica guardada). */
internal const val VISTA_LISTA = "Lista"
internal const val VISTA_PESOS = "Pesos"
internal val VISTAS_POSICOES = listOf(VISTA_LISTA, VISTA_PESOS)

/** Uma parte da base no topo da vista Pesos (uma camada, os ETFs ou o resto do património). */
private data class Bloco(val nome: String, val peso: Double, val cor: Color)

/** Na ordem "Sinal": primeiro o que pede para agir (sair, reduzir, comprar forte, comprar), os ETF no fim. */
private fun prioridadeSinal(l: Linha): Int = when {
    l.etf -> 9
    Sinais.sair(l.sinal) -> 0
    Sinais.reduzir(l.sinal) -> 1
    Sinais.compraForte(l.sinal) -> 2
    Sinais.compra(l.sinal) -> 3
    Sinais.emZona(l.sinal) -> 4
    else -> 5
}

@Composable
fun PosicoesEcra(estado: Estado, vm: CarteiraViewModel) {
    val d = estado.dados ?: return SemDados(estado, vm)
    var procura by rememberSaveable { mutableStateOf("") }
    var filtro by rememberSaveable { mutableStateOf("Todas") }
    val ordem = estado.prefs.ordemPosicoes.takeIf { it in ORDENS_POSICOES } ?: "Valor"
    val vista = estado.prefs.vistaPosicoes.takeIf { it in VISTAS_POSICOES } ?: VISTA_LISTA

    val acoesEur = d.totaisAcoes?.valorEur ?: d.posicoes.sumOf { it.valorEur ?: 0.0 }
    val etfsEur = d.totaisEtfs?.valorEur ?: d.etfs.sumOf { it.valorEur ?: 0.0 }
    val totalCarteira = acoesEur + etfsEur
    // A base dos pesos (só na vista Pesos): só as ações, ações + ETFs (a das regras) ou o património todo.
    val base = if (vista == VISTA_PESOS) {
        Pesos.base(BasePesos.de(estado.prefs.basePesos), acoesEur, etfsEur, d.patrimonio?.classes)
            ?: Pesos.base(BasePesos.CARTEIRA, acoesEur, etfsEur, null)
    } else {
        null
    }
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
    val maximos = d.posicoes.associate { it.ticker to it.pesoMaximo }
    // As cores das camadas são as da Alocação do Início (pela mesma ordem); os ETFs ficam a cinzento.
    val ordemCamadas = d.camadas.filter { (it.pesoHoje ?: 0.0) > 0 || (it.pesoIdeal ?: 0.0) > 0 }.map { it.camada }
    val indiceCamada = { camada: String ->
        ordemCamadas.indexOfFirst { it == camada || (camada.startsWith("Sair") && it.startsWith("Sair")) }
    }
    val corDaCamada = { camada: String ->
        val k = indiceCamada(camada)
        if (k >= 0) Paleta.categoria(k) else Paleta.cinzento
    }

    val camadas = listOf("Core A", "Core B", "Satélite", "Aposta").filter { c -> linhas.any { it.camada == c } } +
        linhas.map { it.camada }.distinct().filter { it !in listOf("Core A", "Core B", "Satélite", "Aposta", "ETFs", "—") }.sorted()
    // Os filtros seguem o SINAL da folha (as tuas regras), não só a zona do preço.
    val opcoes = listOf("Todas", FILTRO_COMPRA, FILTRO_REDUZIR, FILTRO_SAIR) + camadas + listOf("ETFs")

    val filtroAtivo = filtro.takeIf { it in opcoes } ?: "Todas"
    val termo = procura.trim().lowercase()
    val visiveis = linhas.filter { l ->
        (termo.isEmpty() || l.ticker.lowercase().contains(termo) || l.nome.lowercase().contains(termo)) &&
            when (filtroAtivo) {
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
            "Sinal" -> lista.sortedWith(compareBy<Linha> { prioridadeSinal(it) }.thenByDescending { it.valorEur ?: 0.0 })
            "A–Z" -> lista.sortedBy { it.ticker.lowercase() }
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
        item { Filtros(opcoes, filtroAtivo) { filtro = it } }
        // Na base "Ações" os ETFs saem; os pesos, alvos e máximos passam a % da base (o mesmo valor em euros).
        val naBase = if (base == null) visiveis else visiveis.filter { base.incluiEtfs || !it.etf }
        item {
            val soma = naBase.sumOf { it.valorEur ?: 0.0 }
            val totalDaBase = base?.totalEur ?: totalCarteira
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextoPequeno("Ver")
                    Spacer(Modifier.width(8.dp))
                    Filtros(VISTAS_POSICOES, vista, compacto = true) { vm.escolherVistaPosicoes(it) }
                }
                if (base != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextoPequeno("Base")
                        Spacer(Modifier.width(8.dp))
                        Filtros(BasePesos.ROTULOS, base.base.rotulo, compacto = true) { vm.escolherBasePesos(it) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextoPequeno(
                        "${naBase.size} ${if (naBase.size == 1) "posição" else "posições"} · ${Formatos.eur(soma, 0)}" +
                            if (totalDaBase > 0) " · ${Formatos.pct(soma / totalDaBase)} ${base?.base?.nome ?: "da carteira"}" else "",
                        modifier = Modifier.weight(1f),
                    )
                    MenuOrdem(ordem) { vm.escolherOrdemPosicoes(it) }
                }
            }
        }
        val abrir = { l: Linha -> vm.irPara(if (l.etf) Ecra.Etf(l.ticker) else Ecra.Posicao(l.ticker)) }
        if (base != null) {
            // O resto do património (base Tudo) só sem filtro; a procura também o encontra pelo nome.
            val outras = if (filtroAtivo != "Todas") {
                emptyList()
            } else {
                base.outras.filter { base.peso(it.valorEur) >= 0.001 && (termo.isEmpty() || it.nome.lowercase().contains(termo)) }
            }
            val composicao = composicaoDaBase(linhas, base, ordemCamadas, indiceCamada, corDaCamada)
            if (naBase.isEmpty() && outras.isEmpty()) {
                if (visiveis.any { it.etf } && !base.incluiEtfs) {
                    item(key = "pesos") {
                        Cartao { TextoPequeno("Os ETFs não entram na base «Ações». Escolhe «Ações + ETFs» ou «Tudo».") }
                    }
                }
            } else {
                item(key = "pesos") {
                    CartaoPesos(
                        linhas = naBase.map { it.copy(peso = base.naBase(it.peso)) },
                        outras = outras,
                        base = base,
                        composicao = composicao,
                        alvos = alvos.mapValues { base.naBase(it.value) },
                        maximos = maximos.mapValues { base.naBase(it.value) },
                        corDaCamada = corDaCamada,
                        aoTocar = abrir,
                    )
                }
            }
        } else {
            items(visiveis, key = { it.ticker }) { l ->
                CartaoLinha(l, alvos[l.ticker]) { abrir(l) }
            }
        }
    }
}

/** "Ordenar: Valor ▾": abre a lista das ordens, com a escolhida marcada. */
@Composable
private fun MenuOrdem(ordem: String, aoEscolher: (String) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { aberto = true }) { Text("Ordenar: $ordem ▾") }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            ORDENS_POSICOES.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o, fontWeight = if (o == ordem) FontWeight.SemiBold else FontWeight.Normal) },
                    trailingIcon = if (o == ordem) { { Text("✓") } } else null,
                    onClick = {
                        aberto = false
                        aoEscolher(o)
                    },
                )
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

/**
 * O topo da vista Pesos: a base inteira (sem filtro) numa barra, pelas cores das camadas, e o texto que a
 * resume. Nas ações, o peso de cada camada; com os ETFs, ações contra ETFs; no Tudo, também o resto.
 */
private fun composicaoDaBase(
    linhas: List<Linha>,
    base: BaseDosPesos,
    ordemCamadas: List<String>,
    indiceCamada: (String) -> Int,
    corDaCamada: (String) -> Color,
): Pair<List<Bloco>, String> {
    val camadas = linhas.filter { !it.etf }
        .groupBy { l -> ordemCamadas.getOrNull(indiceCamada(l.camada)) ?: l.camada }
        .map { (nome, ls) -> Bloco(nome, ls.sumOf { base.naBase(it.peso) ?: 0.0 }, corDaCamada(nome)) }
        .filter { it.peso > 0 }
        .sortedBy { b -> indiceCamada(b.nome).let { if (it < 0) Int.MAX_VALUE else it } }
    val etfs = Bloco("ETFs", base.peso(base.etfsEur), corDaCamada("ETFs"))
    // O resto a cinzento claro, para não se confundir com os ETFs se estes também ficarem a cinzento.
    val resto = Bloco("resto do património", base.peso(base.outrasEur), Paleta.cinzento.copy(alpha = 0.5f))
    val barra = camadas + (if (base.incluiEtfs && etfs.peso > 0) listOf(etfs) else emptyList()) +
        (if (resto.peso > 0) listOf(resto) else emptyList())
    val partes = when (base.base) {
        BasePesos.ACOES -> camadas
        BasePesos.CARTEIRA -> listOf(Bloco("Ações", base.peso(base.acoesEur), Paleta.cinzento), etfs)
        BasePesos.TUDO -> listOf(Bloco("Ações", base.peso(base.acoesEur), Paleta.cinzento), etfs, resto).filter { it.peso > 0 }
    }
    return barra to partes.joinToString(" · ") { "${it.nome} ${Formatos.pct(it.peso)}" }
}

/**
 * A vista Pesos: o peso de cada posição (e ETF) na base escolhida, numa barra à mesma escala, com o traço no
 * peso máximo da camada e o alvo ao lado (os da folha, convertidos para a base). O peso fica âmbar acima do
 * máximo e vermelho acima de 1,25 × o máximo (onde as regras mandam aparar). Tocar abre a posição. Na base
 * Tudo, o resto do património vem no fim, sem barra (não tem alvo nem máximo).
 */
@Composable
private fun CartaoPesos(
    linhas: List<Linha>,
    outras: List<ParteDoPatrimonio>,
    base: BaseDosPesos,
    composicao: Pair<List<Bloco>, String>,
    alvos: Map<String, Double?>,
    maximos: Map<String, Double?>,
    corDaCamada: (String) -> Color,
    aoTocar: (Linha) -> Unit,
) {
    val pesos = linhas.mapNotNull { it.peso }.sortedDescending()
    val escala = (linhas.maxOfOrNull { l -> max(l.peso ?: 0.0, maximos[l.ticker] ?: 0.0) } ?: 0.0).coerceAtLeast(0.01) * 1.05
    Cartao {
        val (barra, resumo) = composicao
        if (barra.isNotEmpty()) {
            BarraEmpilhada(barra.map { it.peso to it.cor }, altura = 10.dp)
            TextoPequeno(resumo)
        }
        if (pesos.size > 5) {
            val dez = if (pesos.size > 10) " · as 10 maiores: ${Formatos.pct(pesos.take(10).sum())}" else ""
            TextoPequeno("As 5 maiores: ${Formatos.pct(pesos.take(5).sum())}$dez ${base.base.nome}")
        }
        linhas.forEach { l ->
            LinhaPeso(l, alvos[l.ticker], maximos[l.ticker], escala, corDaCamada(l.camada)) { aoTocar(l) }
        }
        if (outras.isNotEmpty()) {
            Text(
                "Resto do património",
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            outras.forEach { LinhaParte(it, base.peso(it.valorEur)) }
        }
        if (base.semPatrimonio) {
            TextoPequeno("A ponte não trouxe o património da folha: «Tudo» fica igual a ações + ETFs.")
        }
        val legenda = when (base.base) {
            BasePesos.CARTEIRA -> "A barra é o peso na carteira e o traço o máximo da camada; ao lado, o alvo. "
            BasePesos.ACOES ->
                "A barra é o peso nas ações (sem os ETFs). O traço (máximo da camada) e o alvo são os da folha, " +
                    "convertidos para esta base com o mesmo valor em euros. "
            BasePesos.TUDO ->
                "A barra é o peso no património todo. O traço (máximo da camada) e o alvo são os da folha, " +
                    "convertidos para esta base com o mesmo valor em euros; o resto é o último saldo da tab Investimentos. "
        }
        TextoPequeno(legenda + "Âmbar: acima do máximo · vermelho: acima de 1,25 × o máximo (aparar).")
    }
}

/** Uma parte do património que não é ação nem ETF (base Tudo): o nome, o valor e o peso, sem barra. */
@Composable
private fun LinhaParte(p: ParteDoPatrimonio, peso: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            p.nome,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            Formatos.eur(p.valorEur, 0),
            style = MaterialTheme.typography.labelSmall.numeros(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            Formatos.pct(peso),
            style = MaterialTheme.typography.bodyMedium.numeros(),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LinhaPeso(l: Linha, alvo: Double?, maximo: Double?, escala: Double, cor: Color, aoTocar: () -> Unit) {
    val peso = l.peso
    val teto = maximo?.takeIf { it > 0 }
    val corPeso = when {
        peso == null || teto == null -> MaterialTheme.colorScheme.onSurface
        peso > teto * 1.25 -> legivel(Paleta.vermelho)
        peso > teto -> legivel(Paleta.ambar)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        Modifier.fillMaxWidth().clickable(onClick = aoTocar).padding(vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(l.ticker, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.width(6.dp))
            Text(
                l.camada,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Formatos.pct(peso),
                style = MaterialTheme.typography.bodyMedium.numeros(),
                fontWeight = FontWeight.SemiBold,
                color = corPeso,
            )
            if (alvo != null && alvo > 0) {
                Text(
                    " · alvo ${Formatos.pct(alvo)}",
                    style = MaterialTheme.typography.labelSmall.numeros(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        BarraComMarca(peso ?: 0.0, teto, escala, cor, altura = 8.dp)
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
