// nav-directions.jsx — three navigation/chrome directions for the main app,
// meant to carry the same warmth/considered pacing as the new conversational
// intake into the persistent Calculator/Insights/History shell. Each demo
// swaps only the chrome (top bar, section nav, tab bar) around the same
// Earnings content so they're easy to compare apples-to-apples.

const ND = B2;
const ND_TABS = [
  { id: 'calculator', label: 'Calculator' }, { id: 'insights', label: 'Insights' }, { id: 'history', label: 'History' },
];

// ── Direction 1 — "Fluid Rail": intake's slim progress rail replaces the
// step-circles; bottom nav becomes a floating capsule instead of a full bar.
function ND1Chrome({ section, setSection, tab, setTab, children }) {
  const idx = SECTIONS.indexOf(section);
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', position: 'relative', background: ND.bg }}>
      <div style={{ padding: '54px 22px 8px', display: 'flex', alignItems: 'center', gap: 10 }}>
        <div style={{ width: 28, height: 28, borderRadius: '50%', background: ND.sage, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white', fontFamily: ND.serif, fontStyle: 'italic', fontSize: 12, fontWeight: 700 }}>i</div>
        <div style={{ fontSize: 15, fontWeight: 700, color: ND.text }}>incomatic</div>
      </div>
      <div style={{ display: 'flex', padding: '2px 22px 0', gap: 14 }}>
        {SECTIONS.map((s, i) => (
          <button key={s} onClick={() => setSection(s)} style={{
            background: 'none', border: 'none', cursor: 'pointer', padding: '4px 0 8px',
            fontFamily: ND.font, fontSize: 11, fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.6,
            color: i === idx ? ND.sage : ND.textMute, transition: 'color 0.2s',
          }}>{SECTION_LABELS[s]}</button>
        ))}
      </div>
      <div style={{ display: 'flex', gap: 4, margin: '0 22px 18px' }}>
        {SECTIONS.map((s, i) => (
          <button key={s} onClick={() => setSection(s)} style={{
            flex: 1, height: 2.5, borderRadius: 2, border: 'none', cursor: 'pointer', padding: 0,
            background: i <= idx ? ND.sage : ND.hairline, transition: 'background 0.3s',
          }} />
        ))}
      </div>
      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 210 }}>
        <div style={{ padding: '0 22px 18px' }}>
          <div style={{ fontFamily: ND.serif, fontSize: 30, lineHeight: '35px', fontWeight: 500, letterSpacing: -0.8, color: ND.text }}>Define your<br/>financial horizon</div>
        </div>
        {children}
      </div>
      <div style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: 140, background: ND.bg, pointerEvents: 'none' }} />
      <div style={{ position: 'absolute', left: 20, right: 20, bottom: 100, display: 'flex', justifyContent: 'center' }}>
        <div style={{ background: ND.surface, borderRadius: 999, padding: 6, display: 'flex', gap: 4, boxShadow: '0 8px 24px rgba(31,42,42,0.14), 0 1px 3px rgba(0,0,0,0.05)', border: `1px solid ${ND.hairline}` }}>
          {ND_TABS.map(t => (
            <button key={t.id} onClick={() => setTab(t.id)} style={{
              padding: '9px 18px', borderRadius: 999, border: 'none', cursor: 'pointer',
              background: tab === t.id ? ND.sage : 'transparent', color: tab === t.id ? 'white' : ND.textMute,
              fontFamily: ND.font, fontSize: 12.5, fontWeight: 700, transition: 'all 0.2s',
            }}>{t.label}</button>
          ))}
        </div>
      </div>
    </div>
  );
}

