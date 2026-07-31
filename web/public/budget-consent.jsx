// budget-consent.jsx — Data-consent gate: blocking bottom sheet shown
// before plan generation. Mirrors ShellAccountSheet's shape (rounded-top
// sheet, backdrop, centered content) from app-shell.jsx.

function BudgetConsentSheet({ onAllow, onNotNow }) {
  return (
    <div style={{ position: 'absolute', inset: 0, zIndex: 40, display: 'flex', alignItems: 'flex-end' }}>
      <div style={{ position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.35)' }} />
      <div style={{
        position: 'relative', width: '100%', background: 'var(--inc-surface)', borderRadius: '26px 26px 0 0',
        padding: '14px 24px 40px', boxShadow: '0 -8px 30px rgba(0,0,0,0.15)',
      }}>
        <div style={{ width: 36, height: 4, borderRadius: 2, background: 'var(--inc-hairlineStrong)', margin: '0 auto 22px' }} />
        <div style={{
          width: 52, height: 52, borderRadius: 16, background: 'var(--inc-sageBg)', margin: '0 auto 18px',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="var(--inc-sage)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 2l8 4v6c0 5-3.4 8.4-8 10-4.6-1.6-8-5-8-10V6l8-4z" />
            <path d="M9 12l2 2 4-4" />
          </svg>
        </div>
        <div style={{ fontFamily: 'var(--inc-serif, "Fraunces", serif)', fontSize: 22, fontWeight: 500, color: 'var(--inc-text)', textAlign: 'center', marginBottom: 10, letterSpacing: -0.4 }}>
          Building your plan takes a little help
        </div>
        <div style={{ fontSize: 13.5, color: 'var(--inc-textDim)', textAlign: 'center', lineHeight: '20px', maxWidth: 300, margin: '0 auto 26px' }}>
          To put together a paycheck-by-paycheck plan, we send your financial inputs — including the amounts you've typed in — to Google's AI. Nothing is shared beyond generating this plan.
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <button onClick={onAllow} style={{
            width: '100%', padding: '14px 0', borderRadius: 14, border: 'none', cursor: 'pointer',
            background: 'var(--inc-sage)', color: 'white', fontFamily: 'inherit', fontSize: 15, fontWeight: 700,
            boxShadow: '0 2px 8px rgba(95,140,124,0.3)',
          }}>Allow</button>
          <button onClick={onNotNow} style={{
            width: '100%', padding: '13px 0', borderRadius: 14, border: 'none', cursor: 'pointer',
            background: 'none', color: 'var(--inc-textDim)', fontFamily: 'inherit', fontSize: 14, fontWeight: 600,
          }}>Not now</button>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { BudgetConsentSheet });
