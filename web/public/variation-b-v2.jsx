// variation-b-v2.jsx — Refined Bright/Sage with progressive CTA
// Iterates on Variation B per feedback:
//   • Top bar simplified — no JD avatar, no search (those features move
//     into a future Settings menu)
//   • Bottom tab bar collapsed to 2 tabs: Calculator · Insights
//   • Sticky CTA replaced with the progressive pattern from Variation C:
//       - live projection ribbon (per period, updates as you type)
//       - primary button: "Continue to <next section>" → "Calculate detailed projection"
//       - secondary escape hatch on non-final sections: "Skip to calc"

// Palette is theme-driven: every token resolves to a CSS custom property
// defined in inc-theme.css (light by default, dark inside .inc-theme-dark).
// Components keep reading B2.sage etc. unchanged — the value is now a var().
const B2 = {
  bg: 'var(--inc-bg)',
  surface: 'var(--inc-surface)',
  surfaceWarm: 'var(--inc-surfaceWarm)',
  text: 'var(--inc-text)',
  textDim: 'var(--inc-textDim)',
  textMute: 'var(--inc-textMute)',
  sage: 'var(--inc-sage)',
  sageDeep: 'var(--inc-sageDeep)',
  sageSoft: 'var(--inc-sageSoft)',
  sageBg: 'var(--inc-sageBg)',
  blush: 'var(--inc-blush)',
  blushBg: 'var(--inc-blushBg)',
  gold: 'var(--inc-gold)',
  track: 'var(--inc-track)',
  disabled: 'var(--inc-disabled)',
  donutTrack: 'var(--inc-donutTrack)',
  barBg: 'var(--inc-barBg)',
  btnSolid: 'var(--inc-btnSolid)',
  btnSolidText: 'var(--inc-btnSolidText)',
  cardBorder: 'var(--inc-cardBorder)',
  hairline: 'var(--inc-hairline)',
  hairlineStrong: 'var(--inc-hairlineStrong)',
  font: '"Plus Jakarta Sans", "Inter", -apple-system, system-ui, sans-serif',
  serif: '"Fraunces", Georgia, serif',
};

function B2Screen({ children, scrollRef }) {
  return (
    <div ref={scrollRef} style={{
      flex: 1, overflow: 'auto', background: B2.bg, color: B2.text,
      fontFamily: B2.font,
    }}>{children}</div>
  );
}

// ─── Top bar — wordmark only ─────────────────────────────────
function B2TopBar() {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'flex-start',
      padding: '54px 22px 18px',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div style={{
          width: 34, height: 34, borderRadius: '50%', background: B2.sage,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: 'white', fontWeight: 700, fontSize: 14,
          boxShadow: '0 2px 8px rgba(95,140,124,0.3)',
          fontFamily: B2.serif, fontStyle: 'italic',
        }}>i</div>
        <div style={{ fontSize: 18, fontWeight: 700, color: B2.text, letterSpacing: -0.4 }}>
          incomatic
        </div>
      </div>
    </div>
  );
}

// ─── Section tabs — numbered step circles + progress line ────
function B2SectionTabs({ section, setSection }) {
  const idx = SECTIONS.indexOf(section);
  return (
    <div style={{ padding: '0 22px' }}>
      <div style={{
        display: 'flex', justifyContent: 'space-between',
        position: 'relative',
      }}>
        {SECTIONS.map((s, i) => {
          const done = i < idx;
          const active = s === section;
          return (
            <button key={s} onClick={() => setSection(s)} style={{
              background: 'none', border: 'none', cursor: 'pointer',
              padding: '8px 0 18px', position: 'relative', zIndex: 1,
              display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6,
            }}>
              <div style={{
                width: 24, height: 24, borderRadius: '50%',
                background: active ? B2.sage : done ? B2.sageSoft : B2.bg,
                border: active || done ? 'none' : `1.5px solid ${B2.hairlineStrong}`,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                color: active ? 'white' : done ? B2.sageDeep : B2.textMute,
                fontSize: 11, fontWeight: 700,
                transition: 'all 0.25s',
              }}>{done ? '✓' : i + 1}</div>
              <span style={{
                fontSize: 11.5, fontWeight: 600,
                color: active ? B2.text : B2.textMute,
                transition: 'color 0.2s',
              }}>{SECTION_LABELS[s]}</span>
            </button>
          );
        })}
        {/* connecting line (background) */}
        <div style={{
          position: 'absolute', left: 22, right: 22, top: 20,
          height: 1.5, background: B2.hairline, zIndex: 0,
        }} />
        {/* connecting line (progress) */}
        <div style={{
          position: 'absolute', left: 22, top: 20,
          width: `calc((100% - 44px) * ${idx / 3})`,
          height: 1.5, background: B2.sage, zIndex: 0,
          transition: 'width 0.35s cubic-bezier(0.4, 0, 0.2, 1)',
        }} />
      </div>
    </div>
  );
}

