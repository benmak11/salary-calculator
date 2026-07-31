// rsu-shared.jsx — RSU/Equity feature: data model, vest-schedule math,
// mock ticker quotes, and the state hook wiring the whole flow together.
// Depends on shared.jsx (fmtMoney) being loaded first.

const CURRENT_TAX_YEAR = new Date().getFullYear();

// ── Mock ticker universe ───────────────────────────────────────
const TICKERS = [
  { symbol: 'AAPL', name: 'Apple Inc.', price: 232.14 },
  { symbol: 'RDDT', name: 'Reddit, Inc.', price: 142.30 },
  { symbol: 'MSFT', name: 'Microsoft Corporation', price: 421.90 },
  { symbol: 'GOOGL', name: 'Alphabet Inc.', price: 178.20 },
  { symbol: 'NVDA', name: 'NVIDIA Corporation', price: 135.40 },
  { symbol: 'AMZN', name: 'Amazon.com, Inc.', price: 186.90 },
  { symbol: 'TSLA', name: 'Tesla, Inc.', price: 248.50 },
  { symbol: 'META', name: 'Meta Platforms, Inc.', price: 512.30 },
  { symbol: 'NFLX', name: 'Netflix, Inc.', price: 680.20 },
  { symbol: 'PLTR', name: 'Palantir Technologies', price: 28.40 },
];

function searchTickers(query) {
  const q = (query || '').trim().toLowerCase();
  if (!q) return [];
  return TICKERS.filter(t =>
    t.symbol.toLowerCase().includes(q) || t.name.toLowerCase().includes(q)
  ).slice(0, 10);
}

function quoteFor(symbol) {
  return TICKERS.find(t => t.symbol === symbol) || null;
}

// ── Vesting schedule presets ───────────────────────────────────
const SCHEDULE_PRESETS = [
  { id: 'annual4', label: '4-year annual', sub: '25% each year', totalMonths: 48, cliffMonths: 12, freqMonths: 12 },
  { id: 'monthly1cliff', label: '4-year monthly, 1-yr cliff', sub: '1-yr cliff, then monthly', totalMonths: 48, cliffMonths: 12, freqMonths: 1 },
  { id: 'quarterly1cliff', label: '4-year quarterly, 1-yr cliff', sub: '1-yr cliff, then quarterly', totalMonths: 48, cliffMonths: 12, freqMonths: 3 },
  { id: 'custom', label: 'Custom', sub: 'Set your own terms', totalMonths: 48, cliffMonths: 12, freqMonths: 1 },
];

function scheduleLabel(schedule) {
  const preset = SCHEDULE_PRESETS.find(p => p.id === schedule.presetId);
  if (schedule.presetId !== 'custom') return preset ? preset.label : '—';
  const years = (schedule.totalMonths / 12).toFixed(schedule.totalMonths % 12 ? 1 : 0);
  const freq = { 1: 'monthly', 3: 'quarterly', 12: 'annual' }[schedule.freqMonths] || `${schedule.freqMonths}mo`;
  const cliff = schedule.cliffMonths > 0 ? `, ${schedule.cliffMonths}mo cliff` : '';
  return `${years}-yr ${freq}${cliff}`;
}

function addMonths(date, months) {
  const d = new Date(date);
  d.setMonth(d.getMonth() + months);
  return d;
}

// Build the list of discrete vest events for a grant.
// Returns [{ date, shares, isCliff }] sorted chronologically.
function buildVestEvents(sharesTotal, grantDateISO, schedule) {
  const { totalMonths, cliffMonths, freqMonths } = schedule;
  const grantDate = new Date(grantDateISO);
  const events = [];
  if (!sharesTotal || sharesTotal <= 0 || !grantDateISO) return events;

  const firstMonth = Math.max(cliffMonths, freqMonths, 1);
  const firstShares = Math.round(sharesTotal * (firstMonth / totalMonths));
  events.push({ date: addMonths(grantDate, firstMonth), shares: firstShares, isCliff: firstMonth > freqMonths });

  let month = firstMonth + freqMonths;
  let allocated = firstShares;
  while (month <= totalMonths + 0.001) {
    const isLast = month >= totalMonths - 0.001;
    const shares = isLast ? (sharesTotal - allocated) : Math.round(sharesTotal * (freqMonths / totalMonths));
    events.push({ date: addMonths(grantDate, Math.round(month)), shares, isCliff: false });
    allocated += shares;
    month += freqMonths;
  }
  return events;
}

// Group vest events by calendar year for the compressed timeline view.
function groupEventsByYear(events, pricePerShare) {
  const byYear = {};
  for (const ev of events) {
    const y = ev.date.getFullYear();
    if (!byYear[y]) byYear[y] = [];
    byYear[y].push(ev);
  }
  return Object.keys(byYear).sort().map(y => {
    const yearEvents = byYear[y].sort((a, b) => a.date - b.date);
    const totalShares = yearEvents.reduce((s, e) => s + e.shares, 0);
    return {
      year: parseInt(y, 10),
      events: yearEvents,
      totalShares,
      totalValue: totalShares * pricePerShare,
      isCurrentYear: parseInt(y, 10) === CURRENT_TAX_YEAR,
    };
  });
}

// Value vesting in the current tax year for a single grant.
function vestingThisYearValue(grant) {
  const events = buildVestEvents(grant.sharesTotal, grant.grantDate, grant.schedule);
  const shares = events
    .filter(e => e.date.getFullYear() === CURRENT_TAX_YEAR)
    .reduce((s, e) => s + e.shares, 0);
  return shares * (grant.pricePerShare || 0);
}

