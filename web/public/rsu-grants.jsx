// rsu-grants.jsx — Surfaces B, C, E: Grants list sheet, Add/Edit form,
// Grant detail. Plus the delete-toast (surface G). Presented as a sheet
// overlay on top of the Calculator tab, standard-detent style.

function RSUSheet({ title, onClose, children, footer }) {
  return (
    <div style={{
      position: 'absolute', inset: 0, zIndex: 30, display: 'flex', flexDirection: 'column',
      background: 'var(--inc-bg)', animation: 'rsuSheetUp .28s cubic-bezier(0.32,0.72,0,1)',
    }}>
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '54px 18px 12px', flexShrink: 0,
      }}>
        <div style={{ width: 30 }} />
        <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--inc-text)' }}>{title}</div>
        <button onClick={onClose} style={{
          width: 30, height: 30, borderRadius: '50%', background: 'var(--inc-surfaceWarm)',
          border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="var(--inc-textDim)" strokeWidth="2.5">
            <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>
      <div style={{ flex: 1, overflow: 'auto', padding: '0 16px 24px' }}>{children}</div>
      {footer}
      <style>{`@keyframes rsuSheetUp { from { transform: translateY(24px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }`}</style>
    </div>
  );
}

// ── Surface B: Grants list ──────────────────────────────────────
function RSUGrantRow({ grant, onOpen, onDelete }) {
  const [dx, setDx] = React.useState(0);
  const drag = React.useRef(null);
  const REVEAL = 76;

  const onDown = (e) => { drag.current = { x: e.clientX, startDx: dx }; };
  const onMove = (e) => {
    if (!drag.current) return;
    const delta = e.clientX - drag.current.x;
    setDx(Math.max(-REVEAL, Math.min(0, drag.current.startDx + delta)));
  };
  const onUp = () => {
    if (!drag.current) return;
    setDx(d => (d < -REVEAL / 2 ? -REVEAL : 0));
    drag.current = null;
  };

  const value = vestingThisYearValue(grant);
  const nv = nextVestDate(grant);

  return (
    <div style={{ position: 'relative', marginBottom: 10, borderRadius: 16, overflow: 'hidden' }}>
      <div style={{
        position: 'absolute', inset: 0, display: 'flex', justifyContent: 'flex-end', alignItems: 'stretch',
      }}>
        <button onClick={() => onDelete(grant.id)} style={{
          width: REVEAL, background: 'var(--inc-red)', border: 'none', cursor: 'pointer',
          color: 'white', fontSize: 12, fontWeight: 700, fontFamily: 'inherit',
        }}>Delete</button>
      </div>
      <div
        onPointerDown={onDown} onPointerMove={onMove} onPointerUp={onUp} onPointerLeave={onUp}
        onClick={() => dx === 0 && onOpen(grant.id)}
        style={{
          position: 'relative', transform: `translateX(${dx}px)`, transition: drag.current ? 'none' : 'transform .2s',
          background: 'var(--inc-surface)', border: '1px solid var(--inc-cardBorder)', borderRadius: 16,
          padding: 16, cursor: 'pointer', touchAction: 'pan-y',
        }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{
            width: 40, height: 40, borderRadius: 12, background: 'var(--inc-sageBg)', flexShrink: 0,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 11, fontWeight: 800, color: 'var(--inc-sageDeep)', letterSpacing: -0.2,
          }}>{grant.ticker.slice(0, 4)}</div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 14.5, fontWeight: 700, color: 'var(--inc-text)' }}>
              {grant.ticker} <span style={{ fontWeight: 500, color: 'var(--inc-textDim)' }}>· {fmtShares(grant.sharesTotal)} sh</span>
            </div>
            <div style={{ fontSize: 11.5, color: 'var(--inc-textMute)', marginTop: 2 }}>
              {scheduleLabel(grant.schedule)}
            </div>
          </div>
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--inc-textMute)" strokeWidth="2.5" style={{ flexShrink: 0 }}>
            <polyline points="9 18 15 12 9 6" />
          </svg>
        </div>
        <div style={{
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          marginTop: 12, paddingTop: 12, borderTop: '1px solid var(--inc-hairline)',
        }}>
          <div style={{ fontSize: 12, color: 'var(--inc-textDim)' }}>
            Vesting in {CURRENT_TAX_YEAR}: <strong style={{ color: 'var(--inc-text)' }}>{fmtMoney(value, { cents: false })}</strong>
          </div>
          <div style={{ fontSize: 11.5, color: 'var(--inc-textMute)' }}>
            Next {nv ? fmtDate(nv) : '—'}
          </div>
        </div>
      </div>
    </div>
  );
}

