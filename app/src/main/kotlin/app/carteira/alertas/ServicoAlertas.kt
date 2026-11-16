package app.carteira.alertas

import android.content.Context
import app.carteira.dados.ErroPonte
import app.carteira.dados.FonteDados
import app.carteira.dados.Leitor
import app.carteira.dados.Mercado
import app.carteira.dados.Repositorio
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Junta as peças dos alertas: pede os preços à ponte, compara com a última verificação, guarda o
 * estado e o histórico. Usado pela verificação em segundo plano e pela app aberta.
 */
class ServicoAlertas(contexto: Context) {
    private val repo = Repositorio(contexto)

    fun preferencias() = PreferenciasAlertas(
        compras = repo.alertaCompras,
        watchlist = repo.alertaWatchlist,
        vendas = repo.alertaVendas,
        variacao = repo.alertaVariacao,
        limiar = repo.limiarVariacao,
    )

    /** Alertas guardados, do mais recente para o mais antigo. */
    fun historico(): List<Alerta> = ArquivoAlertas.historicoDeJson(repo.lerHistoricoAlertas())

    suspend fun processar(mercado: Mercado, agora: LocalDateTime = agoraEmLisboa()): List<Alerta> = withContext(Dispatchers.IO) {
        // O trinco é apanhado e largado dentro do IO: quem o segura nunca fica à espera da thread principal.
        trinco.withLock {
            val antes = ArquivoAlertas.estadoDeJson(repo.lerEstadoAlertas())
            val (novos, depois) = Detetor.comparar(antes, mercado, preferencias(), agora)
            repo.guardarEstadoAlertas(ArquivoAlertas.estadoParaJson(depois))
            if (novos.isNotEmpty()) {
                repo.guardarHistoricoAlertas(ArquivoAlertas.historicoParaJson((novos + historico()).take(MAX_HISTORICO)))
            }
            novos
        }
    }

    suspend fun verificar(fonte: FonteDados): List<Alerta> = processar(obterMercado(fonte))

    companion object {
        val ZONA: ZoneId = ZoneId.of("Europe/Lisbon")
        const val MAX_HISTORICO = 60
        private val trinco = Mutex()

        fun agoraEmLisboa(): LocalDateTime = LocalDateTime.now(ZONA)

        /** Pede só os preços (ponte v2); a ponte v1 não conhece essa ação e aí usa os dados completos. */
        suspend fun obterMercado(fonte: FonteDados): Mercado {
            val texto = fonte.alertas()
            return try {
                Leitor.lerMercado(texto)
            } catch (e: ErroPonte) {
                if (e.message.orEmpty().contains("desconhecida", ignoreCase = true)) Leitor.lerMercado(fonte.dados()) else throw e
            }
        }

        /** Dias úteis entre as 8h e as 22h30 de Lisboa: abertura da Europa até depois do fecho dos EUA. */
        fun mercadoAberto(agora: ZonedDateTime): Boolean {
            val local = agora.withZoneSameInstant(ZONA)
            if (local.dayOfWeek == DayOfWeek.SATURDAY || local.dayOfWeek == DayOfWeek.SUNDAY) return false
            val minutos = local.hour * 60 + local.minute
            return minutos in (8 * 60)..(22 * 60 + 30)
        }
    }
}
