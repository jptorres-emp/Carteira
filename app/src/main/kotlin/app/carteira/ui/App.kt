@file:OptIn(ExperimentalMaterial3Api::class)

package app.carteira.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.carteira.R

private data class Separador(val ecra: Ecra, val rotulo: String, val icone: Int)

private val separadores = listOf(
    Separador(Ecra.Resumo, "Início", R.drawable.ic_resumo),
    Separador(Ecra.Evolucao, "Evolução", R.drawable.ic_evolucao),
    Separador(Ecra.Posicoes, "Posições", R.drawable.ic_posicoes),
    Separador(Ecra.Dividendos, "Dividendos", R.drawable.ic_dividendos),
    Separador(Ecra.Watchlist, "Watchlist", R.drawable.ic_watchlist),
)

@Composable
fun AppCarteira(vm: CarteiraViewModel = viewModel(), abrir: String? = null, aoAbrir: () -> Unit = {}) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }

    // Toque numa notificação: abre a empresa (ou a lista de alertas).
    LaunchedEffect(abrir) {
        if (abrir != null) {
            vm.abrir(abrir)
            aoAbrir()
        }
    }

    // A mensagem só é apagada depois de o aviso fechar. Apagá-la antes mudava a chave deste efeito,
    // que era cancelado e levava o aviso consigo logo a seguir a aparecer.
    LaunchedEffect(estado.mensagem) {
        val m = estado.mensagem ?: return@LaunchedEffect
        avisos.showSnackbar(m, withDismissAction = true, duration = SnackbarDuration.Long)
        vm.mensagemMostrada(m)
    }

    BackHandler(enabled = estado.pilha.size > 1) { vm.voltar() }

    val ecra = estado.ecra
    val emSeparador = estado.pilha.size == 1
    val dados = estado.dados

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tituloDe(ecra, estado), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (!emSeparador) {
                        IconButton(onClick = { vm.voltar() }) {
                            Icon(painterResource(R.drawable.ic_voltar), contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    if (emSeparador) {
                        IconButton(onClick = { vm.alternarPrivacidade() }) {
                            Icon(
                                painterResource(if (estado.privado) R.drawable.ic_oculto else R.drawable.ic_visivel),
                                contentDescription = if (estado.privado) "Mostrar valores" else "Esconder valores",
                            )
                        }
                        // Registar fica aqui em cima: um botão a flutuar tapava os valores à direita das listas.
                        val modo = when (ecra) {
                            Ecra.Resumo, Ecra.Posicoes -> ModoRegisto.TRANSACAO
                            Ecra.Dividendos -> ModoRegisto.DIVIDENDO
                            else -> null
                        }
                        if (modo != null && dados != null) {
                            IconButton(onClick = { vm.irPara(Ecra.Registar(modo)) }) {
                                Icon(painterResource(R.drawable.ic_adicionar), contentDescription = "Registar")
                            }
                        }
                        IconButton(onClick = { vm.irPara(Ecra.Alertas) }) {
                            BadgedBox(
                                badge = {
                                    if (estado.alertasNaoVistos > 0) Badge { Text(estado.alertasNaoVistos.coerceAtMost(99).toString()) }
                                },
                            ) {
                                Icon(painterResource(R.drawable.ic_alertas), contentDescription = "Alertas")
                            }
                        }
                        IconButton(onClick = { vm.irPara(Ecra.Definicoes) }) {
                            Icon(painterResource(R.drawable.ic_definicoes), contentDescription = "Definições")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (emSeparador) {
                // Os rótulos não crescem além de 1,1× com a letra grande do telemóvel: cinco não cabiam e
                // ficavam cortados ("Divide…"). O resto da app segue o tamanho escolhido.
                val escala = LocalDensity.current.fontScale
                val estiloRotulo = MaterialTheme.typography.labelMedium.let { e ->
                    if (escala > 1.1f) e.copy(fontSize = e.fontSize * (1.1f / escala)) else e
                }
                NavigationBar {
                    separadores.forEach { s ->
                        NavigationBarItem(
                            selected = ecra == s.ecra,
                            onClick = { vm.irPara(s.ecra) },
                            icon = { Icon(painterResource(s.icone), contentDescription = null) },
                            // Uma só linha: com letra grande, cinco separadores não cabem em duas.
                            label = { Text(s.rotulo, style = estiloRotulo, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { margens ->
        Box(Modifier.padding(margens).fillMaxSize()) {
            when (ecra) {
                Ecra.Resumo -> InicioEcra(estado, vm)
                Ecra.Evolucao -> EvolucaoEcra(estado, vm)
                Ecra.Posicoes -> PosicoesEcra(estado, vm)
                Ecra.Dividendos -> DividendosEcra(estado, vm)
                Ecra.Watchlist -> WatchlistEcra(estado, vm)
                is Ecra.Posicao -> PosicaoDetalheEcra(estado, vm, ecra.ticker)
                is Ecra.Etf -> EtfDetalheEcra(estado, vm, ecra.ticker)
                is Ecra.Registar -> RegistarEcra(estado, vm, ecra)
                is Ecra.AtualizarPatrimonio -> AtualizarPatrimonioEcra(estado, vm, ecra)
                Ecra.Definicoes -> DefinicoesEcra(estado, vm)
                Ecra.Alertas -> AlertasEcra(estado, vm)
            }
            if (estado.aCarregar && !emSeparador) {
                LinearProgressIndicator(modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

private fun tituloDe(ecra: Ecra, estado: Estado): String = when (ecra) {
    Ecra.Resumo -> "Carteira"
    Ecra.Evolucao -> "Evolução"
    Ecra.Posicoes -> "Posições"
    Ecra.Dividendos -> "Dividendos"
    Ecra.Watchlist -> "Watchlist"
    is Ecra.Posicao -> estado.dados?.posicoes?.firstOrNull { it.ticker == ecra.ticker }?.nome ?: ecra.ticker
    is Ecra.Etf -> ecra.ticker
    is Ecra.Registar -> if (ecra.modo == ModoRegisto.DIVIDENDO) "Registar dividendo" else "Registar compra ou venda"
    is Ecra.AtualizarPatrimonio -> "Atualizar património"
    Ecra.Definicoes -> "Definições"
    Ecra.Alertas -> "Alertas"
}
