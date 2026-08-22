// site-app-views.jsx — live app screens for the marketing site. Renders the
// real prototype components (variation-b-v2 / nav-directions / rsu-* /
// budget-* / history-shared) inside IOSDevice frames so the website shows the
// actual app, not flat mockups. Theme comes from the .inc-theme-* class on an
// ancestor, exactly like the canvas docs.

const SITE_YEAR = new Date().getFullYear();
const SITE_FORM = {
  payFreq: 'Bi-weekly', incomeType: 'salary', salary: '85000', salaryBasis: 'Per Year',
  hourlyRate: '', regularHours: '80', overtimeHours: '',
  bonus: '10000', bonusDate: `${SITE_YEAR}-03-15`, commission: '',
  filingStatus: 'single', useOldW4: false, nonresident: false, multipleJobs: false,
  dependents: '', otherIncome: '', deductions: '', extraWithholding: '',
  stateCode: 'CA', livesElsewhere: false,
  medical: '120', dental: '18', vision: '8', fsa: '100', t401k: 6, roth: 0,
};

// Local mirror of the shell's state hook — keeps the site off the onboarding
// dependency chain while still driving the real Calculator + sticky CTA.
function useSiteAppState() {
  const [section, setSection] = React.useState('earnings');
  const [form, setForm] = React.useState(SITE_FORM);
  const [isCalculating, setIsCalculating] = React.useState(false);
  const [result, setResult] = React.useState(() => mockCalculate(SITE_FORM));
  const update = (patch) => setForm(s => ({ ...s, ...patch }));
  const calculate = (onDone) => {
    setIsCalculating(true);
    setTimeout(() => { const r = mockCalculate(form); setResult(r); setIsCalculating(false); onDone && onDone(r); }, 700);
  };
  return { form, update, section, setSection, isCalculating, calculate, result, canCalc: true };
}

function SiteInsightsScreen({ result }) {
  const rsu = useRSUState(SEED_GRANTS);
  return (
    <B2Insights result={result} onAdjust={() => {}} insightsExtra={(res) => (
      <>
        <RSUSupplementalCard bonus={(res.earnings.bonus || 0) * res.periods} bonusDate={res.bonusInCurrentYear ? res.bonusDate : ''}
          commission={(res.earnings.commission || 0) * res.periods} rsuVesting={rsu.summary.total} />
        <RSUYearlyOutlook grants={rsu.grants} baseAnnual={(res.earnings.salary + res.earnings.commission) * res.periods}
          bonus={res.bonusAmt > 0 ? { amount: res.bonusAmt, year: res.bonusYear } : null} />
        <InsightsBudgetCTACard onStart={() => {}} />
      </>
    )} />
  );
}

// Interactive hero demo — the three real tabs behind the floating pill bar.
function SiteAppDemo() {
  const [tab, setTab] = React.useState('insights');
  const [session, setSession] = React.useState(null);
  const app = useSiteAppState();
  const sessions = useHistorySessions();
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', background: 'var(--inc-bg)', position: 'relative' }}>
      {tab === 'calculator' && (
        <>
          <B2Screen>
            <AppSectionHeader title="Calculator" section={app.section} setSection={app.setSection} />
            <div key={app.section} style={{ animation: 'siteEnter .35s cubic-bezier(.4,0,.2,1)', paddingBottom: 230 }}>
              {app.section === 'earnings' && <B2Earnings form={app.form} update={app.update} />}
              {app.section === 'federal' && <B2Federal form={app.form} update={app.update} />}
              {app.section === 'state' && <B2State form={app.form} update={app.update} />}
              {app.section === 'benefits' && <B2Benefits form={app.form} update={app.update} />}
            </div>
          </B2Screen>
          <B2StickyCTA app={app} onCalc={() => app.calculate(() => setTab('insights'))} />
        </>
      )}
      {tab === 'insights' && <SiteInsightsScreen result={app.result} />}
      {tab === 'history' && (session
        ? <HistorySessionDetail session={session} onBack={() => setSession(null)} />
        : <HistoryTabScreen signedIn onSignIn={() => {}} onOpenSession={setSession} sessions={sessions} />)}
      <AppPillNav tab={tab} setTab={setTab} tabs={[
        { id: 'calculator', label: 'Calculator' }, { id: 'insights', label: 'Insights' }, { id: 'history', label: 'History' },
      ]} />
      <style>{`@keyframes siteEnter{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:translateY(0)}}`}</style>
    </div>
  );
}

