// Copyright (c) 2026 TSI Private Limited. All rights reserved.
import { useState, useMemo } from 'react'

const EV = { AVAILABILITY_REQUEST: ['4', 'Request Sleeper Pod availability'], AVAILABILITY_RESPONSE: ['5', 'Availability & price received'], PREBOOK_REQUEST: ['7', 'Pre-book Sleeper Pod'], PREBOOK_RESPONSE: ['8', 'Offer ID received'], CONFIRM_REQUEST: ['10', 'Confirm Sleeper Pod'], CONFIRM_RESPONSE: ['11', 'Order ID received'] }
export function EventFeed({ events }) {
  if (!events.length) return null
  return (<aside className="feed" aria-label="Live integration events"><b>Live events · PNR {events[0].payload?.pnr}</b>
    {events.slice(-8).map((m, i) => { const [n, l] = EV[m.eventType] || ['', m.eventType]; return <div key={i} className={m.direction === 'IN' ? 'in' : 'out'}>{m.direction === 'OUT' ? '→ IBS' : '← IBS'} · step {n} · {l}{m.payload?.offerId ? ` · ${m.payload.offerId}` : ''}{m.payload?.orderId ? ` · ${m.payload.orderId}` : ''}{m.payload?.success === false ? ' ✗' : ''}</div> })}</aside>)
}
export function PodIbs({ sky, onPrebook, onRemove }) {
  const [sess, setSess] = useState('A'), [inv, setInv] = useState(''), [ok, setOk] = useState(false)
  if (sky.status === 'idle' || sky.status === 'checking') return <div className="sky"><div className="skyimg" /><div className="skyb"><h3>Sleeper Pod</h3><p className="muted">Checking Sleeper Pod availability with IBS…</p></div></div>
  if (sky.status === 'na') return <div className="sky"><div className="skyimg" /><div className="skyb"><h3>Sleeper Pod</h3><p className="muted">{sky.error || 'Sleeper Pod is not available on this journey.'}</p></div></div>
  const av = sky.avail, cur = av.sessions.find(x => x.code === sess) || av.sessions[0], nests = cur.nests.filter(n => n.available)
  const chosen = nests.find(n => String(n.inventoryId) === String(inv)) || nests[0]
  return (<div className="sky"><div className="skyimg" /><div className="skyb"><h3>Sleeper Pod</h3><p>Book a 4-hour lie-flat experience</p><p>{av.flightNo} · {av.availableCount} pods available</p>
    <b className="big2">from ${Math.min(...av.sessions.flatMap(x => x.nests.filter(n => n.available).map(n => n.price)))}</b><small>per nest</small>
    {sky.status === 'held' && <p className="grn">Held for you · Offer {sky.offer.offerId} · Session {sky.offer.sessionCode}, {sky.offer.nestLocation.replace('_', ' ').toLowerCase()} · ${sky.offer.price}<br /><button className="link" onClick={onRemove}>Remove</button></p>}
    {sky.status !== 'held' && <div className="skyf"><div className="row2"><label>Session<select value={sess} onChange={e => { setSess(e.target.value); setInv('') }}>{av.sessions.map(x => <option key={x.code} value={x.code}>{x.label}</option>)}</select></label>
      <label>Pod position<select value={chosen?.inventoryId || ''} onChange={e => setInv(e.target.value)}>{nests.map(n => <option key={n.inventoryId} value={n.inventoryId}>{n.label} - ${n.price}</option>)}</select></label></div>
      {nests.length === 0 && <p className="err">No pods left in this session.</p>}
      <label className="chk"><input type="checkbox" checked={ok} onChange={e => setOk(e.target.checked)} /> I acknowledge that I have read and accept the conditions of Sleeper Pod.</label>
      {sky.error && <p className="err">{sky.error}</p>}
      <button className="btn" disabled={!ok || !chosen || sky.status === 'prebooking'} onClick={() => onPrebook(chosen.inventoryId)}>{sky.status === 'prebooking' ? 'Holding pod…' : 'Add to booking'}</button></div>}</div></div>)
}
export function SeatMap({ seats, sid, pick, mine, msg, p, cont, cabinName }) {
  const by = useMemo(() => Object.fromEntries(seats.map(s => [s.code, s])), [seats])
  const rows = [...new Set(seats.map(s => parseInt(s.code)))].sort((a, b) => a - b), cols = ['A', 'B', 'C', '_', 'D', 'E', 'F', '_', 'H', 'J', 'K']
  const btn = (r, c) => { const s = by[r + c]; if (!s) return <span className="seat ghost" key={c} />
    const st = s.status === 'BOOKED' ? 'booked' : s.heldBy === sid ? 'mine' : s.status === 'HELD' ? 'held' : 'avail'
    return <button key={c} className={`seat ${s.kind} ${st}`} disabled={st === 'booked' || st === 'held'} onClick={() => pick(s)} aria-label={`Seat ${s.code} ${st}`}>{st === 'booked' ? '👤' : st === 'mine' ? '✓' : s.price > 0 ? `$${s.price}` : c}</button> }
  return (<><h2 className="ul">Select seats</h2>
    <div className="seatwrap"><div><div className="plane">{cabinName} cabin</div><div className="cabin">{rows.map(r => <div className="srow" key={r}><span className="rn">{r}</span>{cols.map((c, i) => c === '_' ? <span className="aisle" key={i} /> : btn(r, c))}</div>)}</div></div>
      <aside><div className="panel"><b>{p.first || 'Passenger'} {p.last}</b><p>{mine ? `Seat ${mine.code} · $${mine.price}` : 'Select a seat'}</p></div>
        <ul className="legend"><li><i className="seat LEGROOM" />Extra Legroom</li><li><i className="seat PREFERRED" />Preferred seat</li><li><i className="seat STANDARD" />Available seat</li><li><i className="seat COUCHROW" />Couch Row</li><li><i className="seat booked" />Occupied seat</li><li><i className="seat held" />Being selected by someone else</li></ul>
        <p className="muted">Seats update live as other passengers choose.</p></aside></div>
    {msg && <p className="err" role="alert">{msg}</p>}
    <div className="foot"><div><b>Pick the best seats now or let us decide</b><p>If you don't mind where you sit, we'll assign your seats at check-in for no charge.</p><p>To keep your current seat selection, please complete your booking within 15 minutes.</p></div><button className="btn" onClick={cont}>Continue</button></div></>)
}

