package app.carteira.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.carteira.dados.CompraSugerida
import app.carteira.dados.Dados
import app.carteira.dados.ForaDoReforco
import app.carteira.dados.MesReforco
import app.carteira.dados.PlanoReforco
import app.carteira.dados.Reforco

/** O valor do reforço quando não se escolhe outro: o habitual das Definições ou o reforço mensal do plano da folha. */
internal fun reforcoHabitual(d: Dados, prefs: Preferencias): Pair<Double, String>? = when {
    prefs.reforcoValor > 0 -> prefs.reforcoValor to "o teu valor habitual (Definições)"
    (d.plano?.reforcoMensalEur ?: 0.0) > 0 -> d.plano!!.reforcoMensalEur!! to "o reforço mensal do plano da folha"
    else -> null
}

/** 1500.0 → "1500"; 1234.5 → "1234,5" (sem espaços, para editar). */
internal fun numeroParaEditar(x: Double): String = Formatos.numeroLivre(x, 2).replace(Formatos.NB.toString(), "")

/**
 * Reforço do mês: onde pôr o dinheiro novo pela regra 10 da folha — primeiro os core em 🟢 abaixo do alvo,
 * cada um até ao "Ajuste sugerido", e o resto no ETF global. Cada compra abre o Registar já preenchido; as
 * que registas descontam-se do que falta investir até ao fim do mês.
 */
@Composable
fun ReforcoEcra(estado: Estado, vm: CarteiraViewModel) {
    val d = estado.dados ?: return SemDados(estado, vm)
    LaunchedEffect(Unit) { vm.verificarMesReforco() }
    val r = estado.reforco
    val prefs = estado.prefs
    val habitual = reforcoHabitual(d, prefs)
    val montante = r.montanteEur ?: habitual?.first ?: 0.0
    val falta = (montante - r.registadoEur).coerceAtLeast(0.0)
    val etf = prefs.etfReforco.ifBlank { null }
    val plano = remember(d, falta, prefs.reforcoFracoes, etf, r.registados) {
        Reforco.planear(d, falta, prefs.reforcoFracoes, etf, Reforco.porRefletir(d, r.registados))
    }

    ListaAtualizavel(estado, vm) {
        item { CartaoMontante(r, montante, habitual, prefs.reforcoFracoes, vm) }
        if (r.registados.isNotEmpty()) item { CartaoRegistados(r, falta, vm) }
        item { CartaoResumoReforco(plano, montante, falta, r) }
        items(plano.compras, key = { "c${it.ticker}" }) { c ->
            CartaoCompra(c) {
                vm.irPara(Ecra.Registar(ModoRegisto.TRANSACAO, c.ticker, "Compra", c.unidades, c.preco, doReforco = true))
            }
        }
        // Quem já compraste este mês não aparece como "de fora" (a folha deixa menos do que uma ação a seguir).
        val deFora = plano.deFora.filter { f -> r.registados.none { it.ticker == f.ticker } }
        if (deFora.isNotEmpty()) item { CartaoDeFora(deFora) }
        item { CartaoRegrasReforco(plano, vm) }
    }
}

@Composable
private fun CartaoMontante(r: MesReforco, montante: Double, habitual: Pair<Double, String>?, fracoes: Boolean, vm: CarteiraViewModel) {
    var texto by rememberSaveable { mutableStateOf(if (montante > 0) numeroParaEditar(montante) else "") }
    val valor = Formatos.lerNumero(texto)
    val erro = texto.isNotBlank() && (valor == null || valor < 0)
    Cartao("Quanto investes este mês") {
        OutlinedTextField(
            value = texto,
            onValueChange = { novo ->
                texto = novo
                val v = Formatos.lerNumero(novo)
                if (novo.isBlank()) vm.definirMontanteReforco(0.0) else if (v != null && v >= 0) vm.definirMontanteReforco(v)
            },
            label = { Text("Dinheiro novo") },
            singleLine = true,
            suffix = { Text("€") },
            isError = erro,
            supportingText = if (erro) { { Text("Escreve um valor, por exemplo 1000") } } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (habitual != null) {
            if (r.montanteEur != null && kotlin.math.abs(r.montanteEur - habitual.first) > 0.005) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextoPequeno("O habitual é ${Formatos.eur(habitual.first, 0)}.", Modifier.weight(1f))
                    TextButton(onClick = {
                        vm.definirMontanteReforco(null)
                        texto = numeroParaEditar(habitual.first)
                    }) { Text("Usar o habitual") }
                }
            } else {
                TextoPequeno("É ${habitual.second}.")
            }
        }
        LinhaInterruptor(
            "Frações de ações",
            if (fracoes) "Compra partes de uma ação (se a corretora deixar)."
            else "Só unidades inteiras: o que não chega para mais uma fica para o mês seguinte.",
            fracoes,
        ) { vm.definirFracoesReforco(it) }
    }
}

