package app.carteira.alertas

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.carteira.dados.FonteDados
import app.carteira.dados.Ponte
import app.carteira.dados.Repositorio
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Verificação em segundo plano: de X em X horas pede os preços à ponte e avisa no telemóvel.
 * O Android decide a hora exata (poupança de bateria), por isso pode atrasar uns minutos.
 */
class VerificadorAlertas(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val repo = Repositorio(applicationContext)
        if (!repo.configurado || !repo.alertasAtivos) return Result.success()
        val forcar = inputData.getBoolean(FORCAR, false)
        if (!forcar && repo.soMercadoAberto && !ServicoAlertas.mercadoAberto(ZonedDateTime.now(ServicoAlertas.ZONA))) {
            return Result.success()
        }
        return try {
            val novos = ServicoAlertas(applicationContext).verificar(criarFonte(repo.url, repo.chave))
            Notificacoes.mostrar(applicationContext, novos)
            Result.success()
        } catch (e: Exception) {
            // Sem rede ou a ponte demorou: tenta mais tarde (o Android espaça as tentativas).
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val NOME = "verificar-alertas"
        private const val NOME_JA = "verificar-alertas-agora"
        const val FORCAR = "forcar"

        /** A ponte a sério; os testes trocam por uma fonte falsa. */
        @Volatile
        var criarFonte: (url: String, chave: String) -> FonteDados = { url, chave -> Ponte(url, chave) }

        /** Agenda (ou cancela) a verificação conforme as definições. Seguro de chamar muitas vezes. */
        fun agendar(contexto: Context) {
            val repo = Repositorio(contexto)
            runCatching {
                val wm = WorkManager.getInstance(contexto)
                if (!repo.alertasAtivos || !repo.configurado) {
                    wm.cancelUniqueWork(NOME)
                    return
                }
                val pedido = PeriodicWorkRequestBuilder<VerificadorAlertas>(repo.intervaloHoras.toLong().coerceAtLeast(1), TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                wm.enqueueUniquePeriodicWork(NOME, ExistingPeriodicWorkPolicy.UPDATE, pedido)
            }
        }

        /** Uma verificação já, mesmo fora do horário dos mercados. */
        fun verificarJa(contexto: Context) {
            runCatching {
                val pedido = OneTimeWorkRequestBuilder<VerificadorAlertas>()
                    .setInputData(workDataOf(FORCAR to true))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                WorkManager.getInstance(contexto).enqueueUniqueWork(NOME_JA, ExistingWorkPolicy.REPLACE, pedido)
            }
        }
    }
}
