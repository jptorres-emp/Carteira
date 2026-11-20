package app.carteira

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import app.carteira.alertas.Notificacoes
import app.carteira.alertas.VerificadorAlertas
import app.carteira.ui.AppCarteira
import app.carteira.ui.TemaCarteira

/** Activity única: os ecrãs são trocados dentro do Compose. */
class MainActivity : ComponentActivity() {

    /** O que abrir quando se toca numa notificação ("P:ALFA", "W:KAPA", ...) ou num atalho do ícone ("ECRA:Reforco"). */
    private val abrir = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notificacoes.criarCanal(this)
        // Garante a verificação em segundo plano (por ex. depois de uma atualização da app).
        VerificadorAlertas.agendar(this)
        Atalhos.publicar(this)
        if (savedInstanceState == null) abrir.value = intent?.getStringExtra(EXTRA_ABRIR)
        setContent {
            TemaCarteira {
                AppCarteira(abrir = abrir.value, aoAbrir = { abrir.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_ABRIR)?.let { abrir.value = it }
    }

    companion object {
        const val EXTRA_ABRIR = "app.carteira.ABRIR"
        const val ABRIR_ALERTAS = "alertas"
    }
}
