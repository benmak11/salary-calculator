// budget-expenses.jsx — Expense entry: 50/30/20 donut header + rows grouped
// by Needs/Wants/Savings, and the add-expense sheet.

function BudgetBucketDonut({ needs, wants, savings }) {
  const takeHome = needs + wants + savings || 1;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '4px 0 18px' }}>
      <Donut
        wedges={[
          { value: needs, color: 'var(--inc-sageDeep)' },
          { value: wants, color: 'var(--inc-blush)' },
          { value: savings, color: 'var(--inc-gold)' },
        ]}
        size={168} thickness={20}
        center={
          <>
            <div style={{ fontSize: 10.5, color: 'var(--inc-textMute)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.6 }}>Per paycheck</div>
            <div style={{ fontSize: 22, color: 'var(--inc-text)', fontWeight: 700, marginTop: 4, letterSpacing: -0.4 }}>{fmtMoney(takeHome, { cents: false })}</div>
          </>
        }
      />
      <div style={{ display: 'flex', gap: 18, marginTop: 16 }}>
        {[{ c: 'var(--inc-sageDeep)', l: 'Needs', v: needs }, { c: 'var(--inc-blush)', l: 'Wants', v: wants }, { c: 'var(--inc-gold)', l: 'Savings', v: savings }].map(d => (
          <div key={d.l} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <div style={{ width: 9, height: 9, borderRadius: '50%', background: d.c }} />
            <span style={{ fontSize: 11.5, color: 'var(--inc-textDim)', fontWeight: 600 }}>{d.l} · {fmtMoney(d.v, { cents: false })}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function BudgetExpenseRow({ expense }) {
  const cadenceLabel = (CADENCES.find(c => c.value === expense.cadence) || {}).label || expense.cadence;
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 0' }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 14.5, fontWeight: 600, color: 'var(--inc-text)' }}>{expense.name}</div>
        <div style={{ fontSize: 11.5, color: 'var(--inc-textMute)', marginTop: 2 }}>
          {cadenceLabel}{expense.dueDay ? ` · due the ${expense.dueDay}${expense.dueDay === 1 ? 'st' : 'th'}` : ''}
        </div>
      </div>
      <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--inc-text)', fontVariantNumeric: 'tabular-nums' }}>{fmtMoney(expense.amount, { cents: false })}</div>
    </div>
  );
}

function BudgetExpenseGroup({ title, color, expenses }) {
  if (expenses.length === 0) return null;
  return (
    <B2Card>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
        <div style={{ width: 8, height: 8, borderRadius: '50%', background: color }} />
        <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--inc-textMute)', textTransform: 'uppercase', letterSpacing: 0.7 }}>{title}</div>
      </div>
      <div>
        {expenses.map((e, i) => (
          <div key={e.id} style={{ borderTop: i > 0 ? '1px solid var(--inc-hairline)' : 'none' }}>
            <BudgetExpenseRow expense={e} />
          </div>
        ))}
      </div>
    </B2Card>
  );
}

function BudgetExpensesScreen({ expenses, needsTotal, wantsTotal, savingsTotal, onAddExpense }) {
  const needs = expenses.filter(e => e.bucket === 'needs');
  const wants = expenses.filter(e => e.bucket === 'wants');
  const savings = expenses.filter(e => e.bucket === 'savings');
  return (
    <B2Screen>
      <AppSectionHeader title="Your expenses" />
      <div style={{ padding: '0 16px 100px' }}>
        <B2Card>
          <BudgetBucketDonut needs={needsTotal} wants={wantsTotal} savings={savingsTotal} />
        </B2Card>
        <BudgetExpenseGroup title="Needs" color="var(--inc-sageDeep)" expenses={needs} />
        <BudgetExpenseGroup title="Wants" color="var(--inc-blush)" expenses={wants} />
        <BudgetExpenseGroup title="Savings" color="var(--inc-gold)" expenses={savings} />
        <button onClick={onAddExpense} style={{
          width: '100%', background: 'var(--inc-sageBg)', border: '1.5px dashed var(--inc-sageSoft)',
          borderRadius: 14, padding: 15, cursor: 'pointer', fontFamily: 'inherit', fontSize: 13.5,
          fontWeight: 700, color: 'var(--inc-sageDeep)', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
        }}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
          Add an expense
        </button>
      </div>
    </B2Screen>
  );
}

// ── Add-expense sheet ────────────────────────────────────────────
function BudgetAddExpenseSheet({ onClose, onSave }) {
  const [name, setName] = React.useState('');
  const [amount, setAmount] = React.useState('');
  const [cadence, setCadence] = React.useState('monthly');
  const [bucket, setBucket] = React.useState('needs');
  const [dueDate, setDueDate] = React.useState('');
  return (
    <div style={{
      position: 'absolute', inset: 0, zIndex: 30, display: 'flex', flexDirection: 'column',
      background: 'var(--inc-bg)', animation: 'rsuSheetUp .28s cubic-bezier(0.32,0.72,0,1)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '54px 18px 12px', flexShrink: 0 }}>
        <div style={{ width: 30 }} />
        <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--inc-text)' }}>Add expense</div>
        <button onClick={onClose} style={{
          width: 30, height: 30, borderRadius: '50%', background: 'var(--inc-surfaceWarm)', border: 'none', cursor: 'pointer',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="var(--inc-textDim)" strokeWidth="2.5"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
        </button>
      </div>
      <div style={{ flex: 1, overflow: 'auto', padding: '0 16px 24px' }}>
        <B2Card>
          <B2Label>Name</B2Label>
          <div style={{ marginBottom: 18 }}>
            <input value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Internet" style={{
              width: '100%', border: 'none', borderBottom: '2px solid var(--inc-hairline)', paddingBottom: 8,
              fontFamily: 'inherit', fontSize: 18, fontWeight: 600, color: 'var(--inc-text)', background: 'none', outline: 'none', boxSizing: 'border-box',
            }} />
          </div>
          <B2MoneyField label="Amount" value={amount} onChange={setAmount} />
          <B2Label>Cadence</B2Label>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 18 }}>
            {CADENCES.map(c => (
              <button key={c.value} onClick={() => setCadence(c.value)} style={{
                padding: '8px 13px', borderRadius: 999, border: `1.5px solid ${cadence === c.value ? 'transparent' : 'var(--inc-hairlineStrong)'}`,
                background: cadence === c.value ? 'var(--inc-sage)' : 'transparent', color: cadence === c.value ? 'white' : 'var(--inc-text)',
                fontFamily: 'inherit', fontSize: 12.5, fontWeight: 600, cursor: 'pointer',
              }}>{c.label}</button>
            ))}
          </div>
          <B2Label>Bucket</B2Label>
          <B2Segmented value={bucket} onChange={setBucket} options={BUCKETS.map(b => ({ value: b.value, label: b.label }))} />
          <B2Label>Due date (optional)</B2Label>
          <input type="date" value={dueDate} onChange={e => setDueDate(e.target.value)} style={{
            width: '100%', boxSizing: 'border-box', border: 'none', borderBottom: '2px solid var(--inc-hairline)',
            padding: '0 0 8px', fontFamily: 'inherit', fontSize: 16, fontWeight: 600, color: 'var(--inc-text)', background: 'none', outline: 'none',
          }} />
        </B2Card>
      </div>
      <div style={{ padding: '0 16px 24px' }}>
        <button onClick={() => onSave && onSave({ name, amount: parseFloat(amount) || 0, cadence, bucket, dueDate })} style={{
          width: '100%', padding: '14px 0', borderRadius: 14, border: 'none', cursor: 'pointer',
          background: 'var(--inc-sage)', color: 'white', fontFamily: 'inherit', fontSize: 15, fontWeight: 700,
        }}>Save expense</button>
      </div>
      <style>{`@keyframes rsuSheetUp { from { transform: translateY(24px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }`}</style>
    </div>
  );
}

Object.assign(window, { BudgetExpensesScreen, BudgetAddExpenseSheet, BudgetBucketDonut, BudgetExpenseGroup, BudgetExpenseRow });