// ─── Header ──────────────────────────────────────────────────
function B2Header({ subtitle }) {
  return (
    <div style={{ padding: '24px 22px 16px' }}>
      <div style={{
        fontFamily: B2.serif, fontSize: 36, lineHeight: '40px',
        fontWeight: 500, letterSpacing: -1, color: B2.text,
      }}>
        Define your<br/>
        financial horizon
      </div>
      <div style={{
        marginTop: 12, fontSize: 14, lineHeight: '21px',
        color: B2.textDim, maxWidth: 280,
      }}>{subtitle}</div>
    </div>
  );
}

// ─── Card ────────────────────────────────────────────────────
function B2Card({ children }) {
  return (
    <div style={{
      background: B2.surface, borderRadius: 22,
      padding: 20, marginBottom: 14,
      border: `1px solid ${B2.cardBorder}`,
      boxShadow: '0 1px 2px rgba(0,0,0,0.02), 0 6px 22px rgba(31,42,42,0.04)',
    }}>
      {children}
    </div>
  );
}

function B2CardHeader({ icon, title, subtitle }) {
  return (
    <div style={{ display: 'flex', gap: 12, marginBottom: 16, alignItems: 'flex-start' }}>
      <div style={{
        width: 38, height: 38, borderRadius: 12, background: B2.sageBg,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        flexShrink: 0,
      }}>{icon}</div>
      <div>
        <div style={{ fontSize: 15, fontWeight: 700, color: B2.text }}>{title}</div>
        {subtitle && <div style={{ fontSize: 12, color: B2.textDim, marginTop: 2 }}>{subtitle}</div>}
      </div>
    </div>
  );
}

// ─── Field primitives — underline ────────────────────────────
function B2Label({ children, suffix }) {
  return (
    <div style={{
      display: 'flex', justifyContent: 'space-between', alignItems: 'baseline',
      fontSize: 11, fontWeight: 700, color: B2.textMute,
      textTransform: 'uppercase', letterSpacing: 0.8, marginBottom: 6,
    }}>
      <span>{children}</span>
      {suffix && <span style={{ color: B2.sage, textTransform: 'none', letterSpacing: 0 }}>{suffix}</span>}
    </div>
  );
}

function B2MoneyField({ label, value, onChange, suffix, placeholder = '0.00' }) {
  const [focused, setFocused] = React.useState(false);
  return (
    <div style={{ marginBottom: 18 }}>
      <B2Label suffix={suffix}>{label}</B2Label>
      <div style={{
        display: 'flex', alignItems: 'baseline', gap: 8,
        borderBottom: `2px solid ${focused ? B2.sage : B2.hairline}`,
        paddingBottom: 8, transition: 'border-color 0.2s',
      }}>
        <span style={{ fontSize: 18, color: focused ? B2.sage : B2.textMute, fontWeight: 600 }}>$</span>
        <input
          type="text" inputMode="decimal" value={value} placeholder={placeholder}
          onChange={(e) => onChange(e.target.value)}
          onFocus={() => setFocused(true)} onBlur={() => setFocused(false)}
          style={{
            flex: 1, background: 'none', border: 'none', outline: 'none',
            color: B2.text, fontFamily: B2.font, fontSize: 22,
            fontWeight: 600, letterSpacing: -0.3, padding: 0,
          }}
        />
      </div>
    </div>
  );
}

function B2Picker({ label, value, options, onChange }) {
  const [open, setOpen] = React.useState(false);
  return (
    <div style={{ marginBottom: 18 }}>
      {label && <B2Label>{label}</B2Label>}
      <button onClick={() => setOpen(o => !o)} style={{
        width: '100%', background: 'none', border: 'none', cursor: 'pointer',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        borderBottom: `2px solid ${B2.hairline}`, paddingBottom: 8,
        textAlign: 'left',
      }}>
        <span style={{ fontSize: 18, color: B2.text, fontWeight: 600 }}>{value}</span>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke={B2.textMute} strokeWidth="2.5">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </button>
      {open && (
        <div style={{
          marginTop: 10, padding: 6, background: B2.surface, borderRadius: 14,
          boxShadow: '0 6px 22px rgba(31,42,42,0.1)', border: `1px solid ${B2.hairline}`,
          maxHeight: 220, overflowY: 'auto',
        }}>
          {options.map(o => (
            <div key={o} onClick={() => { onChange(o); setOpen(false); }} style={{
              padding: '10px 12px', cursor: 'pointer', borderRadius: 10,
              fontSize: 15, color: o === value ? B2.sage : B2.text,
              fontWeight: o === value ? 600 : 500,
              background: o === value ? B2.sageBg : 'transparent',
            }}>{o}</div>
          ))}
        </div>
      )}
    </div>
  );
}

