/* ============================================================
   Hanalyser dashboard.js
   Handles: auth guard, tab routing, stock analysis,
            Chart.js rendering, watchlist, history
   ============================================================ */

const token = localStorage.getItem('token');
if (!token) window.location.href = '/login.html';

const API = {
  headers: () => ({
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + localStorage.getItem('token')
  }),
  get: (url) => fetch(url, { headers: API.headers() }),
  post: (url, body) => fetch(url, { method: 'POST', headers: API.headers(), body: JSON.stringify(body) }),
  delete: (url) => fetch(url, { method: 'DELETE', headers: API.headers() })
};

let confidenceChartInst = null;
let valuationChartInst  = null;
let riskChartInst       = null;

let currentAnalysis = null;
let selectedExchange = 'NS';

function updateClock() {
  const el = document.getElementById('topbarTime');
  if (el) el.textContent = new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}
setInterval(updateClock, 1000);
updateClock();

document.addEventListener('DOMContentLoaded', () => {
  const name = localStorage.getItem('userName') || 'Analyst';
  const email = localStorage.getItem('userEmail') || '';

  // Populate user info
  setEl('sidebarName', name);
  setEl('welcomeName', name.split(' ')[0]);
  const av = document.getElementById('userAvatar');
  if (av) av.textContent = name.charAt(0).toUpperCase();

  loadDashboardStats();
  loadRecentHistory();

  // Enter key triggers analyze
  const inp = document.getElementById('analyzeInput');
  if (inp) inp.addEventListener('keydown', e => { if (e.key === 'Enter') analyzeStock(); });
  const qi = document.getElementById('quickTicker');
  if (qi) qi.addEventListener('keydown', e => { if (e.key === 'Enter') quickAnalyze(); });
});

function showTab(tab) {
  document.querySelectorAll('.tab-content').forEach(s => s.classList.remove('active'));
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));

  const section = document.getElementById('tab-' + tab);
  if (section) section.classList.add('active');

  const navItems = document.querySelectorAll('.nav-item');
  const idx = ['dashboard','analyze','watchlist','history'].indexOf(tab);
  if (navItems[idx]) navItems[idx].classList.add('active');

  const breadcrumbs = { dashboard: 'Dashboard', analyze: 'Analyze Stock', watchlist: 'Watchlist', history: 'History' };
  setEl('breadcrumb', breadcrumbs[tab] || tab);

  if (tab === 'watchlist') loadWatchlist();
  if (tab === 'history')   loadHistory();
  if (tab === 'dashboard') loadDashboardStats();

  // Close sidebar on mobile
  if (window.innerWidth <= 768) {
    document.getElementById('sidebar').classList.remove('open');
  }
}

function toggleSidebar() {
  document.getElementById('sidebar').classList.toggle('open');
}

function logout() {
  localStorage.clear();
  window.location.href = '/login.html';
}

async function loadDashboardStats() {
  try {
    const res = await API.get('/api/stocks/stats');
    if (!res.ok) { if (res.status === 401) logout(); return; }
    const data = await res.json();
    animateCount('statTotal', data.totalAnalyses || 0);
    animateCount('statBuy',   data.buyCount  || 0);
    animateCount('statHold',  data.holdCount || 0);
    animateCount('statSell',  data.sellCount || 0);
    setEl('welcomeName', (data.userName || 'Analyst').split(' ')[0]);
  } catch (e) { console.warn('Stats load failed:', e.message); }
}

async function loadRecentHistory() {
  try {
    const res = await API.get('/api/stocks/history/recent');
    if (!res.ok) return;
    const items = await res.json();
    const container = document.getElementById('recentHistory');
    if (!container) return;
    if (!items || items.length === 0) {
      container.innerHTML = '<div class="empty-state">No analyses yet. Try analyzing a stock!</div>';
      return;
    }
    container.innerHTML = items.map(item => `
      <div class="recent-item" onclick="analyzeFromHistory('${item.ticker}')">
        <div>
          <div class="recent-ticker">${item.ticker}</div>
          <div class="recent-company">${item.companyName || ''}</div>
        </div>
        <div>
          <span class="recent-badge badge-${item.recommendation}">${item.recommendation}</span>
        </div>
      </div>
    `).join('');
  } catch (e) { console.warn('Recent history failed:', e.message); }
}

