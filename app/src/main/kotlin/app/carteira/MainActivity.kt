package app.carteira

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.carteira.ui.AppCarteira
import app.carteira.ui.TemaCarteira

/** Activity única: os ecrãs são trocados dentro do Compose. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaCarteira {
                AppCarteira()
            }
        }
    }
}
