// history-shared.jsx — saved-calculation History tab: signed-out CTA, empty
// state, session list (mini donut + amount), and a detail view that reuses
// B2InsightsBreakdown so it stays visually identical to a live result.

const HS_SEED_FORMS = [
  { label: 'California · Bi-weekly', savedAt: 'Jul 18', form: { payFreq: 'Bi-weekly', incomeType: 'salary', salary: '118000', commission: '', bonus: '', stateCode: 'CA', filingStatus: 'single', t401k: 6, roth: 0, medical: '', dental: '', vision: '', fsa: '', dependents: '', otherIncome: '', deductions: '' } },
  { label: 'California · Bi-weekly', savedAt: 'Jun 30', form: { payFreq: 'Bi-weekly', incomeType: 'salary', salary: '112000', commission: '4000', bonus: '', stateCode: 'CA', filingStatus: 'single', t401k: 6, roth: 0, medical: '80', dental: '12', vision: '6', fsa: '', dependents: '', otherIncome: '', deductions: '' } },
  { label: 'Washington · Monthly', savedAt: 'May 4', form: { payFreq: 'Monthly', incomeType: 'salary', salary: '96000', commission: '', bonus: '8000', stateCode: 'WA', filingStatus: 'single', t401k: 4, roth: 0, medical: '', dental: '', vision: '', fsa: '', dependents: '', otherIncome: '', deductions: '' } },
];

function useHistorySessions() {
  const sessions = React.useMemo(() => HS_SEED_FORMS.map((s, i) => ({
    id: 'seed-' + i, label: s.label, savedAt: s.savedAt, result: mockCalculate(s.form),
  })).filter(s => s.result), []);
  return sessions;
}

function HSMiniDonut({ result, size = 44 }) {
  return (
    <Donut size={size} thickness={7}
      wedges={[
        { value: result.perPeriod, color: B2.sage },
        { value: result.taxes.total, color: B2.blush },
        { value: result.benefits.total, color: B2.gold },
      ]} />
  );
}

