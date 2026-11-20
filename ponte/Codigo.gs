/**
 * PONTE DA APP "CARTEIRA"
 *
 * Liga a app Android à folha "Portefolio Principal Melhorado". A folha continua a fazer todas as
 * contas: a ponte só lê o que lá está e escreve as compras, vendas e dividendos que registares na app
 * (nas tabs Transações e Dividendos Mensais).
 *
 * Instalar (uma vez, no computador):
 *  1. Na folha: Extensões > Apps Script. Apaga o que lá estiver, cola este ficheiro inteiro e guarda.
 *  2. Implementar > Nova implementação > tipo "Aplicação Web".
 *     Executar como: Eu · Quem tem acesso: Qualquer pessoa. Implementar e autorizar o acesso.
 *  3. Copia o "URL da aplicação Web" e cola-o na app (Definições), com a chave abaixo.
 * Se mudares este código: Implementar > Gerir implementações > lápis > Versão: Nova versão.
 * Assim o URL mantém-se.
 *
 * A chave impede que alguém com o URL leia a carteira. Se a mudares aqui, muda-a também na app.
 */
const CHAVE = '__CHAVE__';
// v4: todas as compras e vendas, os dividendos por empresa e a tab Vendas (vendas e rentabilidade na app).
// v5: o preço do S&P 500 (VUAA) em cada compra e venda, que a ponte fixa na folha, e os dividendos de cada
//     empresa mês a mês (cada ação contra o índice e a rentabilidade por ano na app).
// v6: a rentabilidade de cada mês da carteira e do S&P 500 (tab Evolução Patrimonial), para o YTD na app.
const VERSAO_PONTE = 6;

const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto',
  'Setembro', 'Outubro', 'Novembro', 'Dezembro'];

// ───────────────────────────── Pedidos ─────────────────────────────

function doGet(e) {
  return responder_(function () {
    const p = (e && e.parameter) || {};
    verificarChave_(p.chave);
    const acao = p.acao || 'dados';
    if (acao === 'ping') {
      return { ok: true, versaoPonte: VERSAO_PONTE, folha: SpreadsheetApp.getActive().getName() };
    }
    if (acao === 'dados') {
      const indice = prepararIndice_(true);
      return { ok: true, versaoPonte: VERSAO_PONTE, dados: lerTudo_(indice) };
    }
    if (acao === 'alertas') {
      prepararIndice_(false);
      return { ok: true, versaoPonte: VERSAO_PONTE, alertas: lerParaAlertas_() };
    }
    throw new Error('Ação desconhecida: ' + acao);
  });
}

function doPost(e) {
  return responder_(function () {
    const texto = (e && e.postData && e.postData.contents) || '{}';
    const p = JSON.parse(texto);
    verificarChave_(p.chave);
    const tranca = LockService.getDocumentLock();
    tranca.waitLock(20000);
    try {
      let r;
      if (p.acao === 'transacao') r = registarTransacao_(p);
      else if (p.acao === 'dividendo') r = registarDividendo_(p);
      else if (p.acao === 'patrimonio') r = registarPatrimonio_(p);
      else throw new Error('Ação desconhecida: ' + p.acao);
      r.ok = true;
      r.versaoPonte = VERSAO_PONTE;
      return r;
    } finally {
      tranca.releaseLock();
    }
  });
}

function responder_(fn) {
  let saida;
  try {
    saida = fn();
  } catch (err) {
    saida = { ok: false, erro: String((err && err.message) || err) };
  }
  return ContentService.createTextOutput(JSON.stringify(saida))
    .setMimeType(ContentService.MimeType.JSON);
}

function verificarChave_(c) {
  if (!c || String(c) !== CHAVE) throw new Error('Chave inválida. Confirma a chave nas Definições da app.');
}

// ───────────────────────────── Ajudas ─────────────────────────────

let FUSO_ = null;
function fuso_() {
  if (!FUSO_) FUSO_ = SpreadsheetApp.getActive().getSpreadsheetTimeZone();
  return FUSO_;
}

function norm_(s) {
  return String(s === null || s === undefined ? '' : s).replace(/\s+/g, ' ').trim();
}

