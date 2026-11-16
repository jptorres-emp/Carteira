# Carteira

App Android pessoal para ver a carteira e registar compras, vendas e dividendos a partir do telemóvel.
A folha Google "Portefolio Principal Melhorado" continua a fazer todas as contas: a app só mostra o que
a folha calcula e escreve lá o que registas.

## Como funciona

```
App Android  ⇄  ponte (Apps Script, na folha)  ⇄  folha Google
```

- **Ponte** (`ponte/Codigo.gs`): aplicação Web do Google Apps Script, publicada a partir da folha.
  `GET ?acao=dados` devolve a carteira em JSON; `POST` regista uma transação (tab Transações) ou um
  dividendo (tab Dividendos Mensais). Todos os pedidos levam uma chave secreta.
- **App** (Kotlin + Jetpack Compose): Resumo, Posições, Dividendos, Watchlist e Registar.
  Guarda a última cópia dos dados para abrir sem rede.

## Instalar a ponte (uma vez)

1. Na folha: **Extensões > Apps Script**. Cola `ponte/Codigo.gs`, troca `__CHAVE__` pela tua chave e guarda.
2. **Implementar > Nova implementação > Aplicação Web**: executar como *Eu*, acesso *Qualquer pessoa*.
3. Na app, em Definições, cola o URL da aplicação Web (acaba em `/exec`) e a chave.

Para atualizar a ponte sem mudar o URL: **Implementar > Gerir implementações > lápis > Nova versão**.

## APK

Cada envio para o `main` corre os testes e gera o APK no CI. O APK fica no ramo `apk` e nos artefactos
do workflow. Vem assinado com uma chave de depuração fixa (`app/debug.keystore`, pública), para cada
versão se instalar por cima da anterior sem perder as definições.
