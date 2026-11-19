# Carteira

App Android pessoal para ver a carteira e registar compras, vendas e dividendos a partir do telemóvel.
A folha Google "Portefolio Principal Melhorado" continua a fazer todas as contas: a app só mostra o que
a folha calcula e escreve lá o que registas.

## Como funciona

```
App Android  ⇄  ponte (Apps Script, na folha)  ⇄  folha Google
```

- **Ponte** (`ponte/Codigo.gs`, versão 3): aplicação Web do Google Apps Script, publicada a partir da folha.
  `GET ?acao=dados` devolve a carteira em JSON (com a evolução mensal, o plano do património até 2030 —
  reforço e taxas assumidos e as metas de referência, base e alargada de cada mês —, os objetivos, o
  património, a comparação com o S&P 500 e os setores); `GET ?acao=alertas` devolve só preços e sinais,
  para os alertas; `POST` regista uma transação (tab Transações), um dividendo (tab Dividendos Mensais) ou o
  património de um mês (coluna Net Worth (€) da tab Evolução Patrimonial).
  Todos os pedidos levam uma chave secreta. Se uma tab mudar de forma, a resposta traz o resto e um aviso.
- **App** (Kotlin + Jetpack Compose): Início (só o estado atual da carteira), Evolução (o património total
  mês a mês face ao plano da folha, com o registo do mês, marcos e ano a ano; e a carteira face ao S&P 500,
  indicadores, objetivos e movimentos), Posições, Dividendos, Watchlist, Registar e Alertas. O olho no topo
  esconde os valores em dinheiro (modo privado). Guarda a última cópia dos dados para abrir sem rede. Com uma
  ponte antiga, o que é novo fica de fora. Segue o tema claro ou escuro do telemóvel; o texto colorido usa
  tons com contraste de pelo menos 4,5:1 (WCAG AA) nos dois temas, e os gráficos podem mudar de mês com
  setas de 48 dp ou, no TalkBack, com as ações "Mês anterior" e "Mês seguinte".
- **Alertas**: uma verificação em segundo plano (WorkManager) compara a folha com a verificação anterior
  e avisa quando o **sinal** de uma posição passa a compra (o sinal já aplica as regras da folha), quando
  uma empresa da watchlist passa a subvalorizada e quando há variações grandes de preço.

## Instalar a ponte (uma vez)

1. Na folha: **Extensões > Apps Script**. Cola `ponte/Codigo.gs`, troca `__CHAVE__` pela tua chave e guarda.
2. **Implementar > Nova implementação > Aplicação Web**: executar como *Eu*, acesso *Qualquer pessoa*.
3. Na app, em Definições, cola o URL da aplicação Web (acaba em `/exec`) e a chave.

Para atualizar a ponte sem mudar o URL: **Implementar > Gerir implementações > lápis > Nova versão**.

## APK

Cada envio para o `main` corre os testes (com capturas de cada ecrã, também num ecrã estreito com letra
grande e no tema escuro, no ramo `screenshots`) e gera o APK no CI. O APK fica no ramo `apk` e nos artefactos
do workflow. Vem assinado com uma chave de depuração fixa (`app/debug.keystore`, pública), para cada
versão se instalar por cima da anterior sem perder as definições.
