// budget-goals.jsx — Savings goals intro/list + goal editor sheet.
// One component serves both the empty-state and populated-state artboards
// (pass different `goals` arrays). Cards show drag handles for priority
// order per the "no limit on goals" story; reordering itself is decorative
// here (static mock), matching the rest of the RSU/History surfaces.

function BudgetGoalChips({ selectedTypes, onToggle }) {
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, padding: '0 16px 4px' }}>
      {GOAL_TYPES.map(t => {
        const active = selectedTypes.includes(t.id);
        return (
          <button key={t.id} onClick={() => onToggle(t.id)} style={{
            display: 'flex', alignItems: 'center', gap: 6, padding: '9px 14px', borderRadius: 999,
            border: `1.5px solid ${active ? 'transparent' : 'var(--inc-hairlineStrong)'}`,
            background: active ? 'var(--inc-sage)' : 'var(--inc-surface)', cursor: 'pointer',
            fontFamily: 'inherit', fontSize: 13, fontWeight: 600, color: active ? 'white' : 'var(--inc-text)',
          }}>
            <GoalTypeIcon type={t.id} color={active ? 'white' : 'var(--inc-sage)'} size={14} />
            {t.label}
          </button>
        );
      })}
    </div>
  );
}

function BudgetGoalCard({ goal, saved = 0 }) {
  const pct = Math.min(100, Math.round((saved / goal.target) * 100));
  return (
    <div style={{
      background: 'var(--inc-surface)', border: '1px solid var(--inc-cardBorder)', borderRadius: 18,
      padding: 16, marginBottom: 12, display: 'flex', gap: 12, alignItems: 'flex-start',
    }}>
      <div style={{ cursor: 'grab', padding: '6px 2px', color: 'var(--inc-textMute)', marginTop: 2 }}>
        <svg width="10" height="16" viewBox="0 0 10 16" fill="currentColor">
          <circle cx="2" cy="2" r="1.3" /><circle cx="8" cy="2" r="1.3" /><circle cx="2" cy="8" r="1.3" /><circle cx="8" cy="8" r="1.3" /><circle cx="2" cy="14" r="1.3" /><circle cx="8" cy="14" r="1.3" />
        </svg>
      </div>
      <div style={{
        width: 40, height: 40, borderRadius: 12, background: 'var(--inc-sageBg)', flexShrink: 0,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}><GoalTypeIcon type={goal.type} /></div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 2 }}>
          <span style={{ fontSize: 15, fontWeight: 700, color: 'var(--inc-text)' }}>{goal.name}</span>
          <span style={{ fontSize: 10.5, fontWeight: 700, color: 'var(--inc-textMute)', textTransform: 'uppercase', letterSpacing: 0.5 }}>Priority {goal.priority}</span>
        </div>
        <div style={{ fontSize: 12.5, color: 'var(--inc-textDim)', marginBottom: 10 }}>
          {fmtMoney(saved, { cents: false })} of {fmtMoney(goal.target, { cents: false })}
          {goal.targetDate && <> · by {fmtMonYear(new Date(goal.targetDate + 'T00:00:00'))}</>}
        </div>
        <div style={{ height: 7, borderRadius: 999, background: 'var(--inc-hairline)', overflow: 'hidden' }}>
          <div style={{ width: `${pct}%`, height: '100%', background: 'var(--inc-sage)', borderRadius: 999 }} />
        </div>
      </div>
    </div>
  );
}

