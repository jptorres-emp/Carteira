@file:OptIn(ExperimentalMaterial3Api::class)

package app.carteira.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Cartão com título opcional, usado em todos os ecrãs. */
@Composable
fun Cartao(titulo: String? = null, modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (titulo != null) {
                Text(
                    titulo,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            conteudo()
        }
    }
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
            style = if (destaque) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                rotulo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Text(valor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (detalhe != null) {
                Text(
                    detalhe,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (corDetalhe == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else corDetalhe,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Selo colorido com a zona da folha (Comprar, Manter, Reduzir, Vender...). */
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

/** Fila de filtros que desliza na horizontal. */
@Composable
fun Filtros(opcoes: List<String>, escolhido: String, aoEscolher: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        opcoes.forEach { o ->
            FilterChip(selected = o == escolhido, onClick = { aoEscolher(o) }, label = { Text(o) })
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