function RSUGrantsList({ rsu }) {
  return (
    <RSUSheet title="RSU Grants" onClose={() => rsu.setView('none')}
      footer={
        <div style={{ padding: '10px 16px 22px', flexShrink: 0 }}>
          <button onClick={() => { rsu.setEditingId(null); rsu.setView('form'); }} style={{
            width: '100%', background: 'var(--inc-sage)', color: 'white', border: 'none', cursor: 'pointer',
            padding: '14px', borderRadius: 14, fontFamily: 'inherit', fontSize: 14.5, fontWeight: 700,
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
            boxShadow: '0 2px 8px rgba(95,140,124,0.3)',
          }}>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5">
              <line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            Add grant
          </button>
        </div>
      }>
      {rsu.grants.length === 0 ? (
        <div style={{ padding: '50px 20px', textAlign: 'center' }}>
          <div style={{
            width: 64, height: 64, borderRadius: '50%', background: 'var(--inc-sageBg)',
            margin: '0 auto 16px', display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}><RSUIcon color="var(--inc-sage)" /></div>
          <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--inc-text)', marginBottom: 6 }}>No grants yet</div>
          <div style={{ fontSize: 13, color: 'var(--inc-textDim)', lineHeight: '19px' }}>
            Add an RSU grant to see how it distributes across vests and flows into your paycheck.
          </div>
        </div>
      ) : (
        <>
          {rsu.grants.map(g => (
            <RSUGrantRow key={g.id} grant={g} onOpen={(id) => { rsu.setEditingId(id); rsu.setView('detail'); }} onDelete={rsu.deleteGrant} />
          ))}
          <div style={{ fontSize: 11, color: 'var(--inc-textMute)', lineHeight: '16px', padding: '10px 4px 0' }}>
            Estimates value all {CURRENT_TAX_YEAR} vests at today's price. Actual tax withholding
            happens at each vest at that day's price. State tax is folded into the main state line.
          </div>
        </>
      )}
    </RSUSheet>
  );
}

