package app.carteira.alertas

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.carteira.MainActivity
import app.carteira.R

/** Notificações dos alertas: um canal próprio e um toque que abre a empresa na app. */
object Notificacoes {
    const val CANAL = "alertas"
    private const val GRUPO = "app.carteira.ALERTAS"
    private const val ID_RESUMO = 1
    private const val ID_TESTE = 2
    private const val COR = 0xFF1F2A44.toInt()

    fun criarCanal(contexto: Context) {
        val canal = NotificationChannel(CANAL, "Alertas da carteira", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Sinais de compra, watchlist abaixo do valor justo e variações grandes de preço"
        }
        contexto.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
    }

    /** No Android 13+ é preciso autorizar; e o utilizador pode desligar as notificações da app. */
    fun podeNotificar(contexto: Context): Boolean {
        val autorizado = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return autorizado && NotificationManagerCompat.from(contexto).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun mostrar(contexto: Context, alertas: List<Alerta>) {
        if (alertas.isEmpty() || !podeNotificar(contexto)) return
        criarCanal(contexto)
        val gestor = NotificationManagerCompat.from(contexto)
        alertas.take(8).forEach { a ->
            val n = base(contexto, a.titulo, a.texto, a.chave)
                .setGroup(GRUPO)
                .build()
            gestor.notify(idDe(a), n)
        }
        if (alertas.size > 1) {
            val caixa = NotificationCompat.InboxStyle()
            alertas.take(6).forEach { caixa.addLine(it.titulo) }
            caixa.setSummaryText("${alertas.size} alertas")
            val resumo = base(contexto, "${alertas.size} alertas na carteira", alertas.joinToString(" · ") { it.ticker }, null)
                .setStyle(caixa)
                .setGroup(GRUPO)
                .setGroupSummary(true)
                .build()
            gestor.notify(ID_RESUMO, resumo)
        }
    }

    /** Para confirmares que o telemóvel mostra os avisos. */
    @SuppressLint("MissingPermission")
    fun mostrarTeste(contexto: Context): Boolean {
        if (!podeNotificar(contexto)) return false
        criarCanal(contexto)
        val n = base(
            contexto,
            "Alertas ligados",
            "É assim que vais ver os sinais de compra, a watchlist e as variações grandes.",
            null,
        ).build()
        NotificationManagerCompat.from(contexto).notify(ID_TESTE, n)
        return true
    }

    private fun base(contexto: Context, titulo: String, texto: String, chave: String?): NotificationCompat.Builder {
        val intencao = Intent(contexto, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_ABRIR, chave ?: MainActivity.ABRIR_ALERTAS)
        }
        val pendente = PendingIntent.getActivity(
            contexto,
            (chave ?: "alertas").hashCode(),
            intencao,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(COR)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(pendente)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
    }

    /** Um id por tipo e empresa: um aviso novo da mesma empresa substitui o anterior. */
    private fun idDe(a: Alerta): Int = 1000 + ("${a.tipo.name}:${a.chave}".hashCode() and 0x7FFFFFF)
}
