// budget-shared.jsx — Budgeting feature: sample data + the paycheck/goal
// simulation engine every budget screen renders from. Depends on shared.jsx
// (fmtMoney) being loaded first. No external state store — each screen
// takes plain data props; buildBudgetPlan() is the single source of truth
// so Overview/Paychecks/Goals/Outlook never disagree with each other.

const BUDGET_YEAR = new Date().getFullYear();
const SUPP_TAX_RATE = 0.2965; // 22% federal supplemental + 7.65% FICA, same rate rsu-cards.jsx uses
function netSupplemental(gross) { return gross * (1 - SUPP_TAX_RATE); }
function addDays(d, n) { const x = new Date(d); x.setDate(x.getDate() + n); return x; }
function fmtShortDate(d) { return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }); }
function fmtMonYear(d) { return d.toLocaleDateString('en-US', { month: 'short', year: 'numeric' }); }

// ── Goal type chips ─────────────────────────────────────────────
const GOAL_TYPES = [
  { id: 'emergency', label: 'Emergency fund' },
  { id: 'vacation', label: 'Vacation' },
  { id: 'home', label: 'Home' },
  { id: 'debt', label: 'Debt payoff' },
  { id: 'car', label: 'Car' },
  { id: 'wedding', label: 'Wedding' },
  { id: 'custom', label: 'Custom' },
];