// ── Surface C: Add / Edit grant form ────────────────────────────
function RSUGrantForm({ rsu }) {
  const editing = rsu.editingId ? rsu.grants.find(g => g.id === rsu.editingId) : null;

  const [query, setQuery] = React.useState(editing ? editing.ticker : '');
  const [results, setResults] = React.useState([]);
  const [selected, setSelected] = React.useState(editing ? { symbol: editing.ticker, name: editing.company, price: editing.pricePerShare } : null);
  const [quoteLoading, setQuoteLoading] = React.useState(false);
  const [manualMode, setManualMode] = React.useState(editing ? editing.manual : false);
  const [manualCompany, setManualCompany] = React.useState(editing && editing.manual ? editing.company : '');
  const [manualPrice, setManualPrice] = React.useState(editing && editing.manual ? String(editing.pricePerShare) : '');

  const [shares, setShares] = React.useState(editing ? String(editing.sharesTotal) : '');
  const [grantDate, setGrantDate] = React.useState(editing ? editing.grantDate : '');
  const [presetId, setPresetId] = React.useState(editing ? editing.schedule.presetId : 'monthly1cliff');
  const [customYears, setCustomYears] = React.useState(4);
  const [customFreq, setCustomFreq] = React.useState(1);
  const [customCliff, setCustomCliff] = React.useState(12);

  // debounced search
  React.useEffect(() => {
    if (manualMode || selected) return;
    const t = setTimeout(() => setResults(searchTickers(query)), 300);
    return () => clearTimeout(t);
  }, [query, manualMode, selected]);

  const pickTicker = (t) => {
    setResults([]);
    setQuoteLoading(true);
    setTimeout(() => { setSelected(t); setQuoteLoading(false); }, 500);
  };

  const schedule = presetId === 'custom'
    ? { presetId: 'custom', totalMonths: customYears * 12, cliffMonths: customCliff, freqMonths: customFreq }
    : SCHEDULE_PRESETS.find(p => p.id === presetId);

  const pricePerShare = manualMode ? (parseFloat(manualPrice) || 0) : (selected ? selected.price : 0);
  const sharesNum = parseFloat(shares) || 0;
  const canSave = sharesNum > 0 && grantDate && pricePerShare > 0 && (manualMode ? manualCompany : selected);

  const handleSave = () => {
    if (!canSave) return;
    const grant = {
      ticker: manualMode ? (manualCompany.slice(0, 6).toUpperCase()) : selected.symbol,
      company: manualMode ? manualCompany : selected.name,
      manual: manualMode,
      sharesTotal: sharesNum,
      pricePerShare,
      grantDate,
      schedule,
    };
    if (editing) rsu.updateGrant(editing.id, grant);
    else rsu.addGrant(grant);
    rsu.setNudge(true);
    rsu.setView('list');
  };

  return (
    <RSUSheet title={editing ? 'Edit Grant' : 'Add Grant'} onClose={() => rsu.setView(editing ? 'detail' : 'list')}
      footer={
        <div style={{ padding: '10px 16px 22px', flexShrink: 0 }}>
          <button onClick={handleSave} disabled={!canSave} style={{
            width: '100%', background: canSave ? 'var(--inc-sage)' : 'var(--inc-disabled)',
            color: 'white', border: 'none', cursor: canSave ? 'pointer' : 'not-allowed',
            padding: '14px', borderRadius: 14, fontFamily: 'inherit', fontSize: 14.5, fontWeight: 700,
            boxShadow: canSave ? '0 2px 8px rgba(95,140,124,0.3)' : 'none',
          }}>Save grant</button>
        </div>
      }>
      {/* Company */}
      <B2Card>
        <B2CardHeader icon={<RSUIcon color="var(--inc-sage)" />} title="Company" subtitle="Search by ticker or name" />
        {!manualMode ? (
          <>
            {!selected ? (
              <div>
                <div style={{ position: 'relative' }}>
                  <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="e.g. AAPL or Apple"
                    style={{
                      width: '100%', boxSizing: 'border-box', border: `2px solid var(--inc-hairline)`, borderRadius: 12,
                      padding: '11px 12px', fontFamily: 'inherit', fontSize: 15, color: 'var(--inc-text)',
                      background: 'var(--inc-surfaceWarm)', outline: 'none',
                    }} />
                </div>
                {results.length > 0 && (
                  <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 2 }}>
                    {results.map(t => (
                      <button key={t.symbol} onClick={() => pickTicker(t)} style={{
                        display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10,
                        background: 'none', border: 'none', cursor: 'pointer', padding: '9px 8px', borderRadius: 10,
                        textAlign: 'left', fontFamily: 'inherit',
                      }}>
                        <span style={{ fontSize: 14, color: 'var(--inc-text)' }}>
                          <strong>{t.symbol}</strong> <span style={{ color: 'var(--inc-textDim)', fontWeight: 500 }}>{t.name}</span>
                        </span>
                        <span style={{ fontSize: 12.5, color: 'var(--inc-textMute)' }}>${t.price.toFixed(2)}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ) : quoteLoading ? (
              <div style={{
                height: 46, borderRadius: 12, background: 'var(--inc-surfaceWarm)',
                animation: 'rsuPulse 1.1s ease-in-out infinite',
              }} />
            ) : (
              <div style={{
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                background: 'var(--inc-sageBg)', borderRadius: 12, padding: '11px 14px',
              }}>
                <div>
                  <div style={{ fontSize: 14.5, fontWeight: 700, color: 'var(--inc-sageDeep)' }}>
                    {selected.symbol} · ${selected.price.toFixed(2)}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--inc-textMute)', marginTop: 1 }}>as of 9:41am</div>
                </div>
                <button onClick={() => { setSelected(null); setQuery(''); }} style={{
                  background: 'none', border: 'none', cursor: 'pointer', fontSize: 12, fontWeight: 700,
                  color: 'var(--inc-sage)', fontFamily: 'inherit',
                }}>Change</button>
              </div>
            )}
            <button onClick={() => { setManualMode(true); setSelected(null); }} style={{
              marginTop: 12, background: 'none', border: 'none', cursor: 'pointer', padding: 0,
              fontFamily: 'inherit', fontSize: 12.5, fontWeight: 600, color: 'var(--inc-textDim)',
            }}>Company not listed? Enter price manually</button>
          </>
        ) : (
          <>
            <div style={{ marginBottom: 14 }}>
              <B2Label>Company name</B2Label>
              <input value={manualCompany} onChange={(e) => setManualCompany(e.target.value)} placeholder="Company name"
                style={{
                  width: '100%', boxSizing: 'border-box', border: 'none', borderBottom: '2px solid var(--inc-hairline)',
                  padding: '0 0 8px', fontFamily: 'inherit', fontSize: 18, fontWeight: 600, color: 'var(--inc-text)',
                  background: 'none', outline: 'none',
                }} />
            </div>
            <B2MoneyField label="Price per share" value={manualPrice} onChange={setManualPrice} />
            <button onClick={() => { setManualMode(false); }} style={{
              background: 'none', border: 'none', cursor: 'pointer', padding: 0, marginTop: -6,
              fontFamily: 'inherit', fontSize: 12.5, fontWeight: 600, color: 'var(--inc-sage)',
            }}>Search for a public company instead</button>
          </>
        )}
      </B2Card>

      {/* Grant terms */}
      <B2Card>
        <B2CardHeader icon={
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--inc-sage)" strokeWidth="2"><polyline points="20 12 20 22 4 22 4 12" /><rect x="2" y="7" width="20" height="5" /><line x1="12" y1="22" x2="12" y2="7" /></svg>
        } title="Grant terms" />
        <B2MoneyField label="Total shares" value={shares} onChange={setShares} placeholder="0" suffix={
          pricePerShare > 0 && sharesNum > 0 ? `≈ ${fmtMoney(sharesNum * pricePerShare, { cents: false })}` : undefined
        } />
        <div>
          <B2Label>Grant date</B2Label>
          <input type="date" value={grantDate} onChange={(e) => setGrantDate(e.target.value)}
            style={{
              width: '100%', boxSizing: 'border-box', border: 'none', borderBottom: '2px solid var(--inc-hairline)',
              padding: '0 0 8px', fontFamily: 'inherit', fontSize: 16, fontWeight: 600, color: 'var(--inc-text)',
              background: 'none', outline: 'none',
            }} />
        </div>
      </B2Card>

      {/* Vesting schedule */}
      <B2Card>
        <B2CardHeader icon={
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--inc-sage)" strokeWidth="2"><circle cx="12" cy="12" r="9" /><polyline points="12 7 12 12 15 14" /></svg>
        } title="Vesting schedule" />
        {SCHEDULE_PRESETS.map(p => (
          <B2Radio key={p.id} label={`${p.label}, ${p.sub}`} checked={presetId === p.id} onClick={() => setPresetId(p.id)} />
        ))}
        {presetId === 'custom' && (
          <div style={{ display: 'flex', gap: 10, marginTop: 10, flexWrap: 'wrap' }}>
            {[
              { label: 'Duration (yrs)', value: customYears, set: setCustomYears, opts: [1, 2, 3, 4, 5] },
              { label: 'Frequency', value: customFreq, set: setCustomFreq, opts: [1, 3, 12], fmt: v => ({ 1: 'Monthly', 3: 'Quarterly', 12: 'Annual' }[v]) },
              { label: 'Cliff (mo)', value: customCliff, set: setCustomCliff, opts: [0, 6, 12, 24] },
            ].map(f => (
              <div key={f.label} style={{ flex: '1 1 140px' }}>
                <B2Label>{f.label}</B2Label>
                <select value={f.value} onChange={(e) => f.set(parseInt(e.target.value, 10))} style={{
                  width: '100%', border: `1.5px solid var(--inc-hairline)`, borderRadius: 10, padding: '8px 8px',
                  fontFamily: 'inherit', fontSize: 14, color: 'var(--inc-text)', background: 'var(--inc-surfaceWarm)',
                }}>
                  {f.opts.map(o => <option key={o} value={o}>{f.fmt ? f.fmt(o) : o}</option>)}
                </select>
              </div>
            ))}
          </div>
        )}
      </B2Card>

      {/* Vest distribution preview */}
      <B2Card>
        <B2CardHeader icon={
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--inc-sage)" strokeWidth="2"><line x1="18" y1="20" x2="18" y2="10" /><line x1="12" y1="20" x2="12" y2="4" /><line x1="6" y1="20" x2="6" y2="14" /></svg>
        } title="Vest distribution preview" subtitle="Updates live as you edit" />
        <RSUVestTimeline sharesTotal={sharesNum} grantDate={grantDate} schedule={schedule} pricePerShare={pricePerShare} compact />
      </B2Card>
      <style>{`@keyframes rsuPulse { 0%,100% { opacity: 1; } 50% { opacity: 0.55; } }`}</style>
    </RSUSheet>
  );
}

