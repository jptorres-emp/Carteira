# Carteira

App Android pessoal para ver a carteira e registar compras, vendas e dividendos a partir do telemóvel.
A folha Google "Portefolio Principal Melhorado" continua a fazer todas as contas: a app só mostra o que
a folha calcula e escreve lá o que registas.

## Como funciona

```
App Android  ⇄  ponte (Apps Script, na folha)  ⇄  folha Google
```

- **Ponte** (`ponte/Codigo.gs`, versão 6): aplicação Web do Google Apps Script, publicada a partir da folha.
  `GET ?acao=dados` devolve a carteira em JSON (com a evolução mensal, o plano do património até 2030 —
  reforço e taxas assumidos e as metas de referência, base e alargada de cada mês —, os objetivos, o
  património, a comparação com o S&P 500 e os setores; e, desde a versão 4, todas as compras e vendas da tab
  Transações, os dividendos por empresa da tab Dividendos Anuais e a tab Vendas; e, desde a versão 5, o preço
  do S&P 500 (VUAA e SPY) em cada compra e venda, os dividendos de cada empresa mês a mês e o câmbio de cada
  mês; e, desde a versão 6, a rentabilidade de cada mês da carteira e do S&P 500 da tab Evolução
  Patrimonial); `GET ?acao=alertas` devolve só preços e sinais,
  para os alertas; `POST` regista uma transação (tab Transações), um dividendo (tab Dividendos Mensais) ou o
  património de um mês (coluna Net Worth (€) da tab Evolução Patrimonial).
  Todos os pedidos levam uma chave secreta. Se uma tab mudar de forma, a resposta traz o resto e um aviso.
  A Google não deixa ler o GOOGLEFINANCE histórico fora do browser (dá #N/A), por isso as colunas «Preço SPY
  na Data» e «Preço VUAA na Data» da tab Transações estão em valores; a ponte guarda o preço de cada dia
  (nas propriedades do script) e, a partir do dia seguinte, passa a valor o preço das transações novas.
- **App** (Kotlin + Jetpack Compose): Início (só o estado atual da carteira), Evolução (o património total
  mês a mês face ao plano da folha, com o registo do mês, marcos e ano a ano; e a carteira face ao S&P 500,
  indicadores, objetivos e movimentos), Posições, Dividendos, Watchlist, Registar e Alertas. Cada posição
  mostra os preços de comprar e vender que contam para a sua camada (as regras do Sinal da folha): na lista
  («compra ≤ … · vende ≥ …») e no detalhe, com uma régua do preço de hoje face a esses preços. Cada posição
  mostra também a rentabilidade total desde a primeira compra (valor de hoje + vendas + dividendos − compras,
  com comissões; em euros com o câmbio de cada transação e em dólares sem o câmbio), e o ecrã Vendas lista as
  vendas com uma linha por ticker (sem repetidos: as vendas do mesmo ticker juntam-se, com as unidades, o preço
  médio, o que recebeste e a mais-valia pelo FIFO, como no IRS; «Ver as N vendas» mostra cada uma), as
  mais-valias por ano e as posições fechadas.
  Só a título informativo, a opção «De borla» desse ecrã (e uma linha no detalhe) mostra que posições já se
  pagaram com o lucro — as mais-valias das vendas e os dividendos pagam o que custaram as ações que ainda tens
  (o custo das ações vendidas é só o teu dinheiro a voltar e não conta) — e quanto falta às outras; ao tocar
  numa posição, mostra de onde vem esse lucro (cada venda e os dividendos de cada ano) e o que ele tem de pagar
  (cada compra que ainda tens). Não entra nas regras de comprar e vender.
  O gráfico «Carteira vs S&P 500» da Evolução tem a opção YTD, como no Google Finance: este ano a partir de 0%
  no fim de dezembro, mês a mês, sem contar o dinheiro que entrou ou saiu (a rentabilidade de cada mês da
  folha, encadeada, como a tabela por ano dela; em dólares e sem dividendos).
  Com a ponte v5, o detalhe compara cada posição com o mesmo dinheiro no S&P 500 (VUAA) nas mesmas datas —
  como a tab Validação S&P 500, mas ação a ação — e mostra a rentabilidade por ano (TIR, só quando o dinheiro
  está lá, em média, há mais de um ano); o ecrã «Contra o S&P 500» (atalho na Evolução) põe todas lado a
  lado. O simulador de venda do detalhe diz o que recebes ao preço de agora, o custo pelo FIFO, a mais-valia
  e o IRS estimado (28% do saldo do ano), e começa na sugestão da folha quando ela diz para reduzir.
  Na Evolução (vista Carteira), a TIR da carteira toda — todas as compras e vendas, os dividendos de cada mês e
  o valor de hoje, em euros — aparece ao lado da TIR do mesmo dinheiro no VUAA.
  O **Reforço do mês** (`dados/Reforco.kt`) diz onde pôr o dinheiro novo pela regra 10 da folha: primeiro os
  core (Core A e Core B) em 🟢 e abaixo do alvo, cada um até ao Ajuste sugerido, e o resto no ETF global; com
  ações inteiras ou com frações. Cada compra abre o Registar já preenchido, e as que registas saem do que falta
  investir até ao fim do mês.
  Estas contas fazem-se na app (`dados/Historico.kt` e `dados/Tir.kt`), a partir das transações da folha.
  Para facilitar: atalhos no Início (reforço, registar, dividendo, vendas e S&P 500) e no ícone da app
  (carregar sem largar), a ordem das Posições numa lista (guardada), cada ecrã fica como o deixaste ao voltar
  de outro, e nas Definições escolhe-se o ecrã de entrada, o valor habitual do reforço, as frações e o ETF do
  dinheiro novo. O olho no topo
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
