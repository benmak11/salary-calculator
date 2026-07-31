// budget-plan.jsx — Budget shell: Overview/Paychecks/Goals/Outlook sub-tabs,
// the Insights entry CTA, and the full-screen generating state.

const BUDGET_TABS = [
  { id: 'overview', label: 'Overview' }, { id: 'paychecks', label: 'Paychecks' },
  { id: 'goals', label: 'Goals' }, { id: 'outlook', label: 'Outlook' },
];

function BudgetSectionHeader({ tab, setTab }) {
  return (
    <div style={{ padding: '58px 26px 20px' }}>
      <div style={{ fontFamily: 'var(--inc-serif, "Fraunces", serif)', fontSize: 34, fontWeight: 500, letterSpacing: -1, color: 'var(--inc-text)', marginBottom: 18 }}>Budget</div>
      <div style={{ display: 'flex', gap: 20, borderBottom: '1px solid var(--inc-hairline)', paddingBottom: 12 }}>
        {BUDGET_TABS.map(t => (
          <button key={t.id} onClick={() => setTab(t.id)} style={{
            background: 'none', border: 'none', cursor: 'pointer', padding: 0,
            fontFamily: 'inherit', fontSize: 13, fontWeight: 700,
            color: t.id === tab ? 'var(--inc-text)' : 'var(--inc-textMute)',
            borderBottom: t.id === tab ? '2px solid var(--inc-sage)' : '2px solid transparent',
            paddingBottom: 10,
          }}>{t.label}</button>
        ))}
      </div>
    </div>
  );
}

// ── Insights entry CTA ───────────────────────────────────────────
function InsightsBudgetCTACard({ onStart }) {
  return (
    <div style={{
      background: 'var(--inc-sageBg)', borderRadius: 22, padding: 20, marginBottom: 14,
      border: '1px solid var(--inc-sageSoft)',
    }}>
      <div style={{ fontFamily: 'var(--inc-serif, "Fraunces", serif)', fontSize: 22, fontWeight: 500, color: 'var(--inc-text)', marginBottom: 8, letterSpacing: -0.4 }}>
        Turn this into a plan
      </div>
      <div style={{ fontSize: 13.5, color: 'var(--inc-textDim)', lineHeight: '20px', marginBottom: 16 }}>
        Add your expenses and savings goals — we'll build a paycheck-by-paycheck budget.
      </div>
      <button onClick={onStart} style={{
        background: 'var(--inc-sage)', color: 'white', border: 'none', cursor: 'pointer',
        padding: '13px 20px', borderRadius: 14, fontFamily: 'inherit', fontSize: 14, fontWeight: 700,
        boxShadow: '0 2px 8px rgba(95,140,124,0.3)',
      }}>Build my budget</button>
    </div>
  );
}