function B2Segmented({ value, onChange, options }) {
  return (
    <div style={{
      display: 'flex', background: B2.sageBg, padding: 4, borderRadius: 12,
      marginBottom: 18,
    }}>
      {options.map(o => {
        const active = o.value === value;
        return (
          <button key={o.value} onClick={() => onChange(o.value)} style={{
            flex: 1, padding: '10px 0',
            background: active ? B2.surface : 'transparent',
            border: 'none', cursor: 'pointer', borderRadius: 9,
            fontSize: 13, fontWeight: 600,
            color: active ? B2.sage : B2.textDim,
            boxShadow: active ? '0 1px 3px rgba(0,0,0,0.06)' : 'none',
            transition: 'all 0.2s',
          }}>{o.label}</button>
        );
      })}
    </div>
  );
}

function B2Radio({ label, checked, onClick }) {
  return (
    <div onClick={onClick} style={{
      display: 'flex', alignItems: 'center', gap: 14, padding: '14px 0',
      cursor: 'pointer',
    }}>
      <div style={{
        width: 22, height: 22, borderRadius: '50%',
        border: `2px solid ${checked ? B2.sage : B2.hairlineStrong}`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        background: checked ? B2.sage : 'transparent', transition: 'all 0.2s',
        flexShrink: 0,
      }}>
        {checked && (
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3.5">
            <polyline points="20 6 9 17 4 12"/>
          </svg>
        )}
      </div>
      <span style={{ fontSize: 15, color: B2.text, fontWeight: checked ? 600 : 500 }}>{label}</span>
    </div>
  );
}

function B2Toggle({ label, sub, checked, onClick }) {
  return (
    <div onClick={onClick} style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      gap: 12, padding: '14px 0', cursor: 'pointer',
    }}>
      <div>
        <div style={{ fontSize: 15, color: B2.text, fontWeight: 500 }}>{label}</div>
        {sub && <div style={{ fontSize: 12, color: B2.textMute, marginTop: 2 }}>{sub}</div>}
      </div>
      <div style={{
        width: 44, height: 26, borderRadius: 13,
        background: checked ? B2.sage : B2.track,
        position: 'relative', transition: 'all 0.2s', flexShrink: 0,
      }}>
        <div style={{
          position: 'absolute', top: 3, left: checked ? 21 : 3,
          width: 20, height: 20, borderRadius: '50%',
          background: '#fff', transition: 'left 0.2s',
          boxShadow: '0 1px 3px rgba(0,0,0,0.15)',
        }} />
      </div>
    </div>
  );
}

function B2Slider({ label, value, onChange, min = 0, max = 25, step = 0.5 }) {
  return (
    <div style={{ marginBottom: 20 }}>
      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'baseline',
        marginBottom: 10,
      }}>
        <B2Label>{label}</B2Label>
        <div style={{
          background: B2.sageBg, padding: '4px 12px', borderRadius: 999,
          fontSize: 13, color: B2.sage, fontWeight: 700,
        }}>{value.toFixed(1)}%</div>
      </div>
      <input type="range" min={min} max={max} step={step} value={value}
        onChange={(e) => onChange(parseFloat(e.target.value))}
        style={{ width: '100%', accentColor: B2.sage }}
      />
    </div>
  );
}

// Bonus payout date — one-time lump sum, not an annual figure. Empty state
// reads "This year"; a future-year date shows an inline caption noting the
// bonus lands in the outlook instead of this year's paycheck.
function B2BonusDateRow({ value, onChange }) {
  const thisYear = new Date().getFullYear();
  const year = value ? new Date(value + 'T00:00:00').getFullYear() : thisYear;
  const future = value && year !== thisYear;
  return (
    <div style={{ marginBottom: 14 }}>
      <B2Label>Paid on</B2Label>
      <div style={{ position: 'relative' }}>
        <input type="date" value={value} onChange={(e) => onChange(e.target.value)} style={{
          width: '100%', boxSizing: 'border-box', border: 'none', borderBottom: `2px solid ${B2.hairline}`,
          padding: '0 0 8px', fontFamily: B2.font, fontSize: 16, fontWeight: 600,
          color: value ? B2.text : 'transparent', background: 'none', outline: 'none',
        }} />
        {!value && (
          <span style={{
            position: 'absolute', left: 0, bottom: 8, fontSize: 16, fontWeight: 600,
            color: B2.textMute, pointerEvents: 'none',
          }}>This year</span>
        )}
      </div>
      {future && (
        <div style={{ fontSize: 11.5, color: B2.textMute, lineHeight: '16px', marginTop: 8 }}>
          Lands in {year}. Shown in your yearly outlook, not this year's paycheck.
        </div>
      )}
    </div>
  );
}