function BudgetGoalsScreen({ goals, onAddGoal, savedByGoal = {} }) {
  const [selected, setSelected] = React.useState([]);
  const toggle = (id) => setSelected(s => s.includes(id) ? s.filter(x => x !== id) : [...s, id]);
  const empty = goals.length === 0;
  return (
    <B2Screen>
      <AppSectionHeader title="What are you saving for?" />
      <div style={{ padding: '0 0 16px' }}>
        <div style={{ padding: '0 26px 14px', fontSize: 13.5, color: 'var(--inc-textDim)', lineHeight: '20px' }}>
          Pick as many as you'd like — there's no limit on goals.
        </div>
        <BudgetGoalChips selectedTypes={selected} onToggle={toggle} />
      </div>
      <div style={{ padding: '20px 16px 100px' }}>
        {empty ? (
          <div style={{ textAlign: 'center', padding: '30px 20px' }}>
            <div style={{ fontFamily: 'var(--inc-serif, "Fraunces", serif)', fontSize: 20, color: 'var(--inc-text)', fontWeight: 500, marginBottom: 8 }}>
              Add your first goal
            </div>
            <div style={{ fontSize: 13.5, color: 'var(--inc-textDim)', lineHeight: '20px', marginBottom: 20 }}>
              Tap a category above, then set an amount — we'll fold it into your paycheck plan.
            </div>
            <button onClick={onAddGoal} style={{
              background: 'var(--inc-sage)', color: 'white', border: 'none', cursor: 'pointer',
              padding: '13px 22px', borderRadius: 14, fontFamily: 'inherit', fontSize: 14, fontWeight: 700,
            }}>Add a goal</button>
          </div>
        ) : (
          <>
            {goals.map(g => <BudgetGoalCard key={g.id} goal={g} saved={savedByGoal[g.id] || 0} />)}
            <button onClick={onAddGoal} style={{
              width: '100%', background: 'var(--inc-sageBg)', border: '1.5px dashed var(--inc-sageSoft)',
              borderRadius: 14, padding: 15, cursor: 'pointer', fontFamily: 'inherit', fontSize: 13.5,
              fontWeight: 700, color: 'var(--inc-sageDeep)', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
            }}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
              Add another goal
            </button>
          </>
        )}
      </div>
    </B2Screen>
  );
}

// ── Goal editor sheet — mirrors GrantFormView / ShellAccountSheet shape ──
function BudgetGoalEditorSheet({ goal, onClose, onSave }) {
  const [name, setName] = React.useState(goal?.name || 'Japan trip');
  const [target, setTarget] = React.useState(goal?.target ? String(goal.target) : '6000');
  const [targetDate, setTargetDate] = React.useState(goal?.targetDate || '');
  return (
    <div style={{ position: 'absolute', inset: 0, zIndex: 40, display: 'flex', alignItems: 'flex-end' }}>
      <div onClick={onClose} style={{ position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.35)' }} />
      <div style={{
        position: 'relative', width: '100%', background: 'var(--inc-surface)', borderRadius: '26px 26px 0 0',
        padding: '14px 22px 40px', boxShadow: '0 -8px 30px rgba(0,0,0,0.15)',
      }}>
        <div style={{ width: 36, height: 4, borderRadius: 2, background: 'var(--inc-hairlineStrong)', margin: '0 auto 20px' }} />
        <div style={{ fontFamily: 'var(--inc-serif, "Fraunces", serif)', fontSize: 22, fontWeight: 500, color: 'var(--inc-text)', marginBottom: 18 }}>
          {goal ? 'Edit goal' : 'New goal'}
        </div>
        <B2Label>Name</B2Label>
        <div style={{ marginBottom: 18 }}>
          <input value={name} onChange={e => setName(e.target.value)} style={{
            width: '100%', border: 'none', borderBottom: '2px solid var(--inc-hairline)', paddingBottom: 8,
            fontFamily: 'inherit', fontSize: 18, fontWeight: 600, color: 'var(--inc-text)', background: 'none', outline: 'none', boxSizing: 'border-box',
          }} />
        </div>
        <B2MoneyField label="Target amount" value={target} onChange={setTarget} />
        <B2Label>Target date (optional)</B2Label>
        <input type="date" value={targetDate} onChange={e => setTargetDate(e.target.value)} style={{
          width: '100%', boxSizing: 'border-box', border: 'none', borderBottom: '2px solid var(--inc-hairline)',
          padding: '0 0 8px', fontFamily: 'inherit', fontSize: 16, fontWeight: 600, color: 'var(--inc-text)', background: 'none', outline: 'none', marginBottom: 26,
        }} />
        <button onClick={() => onSave && onSave({ name, target: parseFloat(target) || 0, targetDate })} style={{
          width: '100%', padding: '14px 0', borderRadius: 14, border: 'none', cursor: 'pointer',
          background: 'var(--inc-sage)', color: 'white', fontFamily: 'inherit', fontSize: 15, fontWeight: 700,
        }}>Save goal</button>
      </div>
    </div>
  );
}

Object.assign(window, { BudgetGoalsScreen, BudgetGoalEditorSheet, BudgetGoalCard, BudgetGoalChips });
