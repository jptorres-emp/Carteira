package app.carteira.dados

import android.content.Context
import java.io.File

/** Definições (link e chave da ponte) e a última cópia dos dados, para abrir sem rede. */
class Repositorio(contexto: Context) {
    private val preferencias = contexto.getSharedPreferences("carteira", Context.MODE_PRIVATE)
    private val ficheiroCache = File(contexto.filesDir, "dados.json")

    var url: String
        get() = preferencias.getString("url", "") ?: ""
        set(valor) = preferencias.edit().putString("url", valor.trim()).apply()

    var chave: String
        get() = preferencias.getString("chave", "") ?: ""
        set(valor) = preferencias.edit().putString("chave", valor.trim()).apply()

    val configurado: Boolean get() = url.isNotBlank() && chave.isNotBlank()

    fun guardarCache(texto: String) {
        runCatching { ficheiroCache.writeText(texto) }
    }

    fun lerCache(): Dados? = runCatching {
        if (ficheiroCache.exists()) Leitor.lerResposta(ficheiroCache.readText()) else null
    }.getOrNull()

    fun apagarCache() {
        runCatching { ficheiroCache.delete() }
    }

    // ── Alertas ──

    var alertasAtivos: Boolean
        get() = preferencias.getBoolean("alertas_ativos", false)
        set(v) = preferencias.edit().putBoolean("alertas_ativos", v).apply()

    var alertaCompras: Boolean
        get() = preferencias.getBoolean("alerta_compras", true)
        set(v) = preferencias.edit().putBoolean("alerta_compras", v).apply()

    var alertaWatchlist: Boolean
        get() = preferencias.getBoolean("alerta_watchlist", true)
        set(v) = preferencias.edit().putBoolean("alerta_watchlist", v).apply()

    var alertaVendas: Boolean
        get() = preferencias.getBoolean("alerta_vendas", false)
        set(v) = preferencias.edit().putBoolean("alerta_vendas", v).apply()

    var alertaVariacao: Boolean
        get() = preferencias.getBoolean("alerta_variacao", true)
        set(v) = preferencias.edit().putBoolean("alerta_variacao", v).apply()

    /** Variação diária que conta como grande (0,05 = 5%). */
    var limiarVariacao: Double
        get() = preferencias.getFloat("limiar_variacao", 0.05f).toDouble()
        set(v) = preferencias.edit().putFloat("limiar_variacao", v.toFloat()).apply()

    var intervaloHoras: Int
        get() = preferencias.getInt("intervalo_horas", 2)
        set(v) = preferencias.edit().putInt("intervalo_horas", v).apply()

    var soMercadoAberto: Boolean
        get() = preferencias.getBoolean("so_mercado_aberto", true)
        set(v) = preferencias.edit().putBoolean("so_mercado_aberto", v).apply()

    /** Momento (aaaa-mm-ddThh:mm) até onde os alertas já foram vistos na app. */
    var alertasVistosAte: String
        get() = preferencias.getString("alertas_vistos_ate", "") ?: ""
        set(v) = preferencias.edit().putString("alertas_vistos_ate", v).apply()

    /** O cartão "Ativar alertas" do ecrã inicial foi dispensado. */
    var conviteAlertasDispensado: Boolean
        get() = preferencias.getBoolean("convite_alertas_dispensado", false)
        set(v) = preferencias.edit().putBoolean("convite_alertas_dispensado", v).apply()

    private val ficheiroEstadoAlertas = File(contexto.filesDir, "alertas-estado.json")
    private val ficheiroHistorico = File(contexto.filesDir, "alertas-historico.json")

    fun lerEstadoAlertas(): String? = runCatching {
        if (ficheiroEstadoAlertas.exists()) ficheiroEstadoAlertas.readText() else null
    }.getOrNull()

    fun guardarEstadoAlertas(texto: String) {
        runCatching { ficheiroEstadoAlertas.writeText(texto) }
    }

    fun lerHistoricoAlertas(): String? = runCatching {
        if (ficheiroHistorico.exists()) ficheiroHistorico.readText() else null
    }.getOrNull()

    fun guardarHistoricoAlertas(texto: String) {
        runCatching { ficheiroHistorico.writeText(texto) }
    }

    /** Ao mudar de folha, os alertas recomeçam do zero. */
    fun apagarAlertas() {
        runCatching { ficheiroEstadoAlertas.delete() }
        runCatching { ficheiroHistorico.delete() }
    }
}