/** Valor pronto para JSON: datas em aaaa-mm-dd, células vazias e erros como null. */
function v_(x) {
  if (x === '' || x === null || x === undefined) return null;
  if (x instanceof Date) return Utilities.formatDate(x, fuso_(), 'yyyy-MM-dd');
  if (typeof x === 'number') return isFinite(x) ? x : null;
  if (typeof x === 'string') {
    const t = x.trim();
    if (t === '' || /^#(N\/A|REF!|VALUE!|DIV\/0!|NAME\?|NUM!|ERROR!|NULL!)/.test(t)) return null;
    return t;
  }
  return x;
}

function num_(x) {
  return typeof x === 'number' && isFinite(x) ? x : null;
}

/** {cabeçalho normalizado: índice} de uma linha de cabeçalhos. */
function indice_(linha) {
  const m = {};
  linha.forEach(function (v, i) {
    const k = norm_(v);
    if (k && !(k in m)) m[k] = i;
  });
  return m;
}

/** Lê a coluna "nome" de uma linha; devolve null se a coluna não existir (por ex. se lhe mudares o nome). */
function campo_(linha, mapa, nome) {
  const i = mapa[norm_(nome)];
  return i === undefined ? null : v_(linha[i]);
}

/** Índice da primeira linha (a partir de "desde") que tem todos os textos pedidos. */
function acharLinha_(dados, textos, desde) {
  for (let r = desde || 0; r < dados.length; r++) {
    const linha = dados[r].map(norm_);
    if (textos.every(function (t) { return linha.indexOf(t) >= 0; })) return r;
  }
  return -1;
}

function folha_(nome) {
  const sh = SpreadsheetApp.getActive().getSheetByName(nome);
  if (!sh) throw new Error('Não encontrei a tab "' + nome + '"');
  return sh;
}

function grelha_(sh, maxLinhas, comTextos) {
  const n = Math.min(sh.getLastRow(), maxLinhas || 1000);
  const c = sh.getLastColumn();
  if (n < 1 || c < 1) return { valores: [], textos: [] };
  const rg = sh.getRange(1, 1, n, c);
  return { valores: rg.getValues(), textos: comTextos ? rg.getDisplayValues() : [] };
}

/**
 * Quadros "rótulo por cima, valor por baixo" (Dashboard e Dividendos): devolve todos os pares
 * em que a célula de baixo tem um número, com o texto tal como a folha o mostra.
 */
function indicadores_(g, maxLinha, soMaiusculas) {
  const out = [];
  const vistos = {};
  const n = Math.min(g.valores.length - 1, maxLinha || g.valores.length);
  for (let r = 0; r < n; r++) {
    for (let c = 0; c < g.valores[r].length; c++) {
      const rot = g.valores[r][c];
      if (typeof rot !== 'string') continue;
      const t = norm_(rot);
      if (!t || t.length > 60 || vistos[t]) continue;
      if (soMaiusculas && t !== t.toUpperCase()) continue;
      if (!/[A-Za-zÀ-ÿ]/.test(t)) continue;
      const abaixo = g.valores[r + 1][c];
      if (typeof abaixo !== 'number' || !isFinite(abaixo)) continue;
      vistos[t] = true;
      out.push({ rotulo: t, valor: abaixo, texto: norm_(g.textos[r + 1][c]) });
    }
  }
  return out;
}

function hojeIso_() {
  return Utilities.formatDate(new Date(), fuso_(), 'yyyy-MM-dd');
}

// ───────────────────────────── Leitura ─────────────────────────────

function lerTudo_(indice) {
  indice = indice || { hoje: null, registo: {}, avisos: [] };
  const ss = SpreadsheetApp.getActive();
  const cart = lerCarteira_();
  const aval = lerAvaliacao_();
  cart.posicoes.forEach(function (p) { p.avaliacao = aval[p.ticker] || null; });
  // Secções da visão geral: se uma tab mudar de forma, a app recebe o resto e um aviso.
  const avisos = indice.avisos.slice();
  const ev = opcional_(avisos, 'Evolução Patrimonial', lerEvolucaoEPlano_, { evolucao: [], plano: null });
  const objetivos = opcional_(avisos, 'Objetivos', lerObjetivos_, []);
  const patrimonio = opcional_(avisos, 'Investimentos', lerPatrimonio_, null);
  const comparacoes = opcional_(avisos, 'Validação S&P 500', lerComparacoes_, []);
  const setores = opcional_(avisos, 'Distribuição', lerSetores_, []);
  // Ponte v4: a app calcula a rentabilidade de cada ação e as mais-valias de cada venda.
  const movimentos = opcional_(avisos, 'Transações (compras e vendas)', function () { return lerMovimentos_(indice.registo); }, []);
  const dividendosPorEmpresa = opcional_(avisos, 'Dividendos Anuais (por empresa)', lerDividendosPorEmpresa_, null);
  const analiseVendas = opcional_(avisos, 'Vendas', lerAnaliseVendas_, []);
  // Ponte v5: cada ação contra o S&P 500 e a rentabilidade por ano.
  const dividendosPorMes = opcional_(avisos, 'Dividendos Mensais (por empresa e mês)', lerDividendosPorMes_, []);
  const cambioMensal = opcional_(avisos, 'Validação S&P 500 (câmbio de cada mês)', lerCambioMensal_, {});
  return {
    meta: {
      folha: ss.getName(),
      atualizadoEm: Utilities.formatDate(new Date(), fuso_(), "yyyy-MM-dd'T'HH:mm:ssXXX"),
      hoje: hojeIso_(),
      avisos: avisos
    },
    evolucao: ev.evolucao,
    plano: ev.plano,
    objetivos: objetivos,
    patrimonio: patrimonio,
    comparacoes: comparacoes,
    setores: setores,
    cambio: cart.cambio,
    totaisAcoes: cart.totais,
    totaisEtfs: cart.totaisEtfs,
    camadas: cart.camadas,
    indicadores: lerIndicadores_(),
    regras: lerRegras_(),
    posicoes: cart.posicoes,
    etfs: cart.etfs,
    tickers: cart.tickers,
    dividendos: lerDividendos_(),
    watchlist: lerWatchlist_(),
    transacoes: lerTransacoes_(15),
    movimentos: movimentos,
    dividendosPorEmpresa: dividendosPorEmpresa,
    analiseVendas: analiseVendas,
    indice: indice.hoje,
    dividendosPorMes: dividendosPorMes,
    cambioMensal: cambioMensal
  };
}

function lerCarteira_() {
  const sh = folha_('Carteira');
  const g = grelha_(sh, 200);
  const d = g.valores;

  const cambio = { usdEur: null, gbpUsd: null };
  d.forEach(function (linha) {
    const rot = norm_(linha[0]);
    if (rot === 'Taxa de câmbio USD/EUR') cambio.usdEur = num_(linha[1]);
    if (rot === 'Taxa de câmbio GBP/USD') cambio.gbpUsd = num_(linha[1]);
  });

  // Ações
  const hA = acharLinha_(d, ['Ticker', 'Nº Ações']);
  if (hA < 0) throw new Error('Não encontrei o cabeçalho das ações na Carteira');
  const mA = indice_(d[hA]);
  const posicoes = [];
  const tickers = [];
  let totais = null;
  let r = hA + 1;
  for (; r < d.length; r++) {
    const linha = d[r];
    const primeira = norm_(linha[0]);
    if (primeira.indexOf('TOTAL') === 0) {
      totais = {
        custoUsd: campo_(linha, mA, 'Custo Base ($)'),
        valorUsd: campo_(linha, mA, 'Valor Mercado ($)'),
        ganhoUsd: campo_(linha, mA, 'Ganho/Perda ($)'),
        ganhoPct: campo_(linha, mA, 'Ganho/Perda (%)'),
        dividendosAnoUsd: campo_(linha, mA, 'Div. Anual/Ação ($)'),
        custoEur: campo_(linha, mA, 'Custo Base em EUR (câmbio real p/ transação)'),
        valorEur: campo_(linha, mA, 'Valor (€)'),
        ajusteSugeridoEur: campo_(linha, mA, 'Ajuste sugerido (€)')
      };
      break;
    }
    const ticker = campo_(linha, mA, 'Ticker');
    if (!ticker) continue;
    const qtd = num_(linha[mA[norm_('Nº Ações')]]) || 0;
    tickers.push({ ticker: ticker, nome: campo_(linha, mA, 'Empresa'), classe: 'Ação', quantidade: qtd });
    if (qtd <= 0) continue;
    const custoEur = campo_(linha, mA, 'Custo Base em EUR (câmbio real p/ transação)');
    const valorEur = campo_(linha, mA, 'Valor (€)');
    posicoes.push({
      ticker: ticker,
      empresa: campo_(linha, mA, 'Empresa'),
      setor: campo_(linha, mA, 'Setor'),
      camada: campo_(linha, mA, 'Camada'),
      quantidade: qtd,
      precoCompraUsd: campo_(linha, mA, 'Preço Compra ($)'),
      precoUsd: campo_(linha, mA, 'Preço Mercado ($)'),
      varDiaPct: num_(campo_(linha, mA, 'Var. Dia %')),
      pe: num_(campo_(linha, mA, 'P/E')),
      custoUsd: campo_(linha, mA, 'Custo Base ($)'),
      valorUsd: campo_(linha, mA, 'Valor Mercado ($)'),
      ganhoUsd: campo_(linha, mA, 'Ganho/Perda ($)'),
      ganhoPct: campo_(linha, mA, 'Ganho/Perda (%)'),
      pesoAcoes: campo_(linha, mA, '% Carteira Ações'),
      divAnualPorAcaoUsd: num_(campo_(linha, mA, 'Div. Anual/Ação ($)')),
      yieldPct: num_(campo_(linha, mA, 'Yield (%)')),
      yieldCustoPct: num_(campo_(linha, mA, 'Yield on Cost (%)')),
      frequencia: campo_(linha, mA, 'Freq. Pagamento'),
      risco: num_(campo_(linha, mA, 'Risco (1-7)')),
      rating: campo_(linha, mA, 'Rating'),
      custoEur: num_(custoEur),
      valorEur: num_(valorEur),
      ganhoEur: num_(valorEur) !== null && num_(custoEur) !== null ? valorEur - custoEur : null,
      pesoTotal: num_(campo_(linha, mA, 'Peso na carteira total')),
      pesoAlvo: num_(campo_(linha, mA, 'Peso alvo')),
      pesoMaximo: num_(campo_(linha, mA, 'Peso máximo')),
      zona: campo_(linha, mA, 'Zona (preço)'),
      sinal: campo_(linha, mA, 'O que fazer (regras + peso)'),
      ajusteSugeridoEur: num_(campo_(linha, mA, 'Ajuste sugerido (€)'))
    });
  }

  // ETFs
  const hE = acharLinha_(d, ['Ticker', 'Nº Unidades'], r);
  const etfs = [];
  let totaisEtfs = null;
  if (hE >= 0) {
    const mE = indice_(d[hE]);
    for (let k = hE + 1; k < d.length; k++) {
      const linha = d[k];
      if (norm_(linha[0]).indexOf('TOTAL') === 0) {
        totaisEtfs = {
          custoEur: campo_(linha, mE, 'Custo Base (€)'),
          valorEur: campo_(linha, mE, 'Valor Mercado (€)'),
          ganhoEur: campo_(linha, mE, 'Ganho/Perda (€)'),
          ganhoPct: campo_(linha, mE, 'Ganho/Perda (%)')
        };
        break;
      }
      const ticker = campo_(linha, mE, 'Ticker');
      if (!ticker) continue;
      const qtd = num_(linha[mE[norm_('Nº Unidades')]]) || 0;
      tickers.push({ ticker: ticker, nome: campo_(linha, mE, 'Nome'), classe: 'ETF', quantidade: qtd });
      if (qtd <= 0) continue;
      etfs.push({
        ticker: ticker,
        nome: campo_(linha, mE, 'Nome'),
        quantidade: qtd,
        precoCompraEur: campo_(linha, mE, 'Preço Compra (€)'),
        precoEur: campo_(linha, mE, 'Preço Mercado (€)'),
        varDiaPct: num_(campo_(linha, mE, 'Var. Dia %')),
        custoEur: campo_(linha, mE, 'Custo Base (€)'),
        valorEur: campo_(linha, mE, 'Valor Mercado (€)'),
        ganhoEur: campo_(linha, mE, 'Ganho/Perda (€)'),
        ganhoPct: campo_(linha, mE, 'Ganho/Perda (%)'),
        pesoEtfs: campo_(linha, mE, '% Carteira ETFs')
      });
    }
  }

  // Peso por camada (hoje vs ideal)
  const camadas = [];
  const hC = acharLinha_(d, ['Camada', 'Nº hoje']);
  if (hC >= 0) {
    const mC = indice_(d[hC]);
    for (let k = hC + 1; k < d.length; k++) {
      const nome = campo_(d[k], mC, 'Camada');
      if (!nome || String(nome).indexOf('TOTAL') === 0) break;
      if (num_(campo_(d[k], mC, 'Nº hoje')) === null) continue;
      camadas.push({
        camada: nome,
        numero: num_(campo_(d[k], mC, 'Nº hoje')),
        numeroIdeal: num_(campo_(d[k], mC, 'Nº ideal')),
        pesoHoje: num_(campo_(d[k], mC, '% hoje')),
        pesoIdeal: num_(campo_(d[k], mC, '% ideal')),
        valorEur: num_(campo_(d[k], mC, 'Valor hoje (€)')),
        diferencaEur: num_(campo_(d[k], mC, 'Diferença (€)'))
      });
    }
  }

  return { cambio: cambio, posicoes: posicoes, etfs: etfs, tickers: tickers, totais: totais,
    totaisEtfs: totaisEtfs, camadas: camadas };
}

function lerAvaliacao_() {
  const g = grelha_(folha_('Avaliação e Zonas'), 200);
  const d = g.valores;
  const h = acharLinha_(d, ['Ticker', '★ Preço ao vivo']);
  const out = {};
  if (h < 0) return out;
  const m = indice_(d[h]);
  for (let r = h + 1; r < d.length; r++) {
    const ticker = campo_(d[r], m, 'Ticker');
    if (!ticker) break;
    out[ticker] = {
      moeda: campo_(d[r], m, 'Moeda'),
      precoLocal: num_(campo_(d[r], m, '★ Preço ao vivo')),
      rendImplicita: num_(campo_(d[r], m, '★ Rend. implícita')),
      hurdle: num_(campo_(d[r], m, '★ Hurdle de compra')),
      valorIntrinseco: num_(campo_(d[r], m, '★ Valor intrínseco (ponderado)')),
      potencial: num_(campo_(d[r], m, '★ Potencial até ao valor intrínseco')),
      reforcarForteAbaixo: num_(campo_(d[r], m, 'Reforçar forte abaixo de')),
      reforcarAbaixo: num_(campo_(d[r], m, 'Reforçar abaixo de')),
      comprarAbaixo: num_(campo_(d[r], m, '★ Comprar abaixo de')),
      reduzirAcima: num_(campo_(d[r], m, 'Manter até / reduzir acima de')),
      venderAcima: num_(campo_(d[r], m, 'Vender acima de')),
      pesoVsMaximo: campo_(d[r], m, 'Peso vs máximo'),
      temaIa: campo_(d[r], m, 'Tema IA-capex') === 'Sim',
      tese: campo_(d[r], m, 'Tese / leitura'),
      alarme: campo_(d[r], m, 'Indicador a vigiar (alarme)'),
      fiscalidade: campo_(d[r], m, 'Fiscalidade (PT)'),
      fonte: campo_(d[r], m, 'Fonte / data')
    };
  }
  return out;
}

function lerIndicadores_() {
  const g = grelha_(folha_('Dashboard'), 104, true);
  return indicadores_(g, 103, false);
}

function lerRegras_() {
  const g = grelha_(folha_('Regras da Carteira'), 200, true);
  const d = g.valores;
  const h = acharLinha_(d, ['Regra', 'Hoje', 'Limite', 'Estado']);
  const out = [];
  if (h < 0) return out;
  const m = indice_(d[h]);
  for (let r = h + 1; r < d.length; r++) {
    const regra = campo_(d[r], m, 'Regra');
    if (!regra) break;
    const t = g.textos[r];
    out.push({
      regra: regra,
      hoje: norm_(t[m.Hoje]),
      limite: norm_(t[m.Limite]),
      estado: norm_(t[m.Estado]),
      nota: m.Nota === undefined ? null : v_(t[m.Nota])
    });
  }
  return out;
}

function lerDividendos_() {
  const sh = folha_('Dividendos');
  const g = grelha_(sh, 400, true);
  const d = g.valores;
  const out = {
    indicadores: indicadores_(g, d.length - 1, true),
    previsaoAno: null,
    calendario: [],
    anos: [],
    esteAno: null
  };
  for (let r = 0; r < d.length; r++) {
    const t = norm_(d[r][0]);
    const m = t.match(/^PREVISÃO DE DIVIDENDOS (\d{4})/);
    if (m) { out.previsaoAno = Number(m[1]); break; }
  }
  const hC = acharLinha_(d, ['Mês', 'Previsto bruto ($)']);
  if (hC >= 0) {
    const mC = indice_(d[hC]);
    for (let r = hC + 1; r < d.length && out.calendario.length < 12; r++) {
      const mes = campo_(d[r], mC, 'Mês');
      if (!mes || MESES.indexOf(mes) < 0) break;
      out.calendario.push({
        mes: mes,
        previstoBrutoUsd: num_(campo_(d[r], mC, 'Previsto bruto ($)')),
        previstoLiquidoEur: num_(campo_(d[r], mC, 'Previsto líquido (€)')),
        recebidoAnoAnteriorUsd: num_(d[r][mC[norm_('Recebido em ' + (out.previsaoAno - 1) + ' ($)')]]),
        recebidoAnoUsd: num_(d[r][mC[norm_('Recebido em ' + out.previsaoAno + ' ($)')]]),
        quemPaga: campo_(d[r], mC, 'Quem paga')
      });
    }
  }

  // Recebido por ano e mês (Dividendos Anuais)
  const ga = grelha_(folha_('Dividendos Anuais'), 60).valores;
  const hA = acharLinha_(ga, ['Ano', 'Total Ano']);
  if (hA >= 0) {
    const mA = indice_(ga[hA]);
    for (let r = hA + 1; r < ga.length; r++) {
      const ano = num_(ga[r][mA.Ano]);
      if (ano === null) break;
      const meses = [];
      for (let k = 0; k < 12; k++) meses.push(num_(ga[r][mA.Ano + 1 + k]) || 0);
      out.anos.push({ ano: ano, meses: meses, totalUsd: num_(campo_(ga[r], mA, 'Total Ano')) });
    }
  }

  // Recebido este ano, por empresa (Dividendos Mensais)
  const ano = Number(hojeIso_().slice(0, 4));
  const tab = tabelaDoAno_(folha_('Dividendos Mensais'), ano);
  if (tab) {
    const linhas = [];
    tab.dados.forEach(function (l) {
      const meses = l.meses.map(function (x) { return num_(x) || 0; });
      const total = meses.reduce(function (a, b) { return a + b; }, 0);
      if (total > 0) linhas.push({ ticker: l.ticker, empresa: l.empresa, meses: meses, totalUsd: total });
    });
    linhas.sort(function (a, b) { return b.totalUsd - a.totalUsd; });
    out.esteAno = { ano: ano, empresas: linhas };
  }
  return out;
}

/** Tabela "DIVIDENDOS RECEBIDOS EM <ano>" de Dividendos Mensais: linhas de dados e posição. */
function tabelaDoAno_(sh, ano) {
  const n = sh.getLastRow();
  const d = sh.getRange(1, 1, n, 15).getValues();
  let titulo = -1;
  for (let r = 0; r < n; r++) {
    if (norm_(d[r][0]).indexOf('DIVIDENDOS RECEBIDOS EM ' + ano) === 0) { titulo = r; break; }
  }
  if (titulo < 0) return null;
  const cab = titulo + 1;
  let total = -1;
  for (let r = cab + 1; r < n; r++) {
    if (norm_(d[r][0]) === 'TOTAL MENSAL') { total = r; break; }
  }
  if (total < 0) return null;
  const dados = [];
  for (let r = cab + 1; r < total; r++) {
    const t = norm_(d[r][0]);
    if (!t) continue;
    dados.push({ linha: r + 1, ticker: t, empresa: v_(d[r][1]), meses: d[r].slice(2, 14) });
  }
  // linhas de folha (1-based)
  return { primeira: cab + 2, total: total + 1, dados: dados };
}

function lerWatchlist_() {
  const d = grelha_(folha_('Watchlist'), 400).valores;
  const out = [];
  for (let h = 0; h < d.length; h++) {
    if (norm_(d[h][0]) !== 'Ticker') continue;
    const m = indice_(d[h]);
    if (m[norm_('Valor Justo')] === undefined) continue;
    let tabela = '';
    for (let k = h - 1; k >= 0 && k >= h - 3; k--) {
      const t = norm_(d[k][0]);
      if (t.indexOf('TABELA') === 0) {
        const mm = t.match(/^TABELA \d+\s*[—-]\s*([^:(]+)/);
        tabela = mm ? norm_(mm[1]) : t;
        break;
      }
    }
    for (let r = h + 1; r < d.length; r++) {
      const ticker = campo_(d[r], m, 'Ticker');
      if (!ticker || String(ticker).indexOf('TABELA') === 0) break;
      out.push({
        tabela: tabela,
        ticker: ticker,
        empresa: campo_(d[r], m, 'Empresa'),
        nota: campo_(d[r], m, 'Setor / Nota'),
        preco: num_(campo_(d[r], m, 'Preço Atual')),
        intervalo52s: campo_(d[r], m, 'Posição no Intervalo 52 Semanas'),
        pe: num_(campo_(d[r], m, 'P/E Atual')),
        peJusto: num_(campo_(d[r], m, 'P/E Justo (a tua estimativa)')),
        valorJusto: num_(campo_(d[r], m, 'Valor Justo')),
        margem: num_(campo_(d[r], m, 'Margem (Justo vs Atual)')),
        sinal: campo_(d[r], m, 'Sinal'),
        prioridade: campo_(d[r], m, 'Prioridade / motivo'),
        moat: campo_(d[r], m, 'Moat'),
        camadaPotencial: campo_(d[r], m, 'Camada potencial'),
        crescimento: num_(campo_(d[r], m, 'Crescimento esperado (2 anos)')),
        fcfYield: num_(campo_(d[r], m, 'FCF yield hoje')),
        testeTroca: campo_(d[r], m, 'Teste de troca')
      });
    }
  }
  return out;
}

// ───────────────────────────── Visão geral (ponte v2) ─────────────────────────────

/** Corre uma leitura opcional; se falhar, guarda o motivo em "avisos" e devolve "vazio". */
function opcional_(avisos, nome, fn, vazio) {
  try {
    return fn();
  } catch (err) {
    avisos.push(nome + ': ' + String((err && err.message) || err));
    return vazio;
  }
}

/** Primeiro número de uma linha a partir da coluna "desde". */
function primeiroNumero_(linha, desde) {
  for (let c = desde; c < linha.length; c++) if (num_(linha[c]) !== null) return linha[c];
  return null;
}

/**
 * Tab Evolução Patrimonial, lida uma só vez:
 *  - evolucao: os meses já vividos (carteira, investido, S&P 500, património, reforço e metas) e, desde a
 *    v6, a rentabilidade de cada mês da carteira (RoR Mensal, sem e com dividendos) e do S&P 500 (preço,
 *    SPY), como a folha as calcula: a app encadeia-as para o YTD, como a tabela por ano da folha;
 *  - plano: o reforço e as taxas assumidas (linha 3) e as 4 metas de cada mês, também as futuras (até 2030).
 */
function lerEvolucaoEPlano_() {
  const d = grelha_(folha_('Evolução Patrimonial'), 400).valores;
  const h = acharLinha_(d, ['Mês', 'Portefólio ($)', 'Investido ($)']);
  if (h < 0) throw new Error('não encontrei a tabela EVOLUÇÃO MENSAL');
  const m = indice_(d[h]);
  // A linha de "preenchimento rápido" tem os valores de agora, para o mês que ainda não fechou.
  let netWorthAgora = null;
  let spyAgora = null;
  for (let r = 0; r < h; r++) {
    for (let c = 0; c < d[r].length; c++) {
      const t = norm_(d[r][c]);
      if (t === 'Net Worth Atual (€)') netWorthAgora = primeiroNumero_(d[r], c + 1);
      if (t.indexOf('SPY Equiv.') === 0) spyAgora = primeiroNumero_(d[r], c + 1);
    }
  }
  // Parâmetros do plano, por cima da tabela: "Reforço Mensal Assumido (€)" e as três taxas anuais.
  const plano = { reforcoMensalEur: null, taxaReferencia: null, taxaBase: null, taxaAlargada: null, meses: [] };
  for (let r = 0; r < h; r++) {
    for (let c = 0; c < d[r].length; c++) {
      const t = norm_(d[r][c]);
      if (t === 'Reforço Mensal Assumido (€)') plano.reforcoMensalEur = num_(primeiroNumero_(d[r], c + 1));
      if (t.indexOf('Taxa Anual') === 0) {
        const taxa = num_(primeiroNumero_(d[r], c + 1));
        if (t.indexOf('Refer') >= 0) plano.taxaReferencia = taxa;
        else if (t.indexOf('Base') >= 0) plano.taxaBase = taxa;
        else if (t.indexOf('Alargada') >= 0) plano.taxaAlargada = taxa;
      }
    }
  }
  const out = [];
  for (let r = h + 1; r < d.length; r++) {
    const data = d[r][m['Mês']];
    if (!(data instanceof Date)) break;
    const mes = Utilities.formatDate(data, fuso_(), 'yyyy-MM');
    const metas = {
      referenciaEur: num_(campo_(d[r], m, 'Meta Referência (€)')),
      baseEur: num_(campo_(d[r], m, 'Meta Base (€)')),
      alargadaEur: num_(campo_(d[r], m, 'Meta Alargada (€)')),
      historicaEur: num_(campo_(d[r], m, 'Meta Histórica (TTWROR real, €)'))
    };
    if (metas.referenciaEur !== null || metas.baseEur !== null || metas.alargadaEur !== null || metas.historicaEur !== null) {
      plano.meses.push({
        mes: mes, referenciaEur: metas.referenciaEur, baseEur: metas.baseEur,
        alargadaEur: metas.alargadaEur, historicaEur: metas.historicaEur
      });
    }
    const carteira = num_(campo_(d[r], m, 'Portefólio ($)'));
    if (carteira === null) continue; // meses futuros: só têm as metas
    out.push({
      mes: mes,
      carteiraUsd: carteira,
      investidoUsd: num_(campo_(d[r], m, 'Investido ($)')),
      sp500Usd: num_(campo_(d[r], m, 'S&P500 ($)')),
      netWorthEur: num_(campo_(d[r], m, 'Net Worth (€)')),
      // O que está escrito na célula; o netWorthEur do mês corrente pode vir do valor de agora.
      netWorthFolhaEur: num_(campo_(d[r], m, 'Net Worth (€)')),
      reforcoEur: num_(campo_(d[r], m, 'Reforço (€)')),
      rorMensal: num_(campo_(d[r], m, 'RoR Mensal')),
      rorComDividendos: num_(campo_(d[r], m, 'RoR c/ dividendos')),
      sp500Mensal: num_(campo_(d[r], m, 'S&P 500 mensal (preço, SPY)')),
      metaBaseEur: metas.baseEur,
      metaReferenciaEur: metas.referenciaEur,
      metaAlargadaEur: metas.alargadaEur
    });
  }
  const ultimo = out[out.length - 1];
  if (ultimo && ultimo.mes === hojeIso_().slice(0, 7)) {
    if (ultimo.sp500Usd === null) ultimo.sp500Usd = num_(spyAgora);
    if (ultimo.netWorthEur === null) ultimo.netWorthEur = num_(netWorthAgora);
  }
  return { evolucao: out, plano: plano.meses.length ? plano : null };
}

/** "Estou no caminho?" (tab Evolução Patrimonial), com os textos tal como a folha os mostra. */
function lerObjetivos_() {
  const g = grelha_(folha_('Evolução Patrimonial'), 40, true);
  const d = g.valores;
  const h = acharLinha_(d, ['Objetivo', 'Meta', 'Real', 'Estado']);
  if (h < 0) throw new Error('não encontrei a tabela de objetivos');
  const m = indice_(d[h]);
  const out = [];
  for (let r = h + 1; r < d.length; r++) {
    const objetivo = campo_(d[r], m, 'Objetivo');
    if (!objetivo) break;
    const real = num_(d[r][m.Real]);
    const estado = campo_(d[r], m, 'Estado');
    if (real === null || !estado) continue; // linhas de parâmetros
    const t = g.textos[r];
    out.push({
      objetivo: objetivo,
      meta: num_(d[r][m.Meta]),
      real: real,
      metaTexto: norm_(t[m.Meta]),
      realTexto: norm_(t[m.Real]),
      diferencaTexto: m['Diferença'] === undefined ? null : norm_(t[m['Diferença']]),
      estado: estado
    });
  }
  return out;
}

/** Património por classe de ativo (tab Investimentos). */
function lerPatrimonio_() {
  const d = grelha_(folha_('Investimentos'), 60).valores;
  const h = acharLinha_(d, ['Classe', 'Valor Atual (EUR)']);
  if (h < 0) throw new Error('não encontrei o património por classe');
  const m = indice_(d[h]);
  const classes = [];
  let total = null;
  for (let r = h + 1; r < d.length; r++) {
    const classe = campo_(d[r], m, 'Classe');
    if (!classe) break;
    const valor = num_(campo_(d[r], m, 'Valor Atual (EUR)'));
    if (String(classe).toUpperCase().indexOf('TOTAL') === 0) { total = valor; break; }
    if (valor === null) continue;
    classes.push({
      classe: classe,
      investidoEur: num_(campo_(d[r], m, 'Investido (EUR)')),
      valorEur: valor,
      valorizacao: num_(campo_(d[r], m, 'Valorização')),
      rendimentoAnualEur: num_(campo_(d[r], m, 'Rendimento Anual (EUR)'))
    });
  }
  return { totalEur: total, classes: classes };
}

/** A carteira contra o mesmo dinheiro no índice (tab Validação S&P 500). */
function lerComparacoes_() {
  const d = grelha_(folha_('Validação S&P 500'), 40).valores;
  const h = acharLinha_(d, ['Comparação', 'Diferença (€)']);
  if (h < 0) throw new Error('não encontrei a tabela de comparação');
  const m = indice_(d[h]);
  const out = [];
  for (let r = h + 1; r < d.length; r++) {
    const comparacao = campo_(d[r], m, 'Comparação');
    if (!comparacao) break;
    const diferenca = num_(campo_(d[r], m, 'Diferença (€)'));
    if (diferenca === null) continue;
    out.push({
      comparacao: comparacao,
      carteiraEur: num_(campo_(d[r], m, 'A tua carteira hoje (€)')),
      indiceEur: num_(campo_(d[r], m, 'Mesmo dinheiro no índice (€)')),
      diferencaEur: diferenca,
      leitura: campo_(d[r], m, 'Leitura'),
      principal: String(campo_(d[r], m, 'Nota') || '').indexOf('MEDIDA PRINCIPAL') >= 0
    });
  }
  return out;
}

/**
 * Câmbio médio de cada mês (euros por dólar), da tabela "Os teus dividendos — € por $ (média de cada mês)"
 * da tab Validação S&P 500 (ponte v5): {"2024-03": 0.9265, ...}. A app passa os dividendos de cada mês a
 * euros com ele, como a folha.
 */
function lerCambioMensal_() {
  const d = grelha_(folha_('Validação S&P 500'), 150).valores;
  const h = acharLinha_(d, ['Os teus dividendos — € por $ (média de cada mês)', 'Jan', 'Dez']);
  if (h < 0) throw new Error('não encontrei a tabela do câmbio de cada mês');
  const out = {};
  for (let r = h + 1; r < d.length; r++) {
    const ano = parseInt(norm_(d[r][0]), 10);
    if (!(ano >= 2000 && ano < 2100)) break;
    for (let mes = 0; mes < 12; mes++) {
      const x = num_(d[r][1 + mes]);
      if (x && x > 0.3 && x < 3) out[ano + '-' + ('0' + (mes + 1)).slice(-2)] = x;
    }
  }
  return out;
}

/** Peso de cada setor nas ações e o limite da tab Distribuição. */
function lerSetores_() {
  const d = grelha_(folha_('Distribuição'), 80).valores;
  const h = acharLinha_(d, ['Setor', 'Valor (EUR)', 'Limite (Setor)', 'Estado']);
  if (h < 0) throw new Error('não encontrei a alocação por setor');
  const m = indice_(d[h]);
  const out = [];
  for (let r = h + 1; r < d.length; r++) {
    const setor = campo_(d[r], m, 'Setor');
    const valor = num_(campo_(d[r], m, 'Valor (EUR)'));
    if (!setor || valor === null) break;
    out.push({
      setor: setor,
      valorEur: valor,
      pesoAcoes: num_(campo_(d[r], m, '% Ações')),
      numero: num_(campo_(d[r], m, 'Nº Posições')),
      limite: num_(campo_(d[r], m, 'Limite (Setor)')),
      estado: campo_(d[r], m, 'Estado')
    });
  }
  return out;
}

/** Resposta leve para a verificação de alertas do telemóvel: preços, variação, zona e sinal. */
function lerParaAlertas_() {
  const cart = lerCarteira_();
  return {
    meta: {
      atualizadoEm: Utilities.formatDate(new Date(), fuso_(), "yyyy-MM-dd'T'HH:mm:ssXXX"),
      hoje: hojeIso_()
    },
    posicoes: cart.posicoes.map(function (p) {
      return {
        ticker: p.ticker, empresa: p.empresa, camada: p.camada, precoUsd: p.precoUsd,
        varDiaPct: p.varDiaPct, zona: p.zona, sinal: p.sinal
      };
    }),
    etfs: cart.etfs.map(function (e) {
      return { ticker: e.ticker, nome: e.nome, precoEur: e.precoEur, varDiaPct: e.varDiaPct };
    }),
    watchlist: lerWatchlist_().map(function (w) {
      return {
        tabela: w.tabela, ticker: w.ticker, empresa: w.empresa, preco: w.preco, valorJusto: w.valorJusto,
        margem: w.margem, sinal: w.sinal, prioridade: w.prioridade, testeTroca: w.testeTroca
      };
    })
  };
}

/** Cabeçalho e bloco de linhas da tab Transações (as linhas livres já têm as fórmulas). */
function blocoTransacoes_(sh) {
  const n = Math.min(sh.getLastRow(), 2000);
  const d = sh.getRange(1, 1, n, sh.getLastColumn()).getValues();
  const h = acharLinha_(d, ['Data', 'Tipo', 'Ticker', 'Nº Unidades']);
  if (h < 0) throw new Error('Não encontrei o cabeçalho da tab Transações');
  const m = indice_(d[h]);
  const cMoeda = m.Moeda;
  if (cMoeda === undefined) throw new Error('Não encontrei a coluna Moeda em Transações');
  const formulas = sh.getRange(h + 2, cMoeda + 1, n - h - 1, 1).getFormulas();
  let fim = h + 1; // última linha (1-based) do bloco com fórmulas
  for (let i = 0; i < formulas.length; i++) {
    if (formulas[i][0]) fim = h + 2 + i;
    else if (d[h + 1 + i][m.Ticker] === '' && d[h + 1 + i][m.Data] === '') break;
  }
  return { dados: d, cab: h + 1, mapa: m, fim: fim };
}

function lerTransacoes_(quantas) {
  const sh = folha_('Transações');
  const b = blocoTransacoes_(sh);
  const out = [];
  for (let r = b.fim - 1; r >= b.cab && out.length < quantas; r--) {
    const l = b.dados[r];
    if (!v_(l[b.mapa.Ticker])) continue;
    out.push({
      linha: r + 1,
      data: campo_(l, b.mapa, 'Data'),
      tipo: campo_(l, b.mapa, 'Tipo'),
      classe: campo_(l, b.mapa, 'Classe'),
      ticker: campo_(l, b.mapa, 'Ticker'),
      unidades: num_(campo_(l, b.mapa, 'Nº Unidades')),
      preco: num_(campo_(l, b.mapa, 'Preço Unitário')),
      moeda: campo_(l, b.mapa, 'Moeda'),
      valor: num_(campo_(l, b.mapa, 'Valor Total')),
      comissao: num_(campo_(l, b.mapa, 'Comissão'))
    });
  }
  return out;
}

/**
 * Todas as compras e vendas da tab Transações, pela ordem da folha (ponte v4). "valor" vem na moeda da
 * linha (dólares nas ações, euros nos ETF); "valorEur" é a coluna Custo em EUR (com o câmbio desse dia).
 */
function lerMovimentos_(registo) {
  const b = blocoTransacoes_(folha_('Transações'));
  const out = [];
  for (let r = b.cab; r < b.fim; r++) {
    const l = b.dados[r];
    const tipo = campo_(l, b.mapa, 'Tipo');
    const ticker = campo_(l, b.mapa, 'Ticker');
    if (!ticker || (tipo !== 'Compra' && tipo !== 'Venda')) continue;
    const data = campo_(l, b.mapa, 'Data');
    const cambio = num_(campo_(l, b.mapa, 'Câmbio USD/EUR'));
    // Preço do índice nesse dia: o da folha (colunas R e S); se ainda for a fórmula, o que a ponte registou.
    let spy = num_(campo_(l, b.mapa, 'Preço SPY na Data ($)'));
    let vuaa = num_(campo_(l, b.mapa, 'Preço VUAA na Data (€)'));
    if ((spy === null || vuaa === null) && registo && typeof data === 'string') {
      const p = precoNoRegisto_(registo, data);
      if (p) {
        if (spy === null) spy = p[0];
        if (vuaa === null && cambio) vuaa = p[1] * cambio;
      }
    }
    out.push({
      linha: r + 1,
      data: data,
      tipo: tipo,
      classe: campo_(l, b.mapa, 'Classe'),
      ticker: String(ticker),
      unidades: num_(campo_(l, b.mapa, 'Nº Unidades')),
      preco: num_(campo_(l, b.mapa, 'Preço Unitário')),
      moeda: campo_(l, b.mapa, 'Moeda'),
      valor: num_(campo_(l, b.mapa, 'Valor Total')),
      cambio: cambio,
      valorEur: num_(campo_(l, b.mapa, 'Custo em EUR')),
      comissaoEur: num_(campo_(l, b.mapa, 'Comissão')),
      precoSpy: spy,
      precoIndiceEur: vuaa
    });
  }
  return out;
}

/**
 * Dividendos recebidos por empresa, em dólares brutos (tabela da direita de Dividendos Anuais, ponte v4).
 * "periodos" são os cabeçalhos das colunas de cada ano, como "2023 (Mai-Dez)".
 */
function lerDividendosPorEmpresa_() {
  const d = grelha_(folha_('Dividendos Anuais'), 150).valores;
  const h = acharLinha_(d, ['Ticker', 'Total']);
  if (h < 0) throw new Error('Não encontrei a tabela de dividendos por empresa');
  const m = indice_(d[h]);
  const cTicker = m.Ticker;
  const cTotal = m.Total;
  const colunas = [];
  for (let c = cTicker + 1; c < cTotal; c++) colunas.push(c);
  const empresas = [];
  for (let r = h + 1; r < d.length; r++) {
    const ticker = norm_(d[r][cTicker]);
    const total = num_(d[r][cTotal]);
    if (!ticker || total === null) break; // acaba na linha vazia ou na nota de baixo
    empresas.push({
      ticker: ticker,
      totalUsd: total,
      porPeriodo: colunas.map(function (c) { return num_(d[r][c]) || 0; })
    });
  }
  return { periodos: colunas.map(function (c) { return norm_(d[h][c]); }), empresas: empresas };
}

/**
 * Dividendos de cada empresa em cada mês, em dólares brutos (todas as tabelas "DIVIDENDOS RECEBIDOS EM
 * <ano>" de Dividendos Mensais, ponte v5). Só os meses com pagamento: [{ticker, mes: "2024-03", usd}].
 */
function lerDividendosPorMes_() {
  const sh = folha_('Dividendos Mensais');
  const n = sh.getLastRow();
  if (n < 1) return [];
  const d = sh.getRange(1, 1, n, 14).getValues();
  const out = [];
  for (let r = 0; r < n; r++) {
    const titulo = /^DIVIDENDOS RECEBIDOS EM (\d{4})/.exec(norm_(d[r][0]).toUpperCase());
    if (!titulo) continue;
    const ano = titulo[1];
    for (let k = r + 2; k < n; k++) {
      const t = norm_(d[k][0]);
      if (t.toUpperCase() === 'TOTAL MENSAL' || /^DIVIDENDOS RECEBIDOS EM/.test(t.toUpperCase())) break;
      if (!t || t === 'Ticker') continue;
      for (let mes = 0; mes < 12; mes++) {
        const x = num_(d[k][2 + mes]);
        if (x) out.push({ ticker: t, mes: ano + '-' + ('0' + (mes + 1)).slice(-2), usd: x });
      }
    }
  }
  return out;
}

// ─────────────────────── Preço do S&P 500 (VUAA) em cada transação ───────────────────────
//
// A Google não deixa a ponte ler o GOOGLEFINANCE histórico: fora do browser as colunas "Preço SPY na Data"
// e "Preço VUAA na Data" da tab Transações dão #N/A, e a comparação com o S&P 500 sai errada. Por isso:
//  1. sempre que a app pede dados ou verifica alertas, a ponte guarda o preço de hoje do SPY e do VUAA
//     (as células "Preço Atual" no topo da tab Transações, que são ao vivo e se leem bem);
//  2. quando a app pede dados, as transações dos dias anteriores que ainda têm a fórmula passam a ter o
//     valor: o da própria fórmula, se a folha o tiver, ou o preço que a ponte guardou desse dia.

const CHAVE_REGISTO_INDICE = 'precosIndice';
const DIAS_NO_REGISTO = 150;

/** Preços de hoje, registo dos últimos dias e (se fixar) transações passadas com o preço já em valor. */
function prepararIndice_(fixar) {
  const res = { hoje: null, registo: {}, avisos: [] };
  try {
    res.hoje = precosDeHoje_();
    res.registo = registarPrecosIndice_(res.hoje);
  } catch (err) {
    res.avisos.push('Preço do S&P 500 de hoje: ' + String((err && err.message) || err));
  }
  if (!fixar) return res;
  try {
    const f = fixarPrecosIndice_(res.registo);
    if (f.semPreco.length) {
      const quais = f.semPreco.slice(0, 6).join(', ') + (f.semPreco.length > 6 ? '…' : '');
      res.avisos.push('Transações: ' + f.semPreco.length + (f.semPreco.length === 1 ? ' linha' : ' linhas') +
        ' sem preço do S&P 500 (' + quais + '). Abre a folha no computador, copia as colunas R e S dessas ' +
        'linhas e cola só os valores (Ctrl+Shift+V).');
    }
  } catch (err) {
    res.avisos.push('Preço do S&P 500 nas transações: ' + String((err && err.message) || err));
  }
  return res;
}

/** Preço atual do SPY (dólares) e do VUAA (euros e dólares), do topo da tab Transações. */
function precosDeHoje_() {
  const sh = folha_('Transações');
  const topo = sh.getRange(1, 1, 5, Math.min(sh.getLastColumn(), 12)).getValues();
  let spy = null;
  let vuaaEur = null;
  topo.forEach(function (l) {
    for (let c = 0; c < l.length - 1; c++) {
      const t = norm_(l[c]);
      if (t === 'Preço Atual SPY ($)') spy = num_(l[c + 1]);
      if (t === 'Preço Atual VUAA (€)') vuaaEur = num_(l[c + 1]);
    }
  });
  let usdEur = null;
  folha_('Carteira').getRange(1, 1, 12, 2).getValues().forEach(function (l) {
    if (norm_(l[0]) === 'Taxa de câmbio USD/EUR') usdEur = num_(l[1]);
  });
  if (!(spy > 0) || !(vuaaEur > 0)) throw new Error('não encontrei "Preço Atual SPY ($)" e "Preço Atual VUAA (€)" no topo da tab Transações');
  return { data: hojeIso_(), spyUsd: spy, vuaaEur: vuaaEur, vuaaUsd: usdEur > 0 ? vuaaEur / usdEur : null, usdEur: usdEur };
}

/** Guarda o preço de hoje (o último do dia fica) e devolve o registo {aaaa-mm-dd: [spy $, vuaa $]}. */
function registarPrecosIndice_(hoje) {
  const props = PropertiesService.getScriptProperties();
  let registo = {};
  try { registo = JSON.parse(props.getProperty(CHAVE_REGISTO_INDICE) || '{}') || {}; } catch (e) { registo = {}; }
  if (hoje && hoje.spyUsd > 0 && hoje.vuaaUsd > 0) {
    const antes = registo[hoje.data];
    const agora = [Math.round(hoje.spyUsd * 100) / 100, Math.round(hoje.vuaaUsd * 10000) / 10000];
    if (!antes || antes[0] !== agora[0] || antes[1] !== agora[1]) {
      registo[hoje.data] = agora;
      const dias = Object.keys(registo).sort();
      dias.slice(0, Math.max(0, dias.length - DIAS_NO_REGISTO)).forEach(function (k) { delete registo[k]; });
      props.setProperty(CHAVE_REGISTO_INDICE, JSON.stringify(registo));
    }
  }
  return registo;
}

/**
 * [spy $, vuaa $, dia] registados nesse dia ou, se não houver (fim de semana, feriado, app sem uso), no
 * primeiro dia seguinte, até 6 (como a fórmula, que usa o primeiro fecho da semana).
 */
function precoNoRegisto_(registo, data) {
  if (!registo || !/^\d{4}-\d{2}-\d{2}$/.test(data)) return null;
  const t0 = Date.UTC(+data.slice(0, 4), +data.slice(5, 7) - 1, +data.slice(8, 10));
  for (let k = 0; k <= 6; k++) {
    const dia = new Date(t0 + k * 86400000);
    const chave = dia.getUTCFullYear() + '-' + ('0' + (dia.getUTCMonth() + 1)).slice(-2) + '-' + ('0' + dia.getUTCDate()).slice(-2);
    const p = registo[chave];
    if (p && p[0] > 0 && p[1] > 0) return [p[0], p[1], chave];
  }
  return null;
}

/**
 * Passa a valor o preço do SPY e do VUAA das transações de dias anteriores que ainda têm a fórmula. Usa o
 * valor da fórmula se a folha o tiver; senão o preço que a ponte registou nesse dia (VUAA em euros = preço
 * em dólares × câmbio da linha, como na fórmula). Devolve as linhas que ficaram sem preço.
 */
function fixarPrecosIndice_(registo) {
  const res = { fixadas: [], semPreco: [] };
  const tranca = LockService.getDocumentLock();
  if (!tranca.tryLock(5000)) return res; // outro pedido está a escrever: fica para a próxima
  try {
    const sh = folha_('Transações');
    const b = blocoTransacoes_(sh);
    const cSpy = b.mapa[norm_('Preço SPY na Data ($)')];
    const cVuaa = b.mapa[norm_('Preço VUAA na Data (€)')];
    const cCambio = b.mapa[norm_('Câmbio USD/EUR')];
    if (cSpy === undefined || cVuaa === undefined || cCambio === undefined) return res;
    const primeira = b.cab + 1; // 1.ª linha de dados (1-based)
    const n = b.fim - b.cab;
    if (n <= 0) return res;
    const fSpy = sh.getRange(primeira, cSpy + 1, n, 1).getFormulas();
    const fVuaa = sh.getRange(primeira, cVuaa + 1, n, 1).getFormulas();
    const hoje = hojeIso_();
    for (let i = 0; i < n; i++) {
      if (!fSpy[i][0] && !fVuaa[i][0]) continue;
      const l = b.dados[b.cab + i];
      if (!norm_(l[b.mapa.Ticker])) continue;
      const data = v_(l[b.mapa.Data]);
      if (typeof data !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(data) || data >= hoje) continue;
      let spy = num_(l[cSpy]);
      let vuaa = num_(l[cVuaa]);
      let doRegisto = null;
      if (spy === null || vuaa === null) {
        const p = precoNoRegisto_(registo, data);
        const cambio = num_(l[cCambio]);
        if (p && cambio) {
          if (spy === null) spy = p[0];
          if (vuaa === null) vuaa = Math.round(p[1] * cambio * 1e6) / 1e6;
          doRegisto = p[2];
        }
      }
      const linha = primeira + i;
      if (spy === null || vuaa === null) { res.semPreco.push(linha); continue; }
      if (fSpy[i][0]) sh.getRange(linha, cSpy + 1).setValue(spy);
      if (fVuaa[i][0]) sh.getRange(linha, cVuaa + 1).setValue(vuaa);
      if (doRegisto) {
        sh.getRange(linha, cVuaa + 1).setNote('Fixado pela ponte a ' + hoje + ' com o preço que registou a ' + doRegisto +
          ' (a fórmula GOOGLEFINANCE histórica não se lê fora do browser). Para voltar à fórmula, copia a de uma linha livre.');
      }
      l[cSpy] = spy;
      l[cVuaa] = vuaa;
      res.fixadas.push(linha);
    }
    if (res.fixadas.length) SpreadsheetApp.flush();
  } finally {
    tranca.releaseLock();
  }
  return res;
}

/** A tab Vendas: cada venda comparada com o preço de hoje ("Vendeste bem" / "Devias ter mantido"), ponte v4. */
function lerAnaliseVendas_() {
  const d = grelha_(folha_('Vendas'), 600).valores;
  const h = acharLinha_(d, ['Data Venda', 'Ticker', 'Veredito']);
  if (h < 0) throw new Error('Não encontrei o cabeçalho da tab Vendas');
  const m = indice_(d[h]);
  // "Dividendos Perdidos (preenche tu, opcional)": procura pelo início, o resto do nome pode mudar.
  let cDiv = -1;
  Object.keys(m).forEach(function (k) { if (k.indexOf('Dividendos Perdidos') === 0) cDiv = m[k]; });
  const out = [];
  for (let r = h + 1; r < d.length; r++) {
    if (norm_(d[r][0]).indexOf('TOTAL') === 0) break;
    const ticker = campo_(d[r], m, 'Ticker');
    if (!ticker) continue;
    out.push({
      data: campo_(d[r], m, 'Data Venda'),
      ticker: String(ticker),
      empresa: campo_(d[r], m, 'Empresa'),
      unidades: num_(campo_(d[r], m, 'Unidades')),
      precoVenda: num_(campo_(d[r], m, 'Preço Venda')),
      valorRecebido: num_(campo_(d[r], m, 'Valor Recebido')),
      precoAtual: num_(campo_(d[r], m, 'Preço Atual')),
      valorSeMantivesse: num_(campo_(d[r], m, 'Valor se Tivesse Mantido')),
      dividendosPerdidos: cDiv >= 0 ? num_(d[r][cDiv]) : null,
      diferenca: num_(campo_(d[r], m, 'Diferença')),
      diferencaPct: num_(campo_(d[r], m, 'Diferença %')),
      veredito: campo_(d[r], m, 'Veredito')
    });
  }
  return out;
}

// ───────────────────────────── Escrita ─────────────────────────────

function lerData_(texto) {
  const t = String(texto || '');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(t)) throw new Error('Data inválida: ' + t);
  const d = Utilities.parseDate(t, fuso_(), 'yyyy-MM-dd');
  if (Utilities.formatDate(d, fuso_(), 'yyyy-MM-dd') !== t) throw new Error('Data inválida: ' + t);
  const amanha = new Date(Date.now() + 36 * 3600 * 1000);
  if (d > amanha) throw new Error('A data não pode ser no futuro');
  return d;
}

function quantidadeNaCarteira_(ticker) {
  const c = lerCarteira_();
  const t = c.tickers.filter(function (x) { return x.ticker === ticker; })[0];
  return t ? { quantidade: t.quantidade, nome: t.nome, classe: t.classe } : null;
}

function registarTransacao_(p) {
  const tipo = p.tipo === 'Compra' || p.tipo === 'Venda' ? p.tipo : null;
  if (!tipo) throw new Error('O tipo tem de ser Compra ou Venda');
  const classe = p.classe === 'Ação' || p.classe === 'ETF' ? p.classe : null;
  if (!classe) throw new Error('A classe tem de ser Ação ou ETF');
  const ticker = String(p.ticker || '').trim().toUpperCase();
  if (!/^[A-Z0-9.:\-]{1,15}$/.test(ticker)) throw new Error('Ticker inválido: ' + p.ticker);
  const unidades = Number(p.unidades);
  const preco = Number(p.preco);
  const comissao = p.comissao === undefined || p.comissao === null || p.comissao === '' ? 0 : Number(p.comissao);
  if (!(unidades > 0)) throw new Error('O número de unidades tem de ser maior que zero');
  if (!(preco > 0)) throw new Error('O preço tem de ser maior que zero');
  if (!(comissao >= 0)) throw new Error('A comissão não pode ser negativa');
  const data = lerData_(p.data);

  const cart = lerCarteira_();
  const naCarteira = cart.tickers.filter(function (x) { return x.ticker === ticker; })[0] || null;
  if (tipo === 'Venda') {
    const tem = naCarteira ? naCarteira.quantidade : 0;
    if (unidades > tem + 1e-9) {
      throw new Error('Só tens ' + tem + ' de ' + ticker + ': não dá para vender ' + unidades);
    }
  }
  let cambio = p.cambio === undefined || p.cambio === null || p.cambio === '' ? null : Number(p.cambio);
  if (cambio === null) cambio = cart.cambio.usdEur;
  if (!(cambio > 0.3 && cambio < 3)) throw new Error('Câmbio USD/EUR inválido: ' + p.cambio);

  const sh = folha_('Transações');
  const b = blocoTransacoes_(sh);
  const m = b.mapa;
  const nCol = sh.getLastColumn();

  // Primeira linha livre do bloco (sem data nem ticker); se não houver, abre-se uma no fim.
  let linha = -1;
  for (let r = b.cab; r < b.fim; r++) {
    const l = b.dados[r];
    if (l[m.Ticker] === '' && l[m.Data] === '') { linha = r + 1; break; }
  }
  if (linha < 0) {
    // Insere dentro do bloco, para as fórmulas com intervalos fixos (até à última linha) crescerem,
    // e mantém a ordem: a última transação desce uma linha e a nova fica no fim.
    sh.insertRowBefore(b.fim);
    sh.getRange(b.fim + 1, 1, 1, nCol).copyTo(sh.getRange(b.fim, 1, 1, nCol));
    linha = b.fim + 1;
  }

  const escrever = function (nome, valor) {
    const c = m[norm_(nome)];
    if (c === undefined) throw new Error('Não encontrei a coluna "' + nome + '" em Transações');
    sh.getRange(linha, c + 1).setValue(valor);
  };
  escrever('Data', data);
  escrever('Tipo', tipo);
  escrever('Classe', classe);
  escrever('Ticker', ticker);
  escrever('Nº Unidades', unidades);
  escrever('Preço Unitário', preco);
  escrever('Câmbio USD/EUR', cambio);
  escrever('Comissão', comissao);
  SpreadsheetApp.flush();

  const depois = quantidadeNaCarteira_(ticker);
  const moeda = classe === 'Ação' ? '$' : '€';
  let msg = (tipo === 'Compra' ? 'Compra' : 'Venda') + ' registada na linha ' + linha + ' de Transações: ' +
    fmt_(unidades) + ' ' + ticker + ' a ' + fmt_(preco) + ' ' + moeda + '.';
  let aviso = null;
  if (!naCarteira) {
    aviso = ticker + ' ainda não está na tab Carteira. Junta-o lá (e na Avaliação e Zonas) para entrar nos totais.';
  } else if (depois) {
    msg += ' Agora tens ' + fmt_(depois.quantidade) + '.';
  }
  return { mensagem: msg, aviso: aviso, linha: linha };
}

function registarDividendo_(p) {
  const ticker = String(p.ticker || '').trim().toUpperCase();
  if (!/^[A-Z0-9.:\-]{1,15}$/.test(ticker)) throw new Error('Ticker inválido: ' + p.ticker);
  const valor = Number(p.valor);
  if (!(valor > 0)) throw new Error('O valor tem de ser maior que zero');
  const moeda = p.moeda || 'USD';
  const data = lerData_(p.data);
  const ano = Number(Utilities.formatDate(data, fuso_(), 'yyyy'));
  const mes = Number(Utilities.formatDate(data, fuso_(), 'M'));

  const cart = lerCarteira_();
  let usd;
  if (moeda === 'USD') usd = valor;
  else if (moeda === 'EUR') usd = valor / cart.cambio.usdEur;
  else if (moeda === 'GBP') usd = valor * cart.cambio.gbpUsd;
  else throw new Error('Moeda não suportada: ' + moeda);
  if (!(usd > 0) || !isFinite(usd)) throw new Error('Não consegui converter o valor para dólares');
  usd = Math.round(usd * 100) / 100;

  const sh = folha_('Dividendos Mensais');
  let tab = tabelaDoAno_(sh, ano);
  if (!tab) throw new Error('Não há a tabela "DIVIDENDOS RECEBIDOS EM ' + ano + '" em Dividendos Mensais');

  let alvo = tab.dados.filter(function (l) { return l.ticker.toUpperCase() === ticker; })[0];
  let novaLinha = false;
  if (!alvo) {
    // Linha nova dentro da tabela (antes da última), para os totais e as referências crescerem sozinhos.
    if (!tab.dados.length) throw new Error('A tabela de ' + ano + ' não tem linhas');
    const ultima = tab.dados[tab.dados.length - 1].linha;
    sh.insertRowBefore(ultima);
    sh.getRange(ultima + 1, 1, 1, 15).copyTo(sh.getRange(ultima, 1, 1, 15), { formatOnly: true });
    const info = cart.tickers.filter(function (x) { return x.ticker === ticker; })[0];
    sh.getRange(ultima, 1, 1, 2).setValues([[ticker, info ? info.nome : ticker]]);
    sh.getRange(ultima, 15).setFormula('=SUM(C' + ultima + ':N' + ultima + ')');
    novaLinha = true;
    alvo = { linha: ultima };
  }
  const celula = sh.getRange(alvo.linha, 2 + mes);
  if (celula.getFormula()) throw new Error('A célula de ' + MESES[mes - 1] + ' de ' + ticker + ' tem uma fórmula: regista-o na folha');
  const antes = num_(celula.getValue()) || 0;
  const depois = Math.round((antes + usd) * 100) / 100;
  celula.setValue(depois);
  const quando = Utilities.formatDate(new Date(), fuso_(), 'dd/MM/yyyy');
  const extra = moeda === 'USD' ? '' : ' (' + fmt_(valor) + ' ' + moeda + ')';
  const nota = celula.getNote();
  celula.setNote((nota ? nota + '\n' : '') + 'App ' + quando + ': +' + fmt_(usd) + ' $' + extra);
  SpreadsheetApp.flush();

  let msg = 'Dividendo de ' + ticker + ' registado em ' + MESES[mes - 1] + ' de ' + ano + ': +' + fmt_(usd) + ' $';
  if (antes > 0) msg += ' (o mês fica com ' + fmt_(depois) + ' $)';
  msg += '.';
  return {
    mensagem: msg,
    aviso: novaLinha ? ticker + ' não tinha linha na tabela de ' + ano + ': criei-a.' : null
  };
}

/**
 * Grava o património (net worth) de um mês na coluna Net Worth (€) da tab Evolução Patrimonial, na
 * linha desse mês — o que se fazia à mão a partir do "Preenchimento rápido". Não mexe em células com
 * fórmulas nem em meses que ainda não chegaram; deixa uma nota na célula.
 */
function registarPatrimonio_(p) {
  const mes = String(p.mes || '');
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(mes)) throw new Error('Mês inválido: ' + p.mes);
  const valor = Math.round(Number(p.valor) * 100) / 100;
  if (!(valor > 0) || !(valor < 1e9)) throw new Error('O valor tem de ser maior que zero');
  const nome = MESES[Number(mes.slice(5, 7)) - 1].toLowerCase() + ' de ' + mes.slice(0, 4);
  if (mes > Utilities.formatDate(new Date(), fuso_(), 'yyyy-MM')) throw new Error('Ainda não chegou ' + nome + '.');

  const sh = folha_('Evolução Patrimonial');
  const d = grelha_(sh, 400).valores;
  const h = acharLinha_(d, ['Mês', 'Portefólio ($)', 'Investido ($)']);
  if (h < 0) throw new Error('Não encontrei a tabela EVOLUÇÃO MENSAL em Evolução Patrimonial');
  const m = indice_(d[h]);
  const cMes = m[norm_('Mês')];
  const cValor = m[norm_('Net Worth (€)')];
  if (cValor === undefined) throw new Error('Não encontrei a coluna "Net Worth (€)" em Evolução Patrimonial');
  let linha = -1;
  for (let r = h + 1; r < d.length; r++) {
    const data = d[r][cMes];
    if (!(data instanceof Date)) break;
    if (Utilities.formatDate(data, fuso_(), 'yyyy-MM') === mes) { linha = r + 1; break; }
  }
  if (linha < 0) throw new Error('Não encontrei a linha de ' + nome + ' em Evolução Patrimonial');

  const celula = sh.getRange(linha, cValor + 1);
  if (celula.getFormula()) throw new Error('O Net Worth de ' + nome + ' tem uma fórmula: muda-o na folha');
  const antes = num_(celula.getValue());
  celula.setValue(valor);
  const quando = Utilities.formatDate(new Date(), fuso_(), 'dd/MM/yyyy');
  const nota = celula.getNote();
  celula.setNote((nota ? nota + '\n' : '') + 'App ' + quando + ': ' + eur_(valor) + (antes !== null ? ' (antes ' + eur_(antes) + ')' : ''));
  SpreadsheetApp.flush();

  let msg = 'Património de ' + nome + ' gravado na folha: ' + eur_(valor) + '.';
  if (antes !== null && antes !== valor) msg += ' Antes: ' + eur_(antes) + '.';
  return { mensagem: msg, aviso: null, linha: linha };
}

/** 12345.6 → "12 345,60 €". */
function eur_(n) {
  const partes = fmt_(n).split(',');
  return partes[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ') + (partes[1] ? ',' + partes[1] : '') + ' €';
}

function fmt_(n) {
  const r = Math.round(Number(n) * 100) / 100;
  let s = String(r);
  if (s.indexOf('.') >= 0) {
    const partes = s.split('.');
    s = partes[0] + ',' + (partes[1].length === 1 ? partes[1] + '0' : partes[1]);
  }
  return s;
}

/** Para testar no editor: Executar > testarPonte (mostra o resumo nos registos). */
function testarPonte() {
  const d = lerTudo_();
  Logger.log('Posições: %s · ETFs: %s · Watchlist: %s · Regras: %s · Indicadores: %s',
    d.posicoes.length, d.etfs.length, d.watchlist.length, d.regras.length, d.indicadores.length);
  Logger.log(JSON.stringify(d).slice(0, 2000));
}
