// Os plugins são declarados aqui uma vez, com a versão do catálogo, e aplicados no módulo da app.
// O plugin Kotlin/JVM fixa a versão do Kotlin que o Kotlin integrado do AGP 9 usa.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
