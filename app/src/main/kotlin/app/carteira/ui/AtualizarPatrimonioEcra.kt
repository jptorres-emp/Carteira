package app.carteira.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.carteira.dados.AnalisePatrimonio
import java.time.LocalDate
import kotlin.math.abs

/**
 * Grava o património (net worth) de um mês na coluna Net Worth (€) da tab Evolução Patrimonial — o que
 * se fazia à mão com o "Preenchimento rápido". Dá para escolher um dos últimos meses e corrigir um valor.
 */
@Composable
fun AtualizarPatrimonioEcra(estado: Estado, vm: CarteiraViewModel, ecra: Ecra.AtualizarPatrimonio) {
    val d = estado.dados ?: return SemDados(estado, vm)
    val hoje = d.meta.hoje.take(7).takeIf { it.length == 7 } ?: LocalDate.now().toString().take(7)
    val meses = (0L..5L).map { AnalisePatrimonio.somarMeses(hoje, -it) }
    var mes by rememberSaveable { mutableStateOf(ecra.mes?.takeIf { it in meses } ?: hoje) }
    val porMes = d.evolucao.associateBy { it.mes }
    val agora = d.patrimonio?.totalEur ?: d.indicador("PATRIMÓNIO TOTAL (NET WORTH)")?.valor
    val naFolha = porMes[mes]?.netWorthFolhaEur
    // Começa com o que está na folha; se o mês estiver vazio, com o valor de agora (no mês corrente ou no
    // que acabou de fechar). No modo privado, o campo começa vazio.
    val sugestao = naFolha ?: agora.takeIf { mes == hoje || mes == meses[1] }
    var valor by rememberSaveable(mes) {
        mutableStateOf(if (sugestao == null || estado.privado) "" else Formatos.paraCampo(sugestao))
    }
    val v = Formatos.lerNumero(valor)
    val erro = if (valor.isNotBlank() && (v == null || v <= 0)) "Valor inválido" else null
    // Um aviso (não impede) quando o valor é muito diferente do de referência: apanha um zero a mais.
    val referencia = naFolha ?: agora
    val estranho = v != null && v > 0 && referencia != null && referencia > 0 && abs(v / referencia - 1) > 0.25

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Grava o teu património total (net worth) de um mês na folha, como no fim de cada mês.",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextoPequeno("Mês")
        Filtros(meses.map { mesCurto(it) }, mesCurto(mes)) { rotulo -> meses.firstOrNull { mesCurto(it) == rotulo }?.let { mes = it } }
        Cartao {
            LinhaValor("Na folha, em ${mesLongo(mes)}", naFolha?.let { Formatos.eur(it, 2) } ?: "vazio")
            if (agora != null) LinhaValor("Agora (tab Investimentos)", Formatos.eur(agora, 2))
        }
        CampoNumero(valor, "Património em ${mesLongo(mes)}", { valor = it }, erro = erro, sufixo = "€")
        if (agora != null) {
            AssistChip(onClick = { valor = Formatos.paraCampo(agora) }, label = { Text("Usar o valor de agora") })
        }
        if (estranho) {
            TextoPequeno(
                "Atenção: é muito diferente de ${Formatos.eur(referencia, 0)}. Confirma se não falta ou sobra um algarismo.",
                cor = Paleta.ambar,
            )
        }
        BotaoEnviar("Gravar na folha", v != null && v > 0, estado.aEnviar) {
            vm.registarPatrimonio(mes, v!!) { ok -> if (ok) vm.voltar() }
        }
        TextoPequeno(
            "Fica na coluna Net Worth (€) da tab Evolução Patrimonial, na linha de ${mesLongo(mes)}, com uma nota. " +
                "Podes gravar outra vez: o valor é substituído. Enquanto o mês não acaba, a app mostra o valor de agora.",
        )
        Spacer(Modifier.size(80.dp))
    }
}