// ── Surface E: Grant detail ─────────────────────────────────────
function RSUGrantDetail({ rsu }) {
  const grant = rsu.grants.find(g => g.id === rsu.editingId);
  const [confirmDelete, setConfirmDelete] = React.useState(false);
  if (!grant) return null;
  const value = vestingThisYearValue(grant);
  const nv = nextVestDate(grant);

  const Fact = ({ label, value }) => (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
      <span style={{ fontSize: 13, color: 'var(--inc-textDim)' }}>{label}</span>
      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--inc-text)' }}>{value}</span>
    </div>
  );

  return (
    <RSUSheet title={grant.ticker} onClose={() => rsu.setView('list')}>
      <B2Card>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4 }}>
          <div style={{
            width: 44, height: 44, borderRadius: 13, background: 'var(--inc-sageBg)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 12, fontWeight: 800, color: 'var(--inc-sageDeep)',
          }}>{grant.ticker.slice(0, 4)}</div>
          <div>
            <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--inc-text)' }}>{grant.company}</div>
            <div style={{ fontSize: 12, color: 'var(--inc-textDim)' }}>{grant.ticker} · ${grant.pricePerShare.toFixed(2)}/sh</div>
          </div>
        </div>
        <div style={{
          marginTop: 12, padding: '12px 14px', background: 'var(--inc-sageBg)', borderRadius: 12,
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        }}>
          <div style={{ fontSize: 12.5, color: 'var(--inc-sageDeep)', fontWeight: 700 }}>Vesting in {CURRENT_TAX_YEAR}</div>
          <div style={{ fontSize: 17, fontWeight: 700, color: 'var(--inc-sageDeep)' }}>{fmtMoney(value, { cents: false })}</div>
        </div>
      </B2Card>

      <B2Card>
        <B2CardHeader icon={
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--inc-sage)" strokeWidth="2"><line x1="18" y1="20" x2="18" y2="10" /><line x1="12" y1="20" x2="12" y2="4" /><line x1="6" y1="20" x2="6" y2="14" /></svg>
        } title="Vest timeline" />
        <RSUVestTimeline sharesTotal={grant.sharesTotal} grantDate={grant.grantDate} schedule={grant.schedule} pricePerShare={grant.pricePerShare} />
      </B2Card>

      <B2Card>
        <B2CardHeader icon={
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--inc-sage)" strokeWidth="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" /><polyline points="14 2 14 8 20 8" /></svg>
        } title="Grant facts" />
        <Fact label="Total shares" value={fmtShares(grant.sharesTotal) + ' sh'} />
        <Fact label="Grant date" value={fmtDate(new Date(grant.grantDate))} />
        <Fact label="Schedule" value={scheduleLabel(grant.schedule)} />
        <Fact label="Next vest" value={nv ? fmtDate(nv) : '—'} />
        <Fact label="Price used" value={`$${grant.pricePerShare.toFixed(2)} ${grant.manual ? '(manual)' : '(live quote)'}`} />
      </B2Card>

      <div style={{ display: 'flex', gap: 10, marginTop: 4 }}>
        <button onClick={() => { rsu.setView('form'); }} style={{
          flex: 1, background: 'transparent', color: 'var(--inc-sage)', border: `1.5px solid var(--inc-sageSoft)`,
          cursor: 'pointer', padding: '13px 14px', borderRadius: 14, fontFamily: 'inherit', fontSize: 13.5, fontWeight: 700,
        }}>Edit grant</button>
        <button onClick={() => setConfirmDelete(true)} style={{
          flex: 1, background: 'var(--inc-redBg)', color: 'var(--inc-red)', border: 'none',
          cursor: 'pointer', padding: '13px 14px', borderRadius: 14, fontFamily: 'inherit', fontSize: 13.5, fontWeight: 700,
        }}>Delete grant</button>
      </div>

      {confirmDelete && (
        <div style={{
          position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.35)', zIndex: 5,
          display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24,
        }} onClick={() => setConfirmDelete(false)}>
          <div onClick={(e) => e.stopPropagation()} style={{
            background: 'var(--inc-surface)', borderRadius: 18, padding: 22, width: '100%', maxWidth: 300,
            boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
          }}>
            <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--inc-text)', marginBottom: 6 }}>Delete this grant?</div>
            <div style={{ fontSize: 13, color: 'var(--inc-textDim)', lineHeight: '19px', marginBottom: 18 }}>
              {grant.ticker} · {fmtShares(grant.sharesTotal)} shares will be removed from your projections.
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button onClick={() => setConfirmDelete(false)} style={{
                flex: 1, background: 'var(--inc-surfaceWarm)', border: 'none', cursor: 'pointer',
                padding: '11px', borderRadius: 12, fontFamily: 'inherit', fontSize: 13, fontWeight: 700, color: 'var(--inc-text)',
              }}>Cancel</button>
              <button onClick={() => { rsu.deleteGrant(grant.id); rsu.setView('list'); }} style={{
                flex: 1, background: 'var(--inc-red)', border: 'none', cursor: 'pointer',
                padding: '11px', borderRadius: 12, fontFamily: 'inherit', fontSize: 13, fontWeight: 700, color: 'white',
              }}>Delete</button>
            </div>
          </div>
        </div>
      )}
    </RSUSheet>
  );
}