function nextVestDate(grant) {
  const events = buildVestEvents(grant.sharesTotal, grant.grantDate, grant.schedule);
  const now = new Date();
  const next = events.find(e => e.date >= now);
  return next ? next.date : null;
}

function fmtDate(d) {
  if (!d) return '—';
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}
function fmtMonthYear(d) {
  return d.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
}
function fmtShares(n) {
  return Math.round(n).toLocaleString('en-US');
}

// ── Demo seed data (used for populated-state artboards) ────────
const SEED_GRANTS = [
  {
    id: 'seed-aapl', ticker: 'AAPL', company: 'Apple Inc.', manual: false,
    sharesTotal: 400, pricePerShare: 232.14,
    grantDate: `${CURRENT_TAX_YEAR - 1}-03-15`,
    schedule: { presetId: 'monthly1cliff', totalMonths: 48, cliffMonths: 12, freqMonths: 1 },
  },
  {
    id: 'seed-rddt', ticker: 'RDDT', company: 'Reddit, Inc.', manual: false,
    sharesTotal: 250, pricePerShare: 142.30,
    grantDate: `${CURRENT_TAX_YEAR}-01-10`,
    schedule: { presetId: 'annual4', totalMonths: 48, cliffMonths: 12, freqMonths: 12 },
  },
];

function uid() { return 'g_' + Math.random().toString(36).slice(2, 10); }

function summarizeGrants(grants) {
  const total = grants.reduce((s, g) => s + vestingThisYearValue(g), 0);
  return {
    total,
    count: grants.length,
    tickers: grants.map(g => g.ticker).join(', '),
  };
}

// ── Top-level state hook for the interactive prototype ─────────
function useRSUState(seedGrants) {
  const [signedIn, setSignedIn] = React.useState(true);
  const [grants, setGrants] = React.useState(seedGrants || []);
  const [manualOverride, setManualOverride] = React.useState('');
  const [overrideOpen, setOverrideOpen] = React.useState(false);
  const [signedOutValue, setSignedOutValue] = React.useState('');
  const [view, setView] = React.useState('none'); // none | list | form | detail
  const [editingId, setEditingId] = React.useState(null);
  const [toast, setToast] = React.useState(null);
  const [nudge, setNudge] = React.useState(false);

  const addGrant = (g) => {
    const withId = { ...g, id: uid() };
    setGrants(gs => [...gs, withId]);
  };
  const updateGrant = (id, patch) => setGrants(gs => gs.map(g => g.id === id ? { ...g, ...patch } : g));
  const deleteGrant = (id) => {
    const removed = grants.find(g => g.id === id);
    setGrants(gs => gs.filter(g => g.id !== id));
    setToast({ msg: `${removed?.ticker || 'Grant'} deleted`, id });
    setTimeout(() => setToast(t => (t && t.id === id ? null : t)), 2600);
  };

  return {
    signedIn, setSignedIn,
    grants, setGrants, addGrant, updateGrant, deleteGrant,
    manualOverride, setManualOverride, overrideOpen, setOverrideOpen,
    signedOutValue, setSignedOutValue,
    view, setView, editingId, setEditingId,
    toast, setToast,
    nudge, setNudge,
    summary: summarizeGrants(grants),
  };
}

// Merge every grant's vest events (plus an optional one-time dated bonus)
// into a year-by-year outlook against a flat base annual income (salary +
// commission, no RSUs/bonus). Spans current tax year through the furthest
// vest year or the bonus payout year, whichever is later.
function buildYearlyOutlook(grants, baseAnnual, bonus) {
  const yearMap = {};
  grants.forEach(g => {
    const events = buildVestEvents(g.sharesTotal, g.grantDate, g.schedule);
    events.forEach(e => {
      const y = e.date.getFullYear();
      yearMap[y] = (yearMap[y] || 0) + e.shares * (g.pricePerShare || 0);
    });
  });
  const years = Object.keys(yearMap).map(Number);
  const bonusAmt = bonus && bonus.amount > 0 ? bonus.amount : 0;
  const bonusYear = bonus && bonus.year ? bonus.year : CURRENT_TAX_YEAR;
  if (years.length === 0 && bonusAmt <= 0) return [];

  let minYear = CURRENT_TAX_YEAR;
  let maxYear = CURRENT_TAX_YEAR;
  if (years.length) { minYear = Math.min(minYear, ...years); maxYear = Math.max(maxYear, ...years); }
  if (bonusAmt > 0) maxYear = Math.max(maxYear, bonusYear);

  const rows = [];
  for (let y = minYear; y <= maxYear; y++) {
    const rsu = yearMap[y] || 0;
    const bns = (bonusAmt > 0 && y === bonusYear) ? bonusAmt : 0;
    const extra = bns + rsu;
    const total = baseAnnual + extra;
    rows.push({
      year: y, base: baseAnnual, bonus: bns, rsu, extra, total,
      extraPct: total > 0 ? (extra / total) * 100 : 0,
      isCurrentYear: y === CURRENT_TAX_YEAR,
    });
  }
  return rows;
}

Object.assign(window, {
  CURRENT_TAX_YEAR, TICKERS, searchTickers, quoteFor,
  SCHEDULE_PRESETS, scheduleLabel, buildVestEvents, groupEventsByYear,
  vestingThisYearValue, nextVestDate, fmtDate, fmtMonthYear, fmtShares,
  SEED_GRANTS, uid, summarizeGrants, useRSUState, buildYearlyOutlook,
});