function analyzeFromHistory(ticker) {
  showTab('analyze');
  setTickerInputFromYahooSymbol(ticker);
  analyzeStock();
}

function setExchange(exchange) {
  selectedExchange = exchange === 'BO' ? 'BO' : 'NS';
  document.querySelectorAll('.exchange-option').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.exchange === selectedExchange);
  });
}

function setQuickTicker(ticker) {
  document.getElementById('quickTicker').value = ticker;
}

function quickAnalyze() {
  const ticker = document.getElementById('quickTicker').value.trim();
  if (!ticker) return;
  showTab('analyze');
  setTickerInputFromYahooSymbol(ticker);
  analyzeStock();
}

async function analyzeStock() {
  const input = document.getElementById('analyzeInput');
  const rawTicker = input ? input.value.trim().toUpperCase() : '';
  const suffixMatch = rawTicker.match(/\.(NS|BO)$/);
  const exchange = suffixMatch ? suffixMatch[1] : selectedExchange;
  if (suffixMatch) setExchange(exchange);
  const baseTicker = stripIndianExchangeSuffix(rawTicker);
  const ticker = baseTicker ? `${baseTicker}.${exchange}` : '';

  if (!baseTicker) {
    showAnalyzeError('Please enter an Indian stock ticker (e.g. TCS, INFY, RELIANCE)');
    return;
  }
  if (!/^[A-Z0-9&\-]{1,20}$/.test(baseTicker)) {
    showAnalyzeError('Invalid ticker format. Enter the ticker only, then choose NSE or BSE (e.g. TCS, M&M, BAJAJ-AUTO)');
    return;
  }

  setAnalyzeLoading(true);
  hideAnalyzeError();
  hideResults();

  try {
    const res = await API.get('/api/stocks/analyze/' + encodeURIComponent(ticker));
    if (res.status === 401) { logout(); return; }
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Analysis failed');
    if (!data.currentPrice || data.currentPrice === 0) throw new Error(`Ticker not found on ${exchange === 'NS' ? 'NSE' : 'BSE'}. Try the other exchange.`);

    currentAnalysis = data;
    renderAnalysis(data);
    loadDashboardStats();
    loadRecentHistory();

  } catch (e) {
    showAnalyzeError(e.message);
  } finally {
    setAnalyzeLoading(false);
  }
}

