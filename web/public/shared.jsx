// shared.jsx — Incomatic prototype shared state + utilities
// Mock calculation that approximates the /v1/calculate output shape so the
// Insights tab can render a believable take-home breakdown without a server.

const SECTIONS = ['earnings', 'federal', 'state', 'benefits'];
const SECTION_LABELS = {
  earnings: 'Earnings', federal: 'Federal', state: 'State', benefits: 'Benefits',
};
const PAY_FREQUENCIES = [
  { id: 'Weekly', periods: 52 },
  { id: 'Bi-weekly', periods: 26 },
  { id: 'Semi-monthly', periods: 24 },
  { id: 'Monthly', periods: 12 },
  { id: 'Annual', periods: 1 },
];
const STATES = [
  { code: 'CA', name: 'California' }, { code: 'NY', name: 'New York' },
  { code: 'TX', name: 'Texas' }, { code: 'FL', name: 'Florida' },
  { code: 'MD', name: 'Maryland' }, { code: 'WA', name: 'Washington' },
  { code: 'IL', name: 'Illinois' }, { code: 'MA', name: 'Massachusetts' },
];

function periodsFor(freq) {
  return (PAY_FREQUENCIES.find(p => p.id === freq) || PAY_FREQUENCIES[1]).periods;
}

function fmtMoney(n, { signed = false, cents = true } = {}) {
  if (n == null || isNaN(n)) return '—';
  const abs = Math.abs(n);
  const s = abs.toLocaleString('en-US', {
    style: 'currency', currency: 'USD',
    minimumFractionDigits: cents ? 2 : 0,
    maximumFractionDigits: cents ? 2 : 0,
  });
  if (signed && n < 0) return '−' + s;
  if (signed && n > 0) return s;
  return n < 0 ? '−' + s : s;
}

function mockCalculate(state) {
  const bonusAmt = parseFloat(state.bonus) || 0;
  const thisYear = new Date().getFullYear();
  const bonusYear = state.bonusDate ? new Date(state.bonusDate + 'T00:00:00').getFullYear() : thisYear;
  const bonusInCurrentYear = bonusYear === thisYear;
  const bonusForYear = bonusInCurrentYear ? bonusAmt : 0;

  const annual = (parseFloat(state.salary) || 0)
    + bonusForYear
    + (parseFloat(state.commission) || 0);
  if (annual <= 0 && bonusAmt <= 0) return null;

  const periods = periodsFor(state.payFreq);
  const t401k = (state.t401k || 0) / 100;
  const roth = (state.roth || 0) / 100;

  // Pre-tax benefits per period -> annual
  const med = (parseFloat(state.medical) || 0) * periods;
  const dent = (parseFloat(state.dental) || 0) * periods;
  const vis = (parseFloat(state.vision) || 0) * periods;
  const fsa = (parseFloat(state.fsa) || 0) * periods;
  const preTaxBenefits = med + dent + vis + fsa + (annual * t401k);

  const taxable = Math.max(0, annual - preTaxBenefits - 14600);
  // Progressive federal (2025 single)
  const brackets = [
    [11600, 0.10], [47150, 0.12], [100525, 0.22], [191950, 0.24],
    [243725, 0.32], [609350, 0.35], [Infinity, 0.37],
  ];
  let fed = 0, prev = 0, rem = taxable;
  for (const [cap, rate] of brackets) {
    const slice = Math.min(rem, cap - prev);
    if (slice <= 0) break;
    fed += slice * rate;
    rem -= slice;
    prev = cap;
    if (rem <= 0) break;
  }
  const stateRates = {
    CA: 0.06, NY: 0.055, TX: 0, FL: 0, MD: 0.0475, WA: 0, IL: 0.0495, MA: 0.05,
  };
  const stateTax = taxable * (stateRates[state.stateCode] ?? 0.04);
  const ss = Math.min(annual, 168600) * 0.062;
  const medicare = annual * 0.0145;
  const rothAmt = annual * roth;
  const totalTax = fed + stateTax + ss + medicare;
  const netAnnual = annual - totalTax - preTaxBenefits - rothAmt;

  return {
    annual,
    perPeriod: netAnnual / periods,
    grossPerPeriod: annual / periods,
    netAnnual,
    payFreq: state.payFreq,
    periods,
    earnings: {
      salary: (parseFloat(state.salary) || 0) / periods,
      bonus: bonusForYear / periods,
      commission: (parseFloat(state.commission) || 0) / periods,
    },
    bonusAmt, bonusDate: state.bonusDate || '', bonusYear, bonusInCurrentYear,
    taxes: {
      federal: fed / periods,
      state: stateTax / periods,
      ss: ss / periods,
      medicare: medicare / periods,
      total: totalTax / periods,
    },
    benefits: {
      medical: med / periods,
      dental: dent / periods,
      vision: vis / periods,
      fsa: fsa / periods,
      retirement: (annual * t401k) / periods,
      roth: rothAmt / periods,
      total: (preTaxBenefits + rothAmt) / periods,
    },
    takeHomePct: (netAnnual / annual) * 100,
  };
}

