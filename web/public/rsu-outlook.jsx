// rsu-outlook.jsx — "Yearly earnings outlook" on Insights. Hidden/collapsed
// by default; tap to reveal how a dated bonus + RSU vesting add to flat
// base pay across years — not just the current tax year. Gross figures.

function RSUYearlyOutlook({ grants, baseAnnual, bonus, defaultOpen = false }) {
  const [open, setOpen] = React.useState(defaultOpen);
  const [showAll, setShowAll] = React.useState(false);
  const allRows = React.useMemo(() => buildYearlyOutlook(grants, baseAnnual, bonus), [grants, baseAnnual, bonus]);
  if (!allRows || allRows.length === 0) return null;

  const CAP = 6;
  const rows = showAll ? allRows : allRows.slice(0, CAP);
  const hiddenCount = allRows.length - rows.length;
  const maxTotal = Math.max(...allRows.map(r => r.total), 1);
  const peak = allRows.reduce((m, r) => r.extraPct > m.extraPct ? r : m, allRows[0]);
  const peakSource = peak.bonus > 0 && peak.rsu > 0 ? 'Bonus + RSUs' : peak.bonus > 0 ? 'Bonus' : 'RSUs';

  const rowCaption = (r) => {
    const parts = [`Base ${fmtMoney(r.base, { cents: false })}`];
    if (r.bonus > 0) parts.push(`+Bonus ${fmtMoney(r.bonus, { cents: false })}`);
    if (r.rsu > 0) parts.push(`+RSU ${fmtMoney(r.rsu, { cents: false })}`);
    const tail = r.extra > 0 ? ` (${Math.round(r.extraPct)}%)` : '';
    return parts.join(' · ') + tail;
  };

  return (
    <B2Card>
      <button onClick={() => setOpen(o => !o)} style={{
        width: '100%', background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left',
        padding: 0, display: 'flex', alignItems: 'center', gap: 12, fontFamily: 'inherit',
      }}>
        <div style={{
          width: 38, height: 38, borderRadius: 12, background: 'var(--inc-sageBg)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
        }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--inc-sage)" strokeWidth="2">
            <line x1="18" y1="20" x2="18" y2="10" /><line x1="12" y1="20" x2="12" y2="4" /><line x1="6" y1="20" x2="6" y2="14" />
          </svg>
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--inc-text)' }}>Yearly earnings outlook</div>
          <div style={{ fontSize: 12, color: 'var(--inc-textDim)', marginTop: 2 }}>
            {allRows[0].year}–{allRows[allRows.length - 1].year} · gross ·{' '}
            {peak.extraPct > 0 ? (
              <>{peakSource} add up to <strong style={{ color: 'var(--inc-sageDeep)' }}>{Math.round(peak.extraPct)}%</strong> in {peak.year}</>
            ) : 'no bonus or RSU vesting yet'}
          </div>
        </div>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--inc-textMute)" strokeWidth="2.5"
          style={{ flexShrink: 0, transform: open ? 'rotate(180deg)' : 'none', transition: 'transform .2s' }}>
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </button>

      {open && (
        <div style={{ marginTop: 14, paddingTop: 14, borderTop: '1px solid var(--inc-hairline)' }}>
          <div style={{ fontSize: 11.5, color: 'var(--inc-textMute)', lineHeight: '16px', marginBottom: 14 }}>
            Figures are gross, before taxes and deductions. Base pay held flat at {fmtMoney(baseAnnual, { cents: false })}/yr.
            Bonus shown once, in its payout year. RSU value uses today's price for every future vest. Actual value will differ.
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {rows.map(r => (
              <div key={r.year} style={{
                borderRadius: 14, padding: '12px 14px',
                background: r.isCurrentYear ? 'var(--inc-sageBg)' : 'var(--inc-surfaceWarm)',
                border: `1px solid ${r.isCurrentYear ? 'var(--inc-sageSoft)' : 'var(--inc-hairline)'}`,
              }}>
                <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 8 }}>
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                    <span style={{ fontSize: 15, fontWeight: 700, color: 'var(--inc-text)' }}>{r.year}</span>
                    {r.isCurrentYear && (
                      <span style={{ fontSize: 10.5, fontWeight: 700, color: 'var(--inc-sageDeep)' }}>THIS YEAR'S PAYCHECK CALC</span>
                    )}
                  </div>
                  <span style={{ fontSize: 15, fontWeight: 700, color: 'var(--inc-text)', fontVariantNumeric: 'tabular-nums' }}>
                    {fmtMoney(r.total, { cents: false })}
                  </span>
                </div>
                <div style={{ display: 'flex', height: 8, borderRadius: 999, overflow: 'hidden', background: 'var(--inc-hairline)', marginBottom: 8 }}>
                  <div style={{ width: `${(r.base / maxTotal) * 100}%`, background: 'var(--inc-textMute)' }} />
                  {r.bonus > 0 && <div style={{ width: `${(r.bonus / maxTotal) * 100}%`, background: 'var(--inc-blush)' }} />}
                  {r.rsu > 0 && <div style={{ width: `${(r.rsu / maxTotal) * 100}%`, background: 'var(--inc-gold)' }} />}
                </div>
                <div style={{ fontSize: 12, color: 'var(--inc-textDim)' }}>{rowCaption(r)}</div>
              </div>
            ))}
          </div>
          {hiddenCount > 0 && (
            <button onClick={() => setShowAll(true)} style={{
              marginTop: 10, background: 'none', border: 'none', cursor: 'pointer', padding: '6px 2px',
              fontFamily: 'inherit', fontSize: 12.5, fontWeight: 700, color: 'var(--inc-sage)',
            }}>+{hiddenCount} more year{hiddenCount !== 1 ? 's' : ''}</button>
          )}
        </div>
      )}
    </B2Card>
  );
}

Object.assign(window, { RSUYearlyOutlook });