// ── Direction 2 — "Quiet Ledger": near-chromeless. Hairlines instead of
// cards, plain text section switcher, thin text-only bottom bar.
function ND2Chrome({ section, setSection, tab, setTab, children }) {
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', position: 'relative', background: ND.bg }}>
      <div style={{ padding: '58px 26px 20px' }}>
        <div style={{ fontFamily: ND.serif, fontSize: 34, fontWeight: 500, letterSpacing: -1, color: ND.text, marginBottom: 18 }}>Earnings</div>
        <div style={{ display: 'flex', gap: 22, borderBottom: `1px solid ${ND.hairline}`, paddingBottom: 12 }}>
          {SECTIONS.map(s => (
            <button key={s} onClick={() => setSection(s)} style={{
              background: 'none', border: 'none', cursor: 'pointer', padding: 0,
              fontFamily: ND.font, fontSize: 13, fontWeight: 700,
              color: s === section ? ND.text : ND.textMute,
              borderBottom: s === section ? `2px solid ${ND.sage}` : '2px solid transparent',
              paddingBottom: 10,
            }}>{SECTION_LABELS[s]}</button>
          ))}
        </div>
      </div>
      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 190 }}>{children}</div>
      <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, height: 130, background: ND.bg, pointerEvents: 'none' }} />
      <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, padding: '18px 26px 30px', display: 'flex', justifyContent: 'space-between' }}>
        {ND_TABS.map(t => (
          <button key={t.id} onClick={() => setTab(t.id)} style={{
            background: 'none', border: 'none', cursor: 'pointer',
            fontFamily: ND.serif, fontSize: t.id === tab ? 16 : 14, fontStyle: 'italic',
            fontWeight: 500, color: t.id === tab ? ND.sageDeep : ND.textMute, transition: 'all 0.2s',
          }}>{t.label}</button>
        ))}
      </div>
    </div>
  );
}

// ── Direction 3 — "Warm Journal": top segmented pill tabs replace the
// bottom bar entirely; sections read as dated journal entries.
function ND3Chrome({ section, setSection, tab, setTab, children }) {
  const idx = SECTIONS.indexOf(section);
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', background: ND.bg }}>
      <div style={{ padding: '54px 22px 14px' }}>
        <div style={{ display: 'flex', background: ND.sageBg, padding: 4, borderRadius: 14, marginBottom: 18 }}>
          {ND_TABS.map(t => (
            <button key={t.id} onClick={() => setTab(t.id)} style={{
              flex: 1, padding: '9px 0', borderRadius: 10, border: 'none', cursor: 'pointer',
              background: tab === t.id ? ND.surface : 'transparent', color: tab === t.id ? ND.sageDeep : ND.textDim,
              fontFamily: ND.font, fontSize: 12.5, fontWeight: 700,
              boxShadow: tab === t.id ? '0 1px 3px rgba(0,0,0,0.06)' : 'none',
            }}>{t.label}</button>
          ))}
        </div>
        <div style={{ fontSize: 10.5, fontWeight: 700, color: ND.sage, textTransform: 'uppercase', letterSpacing: 0.8, marginBottom: 4 }}>Entry 0{idx + 1} of 0{SECTIONS.length}</div>
        <div style={{ fontFamily: ND.serif, fontSize: 26, fontWeight: 500, letterSpacing: -0.6, color: ND.text }}>{SECTION_LABELS[section]}</div>
      </div>
      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 40 }}>{children}</div>
      <div style={{ padding: '10px 22px 30px', display: 'flex', gap: 8 }}>
        {SECTIONS.map((s, i) => (
          <button key={s} onClick={() => setSection(s)} style={{
            flex: 1, height: 4, borderRadius: 2, border: 'none', cursor: 'pointer', padding: 0,
            background: i <= idx ? ND.sage : ND.hairlineStrong,
          }} />
        ))}
      </div>
    </div>
  );
}

// ── Final direction — "Quiet Ledger" body chrome (serif headline, hairline
// section tabs, no cards around the switcher) + the Warm Journal pill bar,
// relocated to the bottom as the persistent tab bar.
function NDFinalChrome({ section, setSection, tab, setTab, children }) {
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', position: 'relative', background: ND.bg }}>
      <div style={{ padding: '58px 26px 20px' }}>
        <div style={{ fontFamily: ND.serif, fontSize: 34, fontWeight: 500, letterSpacing: -1, color: ND.text, marginBottom: 18 }}>{SECTION_LABELS[section]}</div>
        <div style={{ display: 'flex', gap: 22, borderBottom: `1px solid ${ND.hairline}`, paddingBottom: 12 }}>
          {SECTIONS.map(s => (
            <button key={s} onClick={() => setSection(s)} style={{
              background: 'none', border: 'none', cursor: 'pointer', padding: 0,
              fontFamily: ND.font, fontSize: 13, fontWeight: 700,
              color: s === section ? ND.text : ND.textMute,
              borderBottom: s === section ? `2px solid ${ND.sage}` : '2px solid transparent',
              paddingBottom: 10,
            }}>{SECTION_LABELS[s]}</button>
          ))}
        </div>
      </div>
      <div style={{ flex: 1, overflow: 'auto', paddingBottom: 130 }}>{children}</div>
      <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, height: 130, background: ND.bg, pointerEvents: 'none' }} />
      <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, padding: '10px 20px 32px' }}>
        <div style={{ display: 'flex', background: ND.sageBg, padding: 4, borderRadius: 14 }}>
          {ND_TABS.map(t => (
            <button key={t.id} onClick={() => setTab(t.id)} style={{
              flex: 1, padding: '11px 0', borderRadius: 10, border: 'none', cursor: 'pointer',
              background: tab === t.id ? ND.surface : 'transparent', color: tab === t.id ? ND.sageDeep : ND.textDim,
              fontFamily: ND.font, fontSize: 12.5, fontWeight: 700,
              boxShadow: tab === t.id ? '0 1px 3px rgba(0,0,0,0.06)' : 'none', transition: 'all 0.2s',
            }}>{t.label}</button>
          ))}
        </div>
      </div>
    </div>
  );
}

