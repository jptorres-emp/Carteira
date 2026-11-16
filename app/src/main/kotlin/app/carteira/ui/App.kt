@file:OptIn(ExperimentalMaterial3Api::class)

package app.carteira.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.carteira.R

private data class Separador(val ecra: Ecra, val rotulo: String, val icone: Int)

private val separadores = listOf(
    Separador(Ecra.Resumo, "Resumo", R.drawable.ic_resumo),
    Separador(Ecra.Posicoes, "Posições", R.drawable.ic_posicoes),
    Separador(Ecra.Dividendos, "Dividendos", R.drawable.ic_dividendos),
    Separador(Ecra.Watchlist, "Watchlist", R.drawable.ic_watchlist),
)

@Composable
fun AppCarteira(vm: CarteiraViewModel = viewModel()) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }

    LaunchedEffect(estado.mensagem) {
        val m = estado.mensagem ?: return@LaunchedEffect
        vm.mensagemMostrada()
        avisos.showSnackbar(m, withDismissAction = true, duration = SnackbarDuration.Long)
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
                        IconButton(onClick = { vm.atualizar() }, enabled = !estado.aCarregar) {
                            Icon(painterResource(R.drawable.ic_atualizar), contentDescription = "Atualizar")
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
                NavigationBar {
                    separadores.forEach { s ->
                        NavigationBarItem(
                            selected = ecra == s.ecra,
                            onClick = { vm.irPara(s.ecra) },
                            icon = { Icon(painterResource(s.icone), contentDescription = null) },
                            label = { Text(s.rotulo) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (emSeparador && ecra != Ecra.Watchlist && dados != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val modo = if (ecra == Ecra.Dividendos) ModoRegisto.DIVIDENDO else ModoRegisto.TRANSACAO
                        vm.irPara(Ecra.Registar(modo))
                    },
                    // O Material 3 tira o texto deste botão da acessibilidade; a descrição vai no ícone.
                    icon = { Icon(painterResource(R.drawable.ic_adicionar), contentDescription = "Registar") },
                    text = { Text("Registar") },
                )
            }
        },
        snackbarHost = { SnackbarHost(avisos) },
    ) { margens ->
        Box(Modifier.padding(margens).fillMaxSize()) {
            when (ecra) {
                Ecra.Resumo -> ResumoEcra(estado, vm)
                Ecra.Posicoes -> PosicoesEcra(estado, vm)
                Ecra.Dividendos -> DividendosEcra(estado, vm)
                Ecra.Watchlist -> WatchlistEcra(estado, vm)
                is Ecra.Posicao -> PosicaoDetalheEcra(estado, vm, ecra.ticker)
                is Ecra.Etf -> EtfDetalheEcra(estado, vm, ecra.ticker)
                is Ecra.Registar -> RegistarEcra(estado, vm, ecra)
                Ecra.Definicoes -> DefinicoesEcra(estado, vm)
            }
            if (estado.aCarregar && !emSeparador) {
                LinearProgressIndicator(modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

private fun tituloDe(ecra: Ecra, estado: Estado): String = when (ecra) {
    Ecra.Resumo -> "Carteira"
    Ecra.Posicoes -> "Posições"
    Ecra.Dividendos -> "Dividendos"
    Ecra.Watchlist -> "Watchlist"
    is Ecra.Posicao -> estado.dados?.posicoes?.firstOrNull { it.ticker == ecra.ticker }?.nome ?: ecra.ticker
    is Ecra.Etf -> ecra.ticker
    is Ecra.Registar -> if (ecra.modo == ModoRegisto.DIVIDENDO) "Registar dividendo" else "Registar compra ou venda"
    Ecra.Definicoes -> "Ligação à folha"
}
