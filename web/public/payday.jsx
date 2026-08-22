// payday.jsx — the payday loop for the marketing site (app v1.14.0).
//
// Recreates the Insights countdown card and the two Home Screen widgets. The
// figures are derived from the same mockCalculate result the rest of the site
// uses, so the amount shown here agrees with the Calculator and Insights
// screens instead of being a separately invented number.
//
// The Lock Screen circular widget deliberately shows NO dollar figure. That is
// a real product constraint in the app, not a simplification for the website,
// so it must stay that way here too.

const PAYDAY_DAYS_AWAY = 6;

function paydayDate(daysAway) {
  const d = new Date();
  d.setDate(d.getDate() + daysAway);
  return d;
}

function paydayLabel(d) {
  return d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
}

function paydayMoney(n) {
  return '$' + Math.round(n).toLocaleString('en-US');
}

// ── The Insights countdown card ────────────────────────────────
function PaydayCountdownCard({ net, daysAway = PAYDAY_DAYS_AWAY }) {
  const date = paydayDate(daysAway);
  const pct = Math.max(0, Math.min(1, (14 - daysAway) / 14));
  const R = 34, C = 2 * Math.PI * R;
  return (
    <div style={{
      background: 'var(--inc-surface)', borderRadius: 20, padding: 18,
      border: '1px solid var(--inc-cardBorder)', display: 'flex',
      alignItems: 'center', gap: 16, marginBottom: 14,
    }}>
      <div style={{ position: 'relative', width: 84, height: 84, flexShrink: 0 }}>
        <svg width="84" height="84" viewBox="0 0 84 84">
          <circle cx="42" cy="42" r={R} fill="none" stroke="var(--inc-track)" strokeWidth="7" />
          <circle cx="42" cy="42" r={R} fill="none" stroke="var(--inc-sage)" strokeWidth="7"
                  strokeLinecap="round" strokeDasharray={C} strokeDashoffset={C * (1 - pct)}
                  transform="rotate(-90 42 42)" />
        </svg>
        <div style={{
          position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column',
          alignItems: 'center', justifyContent: 'center',
        }}>
          <div style={{ fontSize: 26, fontWeight: 500, color: 'var(--inc-text)', lineHeight: 1 }}>{daysAway}</div>
          <div style={{ fontSize: 9.5, fontWeight: 700, color: 'var(--inc-textMute)', letterSpacing: '.06em' }}>DAYS</div>
        </div>
      </div>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 10.5, fontWeight: 800, letterSpacing: '.09em', color: 'var(--inc-sage)' }}>NEXT PAYDAY</div>
        <div style={{ fontSize: 19, fontWeight: 700, color: 'var(--inc-text)', marginTop: 3 }}>{paydayLabel(date)}</div>
        <div style={{ fontSize: 12.5, color: 'var(--inc-textDim)', marginTop: 3 }}>
          {paydayMoney(net)} expected
        </div>
      </div>
    </div>
  );
}

// ── Home Screen widgets ────────────────────────────────────────
function WidgetTile({ children, w = 155, h = 155 }) {
  return (
    <div style={{
      width: w, height: h, borderRadius: 22, padding: 14,
      background: 'var(--inc-surface)', boxShadow: '0 8px 20px rgba(31,42,42,0.10)',
      display: 'flex', flexDirection: 'column', flexShrink: 0,
    }}>{children}</div>
  );
}

function PaydayWidgetSmall({ net, daysAway = PAYDAY_DAYS_AWAY }) {
  return (
    <WidgetTile>
      <div style={{ fontSize: 9.5, fontWeight: 800, letterSpacing: '.08em', color: 'var(--inc-sage)' }}>INCOMATIC</div>
      <div style={{ flex: 1 }} />
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 4 }}>
        <div style={{ fontSize: 40, fontWeight: 500, color: 'var(--inc-text)', lineHeight: 1 }}>{daysAway}</div>
        <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--inc-textDim)' }}>days</div>
      </div>
      <div style={{ fontSize: 11.5, color: 'var(--inc-textDim)', marginTop: 3 }}>
        {paydayMoney(net)} on {paydayDate(daysAway).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
      </div>
    </WidgetTile>
  );
}

function PaydayWidgetMedium({ net, daysAway = PAYDAY_DAYS_AWAY }) {
  const rows = [0, 14, 28].map(o => paydayDate(daysAway + o));
  return (
    <WidgetTile w={329}>
      <div style={{ display: 'flex', gap: 18, height: '100%' }}>
        <div style={{ width: 120, display: 'flex', flexDirection: 'column' }}>
          <div style={{ fontSize: 9.5, fontWeight: 800, letterSpacing: '.08em', color: 'var(--inc-sage)' }}>INCOMATIC</div>
          <div style={{ flex: 1 }} />
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 4 }}>
            <div style={{ fontSize: 38, fontWeight: 500, color: 'var(--inc-text)', lineHeight: 1 }}>{daysAway}</div>
            <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--inc-textDim)' }}>days</div>
          </div>
          <div style={{ fontSize: 11.5, color: 'var(--inc-textDim)', marginTop: 3 }}>
            until {rows[0].toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
          </div>
        </div>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 9 }}>
          <div style={{ fontSize: 9.5, fontWeight: 800, letterSpacing: '.07em', color: 'var(--inc-textDim)' }}>NEXT THREE</div>
          {rows.map((d, i) => (
            <div key={i} style={{ display: 'flex', alignItems: 'center' }}>
              <div style={{
                fontSize: 12.5, fontWeight: i === 0 ? 700 : 500,
                color: i === 0 ? 'var(--inc-text)' : 'var(--inc-textDim)',
              }}>{d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}</div>
              <div style={{ flex: 1 }} />
              <div style={{
                fontSize: 12.5, fontWeight: 600, fontVariantNumeric: 'tabular-nums',
                color: i === 0 ? 'var(--inc-sageDeep)' : 'var(--inc-textDim)',
              }}>{paydayMoney(net)}</div>
            </div>
          ))}
        </div>
      </div>
    </WidgetTile>
  );
}

// Lock Screen circular. No figure, by design: it sits on a screen anyone
// standing behind you can read.
function PaydayWidgetCircular({ daysAway = PAYDAY_DAYS_AWAY }) {
  const R = 30, C = 2 * Math.PI * R;
  const pct = Math.max(0, Math.min(1, (14 - daysAway) / 14));
  return (
    <div style={{ width: 76, height: 76, position: 'relative', flexShrink: 0 }}>
      <svg width="76" height="76" viewBox="0 0 76 76">
        <circle cx="38" cy="38" r={R} fill="none" stroke="rgba(255,255,255,0.25)" strokeWidth="6" />
        <circle cx="38" cy="38" r={R} fill="none" stroke="#FFFFFF" strokeWidth="6"
                strokeLinecap="round" strokeDasharray={C} strokeDashoffset={C * (1 - pct)}
                transform="rotate(-90 38 38)" />
      </svg>
      <div style={{
        position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center', color: '#fff',
      }}>
        <div style={{ fontSize: 21, fontWeight: 500, lineHeight: 1 }}>{daysAway}</div>
        <div style={{ fontSize: 8, fontWeight: 700, letterSpacing: '.06em', opacity: .8 }}>DAYS</div>
      </div>
    </div>
  );
}

Object.assign(window, {
  PaydayCountdownCard, PaydayWidgetSmall, PaydayWidgetMedium, PaydayWidgetCircular,
  PAYDAY_DAYS_AWAY, paydayMoney, paydayDate,
});
