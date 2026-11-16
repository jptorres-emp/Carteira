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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.carteira.BuildConfig

@Composable
fun DefinicoesEcra(estado: Estado, vm: CarteiraViewModel) {
    var url by rememberSaveable { mutableStateOf(vm.url) }
    var chave by rememberSaveable { mutableStateOf(vm.chave) }
    var aTestar by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cartao("Como ligar a app à folha") {
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
        HorizontalDivider()
        val d = estado.dados
        if (d != null) {
            TextoPequeno("Folha: ${d.meta.folha} · dados de ${Formatos.momento(d.meta.atualizadoEm)}")
        }
        TextoPequeno("A chave e o link ficam só neste telemóvel. Versão ${BuildConfig.VERSION_NAME}.")
    }
}