function useAppState() {
  const [bottomTab, setBottomTab] = React.useState('calculator');
  const [section, setSection] = React.useState('earnings');
  const [isCalculating, setIsCalculating] = React.useState(false);
  const [result, setResult] = React.useState(null);

  const [form, setForm] = React.useState({
    payFreq: 'Bi-weekly',
    incomeType: 'salary',
    salary: '85000',
    salaryBasis: 'Per Year',
    hourlyRate: '',
    regularHours: '80',
    overtimeHours: '',
    bonus: '',
    bonusDate: '',
    commission: '',
    // Federal
    filingStatus: 'single',
    useOldW4: false,
    nonresident: false,
    multipleJobs: false,
    dependents: '',
    otherIncome: '',
    deductions: '',
    extraWithholding: '',
    // State
    stateCode: 'CA',
    livesElsewhere: false,
    // Benefits
    medical: '',
    dental: '',
    vision: '',
    fsa: '',
    t401k: 6,
    roth: 0,
  });

  const update = (patch) => setForm(s => ({ ...s, ...patch }));

  const canCalc = () => (parseFloat(form.salary) || 0) > 0
    || (parseFloat(form.hourlyRate) || 0) > 0;

  const calculate = () => {
    if (!canCalc()) return;
    setIsCalculating(true);
    setTimeout(() => {
      setResult(mockCalculate(form));
      setIsCalculating(false);
      setBottomTab('insights');
    }, 850);
  };

  return {
    form, update,
    section, setSection,
    bottomTab, setBottomTab,
    isCalculating, calculate,
    result, canCalc: canCalc(),
  };
}

// ────────────────────────────────────────────────────────────
// Pull-to-refresh hook — usable on Insights scroll containers
// ────────────────────────────────────────────────────────────
function usePullToRefresh(onRefresh, { threshold = 70 } = {}) {
  const ref = React.useRef(null);
  const [pull, setPull] = React.useState(0);
  const [refreshing, setRefreshing] = React.useState(false);
  const start = React.useRef(null);

  React.useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const onDown = (e) => {
      if (el.scrollTop > 0) return;
      start.current = e.touches ? e.touches[0].clientY : e.clientY;
    };
    const onMove = (e) => {
      if (start.current == null) return;
      const y = e.touches ? e.touches[0].clientY : e.clientY;
      const d = y - start.current;
      if (d <= 0) { setPull(0); return; }
      setPull(Math.min(d * 0.55, threshold * 1.4));
      e.preventDefault();
    };
    const onUp = () => {
      if (start.current == null) return;
      if (pull >= threshold && !refreshing) {
        setRefreshing(true);
        Promise.resolve(onRefresh && onRefresh()).finally(() => {
          setTimeout(() => { setRefreshing(false); setPull(0); }, 700);
        });
      } else {
        setPull(0);
      }
      start.current = null;
    };

    el.addEventListener('touchstart', onDown, { passive: true });
    el.addEventListener('touchmove', onMove, { passive: false });
    el.addEventListener('touchend', onUp);
    el.addEventListener('mousedown', onDown);
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => {
      el.removeEventListener('touchstart', onDown);
      el.removeEventListener('touchmove', onMove);
      el.removeEventListener('touchend', onUp);
      el.removeEventListener('mousedown', onDown);
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
  }, [pull, refreshing, threshold, onRefresh]);

  return { ref, pull, refreshing };
}

// ────────────────────────────────────────────────────────────
// Donut SVG (used in Insights across variations)
// ────────────────────────────────────────────────────────────
function Donut({ wedges, size = 180, thickness = 22, center }) {
  const total = wedges.reduce((s, w) => s + w.value, 0) || 1;
  const radius = (size - thickness) / 2;
  const circ = 2 * Math.PI * radius;
  let offset = 0;
  return (
    <div style={{ position: 'relative', width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ transform: 'rotate(-90deg)' }}>
        <circle cx={size/2} cy={size/2} r={radius} fill="none"
          stroke="var(--inc-donutTrack)" strokeWidth={thickness} />
        {wedges.map((w, i) => {
          const len = (w.value / total) * circ;
          const el = (
            <circle key={i} cx={size/2} cy={size/2} r={radius} fill="none"
              stroke={w.color} strokeWidth={thickness}
              strokeDasharray={`${len} ${circ - len}`}
              strokeDashoffset={-offset} strokeLinecap="butt"
            />
          );
          offset += len;
          return el;
        })}
      </svg>
      <div style={{
        position: 'absolute', inset: 0, display: 'flex',
        flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
        textAlign: 'center',
      }}>
        {center}
      </div>
    </div>
  );
}

Object.assign(window, {
  SECTIONS, SECTION_LABELS, PAY_FREQUENCIES, STATES,
  periodsFor, fmtMoney, mockCalculate,
  useAppState, usePullToRefresh, Donut,
});