function HSSignedOut({ onSignIn }) {
  return (
    <div style={{
      margin: '10px 16px', padding: '32px 24px 24px', textAlign: 'center',
      background: B2.surface, borderRadius: 22, border: `1px solid ${B2.cardBorder}`,
    }}>
      <div style={{ width: 76, height: 76, borderRadius: '50%', background: B2.sageBg, margin: '0 auto 18px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke={B2.sage} strokeWidth="1.6"><polyline points="12 6 12 12 16 14"/><circle cx="12" cy="12" r="9"/></svg>
      </div>
      <div style={{ fontFamily: B2.serif, fontSize: 22, fontWeight: 500, color: B2.text, marginBottom: 10, letterSpacing: -0.4 }}>
        Sign in to save and<br/>view past calculations
      </div>
      <div style={{ fontSize: 13, color: B2.textDim, lineHeight: '19px', maxWidth: 260, margin: '0 auto 20px' }}>
        Every calculation you run is saved privately to your account and synced across devices.
      </div>
      <button onClick={onSignIn} style={{
        width: '100%', padding: '14px 0', borderRadius: 13, border: `1px solid ${B2.hairlineStrong}`,
        background: '#fff', color: '#000', fontFamily: B2.font, fontSize: 14.5, fontWeight: 600, cursor: 'pointer',
        display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
      }}>
        <svg width="16" height="16" viewBox="0 0 17 17" fill="#000"><path d="M11.6 2.6c.6-.7 1-1.7 1-2.6-.9 0-2 .6-2.6 1.3-.6.6-1.1 1.6-1 2.5.9.1 2-.5 2.6-1.2zM14.3 12c-.4.9-.6 1.3-1 2-.6 1-1.5 2.3-2.5 2.3-.9 0-1.1-.6-2.3-.6-1.2 0-1.5.6-2.4.6-1 0-1.7-1-2.4-2C2 11.5 1.5 8 3 6c.8-1.2 2-2 3.4-2 1 0 2 .6 2.6.6.6 0 1.7-.7 3-.6.5 0 2 .2 3 1.5-2.6 1.5-2.2 5-.7 6.5z"/></svg>
        Sign in with Apple
      </button>
    </div>
  );
}

function HSEmpty() {
  return (
    <div style={{ padding: '48px 30px', textAlign: 'center' }}>
      <div style={{ width: 78, height: 78, borderRadius: '50%', background: B2.sageBg, margin: '0 auto 16px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke={B2.sage} strokeWidth="1.6"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>
      </div>
      <div style={{ fontFamily: B2.serif, fontSize: 22, fontWeight: 500, color: B2.text, marginBottom: 8, letterSpacing: -0.4 }}>No saved calculations yet</div>
      <div style={{ fontSize: 13.5, color: B2.textDim, lineHeight: '20px', maxWidth: 260, margin: '0 auto' }}>
        Run a projection from the Calculator tab and it will be saved here automatically.
      </div>
    </div>
  );
}

function HSRow({ session, isLast, onOpen }) {
  const r = session.result;
  return (
    <div onClick={onOpen} style={{
      display: 'flex', alignItems: 'center', gap: 14, padding: '15px 4px',
      borderBottom: isLast ? 'none' : `1px solid ${B2.hairline}`, cursor: 'pointer',
    }}>
      <HSMiniDonut result={r} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 14.5, fontWeight: 700, color: B2.text, marginBottom: 2 }}>{session.label}</div>
        <div style={{ fontSize: 11.5, color: B2.textMute }}>{session.savedAt}</div>
      </div>
      <div style={{ textAlign: 'right' }}>
        <div style={{ fontSize: 15.5, fontWeight: 700, color: B2.text, fontVariantNumeric: 'tabular-nums' }}>{fmtMoney(r.perPeriod)}</div>
        <div style={{ fontSize: 11, color: B2.textDim }}>{r.takeHomePct.toFixed(0)}% of {fmtMoney(r.grossPerPeriod, { cents: false })}</div>
      </div>
      <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke={B2.textMute} strokeWidth="3"><polyline points="9 18 15 12 9 6"/></svg>
    </div>
  );
}

function HistoryTabScreen({ signedIn, onSignIn, onOpenSession, sessions: sessionsProp }) {
  const seeded = useHistorySessions();
  const sessions = sessionsProp || seeded;
  const ptr = usePullToRefresh(() => {});
  return (
    <B2Screen scrollRef={ptr.ref}>
      <B2TopBar />
      <div style={{ padding: '0 16px 90px' }}>
        <div style={{ padding: '0 6px 14px' }}>
          <div style={{ fontFamily: B2.serif, fontSize: 32, color: B2.text, fontWeight: 500, letterSpacing: -1, lineHeight: '36px' }}>History</div>
          <div style={{ fontSize: 13.5, color: B2.textDim, marginTop: 6, maxWidth: 280, lineHeight: '19px' }}>
            {signedIn ? 'Your saved take-home projections, synced to your account.' : 'Keep a record of every projection you run.'}
          </div>
        </div>
        {!signedIn ? <HSSignedOut onSignIn={onSignIn} /> : sessions.length === 0 ? <HSEmpty /> : (
          <>
            <div style={{ fontSize: 11, fontWeight: 700, color: B2.textMute, textTransform: 'uppercase', letterSpacing: 0.7, padding: '0 4px 8px' }}>{sessions.length} saved</div>
            <B2Card>
              {sessions.map((s, i) => <HSRow key={s.id} session={s} isLast={i === sessions.length - 1} onOpen={() => onOpenSession(s)} />)}
            </B2Card>
          </>
        )}
      </div>
    </B2Screen>
  );
}

function HistorySessionDetail({ session, onBack }) {
  const r = session.result;
  return (
    <B2Screen>
      <div style={{ padding: '54px 16px 0' }}>
        <button onClick={onBack} style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6, color: B2.textDim, fontFamily: B2.font, fontSize: 13.5, fontWeight: 600, padding: '4px 6px 18px' }}>
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke={B2.textDim} strokeWidth="2.4"><polyline points="15 18 9 12 15 6"/></svg>
          History
        </button>
      </div>
      <div style={{ padding: '0 16px 90px' }}>
        <div style={{ padding: '0 6px 14px' }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: B2.sage, textTransform: 'uppercase', letterSpacing: 0.8, marginBottom: 6 }}>{session.label} · {session.savedAt}</div>
          <div style={{ fontFamily: B2.serif, fontSize: 30, color: B2.text, fontWeight: 500, letterSpacing: -1, lineHeight: '35px' }}>Earnings breakdown</div>
        </div>
        <B2Card>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            <Donut size={160} thickness={20}
              wedges={[{ value: r.perPeriod, color: B2.sage }, { value: r.taxes.total, color: B2.blush }, { value: r.benefits.total, color: B2.gold }]}
              center={<><div style={{ fontSize: 11, color: B2.textMute, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.6 }}>Take home</div><div style={{ fontSize: 22, color: B2.text, fontWeight: 700, marginTop: 4 }}>{fmtMoney(r.perPeriod)}</div></>} />
          </div>
        </B2Card>
        <B2InsightsBreakdown result={r} />
      </div>
    </B2Screen>
  );
}

Object.assign(window, { useHistorySessions, HistoryTabScreen, HistorySessionDetail, HSMiniDonut });