@Composable
private fun CartaoRegistados(r: MesReforco, falta: Double, vm: CarteiraViewModel) {
    Cartao("Já registaste em ${mesLongo(r.mes).substringBefore(" de ")}") {
        r.registados.forEach { c ->
            LinhaValor("✓ ${c.ticker} · ${Formatos.quantidade(c.unidades)} un.", Formatos.eur(c.custoEur))
        }
        HorizontalDivider(color = corBordaCartao())
        LinhaValor("Falta investir", Formatos.eur(falta), destaque = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextoPequeno("Recomeçar só esquece esta lista; as compras ficam na folha.", Modifier.weight(1f))
            TextButton(onClick = { vm.recomecarReforco() }) { Text("Recomeçar") }
        }
    }
}

@Composable
private fun CartaoResumoReforco(plano: PlanoReforco, montante: Double, falta: Double, r: MesReforco) {
    Cartao("O plano") {
        when {
            montante <= 0 -> Text("Escreve quanto vais investir este mês.", style = MaterialTheme.typography.bodyMedium)
            falta < 0.01 -> Text(
                "✓ Já registaste o reforço deste mês.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = legivel(Paleta.verde),
            )
            else -> {
                Text(
                    "Investe ${Formatos.eur(plano.investidoEur)}" +
                        (if (r.registados.isEmpty()) " de ${Formatos.eur(falta)}" else " dos ${Formatos.eur(falta)} que faltam"),
                    style = MaterialTheme.typography.titleMedium.numeros(),
                    fontWeight = FontWeight.SemiBold,
                )
                if (plano.soEtf) {
                    Text(
                        "As ações pesam ${Formatos.pct(plano.pesoAcoes)} da carteira (limite ${Formatos.pct(plano.limiteAcoes)}): " +
                            "pela regra 10, o dinheiro novo vai todo para o ETF.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = legivel(Paleta.ambar),
                    )
                }
                if (plano.compras.isEmpty()) {
                    TextoPequeno(
                        if (plano.etfGlobal == null) "Nenhum core em 🟢 e a carteira não tem um ETF de ações para o resto."
                        else "Não chega para uma unidade de nada: fica para o mês seguinte.",
                    )
                }
                if (plano.sobraEur >= 0.01 && plano.compras.isNotEmpty()) {
                    TextoPequeno("Sobram ${Formatos.eur(plano.sobraEur)}: não chegam para mais uma unidade e ficam para o mês seguinte.")
                }
                if (plano.compras.isNotEmpty()) {
                    TextoPequeno("Compra na corretora e carrega em Registar: a compra vai para a folha e sai do que falta.")
                }
            }
        }
    }
}

/** Uma compra do plano: o botão fica ao lado do ticker, e as contas por baixo com a largura toda. */
@Composable
private fun CartaoCompra(c: CompraSugerida, aoRegistar: () -> Unit) {
    Cartao {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(c.ticker, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                TextoPequeno(c.motivo)
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = aoRegistar) { Text("Registar") }
        }
        c.nome?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        val sinal = if (c.etf) "=" else "≈"
        Text(
            "${Formatos.quantidade(c.unidades)} × ${Formatos.preco(c.preco, c.moeda)} $sinal ${Formatos.eur(c.custoEur)}",
            style = MaterialTheme.typography.bodyMedium.numeros(),
            fontWeight = FontWeight.Medium,
        )
        c.limiteEur?.let { TextoPequeno("A folha deixa até ${Formatos.eur(it, 0)} (Ajuste sugerido).") }
    }
}

@Composable
private fun CartaoDeFora(deFora: List<ForaDoReforco>) {
    Cartao("Em 🟢, mas ficaram de fora") {
        deFora.forEach { f ->
            TextoPequeno(
                "${f.ticker}: 1 ação custa ${Formatos.eur(f.precoEur, 0)} e " +
                    if (f.faltouDinheiro) "o dinheiro que sobrava não chegava." else "a folha só deixa ${Formatos.eur(f.limiteEur, 0)}.",
            )
        }
    }
}

@Composable
private fun CartaoRegrasReforco(plano: PlanoReforco, vm: CarteiraViewModel) {
    Cartao(
        "Como a app escolhe",
        dica = "Pela regra 10 da folha (Dinheiro novo): 1.º os core (Core A e Core B) em 🟢 e abaixo do peso alvo, cada um " +
            "até ao Ajuste sugerido, que já traz os limites da regra 13 — primeiro os 🟢\u2060🟢, depois o Core A e depois " +
            "quem mais falta para o alvo; 2.º o resto vai para o ETF global. Satélites e apostas não recebem dinheiro " +
            "novo (regras 5 e 10).",
    ) {
        if (plano.pesoAcoes != null) {
            LinhaValor(
                "Peso das ações",
                Formatos.pct(plano.pesoAcoes) + (plano.limiteAcoes?.let { " (limite ${Formatos.pct(it)})" } ?: ""),
            )
        }
        LinhaValor("ETF do dinheiro novo", plano.etfGlobal ?: "—")
        TextButton(onClick = { vm.irPara(Ecra.Definicoes) }) { Text("Mudar o ETF e o valor habitual") }
    }
}
