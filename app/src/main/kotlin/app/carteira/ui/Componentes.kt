@file:OptIn(ExperimentalMaterial3Api::class)

package app.carteira.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Cartão branco com contorno fino, título opcional e uma ação à direita do título ("Ver tudo"). */
@Composable
fun Cartao(
    titulo: String? = null,
    modifier: Modifier = Modifier,
    acao: String? = null,
    aoAcao: (() -> Unit)? = null,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = corCartao()),
        border = BorderStroke(1.dp, corBordaCartao()),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (titulo != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        titulo,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (acao != null && aoAcao != null) {
                        Text(
                            acao,
                            modifier = Modifier.clickable(onClick = aoAcao).padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            conteudo()
        }
    }
}

/** Cartão de uma linha de lista, que abre um detalhe ao tocar. */
@Composable
fun CartaoClicavel(aoTocar: () -> Unit, modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = aoTocar),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = corCartao()),
        border = BorderStroke(1.dp, corBordaCartao()),
        content = conteudo,
    )
}

/** Rótulo à esquerda, valor à direita. */
@Composable
fun LinhaValor(rotulo: String, valor: String, cor: Color = Color.Unspecified, destaque: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            rotulo,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            valor,
            style = (if (destaque) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium).numeros(),
            fontWeight = if (destaque) FontWeight.SemiBold else FontWeight.Normal,
            color = if (cor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else cor,
            textAlign = TextAlign.End,
        )
    }
}

/** Quadrado com um número grande, como os quadros do Dashboard. */
@Composable
fun Mosaico(rotulo: String, valor: String, modifier: Modifier = Modifier, detalhe: String? = null, corDetalhe: Color = Color.Unspecified) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = corCartao()),
        border = BorderStroke(1.dp, corBordaCartao()),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                rotulo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            // Sem cortes: com letra grande, o texto passa para a linha de baixo (os mosaicos lado a lado
            // ficam todos da altura do maior).
            Text(
                valor,
                style = MaterialTheme.typography.titleMedium.numeros(),
                fontWeight = FontWeight.SemiBold,
            )
            if (detalhe != null) {
                Text(
                    detalhe,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (corDetalhe == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else corDetalhe,
                )
            }
        }
    }
}

/** Linha de mosaicos todos da mesma altura (passa Modifier.weight(1f).fillMaxHeight() a cada um). */
@Composable
fun LinhaDeMosaicos(conteudo: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = conteudo,
    )
}

/** Selo colorido com a zona ou o sinal da folha (Comprar, Manter, Reduzir, Sair...). */
@Composable
fun SeloZona(zona: String?, modifier: Modifier = Modifier) {
    if (zona.isNullOrBlank()) return
    val e = estiloZona(zona)
    Surface(color = e.fundo, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            e.rotulo,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = e.texto,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Selo com a cor de um estado ("No limite", "Acima do plano", "🔴 Acima do limite"). */
@Composable
fun SeloEstado(texto: String, cor: Color, modifier: Modifier = Modifier) {
    Surface(color = cor.copy(alpha = 0.14f), shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            texto,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = cor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Fila de filtros que desliza na horizontal. */
@Composable
fun Filtros(opcoes: List<String>, escolhido: String, compacto: Boolean = false, aoEscolher: (String) -> Unit) {
    Row(
        (if (compacto) Modifier else Modifier.fillMaxWidth()).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(if (compacto) 6.dp else 8.dp),
    ) {
        opcoes.forEach { o ->
            FilterChip(
                selected = o == escolhido,
                onClick = { aoEscolher(o) },
                label = { Text(o, style = if (compacto) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge) },
                modifier = if (compacto) Modifier.height(30.dp) else Modifier,
            )
        }
    }
}

/** Aviso de erro com botão para tentar outra vez. */
@Composable
fun AvisoErro(mensagem: String, temDados: Boolean, aoTentar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                mensagem,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (temDados) {
                Text(
                    "Estás a ver os últimos dados guardados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = aoTentar) { Text("Tentar outra vez") }
        }
    }
}

@Composable
fun TextoPequeno(texto: String, modifier: Modifier = Modifier, cor: Color = Color.Unspecified) {
    Text(
        texto,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = if (cor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else cor,
    )
}

/** Lista que se atualiza ao puxar para baixo, comum aos quatro separadores. */
@Composable
fun ListaAtualizavel(
    estado: Estado,
    vm: CarteiraViewModel,
    estadoLista: LazyListState = rememberLazyListState(),
    conteudo: LazyListScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = estado.aCarregar,
        onRefresh = { vm.atualizar() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = estadoLista,
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