// ── Production nav (final direction, extracted for the real shell) ──
// Serif page title + hairline section tabs (Calculator's 4 sub-sections
// only — Insights/History pass section=null to just show the title), and
// the pill bar relocated to the bottom as the persistent Calculator ·
// Insights · History switcher.
function AppSectionHeader({ title, section, setSection }) {
  return (
    <div style={{ padding: '58px 26px 20px' }}>
      <div style={{ fontFamily: ND.serif, fontSize: 34, fontWeight: 500, letterSpacing: -1, color: ND.text, marginBottom: section ? 18 : 0 }}>{title}</div>
      {section && (
        <div style={{ display: 'flex', gap: 22, borderBottom: `1px solid ${ND.hairline}`, paddingBottom: 12 }}>
          {SECTIONS.map(s => (
            <button key={s} onClick={() => setSection(s)} style={{
              background: 'none', border: 'none', cursor: 'pointer', padding: 0,
              fontFamily: ND.font, fontSize: 13, fontWeight: 700,
              color: s === section ? ND.text : ND.textMute,
              borderBottom: s === section ? `2px solid ${ND.sage}` : '2px solid transparent',
              paddingBottom: 10,
            }}>{SECTION_LABELS[s]}</button>
          ))}
        </div>
      )}
    </div>
  );
}

function AppPillNav({ tab, setTab, tabs = ND_TABS }) {
  return (
    <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, padding: '10px 20px 32px', zIndex: 20 }}>
      <div style={{ display: 'flex', background: ND.sageBg, padding: 4, borderRadius: 14, boxShadow: '0 8px 24px rgba(31,42,42,0.12), 0 1px 3px rgba(0,0,0,0.05)' }}>
        {tabs.map(t => (
          <button key={t.id} onClick={() => setTab(t.id)} style={{
            flex: 1, padding: '11px 0', borderRadius: 10, border: 'none', cursor: 'pointer',
            background: tab === t.id ? ND.surface : 'transparent', color: tab === t.id ? ND.sageDeep : ND.textDim,
            fontFamily: ND.font, fontSize: 12.5, fontWeight: 700,
            boxShadow: tab === t.id ? '0 1px 3px rgba(0,0,0,0.06)' : 'none', transition: 'all 0.2s',
          }}>{t.label}</button>
        ))}
      </div>
    </div>
  );
}

function NavDirectionDemo({ direction }) {
  const [section, setSection] = React.useState('earnings');
  const [tab, setTab] = React.useState('calculator');
  const [form, setForm] = React.useState({ payFreq: 'Bi-weekly', incomeType: 'salary', salary: '120000', salaryBasis: 'Per Year', bonus: '', bonusDate: '', commission: '', filingStatus: 'single', stateCode: 'CA', medical: '', dental: '', vision: '', fsa: '', t401k: 6, roth: 0 });
  const update = (p) => setForm(s => ({ ...s, ...p }));
  const Chrome = direction === 1 ? ND1Chrome : direction === 2 ? ND2Chrome : direction === 3 ? ND3Chrome : NDFinalChrome;
  const body = section === 'earnings' ? <B2Earnings form={form} update={update} />
    : section === 'federal' ? <B2Federal form={form} update={update} />
    : section === 'state' ? <B2State form={form} update={update} />
    : <B2Benefits form={form} update={update} />;
  return (
    <div style={{ height: '100%', fontFamily: ND.font, color: ND.text }}>
      <Chrome section={section} setSection={setSection} tab={tab} setTab={setTab}>{body}</Chrome>
    </div>
  );
}

Object.assign(window, { NavDirectionDemo, NDFinalChrome, ND_TABS, AppSectionHeader, AppPillNav });