function renderAnalysis(d) {
  // Company Header
  setEl('rTicker', d.ticker);
  setEl('rCompanyName', d.companyName || d.ticker);
  setEl('rExchange', d.exchange || 'N/A');
  setEl('rCurrency', d.currency || 'INR');

  const sym = currencySymbol(d.currency);
  setEl('rCurrentPrice', sym + fmt(d.currentPrice));

  // Price change vs previous close
  if (d.previousClose && d.previousClose > 0) {
    const chg = d.currentPrice - d.previousClose;
    const pct = (chg / d.previousClose * 100);
    const sign = chg >= 0 ? '+' : '';
    const el = document.getElementById('rPriceChange');
    if (el) {
      el.textContent = `${sign}${sym}${fmt(Math.abs(chg))} (${sign}${pct.toFixed(2)}%)`;
      el.style.color = 'var(--text)';
    }
  }

  // Recommendation badge
  const badge = document.getElementById('rBadge');
  if (badge) {
    badge.textContent = d.recommendation;
    badge.className = 'recommendation-badge rec-' + d.recommendation;
  }

  // Key Metrics
  setEl('rFairValue',      sym + fmt(d.fairValue));
  setEl('rIntrinsicValue', sym + fmt(d.intrinsicValue));
  setEl('rGrahamValue',    sym + fmt(d.grahamValue));

  const mosEl = document.getElementById('rMOS');
  if (mosEl) {
    const mos = d.marginOfSafety || 0;
    mosEl.textContent = (mos >= 0 ? '+' : '') + mos.toFixed(1) + '%';
    mosEl.style.color = 'var(--text)';
  }

  setEl('rPE',  (d.peRatio > 0 ? d.peRatio.toFixed(1) + 'x' : 'N/A'));
  setEl('rEPS', d.eps > 0 ? sym + fmt(d.eps) : 'N/A');

  // Recommendation panel
  const recAction = document.getElementById('recAction');
  if (recAction) {
    recAction.textContent = d.recommendation;
    recAction.className = 'rec-action ' + d.recommendation;
  }
  const recVal = document.getElementById('recValuation');
  if (recVal) {
    recVal.textContent = d.valuation.replace('_', ' ');
    recVal.className = 'rec-valuation ' + d.valuation;
  }

  setEl('rBuyBelow',  sym + fmt(d.buyBelow));
  setEl('rHoldUntil', sym + fmt(d.holdUntil));
  setEl('rSellAbove', sym + fmt(d.sellAbove));
  setEl('rSummary', d.analystSummary || '');

  // Fundamentals
  setEl('fMarketCap',  formatMarketCap(d.marketCap, sym));
  setEl('fVolume',     formatVolume(d.volume));
  setEl('f52High',     sym + fmt(d.fiftyTwoWeekHigh));
  setEl('f52Low',      sym + fmt(d.fiftyTwoWeekLow));
  setEl('fBeta',       d.beta > 0 ? d.beta.toFixed(2) : 'N/A');
  setEl('fPB',         d.pbRatio > 0 ? d.pbRatio.toFixed(2) + 'x' : 'N/A');
  setEl('fDivYield',   d.dividendYield > 0 ? d.dividendYield.toFixed(2) + '%' : 'N/A');
  setEl('fDE',         d.debtToEquity > 0 ? d.debtToEquity.toFixed(1) + '%' : 'N/A');
  setEl('fRevGrowth',  d.revenueGrowth != null ? d.revenueGrowth.toFixed(1) + '%' : 'N/A');
  setEl('fEPSGrowth',  d.earningsGrowth != null ? d.earningsGrowth.toFixed(1) + '%' : 'N/A');
  setEl('fForwardPE',  d.forwardPE > 0 ? d.forwardPE.toFixed(1) + 'x' : 'N/A');
  setEl('fDayRange',   sym + fmt(d.dayLow) + ' - ' + sym + fmt(d.dayHigh));

  // Charts
  renderConfidenceChart(d);
  renderValuationChart(d, sym);
  renderRiskMeter(d);

  // Risk info
  setEl('rRiskScore', (d.riskScore || 0).toFixed(1) + '/10');
  const riskLevelEl = document.getElementById('rRiskLevel');
  if (riskLevelEl) {
    riskLevelEl.textContent = d.riskLevel || 'MEDIUM';
    riskLevelEl.style.color = 'var(--text)';
  }

  // Confidence center
  setEl('rConfidence', (d.confidence || 0) + '%');

  // Legend values
  setEl('legBuy',  (d.buyConfidence  || 0).toFixed(0) + '%');
  setEl('legHold', (d.holdConfidence || 0).toFixed(0) + '%');
  setEl('legSell', (d.sellConfidence || 0).toFixed(0) + '%');

  document.getElementById('analysisResults').style.display = 'block';
}

function renderConfidenceChart(d) {
  const ctx = document.getElementById('confidenceChart');
  if (!ctx) return;
  if (confidenceChartInst) { confidenceChartInst.destroy(); confidenceChartInst = null; }

  confidenceChartInst = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: ['BUY', 'HOLD', 'SELL'],
      datasets: [{
        data: [
          +(d.buyConfidence  || 0).toFixed(1),
          +(d.holdConfidence || 0).toFixed(1),
          +(d.sellConfidence || 0).toFixed(1)
        ],
        backgroundColor: ['rgba(212,175,55,0.95)', 'rgba(150,150,150,0.85)', 'rgba(255,255,255,0.72)'],
        borderColor: ['#d4af37', '#969696', '#ffffff'],
        borderWidth: 2,
        hoverOffset: 6
      }]
    },
    options: {
      cutout: '72%',
      responsive: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: { label: ctx => ` ${ctx.label}: ${ctx.parsed.toFixed(1)}%` }
        }
      },
      animation: { animateRotate: true, duration: 800 }
    }
  });
}

