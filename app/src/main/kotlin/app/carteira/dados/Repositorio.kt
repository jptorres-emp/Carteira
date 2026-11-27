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

    /** Modo privado: esconder os valores em dinheiro. */
    var privado: Boolean
        get() = preferencias.getBoolean("privado", false)
        set(v) = preferencias.edit().putBoolean("privado", v).apply()

    fun guardarCache(texto: String) {
        runCatching { ficheiroCache.writeText(texto) }
    }

    fun lerCache(): Dados? = runCatching {
        if (ficheiroCache.exists()) Leitor.lerResposta(ficheiroCache.readText()) else null
    }.getOrNull()

    fun apagarCache() {
        runCatching { ficheiroCache.delete() }
    }

    // ── Preferências ──

    /** O ecrã que abre ao entrar na app: "Resumo", "Evolucao" ou "Posicoes". */
    var ecraInicial: String
        get() = preferencias.getString("ecra_inicial", "Resumo") ?: "Resumo"
        set(v) = preferencias.edit().putString("ecra_inicial", v).apply()

    /** Valor habitual do reforço do mês, em euros (0 = o do plano da folha). */
    var reforcoValor: Double
        get() = preferencias.getFloat("reforco_valor", 0f).toDouble()
        set(v) = preferencias.edit().putFloat("reforco_valor", v.toFloat()).apply()

    /** O reforço pode comprar frações de ações. */
    var reforcoFracoes: Boolean
        get() = preferencias.getBoolean("reforco_fracoes", false)
        set(v) = preferencias.edit().putBoolean("reforco_fracoes", v).apply()

    /** O ETF que recebe o dinheiro novo ("" = automático: o ETF global de ações; ver Reforco.etfGlobal). */
    var etfReforco: String
        get() = preferencias.getString("etf_reforco", "") ?: ""
        set(v) = preferencias.edit().putString("etf_reforco", v).apply()

    /** O reforço do mês em curso (ver MesReforco.paraTexto). */
    var reforcoMes: String
        get() = preferencias.getString("reforco_mes", "") ?: ""
        set(v) = preferencias.edit().putString("reforco_mes", v).apply()

    /** A última ordem escolhida nas Posições. */
    var ordemPosicoes: String
        get() = preferencias.getString("ordem_posicoes", "Valor") ?: "Valor"
        set(v) = preferencias.edit().putString("ordem_posicoes", v).apply()

    /** A última vista das Posições: "Lista" (cartões) ou "Pesos". */
    var vistaPosicoes: String
        get() = preferencias.getString("vista_posicoes", "Lista") ?: "Lista"
        set(v) = preferencias.edit().putString("vista_posicoes", v).apply()

    /** A última base da vista Pesos: "Ações", "Ações + ETFs" (a das regras) ou "Tudo" (o património todo). */
    var basePesos: String
        get() = preferencias.getString("base_pesos", BasePesos.CARTEIRA.rotulo) ?: BasePesos.CARTEIRA.rotulo
        set(v) = preferencias.edit().putString("base_pesos", v).apply()

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
