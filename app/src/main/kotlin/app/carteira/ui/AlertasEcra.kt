package app.carteira.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.carteira.alertas.Notificacoes

/** Histórico dos alertas, do mais recente para o mais antigo. */
@Composable
fun AlertasEcra(estado: Estado, vm: CarteiraViewModel) {
    LaunchedEffect(estado.alertas.firstOrNull()?.quando) { vm.marcarAlertasVistos() }
    if (estado.alertas.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Ainda não há alertas.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(8.dp))
            Text(
                if (estado.defAlertas.ativo) {
                    "Estão ligados: quando uma posição tiver sinal de compra, uma empresa da watchlist ficar abaixo do justo ou " +
                        "houver uma variação grande, aparecem aqui e no telemóvel."
                } else {
                    "Liga-os nas Definições para seres avisado dos sinais de compra, da watchlist e das variações grandes."
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(16.dp))
            Button(onClick = { vm.irPara(Ecra.Definicoes) }) { Text("Definições dos alertas") }
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Cartao {
                estado.alertas.forEachIndexed { k, a ->
                    if (k > 0) HorizontalDivider(color = corBordaCartao())
                    LinhaAlerta(a) { vm.abrir(a.chave) }
                }
            }
        }
        item {
            TextoPequeno(
                "Guardo os últimos ${app.carteira.alertas.ServicoAlertas.MAX_HISTORICO} alertas. Os preços vêm da folha " +
                    "(GOOGLEFINANCE, com até 20 minutos de atraso).",
            )
        }
    }
}

/**
 * Devolve uma ação que pede a autorização das notificações (Android 13+) e depois chama
 * "depois" com o resultado. Nas versões antigas não há pedido: chama logo.
 */
@Composable
fun lembrarPedidoNotificacoes(depois: (Boolean) -> Unit): () -> Unit {
    val contexto = LocalContext.current
    val lancador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida -> depois(concedida) }
    return {
        val falta = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (falta) lancador.launch(Manifest.permission.POST_NOTIFICATIONS) else depois(Notificacoes.podeNotificar(contexto))
    }
}

/** Definições dos alertas (no ecrã das Definições). */
@Composable
fun CartaoDefinicoesAlertas(estado: Estado, vm: CarteiraViewModel) {
    val def = estado.defAlertas
    val pedir = lembrarPedidoNotificacoes { concedida ->
        vm.definirAlertas(def.copy(ativo = true))
        if (!concedida) vm.mostrar("Sem autorização para notificações: os alertas ficam só na app (no sino).")
    }
    Cartao("Alertas") {
        LinhaInterruptor(
            "Avisar-me no telemóvel",
            "A app vai à folha de ${def.intervaloHoras} em ${def.intervaloHoras} horas e avisa quando algo muda.",
            def.ativo,
        ) { ligar -> if (ligar) pedir() else vm.definirAlertas(def.copy(ativo = false)) }
        if (def.ativo) {
            HorizontalDivider(color = corBordaCartao())
            LinhaInterruptor(
                "Sinal de compra nas posições",
                "Quando o SINAL da folha passa a 🟢 (já com as tuas regras: satélites só em 🟢🟢, apostas e residuais não contam).",
                def.compras,
            ) { vm.definirAlertas(def.copy(compras = it)) }
            LinhaInterruptor(
                "Watchlist abaixo do valor justo",
                "Quando uma empresa passa a 🟢 Subvalorizada, com o teste de troca da folha.",
                def.watchlist,
            ) { vm.definirAlertas(def.copy(watchlist = it)) }
            LinhaInterruptor(
                "Variações grandes de preço",
                "Num dia, nas posições, ETFs e watchlist. Outro aviso se chegar ao dobro.",
                def.variacao,
            ) { vm.definirAlertas(def.copy(variacao = it)) }
            if (def.variacao) {
                val opcoes = listOf(0.03, 0.05, 0.08, 0.10)
                Filtros(opcoes.map { "±" + Formatos.pct(it, 0) }, "±" + Formatos.pct(def.limiar, 0), compacto = true) { escolhido ->
                    opcoes.firstOrNull { "±" + Formatos.pct(it, 0) == escolhido }?.let { vm.definirAlertas(def.copy(limiar = it)) }
                }
            }
            LinhaInterruptor(
                "Sinal de reduzir ou sair",
                "Quando o sinal passa a 🟠 Reduzir ou 🔴 Sair.",
                def.vendas,
            ) { vm.definirAlertas(def.copy(vendas = it)) }
            HorizontalDivider(color = corBordaCartao())
            Text("Verificar a cada", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val horas = listOf(1, 2, 4)
            Filtros(horas.map { "$it h" }, "${def.intervaloHoras} h", compacto = true) { escolhido ->
                horas.firstOrNull { "$it h" == escolhido }?.let { vm.definirAlertas(def.copy(intervaloHoras = it)) }
            }
            LinhaInterruptor(
                "Só com os mercados abertos",
                "Dias úteis, das 8h às 22h30 (Europa e EUA). Poupa bateria e dados.",
                def.soMercadoAberto,
            ) { vm.definirAlertas(def.copy(soMercadoAberto = it)) }
            HorizontalDivider(color = corBordaCartao())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { vm.verificarAlertasAgora() }, enabled = !estado.aVerificarAlertas) {
                    if (estado.aVerificarAlertas) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Verificar agora")
                }
                OutlinedButton(onClick = { vm.notificacaoDeTeste() }) { Text("Testar aviso") }
            }
            TextoPequeno(
                "Os preços vêm da folha (GOOGLEFINANCE, até 20 min de atraso). Se os avisos não chegarem, nas " +
                    "definições do Android põe a bateria da app Carteira em \"Sem restrições\".",
            )
        }
        if (estado.alertas.isNotEmpty()) {
            TextButton(onClick = { vm.irPara(Ecra.Alertas) }) { Text("Ver os alertas (${estado.alertas.size})") }
        }
    }
}

@Composable
private fun LinhaInterruptor(titulo: String, detalhe: String, ligado: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            TextoPequeno(detalhe)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = ligado, onCheckedChange = aoMudar)
    }
}
