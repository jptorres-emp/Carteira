package app.carteira

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Atalhos do ícone da app (carregar no ícone sem largar): abrem logo o reforço do mês, o registo de uma compra
 * ou de um dividendo, ou as posições. Cada um abre a MainActivity com "ECRA:…" (ver CarteiraViewModel.abrir).
 */
object Atalhos {

    private data class Atalho(val id: String, val curto: String, val longo: String, val icone: Int, val abrir: String)

    private val lista = listOf(
        Atalho("reforco", "Reforço", "Reforço do mês", R.drawable.ic_atalho_reforco, "ECRA:Reforco"),
        Atalho("compra", "Compra", "Registar compra ou venda", R.drawable.ic_atalho_compra, "ECRA:Registar"),
        Atalho("dividendo", "Dividendo", "Registar dividendo", R.drawable.ic_atalho_dividendo, "ECRA:Dividendo"),
        Atalho("posicoes", "Posições", "Posições", R.drawable.ic_atalho_posicoes, "ECRA:Posicoes"),
    )

    /** Publica (ou atualiza) os atalhos. Se o telemóvel não os suportar, não faz nada. */
    fun publicar(contexto: Context) {
        runCatching {
            val maximo = ShortcutManagerCompat.getMaxShortcutCountPerActivity(contexto).coerceAtLeast(1)
            val atalhos = lista.take(maximo).mapIndexed { i, a ->
                ShortcutInfoCompat.Builder(contexto, a.id)
                    .setShortLabel(a.curto)
                    .setLongLabel(a.longo)
                    .setIcon(IconCompat.createWithResource(contexto, a.icone))
                    .setIntent(
                        Intent(contexto, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .putExtra(MainActivity.EXTRA_ABRIR, a.abrir),
                    )
                    .setRank(i)
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(contexto, atalhos)
        }
    }
}