function renderValuationChart(d, sym) {
  const ctx = document.getElementById('valuationChart');
  if (!ctx) return;
  if (valuationChartInst) { valuationChartInst.destroy(); valuationChartInst = null; }

  const labels = ['Current Price', 'Fair Value', 'Intrinsic Value', 'Graham Value', 'Buy Below', 'Sell Above'];
  const values = [d.currentPrice, d.fairValue, d.intrinsicValue, d.grahamValue, d.buyBelow, d.sellAbove];

  const colors = values.map((v, i) => {
    if (i === 0) return 'rgba(255,255,255,0.90)';
    if (i === 1) return 'rgba(212,175,55,0.92)';
    if (i === 4) return 'rgba(170,170,170,0.88)';
    if (i === 5) return 'rgba(105,105,105,0.88)';
    return 'rgba(255,255,255,0.34)';
  });

  valuationChartInst = new Chart(ctx, {
    type: 'bar',
    data: {
      labels,
      datasets: [{
        label: 'Price (' + (d.currency || 'INR') + ')',
        data: values,
        backgroundColor: colors,
        borderColor: colors.map(c => c.replace('0.85','1').replace('0.75','1').replace('0.15','0.4')),
        borderWidth: 1,
        borderRadius: 6
      }]
    },
    options: {
      responsive: true,
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: { label: ctx => ` ${sym}${ctx.parsed.y.toLocaleString('en-IN', { maximumFractionDigits: 2 })}` }
        }
      },
      scales: {
        x: {
          ticks: { color: '#d7d7d7', font: { size: 11 } },
          grid: { color: 'rgba(255,255,255,0.04)' }
        },
        y: {
          ticks: {
            color: '#d7d7d7', font: { size: 11 },
            callback: v => sym + (v >= 1000 ? (v/1000).toFixed(0) + 'K' : v)
          },
          grid: { color: 'rgba(255,255,255,0.06)' }
        }
      },
      animation: { duration: 700 }
    }
  });
}

function renderRiskMeter(d) {
  const ctx = document.getElementById('riskChart');
  if (!ctx) return;
  if (riskChartInst) { riskChartInst.destroy(); riskChartInst = null; }

  const score = Math.max(0, Math.min(10, d.riskScore || 0));
  const remainder = 10 - score;

  // Semi-circle: rotation=-90deg, circumference=half
  riskChartInst = new Chart(ctx, {
    type: 'doughnut',
    data: {
      datasets: [{
        data: [score, remainder, 10],  // filled, empty, hidden bottom half
        backgroundColor: [
          score < 3.5 ? 'rgba(212,175,55,0.95)' : score < 6.5 ? 'rgba(170,170,170,0.85)' : 'rgba(255,255,255,0.65)',
          'rgba(255,255,255,0.08)',
          'rgba(0,0,0,0)'             // transparent bottom
        ],
        borderWidth: 0,
        borderRadius: 4
      }]
    },
    options: {
      cutout: '68%',
      rotation: -90,
      circumference: 180,
      responsive: false,
      plugins: { legend: { display: false }, tooltip: { enabled: false } },
      animation: { duration: 800 }
    }
  });
}

