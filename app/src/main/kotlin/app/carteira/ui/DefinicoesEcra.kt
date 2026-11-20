package app.carteira.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.carteira.BuildConfig
import app.carteira.dados.Reforco

@Composable
fun DefinicoesEcra(estado: Estado, vm: CarteiraViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Com a ponte ligada, o que se muda mais vem primeiro; a ligação fica para baixo.
        if (estado.configurado) {
            CartaoPreferencias(estado, vm)
            CartaoDefinicoesAlertas(estado, vm)
            CartaoPrivacidade(estado, vm)
            SeccaoLigacao(vm)
        } else {
            SeccaoLigacao(vm)
            CartaoPrivacidade(estado, vm)
        }
        Cartao("Atualizar a ponte") {
            Text(
                "Quando receberes uma versão nova do código da ponte (a versão 5 traz cada ação contra o S&P 500, a " +
                    "rentabilidade por ano e o simulador de venda, e fixa na folha o preço do S&P 500 de cada transação):",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextoPequeno("1. Na folha: Extensões > Apps Script. Apaga o código antigo, cola o novo e guarda.")
            TextoPequeno("2. Implementar > Gerir implementações > lápis (editar) > Versão: Nova versão > Implementar.")
            TextoPequeno("3. O link mantém-se: não precisas de mudar nada aqui.")
        }
        HorizontalDivider()
        val d = estado.dados
        if (d != null) {
            TextoPequeno("Folha: ${d.meta.folha} · ponte v${d.versaoPonte} · dados de ${Formatos.momento(d.meta.atualizadoEm)}")
            if (d.meta.avisos.isNotEmpty()) {
                TextoPequeno("A ponte não conseguiu ler: " + d.meta.avisos.joinToString("; "), cor = MaterialTheme.colorScheme.error)
            }
        }
        TextoPequeno("A chave e o link ficam só neste telemóvel. Versão ${BuildConfig.VERSION_NAME}.")
    }
}

/** O link e a chave da ponte, com os passos para a publicar. */
@Composable
private fun SeccaoLigacao(vm: CarteiraViewModel) {
    var url by rememberSaveable { mutableStateOf(vm.url) }
    var chave by rememberSaveable { mutableStateOf(vm.chave) }
    var aTestar by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    Cartao("Ligação à folha") {
        Text(
            "A app lê a folha através de uma pequena ponte (Apps Script) que publicas uma vez no computador:",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextoPequeno("1. Na folha: Extensões > Apps Script. Cola o código da ponte e guarda.")
        TextoPequeno("2. Implementar > Nova implementação > Aplicação Web. Executar como: Eu. Quem tem acesso: Qualquer pessoa.")
        TextoPequeno("3. Copia o URL da aplicação Web (acaba em /exec) e cola-o aqui, com a chave que vem no topo do código.")
    }
    OutlinedTextField(
        value = url,
        onValueChange = { url = it.trim() },
        label = { Text("Link da ponte (URL da aplicação Web)") },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 4,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
    OutlinedTextField(
        value = chave,
        onValueChange = { chave = it.trim() },
        label = { Text("Chave") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
    )
    Button(
        onClick = {
            aTestar = true
            erro = null
            vm.testarEGuardar(url, chave) { ok, mensagem ->
                aTestar = false
                if (ok) {
                    vm.mostrar(mensagem)
                    vm.voltar()
                } else {
                    erro = mensagem
                }
            }
        },
        enabled = !aTestar && url.isNotBlank() && chave.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (aTestar) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("A testar a ligação…")
        } else {
            Text("Testar e guardar")
        }
    }
    erro?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CartaoPrivacidade(estado: Estado, vm: CarteiraViewModel) {
    Cartao("Privacidade") {
        LinhaInterruptor(
            "Esconder os valores",
            "Os montantes em € e $ e as quantidades aparecem como •••; as percentagens e os preços ficam. " +
                "Também se liga no olho, no topo dos separadores.",
            estado.privado,
        ) { vm.alternarPrivacidade() }
    }
}

/**
 * O que facilita o dia a dia: o ecrã que abre ao entrar e o reforço do mês (o valor habitual, as frações e o
 * ETF que recebe o dinheiro novo).
 */
@Composable
private fun CartaoPreferencias(estado: Estado, vm: CarteiraViewModel) {
    val prefs = estado.prefs
    val d = estado.dados
    var valor by rememberSaveable { mutableStateOf(if (prefs.reforcoValor > 0) numeroParaEditar(prefs.reforcoValor) else "") }
    val lido = Formatos.lerNumero(valor)
    val erroValor = valor.isNotBlank() && (lido == null || lido < 0)
    Cartao("Preferências") {
        Text("Ao abrir a app", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        val inicial = ECRAS_INICIAIS.firstOrNull { it.second == prefs.ecraInicial }?.first ?: "Início"
        Filtros(ECRAS_INICIAIS.map { it.first }, inicial, quebrar = true) { nome ->
            ECRAS_INICIAIS.firstOrNull { it.first == nome }?.let { vm.definirPreferencias(prefs.copy(ecraInicial = it.second)) }
        }
        HorizontalDivider(color = corBordaCartao())
        Text("Reforço do mês", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        CampoNumero(
            valor,
            "Valor habitual",
            { novo ->
                valor = novo
                val v = Formatos.lerNumero(novo)
                if (novo.isBlank()) vm.definirPreferencias(estado.prefs.copy(reforcoValor = 0.0))
                else if (v != null && v >= 0) vm.definirPreferencias(estado.prefs.copy(reforcoValor = v))
            },
            if (erroValor) "Escreve um valor, por exemplo 1000" else null,
            "€",
        )
        val doPlano = d?.plano?.reforcoMensalEur
        TextoPequeno(
            if (doPlano != null && doPlano > 0) "Vazio: usa o reforço mensal do plano da folha (${Formatos.eur(doPlano, 0)})."
            else "O valor que aparece no Reforço do mês; muda-se lá só para um mês.",
        )
        LinhaInterruptor(
            "Frações de ações",
            "Compra partes de uma ação (Trading 212, Revolut…). Desligado, só unidades inteiras.",
            prefs.reforcoFracoes,
        ) { vm.definirFracoesReforco(it) }
        val etfs = d?.etfs?.filter { !Reforco.obrigacoes(it.nome) }?.map { it.ticker }.orEmpty()
        if (etfs.isNotEmpty()) {
            Text("ETF do dinheiro novo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val escolhido = prefs.etfReforco.takeIf { it in etfs } ?: AUTOMATICO
            Filtros(listOf(AUTOMATICO) + etfs, escolhido, quebrar = true) { e ->
                vm.definirPreferencias(prefs.copy(etfReforco = if (e == AUTOMATICO) "" else e))
            }
            TextoPequeno(
                "Automático: o maior ETF de ações do mundo todo (All-World, ACWI, Global…) ou, sem ele, o ETF de ações com " +
                    "mais valor. Os de obrigações ficam de fora.",
            )
        }
    }
}

private const val AUTOMATICO = "Automático"