// ─── Sections ────────────────────────────────────────────────
function B2Earnings({ form, update, extra }) {
  return (
    <div style={{ padding: '0 16px' }}>
      <B2Card>
        <B2CardHeader
          icon={<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={B2.sage} strokeWidth="2"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>}
          title="Pay frequency"
          subtitle="When your paycheck lands"
        />
        <B2Picker value={form.payFreq}
          options={PAY_FREQUENCIES.map(p => p.id)}
          onChange={(v) => update({ payFreq: v })} />
      </B2Card>

      <B2Card>
        <B2CardHeader
          icon={<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={B2.sage} strokeWidth="2"><rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/></svg>}
          title="How you're paid"
        />
        <B2Segmented value={form.incomeType} onChange={(v) => update({ incomeType: v })}
          options={[{ value: 'salary', label: 'Salary' }, { value: 'hourly', label: 'Hourly' }]} />
        {form.incomeType === 'salary' ? (
          <>
            <B2MoneyField label="Gross amount" value={form.salary} onChange={(v) => update({ salary: v })} suffix={form.salaryBasis} />
            <B2Picker label="Method" value={form.salaryBasis}
              options={['Per Year', 'Per Period']} onChange={(v) => update({ salaryBasis: v })} />
          </>
        ) : (
          <>
            <B2MoneyField label="Hourly rate" value={form.hourlyRate} onChange={(v) => update({ hourlyRate: v })} />
            <B2MoneyField label="Regular hours / period" placeholder="80"
              value={form.regularHours} onChange={(v) => update({ regularHours: v })} />
            <B2MoneyField label="Overtime hours / period (1.5×)"
              value={form.overtimeHours} onChange={(v) => update({ overtimeHours: v })} />
          </>
        )}
      </B2Card>

      <B2Card>
        <B2CardHeader
          icon={<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={B2.sage} strokeWidth="2"><polyline points="20 12 20 22 4 22 4 12"/><rect x="2" y="7" width="20" height="5"/><line x1="12" y1="22" x2="12" y2="7"/></svg>}
          title="Bonus & commission"
          subtitle="Taxed at 22% supplemental"
        />
        <B2MoneyField label="Bonus (one-time)" value={form.bonus} onChange={(v) => update({ bonus: v })} />
        <B2BonusDateRow value={form.bonusDate} onChange={(v) => update({ bonusDate: v })} />
        <B2MoneyField label="Commission (annual)" value={form.commission} onChange={(v) => update({ commission: v })} />
      </B2Card>

      {extra}
    </div>
  );
}

function B2Federal({ form, update }) {
  return (
    <div style={{ padding: '0 16px' }}>
      <B2Card>
        <B2CardHeader
          icon={<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={B2.sage} strokeWidth="2"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>}
          title="Filing status"
        />
        <B2Radio label="Single or Married filing separately"
          checked={form.filingStatus === 'single'} onClick={() => update({ filingStatus: 'single' })} />
        <B2Radio label="Married filing jointly"
          checked={form.filingStatus === 'marriedJoint'} onClick={() => update({ filingStatus: 'marriedJoint' })} />
        <B2Radio label="Head of Household"
          checked={form.filingStatus === 'headOfHousehold'} onClick={() => update({ filingStatus: 'headOfHousehold' })} />
      </B2Card>
    </div>
  );
}