async function addCurrentToWatchlist() {
  if (!currentAnalysis) return;
  const btn = document.getElementById('watchlistBtn');
  if (btn) btn.textContent = 'Adding...';
  try {
    const res = await API.post('/api/watchlist/add', {
      ticker: currentAnalysis.ticker,
      companyName: currentAnalysis.companyName,
      targetPrice: currentAnalysis.fairValue
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Failed');
    if (btn) { btn.textContent = 'Added to Watchlist'; btn.style.color = 'var(--text)'; }
    setTimeout(() => {
      if (btn) { btn.textContent = '+ Add to Watchlist'; btn.style.color = ''; }
    }, 3000);
  } catch (e) {
    if (btn) { btn.textContent = e.message.includes('Already') ? 'Already in Watchlist' : 'Failed'; }
    setTimeout(() => { if (btn) btn.textContent = '+ Add to Watchlist'; }, 2500);
  }
}

async function loadWatchlist() {
  const container = document.getElementById('watchlistContent');
  if (!container) return;
  container.innerHTML = '<div class="empty-state">Loading...</div>';
  try {
    const res = await API.get('/api/watchlist');
    if (!res.ok) { container.innerHTML = '<div class="empty-state card">Failed to load watchlist.</div>'; return; }
    const items = await res.json();
    if (!items || items.length === 0) {
      container.innerHTML = '<div class="empty-state card">No stocks in watchlist.<br>Analyze a stock and click "+ Add to Watchlist".</div>';
      return;
    }
    container.innerHTML = items.map(item => `
      <div class="watchlist-card" id="wl-${item.ticker}">
        <div class="wl-header">
          <div class="wl-ticker">${item.ticker}</div>
          <button class="wl-remove" onclick="removeFromWatchlist('${item.ticker}')" title="Remove">x</button>
        </div>
        <div class="wl-name">${item.companyName || item.ticker}</div>
        ${item.targetPrice ? `<div style="font-size:12px;color:var(--text2);margin-bottom:10px">Target: <span style="color:var(--gold);font-weight:600">${currencySymbol('INR')}${fmt(item.targetPrice)}</span></div>` : ''}
        <button class="wl-analyze" onclick="analyzeFromWatchlist('${item.ticker}')">Analyze Now</button>
      </div>
    `).join('');
  } catch (e) {
    container.innerHTML = '<div class="empty-state card">Failed to load watchlist.</div>';
  }
}

async function removeFromWatchlist(ticker) {
  try {
    const res = await API.delete('/api/watchlist/' + encodeURIComponent(ticker));
    if (res.ok) {
      const card = document.getElementById('wl-' + ticker);
      if (card) { card.style.opacity = '0'; card.style.transform = 'scale(0.95)'; card.style.transition = 'all 0.3s'; setTimeout(() => loadWatchlist(), 350); }
    }
  } catch (e) { console.warn('Remove failed:', e); }
}

function analyzeFromWatchlist(ticker) {
  showTab('analyze');
  setTickerInputFromYahooSymbol(ticker);
  analyzeStock();
}

function stripIndianExchangeSuffix(ticker) {
  return ticker.replace(/\.(NS|BO)$/i, '');
}

function setTickerInputFromYahooSymbol(ticker) {
  const symbol = (ticker || '').trim().toUpperCase();
  const suffixMatch = symbol.match(/\.(NS|BO)$/);
  if (suffixMatch) setExchange(suffixMatch[1]);
  document.getElementById('analyzeInput').value = stripIndianExchangeSuffix(symbol);
}

async function loadHistory() {
  const container = document.getElementById('historyContent');
  if (!container) return;
  container.innerHTML = '<div class="empty-state card">Loading...</div>';
  try {
    const res = await API.get('/api/stocks/history?page=0&size=25');
    if (!res.ok) { container.innerHTML = '<div class="empty-state card">Failed to load history.</div>'; return; }
    const page = await res.json();
    const items = page.content || [];
    if (!items.length) {
      container.innerHTML = '<div class="empty-state card">No analysis history yet.</div>';
      return;
    }
    container.innerHTML = `
      <div class="card" style="padding:0;overflow:hidden">
        <table class="history-table">
          <thead>
            <tr>
              <th>Ticker</th>
              <th>Company</th>
              <th>Price</th>
              <th>Fair Value</th>
              <th>MoS</th>
              <th>P/E</th>
              <th>Valuation</th>
              <th>Signal</th>
              <th>Confidence</th>
              <th>Date</th>
            </tr>
          </thead>
          <tbody>
            ${items.map(h => `
              <tr onclick="analyzeFromHistory('${h.ticker}')" style="cursor:pointer">
                <td class="ht-ticker">${h.ticker}</td>
                <td style="max-width:160px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px;color:var(--text2)">${h.companyName || '-'}</td>
                <td style="font-family:var(--mono)">${currencySymbol('INR')}${fmt(h.currentPrice)}</td>
                <td style="font-family:var(--mono);color:var(--gold)">${currencySymbol('INR')}${fmt(h.fairValue)}</td>
                <td style="color:var(--text);font-weight:600">${h.marginOfSafety != null ? (h.marginOfSafety >= 0 ? '+' : '') + h.marginOfSafety.toFixed(1) + '%' : '-'}</td>
                <td style="font-family:var(--mono)">${h.peRatio > 0 ? h.peRatio.toFixed(1) + 'x' : '-'}</td>
                <td><span class="valuation-tag tag-${h.valuation}">${(h.valuation || '').replace('_',' ')}</span></td>
                <td><span class="recent-badge badge-${h.recommendation}">${h.recommendation}</span></td>
                <td style="font-family:var(--mono);color:var(--text2)">${h.confidence != null ? h.confidence + '%' : '-'}</td>
                <td class="ht-date">${formatDate(h.analyzedAt)}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    `;
  } catch (e) {
    container.innerHTML = '<div class="empty-state card">Failed to load history.</div>';
  }
}

function setEl(id, val) {
  const el = document.getElementById(id);
  if (el) el.textContent = val;
}

function fmt(n) {
  if (n == null || n === 0) return '0';
  return Number(n).toLocaleString('en-IN', { maximumFractionDigits: 2, minimumFractionDigits: 0 });
}

function formatMarketCap(mc, sym) {
  if (!mc || mc === 0) return 'N/A';
  if (mc >= 1e12) return sym + (mc / 1e12).toFixed(2) + 'T';
  if (mc >= 1e9)  return sym + (mc / 1e9).toFixed(2)  + 'B';
  if (mc >= 1e7)  return sym + (mc / 1e7).toFixed(2)  + 'Cr';
  if (mc >= 1e5)  return sym + (mc / 1e5).toFixed(2)  + 'L';
  return sym + fmt(mc);
}

function formatVolume(v) {
  if (!v || v === 0) return 'N/A';
  if (v >= 1e7) return (v / 1e7).toFixed(2) + 'Cr';
  if (v >= 1e5) return (v / 1e5).toFixed(2) + 'L';
  if (v >= 1000) return (v / 1000).toFixed(1) + 'K';
  return v.toString();
}

function currencySymbol(currency) {
  const map = { INR: '\u20B9', USD: '$', EUR: '\u20AC', GBP: '\u00A3', JPY: '\u00A5' };
  return map[(currency || 'INR').toUpperCase()] || '\u20B9';
}

function formatDate(dt) {
  if (!dt) return '-';
  const d = new Date(dt);
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: '2-digit' })
       + ' ' + d.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
}

function animateCount(id, target) {
  const el = document.getElementById(id);
  if (!el) return;
  const duration = 600;
  const start = parseInt(el.textContent) || 0;
  const range = target - start;
  const startTime = performance.now();
  const step = (now) => {
    const elapsed = now - startTime;
    const progress = Math.min(elapsed / duration, 1);
    el.textContent = Math.floor(start + range * progress);
    if (progress < 1) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}

function setAnalyzeLoading(loading) {
  const btnText   = document.getElementById('analyzeBtnText');
  const btnLoader = document.getElementById('analyzeBtnLoader');
  const btn       = document.getElementById('analyzeBtn');
  const loadDiv   = document.getElementById('analyzeLoading');
  if (btnText)   btnText.style.display   = loading ? 'none' : 'inline';
  if (btnLoader) btnLoader.style.display = loading ? 'inline' : 'none';
  if (btn)       btn.disabled            = loading;
  if (loadDiv)   loadDiv.style.display   = loading ? 'flex' : 'none';
}

function hideResults() {
  const r = document.getElementById('analysisResults');
  if (r) r.style.display = 'none';
  // Destroy charts so canvas is clean
  if (confidenceChartInst) { confidenceChartInst.destroy(); confidenceChartInst = null; }
  if (valuationChartInst)  { valuationChartInst.destroy();  valuationChartInst  = null; }
  if (riskChartInst)       { riskChartInst.destroy();       riskChartInst       = null; }
}

function showAnalyzeError(msg) {
  const el = document.getElementById('analyzeError');
  if (el) { el.textContent = msg; el.style.display = 'block'; }
}

function hideAnalyzeError() {
  const el = document.getElementById('analyzeError');
  if (el) el.style.display = 'none';
}