// Static gallery screens.
function SiteGalleryScreen({ kind }) {
  const app = useSiteAppState();
  const sessions = useHistorySessions();
  if (kind === 'calculator') return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', background: 'var(--inc-bg)', position: 'relative' }}>
      <B2Screen>
        <AppSectionHeader title="Calculator" section="earnings" setSection={() => {}} />
        <div style={{ paddingBottom: 230 }}><B2Earnings form={app.form} update={() => {}} /></div>
      </B2Screen>
      <B2StickyCTA app={app} onCalc={() => {}} />
      <AppPillNav tab="calculator" setTab={() => {}} tabs={[{ id: 'calculator', label: 'Calculator' }, { id: 'insights', label: 'Insights' }, { id: 'history', label: 'History' }]} />
    </div>
  );
  if (kind === 'insights') return (
    <div style={{ height: '100%', background: 'var(--inc-bg)', position: 'relative' }}>
      <SiteInsightsScreen result={app.result} />
      <AppPillNav tab="insights" setTab={() => {}} tabs={[{ id: 'calculator', label: 'Calculator' }, { id: 'insights', label: 'Insights' }, { id: 'history', label: 'History' }]} />
    </div>
  );
  if (kind === 'history') return (
    <div style={{ height: '100%', background: 'var(--inc-bg)', position: 'relative' }}>
      <HistoryTabScreen signedIn onSignIn={() => {}} onOpenSession={() => {}} sessions={sessions} />
      <AppPillNav tab="history" setTab={() => {}} tabs={[{ id: 'calculator', label: 'Calculator' }, { id: 'insights', label: 'Insights' }, { id: 'history', label: 'History' }]} />
    </div>
  );
  // Payday loop (app v1.14.0). Composed here rather than threaded through
  // B2Insights, which has no slot above the breakdown and is shared with the
  // other gallery screens.
  if (kind === 'payday') return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', background: 'var(--inc-bg)', position: 'relative' }}>
      <B2Screen>
        <B2TopBar />
        <div style={{ padding: '0 18px 230px' }}>
          <PaydayCountdownCard net={app.result.perPeriod} />
          <div style={{
            background: 'var(--inc-surface)', borderRadius: 20, padding: 18,
            border: '1px solid var(--inc-cardBorder)', marginBottom: 14,
          }}>
            <div style={{ fontSize: 10.5, fontWeight: 800, letterSpacing: '.09em', color: 'var(--inc-textMute)' }}>ON PAYDAY MORNING</div>
            <div style={{ fontSize: 14, color: 'var(--inc-text)', marginTop: 8, lineHeight: 1.5 }}>
              A notification at 8am, after the deposit posts. It states the amount, so it is
              worth keeping unmuted.
            </div>
          </div>
          <div style={{
            background: 'var(--inc-sageBg)', borderRadius: 20, padding: 18,
            border: '1px solid var(--inc-sageSoft)',
          }}>
            <div style={{ fontSize: 10.5, fontWeight: 800, letterSpacing: '.09em', color: 'var(--inc-sageDeep)' }}>WEEKENDS AND HOLIDAYS</div>
            <div style={{ fontSize: 14, color: 'var(--inc-text)', marginTop: 8, lineHeight: 1.5 }}>
              If payday lands on a Saturday or a bank holiday, the countdown moves with it and
              says which day it moved from.
            </div>
          </div>
        </div>
      </B2Screen>
      <AppPillNav tab="insights" setTab={() => {}} tabs={[{ id: 'calculator', label: 'Calculator' }, { id: 'insights', label: 'Insights' }, { id: 'history', label: 'History' }]} />
    </div>
  );
  // Home Screen, so the widgets are shown where they actually live.
  if (kind === 'widgets') return (
    <div style={{
      height: '100%', position: 'relative',
      background: 'linear-gradient(165deg, #3F6B5C 0%, #5F8C7C 42%, #C9B79A 100%)',
      padding: '78px 16px 0', display: 'flex', flexDirection: 'column', gap: 18,
      overflow: 'hidden',
    }}>
      {/* iOS dims the wallpaper behind Lock Screen widgets so white glyphs stay
          legible on a light photo. Without it, the gradient's warm end washes
          the caption out to roughly 2:1 contrast. */}
      <div style={{
        position: 'absolute', left: 0, right: 0, bottom: 0, height: 190,
        background: 'linear-gradient(to bottom, rgba(24,38,33,0) 0%, rgba(24,38,33,0.55) 55%, rgba(24,38,33,0.72) 100%)',
        pointerEvents: 'none',
      }} />
      <div style={{ display: 'flex', gap: 19 }}>
        <PaydayWidgetSmall net={app.result.perPeriod} />
        <div style={{
          width: 155, height: 155, borderRadius: 22,
          background: 'rgba(255,255,255,0.16)', border: '1px solid rgba(255,255,255,0.20)',
        }} />
      </div>
      <PaydayWidgetMedium net={app.result.perPeriod} />
      <div style={{ flex: 1 }} />
      <div style={{
        display: 'flex', alignItems: 'center', gap: 14, padding: '16px 18px 30px',
        position: 'relative',
      }}>
        <PaydayWidgetCircular />
        <div style={{ color: '#fff' }}>
          <div style={{ fontSize: 11, fontWeight: 800, letterSpacing: '.08em', opacity: .85 }}>LOCK SCREEN</div>
          <div style={{ fontSize: 13, marginTop: 4, opacity: .95, lineHeight: 1.45, maxWidth: 200 }}>
            Days only. The amount never appears on a locked screen.
          </div>
        </div>
      </div>
    </div>
  );
  if (kind === 'budget') return <BudgetShell initialTab="overview" />;
  if (kind === 'paychecks') return <BudgetShell initialTab="paychecks" />;
  return null;
}

// Device frame sized down for the gallery. IOSDevice draws at 390×844; we
// scale the whole frame so bezel, corner radius and content stay in
// proportion instead of re-laying out at a smaller width.
function SiteDevice({ children, scale = 1, dark = false }) {
  return (
    <div style={{ width: 390 * scale, height: 844 * scale, flexShrink: 0 }}>
      <div style={{ width: 390, height: 844, transform: `scale(${scale})`, transformOrigin: 'top left' }}>
        <IOSDevice width={390} height={844} dark={dark}>{children}</IOSDevice>
      </div>
    </div>
  );
}

Object.assign(window, { SiteAppDemo, SiteGalleryScreen, SiteDevice, SiteInsightsScreen, SITE_FORM });