function GoalTypeIcon({ type, color = 'var(--inc-sage)', size = 16 }) {
  const paths = {
    emergency: <path d="M12 2l8 4v6c0 5-3.4 8.4-8 10-4.6-1.6-8-5-8-10V6l8-4z" />,
    vacation: <><circle cx="12" cy="9" r="3.5" /><path d="M3 21c1.8-4 5-6 9-6s7.2 2 9 6" /></>,
    home: <path d="M3 11l9-8 9 8M5 10v10h14V10" />,
    debt: <path d="M3 17l6-6 4 4 8-8M15 5h6v6" />,
    car: <path d="M3 16V9l3-5h12l3 5v7M3 16h18M6 16v3M18 16v3" />,
    wedding: <path d="M12 21s-8-4.6-8-10.5A4.5 4.5 0 0112 6a4.5 4.5 0 018 4.5C20 16.4 12 21 12 21z" />,
    custom: <path d="M12 2l3 6.5 7 1-5 5 1.5 7L12 18l-6.5 3.5 1.5-7-5-5 7-1z" />,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">{paths[type] || paths.custom}</svg>;
}

// ── Cadences ──────────────────────────────────────────────────
const CADENCES = [
  { value: 'weekly', label: 'Weekly' },
  { value: 'biweekly', label: 'Biweekly' },
  { value: 'semimonthly', label: 'Semi-monthly' },
  { value: 'monthly', label: 'Monthly' },
  { value: 'quarterly', label: 'Quarterly' },
  { value: 'annual', label: 'Annual' },
  { value: 'onetime', label: 'One-time' },
];

const BUCKETS = [
  { value: 'needs', label: 'Needs', color: 'var(--inc-sageDeep)' },
  { value: 'wants', label: 'Wants', color: 'var(--inc-blush)' },
  { value: 'savings', label: 'Savings', color: 'var(--inc-gold)' },
];

// ── Sample data (ties every screen together) ────────────────────
const SAMPLE_EXPENSES = [
  { id: 'rent', name: 'Rent', amount: 1800, cadence: 'monthly', bucket: 'needs', dueDay: 1 },
  { id: 'groceries', name: 'Groceries', amount: 150, cadence: 'weekly', bucket: 'needs', dueDay: null },
  { id: 'streaming', name: 'Streaming', amount: 45, cadence: 'monthly', bucket: 'wants', dueDay: 5 },
  { id: 'gym', name: 'Gym', amount: 60, cadence: 'monthly', bucket: 'wants', dueDay: 10 },
];

const SAMPLE_GOALS = [
  { id: 'goal-emergency', type: 'emergency', name: 'Emergency fund', target: 18000, targetDate: null, priority: 1 },
  { id: 'goal-japan', type: 'vacation', name: 'Japan trip', target: 6000, targetDate: `${BUDGET_YEAR + 1}-06-01`, priority: 2 },
];

const WINDFALLS = {
  bonus: { grossAmount: 10000, month: 2, day: 15, label: 'Bonus' },   // March
  rsu: { grossAmount: 12000, month: 5, day: 15, label: 'RSU vest' },  // June
};

// ── Paycheck engine ──────────────────────────────────────────────
// 26 biweekly periods tiling the year; each period is a 14-day window
// ending on the pay date. Expense due-dates and windfalls are assigned to
// whichever window contains them, so a rent-due period reads visibly
// tighter than an off-week — the real "some paychecks are tighter" story.
function buildPaychecks(year) {
  const periods = [];
  let payDate = new Date(year, 0, 14);
  for (let i = 0; i < 26; i++) {
    periods.push({ index: i, payDate: new Date(payDate), windowStart: addDays(payDate, -13), items: [], needs: 0, wants: 0 });
    payDate = addDays(payDate, 14);
  }
  const findPeriod = (date) => periods.find(p => date >= p.windowStart && date <= p.payDate);

  // Rent / streaming / gym — monthly, fixed due day
  SAMPLE_EXPENSES.filter(e => e.cadence === 'monthly').forEach(e => {
    for (let m = 0; m < 12; m++) {
      const due = new Date(year, m, e.dueDay);
      const p = findPeriod(due);
      if (p) { p.items.push({ name: e.name, amount: e.amount }); p[e.bucket] += e.amount; }
    }
  });
  // Groceries — weekly, same day-of-week as pay date (lands ~2x/period)
  SAMPLE_EXPENSES.filter(e => e.cadence === 'weekly').forEach(e => {
    let d = new Date(year, 0, 14);
    while (d.getFullYear() === year || d <= periods[periods.length - 1].payDate) {
      const p = findPeriod(d);
      if (p) { p.items.push({ name: e.name, amount: e.amount }); p[e.bucket] += e.amount; }
      d = addDays(d, -7);
      if (d < periods[0].windowStart) break;
    }
    d = addDays(new Date(year, 0, 14), 7);
    while (d <= periods[periods.length - 1].payDate) {
      const p = findPeriod(d);
      if (p) { p.items.push({ name: e.name, amount: e.amount }); p[e.bucket] += e.amount; }
      d = addDays(d, 7);
    }
  });

  const bonusNet = netSupplemental(WINDFALLS.bonus.grossAmount);
  const rsuNet = netSupplemental(WINDFALLS.rsu.grossAmount);
  const bonusDate = new Date(year, WINDFALLS.bonus.month, WINDFALLS.bonus.day);
  const rsuDate = new Date(year, WINDFALLS.rsu.month, WINDFALLS.rsu.day);
  const bonusPeriod = findPeriod(bonusDate);
  const rsuPeriod = findPeriod(rsuDate);

  let runningBalance = 0;
  periods.forEach(p => {
    p.isBonus = p === bonusPeriod;
    p.isRSU = p === rsuPeriod;
    p.windfallGross = p.isBonus ? WINDFALLS.bonus.grossAmount : p.isRSU ? WINDFALLS.rsu.grossAmount : 0;
    p.windfallNet = p.isBonus ? bonusNet : p.isRSU ? rsuNet : 0;
    p.takeHome = 2400 + p.windfallNet;
    // Base goal contributions; tighter on rent periods, boosted on windfalls
    // (70% of the net windfall routes to the emergency fund — the rest
    // flows straight to leftover, which is what makes windfall paychecks
    // read as visibly bigger).
    const isRentPeriod = p.needs >= 1800;
    p.emergencyContrib = (isRentPeriod ? 150 : 250) + p.windfallNet * 0.7;
    p.japanContrib = isRentPeriod ? 75 : 150;
    p.savings = p.emergencyContrib + p.japanContrib;
    p.leftover = p.takeHome - p.needs - p.wants - p.savings;
    runningBalance += p.leftover;
    p.runningBalance = runningBalance;
  });
  return periods;
}

// ── Goal projection — extrapolates past the sample year at a steady
// contribution rate (no future windfalls) so every goal has an ETA. ──
function projectGoalETA(periods, contribKey, baseRate, target) {
  let cum = 0, etaDate = null, etaIndex = -1;
  for (const p of periods) {
    cum += p[contribKey];
    if (etaDate === null && cum >= target) { etaDate = p.payDate; etaIndex = p.index; }
  }
  const cumAtYearEnd = cum;
  let extra = 0;
  while (etaDate === null) {
    cum += baseRate;
    extra++;
    if (cum >= target) etaDate = addDays(periods[periods.length - 1].payDate, extra * 14);
  }
  return { etaDate, cumAtYearEnd, reachedThisYear: etaIndex >= 0, cumSeries: cumSeriesFor(periods, contribKey) };
}
function cumSeriesFor(periods, key) {
  let cum = 0;
  return periods.map(p => (cum += p[key], cum));
}

// Demo "today" — just after the June RSU vest, so both windfalls already
// show up as banked progress and the rest of the year is still projected.
function demoTodayIndex(periods) {
  const idx = periods.findIndex(p => p.payDate > new Date(BUDGET_YEAR, 6, 1));
  return idx >= 0 ? idx : periods.length - 1;
}

function buildBudgetPlan() {
  const periods = buildPaychecks(BUDGET_YEAR);
  const todayIdx = demoTodayIndex(periods);
  const emergency = SAMPLE_GOALS[0], japan = SAMPLE_GOALS[1];
  const emergencyProj = projectGoalETA(periods, 'emergencyContrib', 250, emergency.target);
  const japanProj = projectGoalETA(periods, 'japanContrib', 150, japan.target);
  const japanTargetDate = new Date(japan.targetDate + 'T00:00:00');
  const japanBehindDays = Math.round((japanProj.etaDate - japanTargetDate) / 86400000);
  const japanBehindMonths = Math.round(japanBehindDays / 30);

  const warnings = [];
  if (japanBehindMonths >= 1) warnings.push(`Japan trip is ~${japanBehindMonths} month${japanBehindMonths === 1 ? '' : 's'} behind its date at this rate.`);

  const needsTotal = periods.reduce((s, p) => s + p.needs, 0) / 26;
  const wantsTotal = periods.reduce((s, p) => s + p.wants, 0) / 26;
  const savingsTotal = periods.reduce((s, p) => s + p.savings, 0) / 26;

  const aiRationale = `You've got room to fund both goals. I front-loaded the emergency fund and routed most of your June RSU vest to it — at this pace you'll hit your ${fmtMoney(emergency.target, { cents: false })} cushion by ${fmtMonYear(emergencyProj.etaDate)}, and Japan is ${japanBehindMonths >= 1 ? `about ${japanBehindMonths} month${japanBehindMonths === 1 ? '' : 's'} behind` : 'on track for'} your ${fmtMonYear(japanTargetDate)} date.`;

  // Yearly outlook — 3 years, carrying cumulative goal progress forward.
  // Once a goal is met its share of the contribution flows into surplus
  // instead, which is what makes later years' surplus grow.
  const years = [];
  let emergencyCum = 0, japanCum = 0;
  const annualTakeHomeBase = 26 * 2400;
  const annualBonusNet = netSupplemental(WINDFALLS.bonus.grossAmount);
  const annualRsuNet = netSupplemental(WINDFALLS.rsu.grossAmount);
  const annualNeeds = needsTotal * 26;
  const annualWants = wantsTotal * 26;
  const perPeriodEmergency = periods.map(p => p.emergencyContrib);
  const perPeriodJapan = periods.map(p => p.japanContrib);
  for (let y = 0; y < 3; y++) {
    let yearEmergencyGiven = 0, yearJapanGiven = 0, yearRedirected = 0;
    perPeriodEmergency.forEach(amt => {
      if (emergencyCum < emergency.target) { const take = Math.min(amt, emergency.target - emergencyCum); emergencyCum += take; yearEmergencyGiven += take; yearRedirected += amt - take; }
      else yearRedirected += amt;
    });
    perPeriodJapan.forEach(amt => {
      if (japanCum < japan.target) { const take = Math.min(amt, japan.target - japanCum); japanCum += take; yearJapanGiven += take; yearRedirected += amt - take; }
      else yearRedirected += amt;
    });
    const total = annualTakeHomeBase + annualBonusNet + annualRsuNet;
    const surplus = total - annualNeeds - annualWants - yearEmergencyGiven - yearJapanGiven;
    years.push({
      year: BUDGET_YEAR + y, total, needs: annualNeeds, wants: annualWants,
      goalFunding: yearEmergencyGiven + yearJapanGiven, surplus, redirected: yearRedirected,
      isWindfallYear: true, emergencyMet: emergencyCum >= emergency.target, japanMet: japanCum >= japan.target,
    });
  }

  return {
    periods, todayIdx, emergency, japan, emergencyProj, japanProj,
    japanBehindMonths, warnings, aiRationale, needsTotal, wantsTotal, savingsTotal,
    years,
  };
}

Object.assign(window, {
  BUDGET_YEAR, GOAL_TYPES, GoalTypeIcon, CADENCES, BUCKETS,
  SAMPLE_EXPENSES, SAMPLE_GOALS, WINDFALLS, netSupplemental,
  fmtShortDate, fmtMonYear, buildPaychecks, buildBudgetPlan,
});