function B2State({ form, update }) {
  return (
    <div style={{ padding: '0 16px' }}>
      <B2Card>
        <B2CardHeader
          icon={<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={B2.sage} strokeWidth="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z"/><circle cx="12" cy="10" r="3"/></svg>}
          title="Where you work"
        />
        <B2Picker label="State or territory"
          value={STATES.find(s => s.code === form.stateCode)?.name || ''}
          options={STATES.map(s => s.name)}
          onChange={(name) => update({ stateCode: STATES.find(s => s.name === name).code })} />
        <B2Toggle label="Resides in a different state"
          sub="Multi-state withholding split"
          checked={form.livesElsewhere}
          onClick={() => update({ livesElsewhere: !form.livesElsewhere })} />
      </B2Card>

      <div style={{
        padding: 18, background: B2.blushBg, borderRadius: 22, marginBottom: 14,
        display: 'flex', gap: 14, alignItems: 'flex-start',
      }}>
        <div style={{
          width: 36, height: 36, borderRadius: 12, background: B2.surface,
          display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
        }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={B2.blush} strokeWidth="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/>
          </svg>
        </div>
        <div>
          <div style={{ fontSize: 13, fontWeight: 700, color: B2.text, marginBottom: 4 }}>Rule pack 2025.11</div>
          <div style={{ fontSize: 12.5, color: B2.textDim, lineHeight: '19px' }}>
            Multi-state withholding split applies the work-state rate. Your state of residence still files an annual return.
          </div>
        </div>
      </div>
    </div>
  );
}

function B2Benefits({ form, update }) {
  return (
    <div style={{ padding: '0 16px' }}>
      <B2Card>
        <B2CardHeader
          icon={<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={B2.sage} strokeWidth="2"><path d="M20.84 4.61a5.5 5.5 0 00-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 00-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 000-7.78z"/></svg>}
          title="Pre-tax benefits"
          subtitle={`Per ${form.payFreq.toLowerCase()} period`}
        />
        <B2MoneyField label="Medical" value={form.medical} onChange={(v) => update({ medical: v })} />
        <B2MoneyField label="Dental" value={form.dental} onChange={(v) => update({ dental: v })} />
        <B2MoneyField label="Vision" value={form.vision} onChange={(v) => update({ vision: v })} />
        <B2MoneyField label="Healthcare FSA" value={form.fsa} onChange={(v) => update({ fsa: v })} />
      </B2Card>
      <B2Card>
        <B2CardHeader
          icon={<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={B2.sage} strokeWidth="2"><path d="M3 21h18M5 21V7l7-4 7 4v14M9 9v12m6-12v12"/></svg>}
          title="Retirement"
          subtitle="% of gross pay"
        />
        <B2Slider label="Traditional 401(k)" value={form.t401k} onChange={(v) => update({ t401k: v })} />
        <B2Slider label="Roth 401(k)" value={form.roth} onChange={(v) => update({ roth: v })} />
      </B2Card>
    </div>
  );
}

// ─── Sticky CTA — progressive Continue / Calculate with ribbon ─
function B2StickyCTA({ app, onCalc }) {
  const live = React.useMemo(() => mockCalculate(app.form), [app.form]);
  const idx = SECTIONS.indexOf(app.section);
  const isLast = idx === SECTIONS.length - 1;
  const canCalc = app.canCalc;

  const handlePrimary = () => {
    if (isLast) onCalc();
    else app.setSection(SECTIONS[idx + 1]);
  };
  const primaryLabel = isLast
    ? (app.isCalculating ? 'Calculating…' : 'Calculate detailed projection')
    : `Continue to ${SECTION_LABELS[SECTIONS[idx + 1]]}`;

  return (
    <div style={{
      position: 'absolute', left: 0, right: 0, bottom: 100,
      padding: '0 14px', zIndex: 5, pointerEvents: 'none',
    }}>
      <div style={{
        background: B2.surface, borderRadius: 22,
        boxShadow: '0 6px 24px rgba(31,42,42,0.12), 0 1px 3px rgba(0,0,0,0.04)',
        border: `1px solid ${B2.hairline}`,
        pointerEvents: 'auto', overflow: 'hidden',
      }}>
        {/* live projection ribbon */}
        <div style={{
          padding: '12px 16px',
          background: B2.sageBg,
          borderBottom: `1px solid ${B2.hairline}`,
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        }}>
          <div>
            <div style={{
              fontSize: 10, fontWeight: 700, color: B2.sageDeep, letterSpacing: 0.6,
              textTransform: 'uppercase', marginBottom: 1,
            }}>Projected · per {app.form.payFreq}</div>
            <div style={{ fontSize: 11.5, color: B2.textDim, lineHeight: '14px' }}>
              {live ? `${live.takeHomePct.toFixed(1)}% of gross` : 'Enter earnings to preview'}
            </div>
          </div>
          <div style={{
            fontSize: 19, fontWeight: 700, color: B2.sageDeep, letterSpacing: -0.4,
            fontVariantNumeric: 'tabular-nums',
          }}>{live ? fmtMoney(live.perPeriod) : '$0.00'}</div>
        </div>
        {/* button row */}
        <div style={{ display: 'flex', gap: 8, padding: 10 }}>
          {!isLast && (
            <button onClick={onCalc} disabled={!canCalc || app.isCalculating} style={{
              background: 'transparent', color: canCalc ? B2.sage : B2.textMute,
              border: `1.5px solid ${canCalc ? B2.sageSoft : B2.hairline}`,
              padding: '12px 14px', borderRadius: 14,
              fontFamily: B2.font, fontSize: 12.5, fontWeight: 700,
              cursor: canCalc ? 'pointer' : 'not-allowed',
              whiteSpace: 'nowrap',
            }}>Skip to calc</button>
          )}
          <button onClick={handlePrimary}
            disabled={(isLast && !canCalc) || app.isCalculating}
            style={{
              flex: 1,
              background: isLast && !canCalc ? B2.disabled : B2.sage,
              color: 'white', border: 'none',
              cursor: (isLast && !canCalc) ? 'not-allowed' : 'pointer',
              padding: '12px 16px', borderRadius: 14,
              fontFamily: B2.font, fontSize: 14, fontWeight: 700,
              display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
              letterSpacing: -0.2,
              boxShadow: (isLast && !canCalc) ? 'none' : '0 2px 8px rgba(95,140,124,0.3)',
            }}>
            {primaryLabel}
            {!app.isCalculating && (
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5">
                <line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/>
              </svg>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Tab bar — 2 tabs ────────────────────────────────────────
function B2TabBar({ tab, setTab }) {
  const tabs = [
    { id: 'calculator', label: 'Calculator',
      icon: (a) => <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={a ? B2.sage : B2.textMute} strokeWidth="2"><rect x="4" y="2" width="16" height="20" rx="2"/><line x1="8" y1="6" x2="16" y2="6"/><line x1="8" y1="10" x2="10" y2="10"/><line x1="12" y1="10" x2="14" y2="10"/><line x1="16" y1="10" x2="16" y2="10"/><line x1="8" y1="14" x2="10" y2="14"/></svg> },
    { id: 'insights', label: 'Insights',
      icon: (a) => <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke={a ? B2.sage : B2.textMute} strokeWidth="2"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg> },
  ];
  return (
    <div style={{
      position: 'absolute', bottom: 0, left: 0, right: 0,
      paddingBottom: 28, paddingTop: 10,
      background: B2.barBg,
      backdropFilter: 'blur(20px) saturate(180%)',
      WebkitBackdropFilter: 'blur(20px) saturate(180%)',
      borderTop: `1px solid ${B2.hairline}`,
      display: 'flex', justifyContent: 'space-around',
      zIndex: 10,
    }}>
      {tabs.map(t => {
        const active = t.id === tab;
        return (
          <button key={t.id} onClick={() => setTab(t.id)} style={{
            background: 'none', border: 'none', cursor: 'pointer',
            display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4,
            padding: '0 24px',
          }}>
            {t.icon(active)}
            <div style={{ fontSize: 10.5, fontWeight: 700, color: active ? B2.sage : B2.textMute, letterSpacing: 0.3 }}>{t.label}</div>
          </button>
        );
      })}
    </div>
  );
}

// ─── Insights ────────────────────────────────────────────────
function B2Insights({ result, onAdjust, insightsExtra }) {
  const ptr = usePullToRefresh(() => {});
  if (!result) {
    return (
      <B2Screen scrollRef={ptr.ref}>
        <B2TopBar />
        <div style={{ padding: '60px 28px', textAlign: 'center' }}>
          <div style={{
            width: 80, height: 80, borderRadius: '50%', background: B2.sageBg,
            margin: '0 auto 18px', display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke={B2.sage} strokeWidth="1.5">
              <line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/>
            </svg>
          </div>
          <div style={{ fontFamily: B2.serif, fontSize: 26, color: B2.text, fontWeight: 500, marginBottom: 8 }}>
            No results yet
          </div>
          <div style={{ fontSize: 14, color: B2.textDim, marginBottom: 24, lineHeight: '21px' }}>
            Run a calculation to see your earnings breakdown.
          </div>
          <button onClick={onAdjust} style={{
            background: B2.sage, color: 'white', border: 'none', cursor: 'pointer',
            padding: '13px 22px', borderRadius: 14,
            fontFamily: B2.font, fontSize: 14, fontWeight: 700,
            boxShadow: '0 2px 8px rgba(95,140,124,0.3)',
          }}>Open Calculator</button>
        </div>
      </B2Screen>
    );
  }
  return (
    <B2Screen scrollRef={ptr.ref}>
      <div style={{
        height: ptr.pull, display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: B2.sage, fontSize: 12, fontWeight: 700,
        textTransform: 'uppercase', letterSpacing: 0.6,
        transition: ptr.refreshing ? 'none' : 'height 0.2s',
      }}>
        {ptr.refreshing ? (
          <div style={{
            width: 18, height: 18, borderRadius: '50%',
            border: `2px solid ${B2.sageSoft}`, borderTopColor: B2.sage,
            animation: 'spin 0.7s linear infinite',
          }} />
        ) : ptr.pull > 50 ? 'Release to refresh' : ''}
      </div>
      <B2TopBar />
      <div style={{ padding: '0 16px 90px' }}>
        <div style={{ padding: '0 6px 14px' }}>
          <div style={{
            fontSize: 11, fontWeight: 700, color: B2.sage,
            textTransform: 'uppercase', letterSpacing: 0.8, marginBottom: 6,
          }}>Results · {result.payFreq}</div>
          <div style={{ fontFamily: B2.serif, fontSize: 32, color: B2.text, fontWeight: 500, letterSpacing: -1, lineHeight: '36px' }}>
            Earnings breakdown
          </div>
        </div>

        <B2Card>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            <Donut
              wedges={[
                { value: result.perPeriod, color: B2.sage },
                { value: result.taxes.total, color: B2.blush },
                { value: result.benefits.total, color: B2.gold },
              ]}
              size={172} thickness={22}
              center={
                <>
                  <div style={{ fontSize: 11, color: B2.textMute, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.6 }}>Take home</div>
                  <div style={{ fontSize: 24, color: B2.text, fontWeight: 700, marginTop: 4, letterSpacing: -0.5 }}>{fmtMoney(result.perPeriod)}</div>
                </>
              }
            />
            <div style={{ display: 'flex', gap: 16, marginTop: 18 }}>
              {[{ c: B2.sage, l: 'Take home' }, { c: B2.blush, l: 'Taxes' }, { c: B2.gold, l: 'Benefits' }].map(d => (
                <div key={d.l} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <div style={{ width: 10, height: 10, borderRadius: '50%', background: d.c }} />
                  <span style={{ fontSize: 11, color: B2.textDim, fontWeight: 600 }}>{d.l}</span>
                </div>
              ))}
            </div>
            <div style={{ marginTop: 14, fontSize: 12.5, color: B2.textDim }}>
              <strong style={{ color: B2.text }}>{result.takeHomePct.toFixed(1)}%</strong> of gross is yours per paycheck
            </div>
          </div>
        </B2Card>

        <B2InsightsBreakdown result={result} />
        {typeof insightsExtra === 'function' ? insightsExtra(result) : insightsExtra}
        <div style={{ marginTop: 10, display: 'flex', gap: 10 }}>
          <button onClick={onAdjust} style={{
            flex: 1, background: 'transparent', color: B2.sage,
            border: `1.5px solid ${B2.sageSoft}`, cursor: 'pointer',
            padding: '12px 14px', borderRadius: 14,
            fontFamily: B2.font, fontSize: 13.5, fontWeight: 700,
          }}>Adjust parameters</button>
          <button style={{
            flex: 1, background: B2.btnSolid, color: B2.btnSolidText, border: 'none',
            cursor: 'pointer', padding: '12px 14px', borderRadius: 14,
            fontFamily: B2.font, fontSize: 13.5, fontWeight: 700,
          }}>Download PDF</button>
        </div>
      </div>
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </B2Screen>
  );
}

function B2InsightsBreakdown({ result }) {
  const Row = ({ label, value, indent, bold }) => (
    <div style={{
      display: 'flex', justifyContent: 'space-between',
      padding: '8px 0', paddingLeft: indent ? 14 : 0,
    }}>
      <span style={{ fontSize: indent ? 12.5 : 14, color: indent ? B2.textDim : B2.text, fontWeight: bold ? 700 : 500 }}>{label}</span>
      <span style={{ fontSize: indent ? 12.5 : 14, color: indent ? B2.textDim : B2.text, fontWeight: bold ? 700 : 500, fontVariantNumeric: 'tabular-nums' }}>{value}</span>
    </div>
  );
  return (
    <B2Card>
      <Row label="Earnings" value={fmtMoney(result.grossPerPeriod)} bold />
      <Row label="Salary" value={fmtMoney(result.earnings.salary)} indent />
      {result.earnings.bonus > 0 && <Row label="Bonus" value={fmtMoney(result.earnings.bonus)} indent />}
      {result.earnings.commission > 0 && <Row label="Commission" value={fmtMoney(result.earnings.commission)} indent />}

      <div style={{ height: 1, background: B2.hairline, margin: '8px 0' }} />
      <Row label="Taxes" value={fmtMoney(-result.taxes.total, { signed: true })} bold />
      <Row label="Federal Income" value={fmtMoney(-result.taxes.federal, { signed: true })} indent />
      <Row label="State Income" value={fmtMoney(-result.taxes.state, { signed: true })} indent />
      <Row label="Social Security" value={fmtMoney(-result.taxes.ss, { signed: true })} indent />
      <Row label="Medicare" value={fmtMoney(-result.taxes.medicare, { signed: true })} indent />

      {result.benefits.total > 0.01 && (
        <>
          <div style={{ height: 1, background: B2.hairline, margin: '8px 0' }} />
          <Row label="Benefits" value={fmtMoney(-result.benefits.total, { signed: true })} bold />
          {result.benefits.medical > 0 && <Row label="Medical" value={fmtMoney(-result.benefits.medical, { signed: true })} indent />}
          {result.benefits.dental > 0 && <Row label="Dental" value={fmtMoney(-result.benefits.dental, { signed: true })} indent />}
          {result.benefits.vision > 0 && <Row label="Vision" value={fmtMoney(-result.benefits.vision, { signed: true })} indent />}
          {result.benefits.retirement > 0 && <Row label="401(k)" value={fmtMoney(-result.benefits.retirement, { signed: true })} indent />}
        </>
      )}
      <div style={{ height: 2, background: B2.sageBg, margin: '12px 0' }} />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <span style={{ fontFamily: B2.serif, fontSize: 18, color: B2.text, fontWeight: 600 }}>Take home</span>
        <span style={{ fontFamily: B2.serif, fontSize: 24, color: B2.sage, fontWeight: 700, letterSpacing: -0.4 }}>{fmtMoney(result.perPeriod)}</span>
      </div>
    </B2Card>
  );
}

// ─── Calculator wrapper ──────────────────────────────────────
function B2Calculator({ app, earningsExtra }) {
  const sectionMap = {
    earnings: <B2Earnings form={app.form} update={app.update} extra={earningsExtra} />,
    federal:  <B2Federal form={app.form} update={app.update} />,
    state:    <B2State form={app.form} update={app.update} />,
    benefits: <B2Benefits form={app.form} update={app.update} />,
  };
  return (
    <B2Screen>
      <B2TopBar />
      <B2SectionTabs section={app.section} setSection={app.setSection} />
      <B2Header subtitle="Enter your earnings and deductions to project your take-home pay." />
      <div key={app.section} style={{
        animation: 'b2SectionEnter 0.35s cubic-bezier(0.4, 0, 0.2, 1)',
        paddingBottom: 210,
      }}>
        {sectionMap[app.section]}
      </div>
      <style>{`
        @keyframes b2SectionEnter {
          from { opacity: 0; transform: translateY(8px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}</style>
    </B2Screen>
  );
}

function VariationB2({ initialSection, initialResult, dark = false, earningsExtra, insightsExtra, overlay }) {
  const app = useAppState();
  React.useEffect(() => {
    if (initialSection) app.setSection(initialSection);
  }, [initialSection]);
  React.useEffect(() => {
    if (initialResult) {
      // jump straight to Insights with pre-computed result
      app.setBottomTab('insights');
    }
  }, [initialResult]);
  const result = initialResult || app.result;
  return (
    <IOSDevice width={390} height={844} dark={dark}>
      <div style={{
        height: '100%', display: 'flex', flexDirection: 'column',
        background: B2.bg, position: 'relative',
      }}>
        {app.bottomTab === 'calculator' && <B2Calculator app={app} earningsExtra={earningsExtra} />}
        {app.bottomTab === 'insights' && (
          <B2Insights result={result} onAdjust={() => app.setBottomTab('calculator')} insightsExtra={insightsExtra} />
        )}
        {app.bottomTab === 'calculator' && (
          <B2StickyCTA app={app} onCalc={app.calculate} />
        )}
        <B2TabBar tab={app.bottomTab} setTab={app.setBottomTab} />
        {overlay}
      </div>
    </IOSDevice>
  );
}

Object.assign(window, {
  VariationB2,
  // Exported for reuse by the Sign-in-with-Apple flow (auth-flow.jsx) and
  // the RSU/Equity feature (rsu-*.jsx)
  B2, B2Screen, B2TopBar, B2Card, B2CardHeader,
  B2Calculator, B2Insights, B2InsightsBreakdown, B2StickyCTA, B2TabBar,
  B2Label, B2MoneyField, B2Picker, B2Segmented, B2Radio, B2Toggle, B2Slider,
});