// ── Toast + recalc nudge ─────────────────────────────────────────
function RSUToast({ toast }) {
  if (!toast) return null;
  return (
    <div style={{
      position: 'absolute', left: 16, right: 16, bottom: 100, zIndex: 40,
      background: 'var(--inc-toastBg)', color: 'var(--inc-toastText)', borderRadius: 12,
      padding: '12px 16px', fontSize: 13, fontWeight: 600, textAlign: 'center',
      animation: 'rsuToastIn .25s ease-out', boxShadow: '0 8px 24px rgba(0,0,0,0.25)',
    }}>
      {toast.msg}
      <style>{`@keyframes rsuToastIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }`}</style>
    </div>
  );
}

function RSURecalcNudge({ nudge, setNudge }) {
  if (!nudge) return null;
  return (
    <div style={{
      position: 'absolute', left: 16, right: 16, bottom: 100, zIndex: 40,
      background: 'var(--inc-sage)', color: 'white', borderRadius: 12,
      padding: '12px 16px', fontSize: 12.5, fontWeight: 600, textAlign: 'center',
      display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10,
      animation: 'rsuToastIn .25s ease-out', boxShadow: '0 8px 24px rgba(95,140,124,0.35)',
    }}>
      Recalculate to include RSUs
      <button onClick={() => setNudge(false)} style={{
        background: 'rgba(255,255,255,0.2)', border: 'none', cursor: 'pointer', color: 'white',
        borderRadius: 8, padding: '5px 10px', fontFamily: 'inherit', fontSize: 11.5, fontWeight: 700,
      }}>Dismiss</button>
    </div>
  );
}

Object.assign(window, {
  RSUSheet, RSUGrantsList, RSUGrantForm, RSUGrantDetail, RSUToast, RSURecalcNudge,
});