export function Pay({ onPay }) {
  const [c, setC] = useState({ name: '', num: '', exp: '', cvc: '' }), [t, setT] = useState(false), [busy, setBusy] = useState(false)
  const v = { name: c.name.trim(), num: c.num.replace(/\s/g, '').length === 16, exp: /^(0[1-9]|1[0-2])\/\d{2}$/.test(c.exp), cvc: /^\d{3,4}$/.test(c.cvc) }
  const submit = async () => { setT(true); if (!(v.name && v.num && v.exp && v.cvc)) return; setBusy(true); await onPay(c); setBusy(false) }
  return (<><label>Name on card<input value={c.name} className={t && !v.name ? 'bad' : ''} onChange={e => setC({ ...c, name: e.target.value })} /></label>
    <label>Card number<input inputMode="numeric" maxLength={19} placeholder="4242 4242 4242 4242" value={c.num} className={t && !v.num ? 'bad' : ''} onChange={e => setC({ ...c, num: e.target.value.replace(/\D/g, '').replace(/(.{4})/g, '$1 ').trim() })} /></label>
    <div className="row2"><label>Expiry (MM/YY)<input maxLength={5} value={c.exp} className={t && !v.exp ? 'bad' : ''} onChange={e => setC({ ...c, exp: e.target.value })} /></label><label>CVC<input maxLength={4} value={c.cvc} className={t && !v.cvc ? 'bad' : ''} onChange={e => setC({ ...c, cvc: e.target.value.replace(/\D/g, '') })} /></label></div>
    {t && !(v.name && v.num && v.exp && v.cvc) && <p className="err">Check the highlighted card details.</p>}
    <div className="end"><button className="btn" disabled={busy} onClick={submit}>{busy ? 'Processing…' : 'Confirm and pay'}</button></div></>)
}