// ── Generating state ─────────────────────────────────────────────
function BudgetGeneratingScreen() {
  return (
    <div style={{
      height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
      background: 'var(--inc-bg)', padding: '0 40px', textAlign: 'center',
    }}>
      <div style={{
        width: 56, height: 56, borderRadius: '50%', border: '3.5px solid var(--inc-sageSoft)',
        borderTopColor: 'var(--inc-sage)', animation: 'budgetSpin 0.9s linear infinite', marginBottom: 22,
      }} />
      <div style={{ fontFamily: 'var(--inc-serif, "Fraunces", serif)', fontSize: 20, fontWeight: 500, color: 'var(--inc-text)', marginBottom: 8 }}>
        Building your plan…
      </div>
      <div style={{ fontSize: 13.5, color: 'var(--inc-textDim)', lineHeight: '20px' }}>
        Fitting your expenses and goals around every paycheck.
      </div>
      <style>{`@keyframes budgetSpin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}

// ── Overview tab ─────────────────────────────────────────────────
function BudgetOverviewTab({ plan }) {
  const { needsTotal, wantsTotal, savingsTotal, aiRationale, warnings, emergency, japan, emergencyProj, japanProj, todayIdx, periods } = plan;
  const takeHome = needsTotal + wantsTotal + savingsTotal;
  const savedEmergency = emergencyProj.cumSeries[todayIdx];
  const savedJapan = japanProj.cumSeries[todayIdx];
  return (
    <div style={{ padding: '0 16px 100px' }}>
      <B2Card>
        <BudgetBucketDonut needs={needsTotal} wants={wantsTotal} savings={savingsTotal} />
      </B2Card>
      <B2Card>
        <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
          <div style={{
            width: 38, height: 38, borderRadius: 12, background: 'var(--inc-sageBg)', flexShrink: 0,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--inc-sage)" strokeWidth="2"><path d="M12 2l3 6.5 7 1-5 5 1.5 7L12 18l-6.5 3.5 1.5-7-5-5 7-1z" /></svg>
          </div>
          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--inc-text)', marginBottom: 6 }}>How I built this</div>
            <div style={{ fontSize: 13, color: 'var(--inc-textDim)', lineHeight: '20px' }}>{aiRationale}</div>
          </div>
        </div>
      </B2Card>
      {warnings.length > 0 && (
        <div style={{
          padding: 16, background: 'var(--inc-blushBg)', borderRadius: 18, marginBottom: 14,
          display: 'flex', gap: 10, alignItems: 'flex-start',
        }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--inc-blush)" strokeWidth="2" style={{ flexShrink: 0, marginTop: 1 }}>
            <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="13" /><circle cx="12" cy="16.5" r="0.5" fill="var(--inc-blush)" />
          </svg>
          <div style={{ fontSize: 12.5, color: 'var(--inc-text)', lineHeight: '18px' }}>{warnings[0]}</div>
        </div>
      )}
      <B2Card>
        <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--inc-textMute)', textTransform: 'uppercase', letterSpacing: 0.7, marginBottom: 12 }}>Goals</div>
        {[{ g: emergency, saved: savedEmergency }, { g: japan, saved: savedJapan }].map(({ g, saved }, i) => (
          <div key={g.id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 0', borderTop: i > 0 ? '1px solid var(--inc-hairline)' : 'none' }}>
            <div style={{ width: 32, height: 32, borderRadius: 10, background: 'var(--inc-sageBg)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              <GoalTypeIcon type={g.type} size={14} />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--inc-text)' }}>{g.name}</div>
              <div style={{ fontSize: 12, color: 'var(--inc-textMute)' }}>{Math.round((saved / g.target) * 100)}% funded</div>
            </div>
            <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--inc-sageDeep)', fontVariantNumeric: 'tabular-nums' }}>{fmtMoney(saved, { cents: false })}</div>
          </div>
        ))}
      </B2Card>
    </div>
  );
}

// ── Paychecks tab ────────────────────────────────────────────────
function PaycheckCard({ p, todayIdx }) {
  const amplified = p.isBonus || p.isRSU;
  const maxBar = Math.max(p.needs + p.wants + p.savings, 1);
  return (
    <div style={{
      minWidth: amplified ? 208 : 172, background: 'var(--inc-surface)', borderRadius: 18,
      border: `1.5px solid ${amplified ? (p.isRSU ? 'var(--inc-gold)' : 'var(--inc-blush)') : 'var(--inc-cardBorder)'}`,
      padding: 16, flexShrink: 0, position: 'relative',
    }}>
      {p.index === todayIdx && (
        <div style={{ position: 'absolute', top: -9, left: 14, background: 'var(--inc-text)', color: 'var(--inc-bg)', fontSize: 9.5, fontWeight: 700, padding: '2px 8px', borderRadius: 999, letterSpacing: 0.4 }}>TODAY</div>
      )}
      {amplified && (
        <div style={{
          display: 'inline-block', marginBottom: 8, padding: '3px 9px', borderRadius: 999,
          background: p.isRSU ? 'var(--inc-gold)' : 'var(--inc-blush)', color: p.isRSU ? '#4a3c0f' : 'white',
          fontSize: 10, fontWeight: 800, letterSpacing: 0.3,
        }}>{p.isRSU ? '+RSU' : '+Bonus'}</div>
      )}
      <div style={{ fontSize: 11.5, color: 'var(--inc-textMute)', fontWeight: 600 }}>{fmtShortDate(p.payDate)}</div>
      <div style={{ fontSize: amplified ? 22 : 18, fontWeight: 700, color: 'var(--inc-text)', margin: '2px 0 10px', fontVariantNumeric: 'tabular-nums' }}>{fmtMoney(p.takeHome, { cents: false })}</div>
      <div style={{ display: 'flex', height: amplified ? 14 : 8, borderRadius: 999, overflow: 'hidden', background: 'var(--inc-hairline)', marginBottom: 10 }}>
        <div style={{ width: `${(p.needs / maxBar) * 100}%`, background: 'var(--inc-sageDeep)' }} />
        <div style={{ width: `${(p.wants / maxBar) * 100}%`, background: 'var(--inc-blush)' }} />
        <div style={{ width: `${(p.savings / maxBar) * 100}%`, background: 'var(--inc-gold)' }} />
      </div>
      <div style={{ fontSize: 11, color: 'var(--inc-textDim)', lineHeight: '16px', marginBottom: 10 }}>
        Needs {fmtMoney(p.needs, { cents: false })} · Wants {fmtMoney(p.wants, { cents: false })} · Goals {fmtMoney(p.savings, { cents: false })}
      </div>
      <div style={{ borderTop: '1px solid var(--inc-hairline)', paddingTop: 10, display: 'flex', justifyContent: 'space-between' }}>
        <div>
          <div style={{ fontSize: 10, color: 'var(--inc-textMute)', fontWeight: 700, textTransform: 'uppercase' }}>Leftover</div>
          <div style={{ fontSize: 14, fontWeight: 700, color: p.leftover < 100 ? 'var(--inc-blush)' : 'var(--inc-sageDeep)' }}>{fmtMoney(p.leftover, { cents: false })}</div>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: 10, color: 'var(--inc-textMute)', fontWeight: 700, textTransform: 'uppercase' }}>Balance</div>
          <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--inc-text)' }}>{fmtMoney(p.runningBalance, { cents: false })}</div>
        </div>
      </div>
    </div>
  );
}

function BudgetPaychecksTab({ plan }) {
  return (
    <div style={{ padding: '0 0 100px' }}>
      <div style={{ padding: '0 16px 14px', fontSize: 13, color: 'var(--inc-textDim)', lineHeight: '19px' }}>
        Some paychecks run tighter than others — rent lands the 1st of each month, and March/June carry your bonus and RSU vest.
      </div>
      <div style={{ display: 'flex', gap: 12, overflowX: 'auto', padding: '0 16px 4px' }}>
        {plan.periods.map(p => <PaycheckCard key={p.index} p={p} todayIdx={plan.todayIdx} />)}
      </div>
    </div>
  );
}

// ── Goals tab ────────────────────────────────────────────────────
function GoalRing({ goal, saved, proj, japanTargetDate, behindMonths }) {
  const pct = Math.min(100, (saved / goal.target) * 100);
  const dated = !!goal.targetDate;
  const behind = dated && behindMonths >= 1;
  return (
    <B2Card>
      <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
        <Donut wedges={[{ value: pct, color: behind ? 'var(--inc-blush)' : 'var(--inc-sage)' }, { value: 100 - pct, color: 'var(--inc-donutTrack)' }]}
          size={92} thickness={10}
          center={<div style={{ fontSize: 16, fontWeight: 700, color: 'var(--inc-text)' }}>{Math.round(pct)}%</div>} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
            <GoalTypeIcon type={goal.type} size={14} />
            <span style={{ fontSize: 15, fontWeight: 700, color: 'var(--inc-text)' }}>{goal.name}</span>
          </div>
          <div style={{ fontSize: 13, color: 'var(--inc-textDim)', marginBottom: 6 }}>
            {fmtMoney(saved, { cents: false })} of {fmtMoney(goal.target, { cents: false })}
          </div>
          {dated ? (
            <div style={{
              display: 'inline-block', fontSize: 11.5, fontWeight: 700, padding: '3px 9px', borderRadius: 999,
              background: behind ? 'var(--inc-blushBg)' : 'var(--inc-sageBg)', color: behind ? 'var(--inc-blush)' : 'var(--inc-sageDeep)',
            }}>{behind ? `~${behindMonths} mo behind` : 'On track'} · {fmtMonYear(japanTargetDate)}</div>
          ) : (
            <div style={{ fontSize: 11.5, fontWeight: 700, color: 'var(--inc-sageDeep)' }}>ETA ~{fmtMonYear(proj.etaDate)}</div>
          )}
        </div>
      </div>
    </B2Card>
  );
}

function BudgetGoalsTab({ plan }) {
  const { emergency, japan, emergencyProj, japanProj, todayIdx, japanBehindMonths } = plan;
  const japanTargetDate = new Date(japan.targetDate + 'T00:00:00');
  return (
    <div style={{ padding: '0 16px 100px' }}>
      <GoalRing goal={emergency} saved={emergencyProj.cumSeries[todayIdx]} proj={emergencyProj} />
      <div style={{ fontSize: 11.5, color: 'var(--inc-textMute)', lineHeight: '16px', margin: '-6px 0 14px 4px' }}>
        June vest +{fmtMoney(netSupplemental(WINDFALLS.rsu.grossAmount) * 0.7, { cents: false })} → emergency fund
      </div>
      <GoalRing goal={japan} saved={japanProj.cumSeries[todayIdx]} proj={japanProj} japanTargetDate={japanTargetDate} behindMonths={japanBehindMonths} />
      <div style={{ fontSize: 11.5, color: 'var(--inc-textMute)', lineHeight: '16px', margin: '-6px 0 0 4px' }}>
        March bonus +{fmtMoney(netSupplemental(WINDFALLS.bonus.grossAmount) * 0.3, { cents: false })}, held toward Japan
      </div>
    </div>
  );
}

// ── Outlook tab ──────────────────────────────────────────────────
function BudgetOutlookRow({ y, maxTotal, defaultOpen }) {
  const [open, setOpen] = React.useState(!!defaultOpen);
  return (
    <div style={{
      borderRadius: 14, padding: '12px 14px', marginBottom: 10,
      background: y.year === BUDGET_YEAR ? 'var(--inc-sageBg)' : 'var(--inc-surfaceWarm)',
      border: `1px solid ${y.year === BUDGET_YEAR ? 'var(--inc-sageSoft)' : 'var(--inc-hairline)'}`,
    }}>
      <button onClick={() => setOpen(o => !o)} style={{
        width: '100%', background: 'none', border: 'none', cursor: 'pointer', padding: 0, textAlign: 'left', fontFamily: 'inherit',
      }}>
        <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 8 }}>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
            <span style={{ fontSize: 15, fontWeight: 700, color: 'var(--inc-text)' }}>{y.year}</span>
            <span style={{ fontSize: 10.5, fontWeight: 700, color: 'var(--inc-gold)' }}>WINDFALL YEAR</span>
          </div>
          <span style={{ fontSize: 15, fontWeight: 700, color: 'var(--inc-text)', fontVariantNumeric: 'tabular-nums' }}>{fmtMoney(y.surplus, { cents: false })} surplus</span>
        </div>
        <div style={{ display: 'flex', height: 8, borderRadius: 999, overflow: 'hidden', background: 'var(--inc-hairline)' }}>
          <div style={{ width: `${(y.needs / maxTotal) * 100}%`, background: 'var(--inc-sageDeep)' }} />
          <div style={{ width: `${(y.wants / maxTotal) * 100}%`, background: 'var(--inc-blush)' }} />
          <div style={{ width: `${(y.goalFunding / maxTotal) * 100}%`, background: 'var(--inc-gold)' }} />
          <div style={{ width: `${(y.surplus / maxTotal) * 100}%`, background: 'var(--inc-textMute)' }} />
        </div>
      </button>
      {open && (
        <div style={{ marginTop: 10, paddingTop: 10, borderTop: '1px solid var(--inc-hairline)', fontSize: 12, color: 'var(--inc-textDim)', lineHeight: '19px' }}>
          Total {fmtMoney(y.total, { cents: false })} · Needs {fmtMoney(y.needs, { cents: false })} · Wants {fmtMoney(y.wants, { cents: false })} · Goal funding {fmtMoney(y.goalFunding, { cents: false })}
          {y.emergencyMet && <> · Emergency fund fully met</>}
          {y.japanMet && <> · Japan trip fully met</>}
        </div>
      )}
    </div>
  );
}

function BudgetOutlookTab({ plan }) {
  const maxTotal = Math.max(...plan.years.map(y => y.total), 1);
  return (
    <div style={{ padding: '0 16px 100px' }}>
      <B2Card>
        <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--inc-text)', marginBottom: 2 }}>Yearly surplus outlook</div>
        <div style={{ fontSize: 12, color: 'var(--inc-textDim)', marginBottom: 14 }}>After expenses and goal funding, gross of ongoing contributions</div>
        {plan.years.map((y, i) => <BudgetOutlookRow key={y.year} y={y} maxTotal={maxTotal} defaultOpen={i === 0} />)}
        <div style={{ fontSize: 11, color: 'var(--inc-textMute)', lineHeight: '16px', marginTop: 4 }}>
          RSU valued at today's price; base pay and expenses held flat. Bonus and RSU assumed to recur each year.
        </div>
      </B2Card>
    </div>
  );
}

// ── Budget shell ─────────────────────────────────────────────────
function BudgetShell({ initialTab = 'overview' }) {
  const [tab, setTab] = React.useState(initialTab);
  const plan = React.useMemo(() => buildBudgetPlan(), []);
  const tabMap = {
    overview: <BudgetOverviewTab plan={plan} />,
    paychecks: <BudgetPaychecksTab plan={plan} />,
    goals: <BudgetGoalsTab plan={plan} />,
    outlook: <BudgetOutlookTab plan={plan} />,
  };
  return (
    <B2Screen>
      <BudgetSectionHeader tab={tab} setTab={setTab} />
      <div key={tab} style={{ animation: 'b2SectionEnter 0.3s cubic-bezier(0.4,0,0.2,1)' }}>
        {tabMap[tab]}
      </div>
      <style>{`@keyframes b2SectionEnter { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }`}</style>
    </B2Screen>
  );
}

Object.assign(window, {
  BUDGET_TABS, BudgetSectionHeader, InsightsBudgetCTACard, BudgetGeneratingScreen,
  BudgetOverviewTab, BudgetPaychecksTab, BudgetGoalsTab, BudgetOutlookTab, BudgetShell,
});
