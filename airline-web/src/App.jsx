// Copyright (c) 2026 TSI Private Limited. All rights reserved.
import { useState, useEffect, useRef } from 'react'
import { PodIbs, SeatMap, Pay, EventFeed } from './Parts.jsx'

const AIRPORTS = [['JFK','New York JFK'],['EWR','Newark, NJ'],['AKL','Auckland'],['WLG','Wellington'],['CHC','Christchurch'],['ZQN','Queenstown'],['SYD','Sydney'],['MAA','Chennai'],['SIN','Singapore']]
const city = c => (AIRPORTS.find(a => a[0] === c) || [c, c])[1]
const CABINS = { ECONOMY: 'Economy', PREMIUM: 'Premium Economy', BUSINESS: 'Business' }
const CAB_INFO = { ECONOMY: ['Streamlined check-in', 'Inflight meal & beverages', 'On-demand entertainment'], PREMIUM: ['Premium check-in and priority boarding', 'Premium seating, more legroom', 'Fine cuisine and beverages'], BUSINESS: ['Lounge access', 'Premium check-in & priority boarding', 'Luxury lie-flat bed', 'Fine cuisine & beverages'] }
const ALL = [['flights', 'Flights'], ['comfort', 'Comfort'], ['pax', 'Passengers'], ['extras', 'Extras'], ['seats', 'Seats'], ['pay', 'Review & pay']]
const maxDate = new Date(Date.now() + 183 * 864e5).toISOString().slice(0, 10)   // flights are loaded 6 months ahead
const TAX = 66.4, BAG = 80, SAF = [0, 10, 25, 44.88]
const CARS = [['4/5 Door Compact', 'Mazda 3, Automatic', 233.27], ['SUV Intermediate', 'Mitsubishi Outlander, Automatic', 314.9], ['SUV Compact', 'Mazda CX-30, Automatic', 259.83], ['SUV Intermediate', 'Hyundai Kona Electric, Automatic', 271.93]]
const today = new Date().toISOString().slice(0, 10)
const usd = n => `USD ${n.toFixed(2)}`, usd0 = n => '$' + Math.round(n).toLocaleString('en-US')
const dt = s => new Date(s), pad = n => String(n).padStart(2, '0')
const clock = (s, h24) => { const h = +s.slice(11, 13), m = s.slice(14, 16); return h24 ? `${pad(h)}:${m}` : `${h % 12 || 12}:${m}${h < 12 ? 'AM' : 'PM'}` }
const dayShort = s => dt(s).toLocaleDateString('en-US', { weekday: 'short', day: '2-digit' })
const dayLong = s => dt(s).toLocaleDateString('en-US', { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric' })
const dur = m => `${Math.floor(m / 60)}h ${m % 60}m`
const sid = Math.random().toString(36).slice(2, 10)
const lowest = i => Math.min(...Object.values(i.fares).filter(f => f.available).map(f => f.price), Infinity)

export default function App() {
  const [step, setStep] = useState('flights')
  const [q, setQ] = useState({ from: 'JFK', to: 'AKL', date: today })
  const [data, setData] = useState(null), [loading, setLoading] = useState(false), [editing, setEditing] = useState(true)
  const [sort, setSort] = useState('price'), [h24, setH24] = useState(false), [sel, setSel] = useState(null)
  const [p, setP] = useState({ title: 'Mr', first: '', middle: '', last: '', gender: 'Male', d: '', m: '', y: '', ffp: 'None', cc: '+64', phone: '', email: '', meal: 'Regular', assist: 'No assistance required' })
  const [ex, setEx] = useState({ nest: null, bags: 0, car: null, saf: 0 })
  const [pnr, setPnr] = useState(null), [events, setEvents] = useState([]), [info, setInfo] = useState('')
  const [sky, setSky] = useState({ status: 'idle' })
  const [seats, setSeats] = useState([]), [msg, setMsg] = useState(''), [res, setRes] = useState(null), [tried, setTried] = useState(false)
  const sock = useRef(null)
  const itin = sel?.itin, cabin = sel?.cabin, fare = itin?.fares[cabin]
  const steps = cabin === 'ECONOMY' || !sel ? ALL : ALL.filter(s => s[0] !== 'comfort')
  const stepRef = useRef('flights'); stepRef.current = step
  const go = k => { window.history.pushState({ k }, ''); setStep(k); setTried(false); setMsg(''); window.scrollTo(0, 0) }
  const back = () => { const at = steps.findIndex(s => s[0] === step); if (at > 0) go(steps[at - 1][0]) }
  useEffect(() => {   // browser Back / Forward buttons move between booking steps
    window.history.replaceState({ k: 'flights' }, '')
    const h = e => { if (stepRef.current === 'done') return window.location.reload(); setStep(e.state?.k || 'flights'); setTried(false); setMsg(''); window.scrollTo(0, 0) }
    window.addEventListener('popstate', h); return () => window.removeEventListener('popstate', h)
  }, [])

  const load = async (qq = q) => {
    setLoading(true); setEditing(false); setSel(null)
    try { setData(await (await fetch(`/api/flights/search?from=${qq.from}&to=${qq.to}&date=${qq.date}`)).json()) } catch { setData({ itineraries: [], dates: [] }) }
    setLoading(false)
  }
  const pickDate = d => { const n = { ...q, date: d }; setQ(n); load(n) }

  useEffect(() => {
    if (!itin) return
    let alive = true
    fetch(`/api/flights/${itin.id}/seats`).then(r => r.json()).then(s => alive && setSeats(s))
    const ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/seats?itineraryId=${itin.id}&sid=${sid}`)
    ws.onmessage = e => {
      const m = JSON.parse(e.data)
      if (m.type === 'SEAT_UPDATE') setSeats(a => a.map(s => s.code === m.seat ? { ...s, status: m.status, heldBy: m.heldBy } : s))
      if (m.type === 'DENIED') setMsg(`Seat ${m.seat} was just taken by another passenger. Choose a different seat.`)
    }
    sock.current = ws
    return () => { alive = false; ws.close() }
  }, [itin?.id])

  const onEvt = m => {
    const p = m.payload || {}
    if (m.eventType === 'AVAILABILITY_RESPONSE') setSky(s => ({ ...s, status: p.available ? 'ready' : 'na', avail: p, error: p.reason }))
    if (m.eventType === 'PREBOOK_RESPONSE') setSky(s => p.success ? { ...s, status: 'held', offer: p, error: '' } : { ...s, status: 'ready', error: p.reason || 'Could not hold that pod.' })
  }
  useEffect(() => {
    if (!pnr) return
    const ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/events?pnr=${pnr.ref}`)
    ws.onmessage = e => {
      const m = JSON.parse(e.data)
      if (m.kind === 'EVENT') { setEvents(a => [...a, m]); onEvt(m) }
      if (m.kind === 'BOOKING') { setInfo(''); if (m.status === 'CONFIRMED') { setRes(m); go('done') } else setMsg(m.reason) }
    }
    return () => ws.close()
  }, [pnr?.ref])
  useEffect(() => {   // step 3-4: opening the Extras page requests Sleeper Pod availability from IBS
    if (step === 'extras' && pnr && sky.status === 'idle') {
      setSky({ status: 'checking' })
      fetch(`/api/pnr/${pnr.ref}/pod/availability`, { method: 'POST' }).then(r => r.json()).then(j => { if (!j.sent) setSky({ status: 'na', error: j.reason }) }).catch(() => setSky({ status: 'na', error: 'Could not reach the booking service.' }))
    }
  }, [step, pnr, sky.status])
  const prebook = id => { setSky(s => ({ ...s, status: 'prebooking', error: '' })); fetch(`/api/pnr/${pnr.ref}/pod/prebook`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ inventoryId: id }) }) }
  const removeSky = () => { fetch(`/api/pnr/${pnr.ref}/pod/remove`, { method: 'POST' }); setSky(s => ({ ...s, status: 'ready', offer: null })) }
  const createPnr = async () => {
    setMsg('')
    try {
      const same = pnr && pnr.itineraryId === itin.id && pnr.cabin === cabin   // came back to this page: update the same PNR
      const r = await fetch(same ? `/api/pnr/${pnr.ref}` : '/api/pnr', { method: same ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ itineraryId: itin.id, cabin, passenger: p }) })
      const j = await r.json(); if (!r.ok) return setMsg(j.error || 'Could not create your booking. Please try again.')
      if (!same) { setPnr({ ...j, itineraryId: itin.id, cabin }); setEvents([]); setSky({ status: 'idle' }) }
      go('extras')
    } catch { setMsg('Cannot reach the booking service. Check that airline-service is running on port 8081.') }
  }
  const choose = v => {   // a different flight or cabin means a different PNR
    if (pnr && (pnr.itineraryId !== v.itin.id || pnr.cabin !== v.cabin)) { fetch(`/api/pnr/${pnr.ref}/pod/remove`, { method: 'POST' }); setPnr(null); setSky({ status: 'idle' }); setEvents([]) }
    setSel(v)
  }

  const cabinSeats = seats.filter(s => s.cabin === cabin)
  const mine = cabinSeats.find(s => s.heldBy === sid && s.status === 'HELD')
  const extrasTotal = ex.bags * BAG + (sky.offer?.price || 0) + ex.saf
  const total = fare ? fare.price + TAX + (mine?.price || 0) + extrasTotal : 0
  const pickSeat = s => {
    setMsg('')
    if (mine?.code === s.code) return sock.current.send(JSON.stringify({ type: 'RELEASE', seat: s.code }))
    if (mine) sock.current.send(JSON.stringify({ type: 'RELEASE', seat: mine.code }))
    sock.current.send(JSON.stringify({ type: 'HOLD', seat: s.code }))
  }
  const reset = () => { setPnr(null); setEvents([]); setSky({ status: 'idle' }); setInfo(''); setSel(null); setRes(null); setSeats([]); setData(null); setEditing(true); setEx({ nest: null, bags: 0, car: null, saf: 0 }); go('flights') }
  const dobOk = (() => { const d = +p.d, m = +p.m, y = +p.y, x = new Date(y, m - 1, d); return y > 1900 && y <= new Date().getFullYear() && x.getFullYear() === y && x.getMonth() === m - 1 && x.getDate() === d })()
  const pOk = p.first && p.last && dobOk && p.phone && /\S+@\S+\.\S+/.test(p.email)
  const set = (k, v) => setP(x => ({ ...x, [k]: v }))

  const pay = async c => {   // step 9: payment, then the airline confirms Sleeper Pod with IBS (steps 10-11)
    setMsg(''); const r = await fetch(`/api/pnr/${pnr.ref}/pay`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sid, seats: mine ? [mine.code] : [], extras: { bags: ex.bags, saf: ex.saf, car: ex.car }, card: { holder: c.name, num: c.num } }) })
    const j = await r.json()
    if (!r.ok) return setMsg(j.error || 'Payment failed. Try again.')
    if (j.status === 'CONFIRMED') { setRes({ ...j, status: 'CONFIRMED' }); sock.current?.close(); go('done') } else setInfo('Payment received. Confirming your Sleeper Pod with IBS…')
  }
  const next = () => go(cabin === 'ECONOMY' ? 'comfort' : 'pax')
  const rows = data ? [...data.itineraries].sort((a, b) => sort === 'price' ? lowest(a) - lowest(b) : sort === 'departs' ? a.departAt.localeCompare(b.departAt) : sort === 'arrives' ? a.arriveAt.localeCompare(b.arriveAt) : a.durationMin - b.durationMin) : []

  const searchBox = (<div className="search">
    <label>From<select value={q.from} onChange={e => setQ({ ...q, from: e.target.value })}>{AIRPORTS.map(a => <option key={a[0]} value={a[0]}>{a[1]} ({a[0]})</option>)}</select></label>
    <label>To<select value={q.to} onChange={e => setQ({ ...q, to: e.target.value })}>{AIRPORTS.map(a => <option key={a[0]} value={a[0]}>{a[1]} ({a[0]})</option>)}</select></label>
    <label>Depart<input type="date" min={today} max={maxDate} value={q.date} onChange={e => setQ({ ...q, date: e.target.value })} /></label>
    <button className="btn" disabled={q.from === q.to} onClick={() => load()}>Search flights</button></div>)

  return (<>
    <header className="top"><a className="brand" href="#top"><Logo />Aerolyn<small>by TSI</small></a><nav><a href="#book">Book</a><a href="#manage">Manage trip</a><span className="live">● Live seat updates</span></nav></header>
    {step !== 'done' && <ol className="steps" aria-label="Booking progress">{steps.map(([k, l], i) => { const at = steps.findIndex(s => s[0] === step); return <li key={k} className={i < at ? 'done' : i === at ? 'cur' : ''} onClick={i < at ? () => go(k) : undefined} role={i < at ? 'button' : undefined} tabIndex={i < at ? 0 : undefined} onKeyDown={i < at ? e => e.key === 'Enter' && go(k) : undefined} style={i < at ? { cursor: 'pointer' } : undefined}><span>{i < at ? '✓' : i + 1}</span><em>{l}</em></li> })}</ol>}
    <div className={`layout ${itin && step !== 'done' ? 'two' : ''}`}>
    <main>
      {step !== 'flights' && step !== 'done' && <button className="back" onClick={back}>‹ Back</button>}
      {step === 'flights' && <>
        {!data && <section className="hero"><span className="eyebrow">Aerolyn · by TSI</span><h1>Where to next?</h1><p>Search flights up to six months ahead, hold a sleeper pod and choose your seat in one smooth booking.</p>{searchBox}</section>}
        {data && !editing && <button className="pill" onClick={() => setEditing(true)}>{city(q.from)} → {city(q.to)} · {dayLong(q.date)} · 1 adult <u>Change</u></button>}
        {data && editing && searchBox}
        {data && <>
          <h2 className="sec">Choose your flight to {city(q.to)}</h2>
          <div className="cal" role="group" aria-label="Lowest fare each day"><button aria-label="Previous day" className="arr" disabled={q.date <= today} onClick={() => pickDate(new Date(+dt(q.date) - 864e5).toISOString().slice(0, 10))}>‹</button>
            {data.dates.map((d, i) => { const mx = Math.max(1, ...data.dates.map(x => x.minPrice)); return <button key={d.date} disabled={d.minPrice < 0} className={i === 3 ? 'on' : ''} onClick={() => pickDate(d.date)}><span className="bar" style={{ height: d.minPrice < 0 ? 4 : 14 + Math.round(40 * d.minPrice / mx) }} /><small>{dt(d.date).toLocaleDateString('en-US', { weekday: 'short' })} {dt(d.date).getDate()}</small><b>{d.minPrice < 0 ? '–' : usd0(d.minPrice)}</b></button> })}
            <button aria-label="Next day" className="arr" disabled={q.date >= maxDate} onClick={() => pickDate(new Date(+dt(q.date) + 864e5).toISOString().slice(0, 10))}>›</button></div>
          <div className="toolbar"><div className="tabs">{[['price', 'Cheapest'], ['departs', 'Earliest'], ['arrives', 'Arrives'], ['duration', 'Fastest']].map(([k, l]) => <button key={k} className={sort === k ? 'on' : ''} onClick={() => setSort(k)}>{l}</button>)}</div>
            <label className="inl"><input type="checkbox" checked={h24} onChange={e => setH24(e.target.checked)} /> 24-hour clock</label></div>
          <div className="perks">{Object.keys(CABINS).map(c => <span key={c}><b>{CABINS[c]}</b>{CAB_INFO[c][0]}</span>)}</div>
          {loading && <p className="muted">Finding flights…</p>}
          {!loading && rows.length === 0 && <p className="muted">No flights on this date. Pick another day above.</p>}
          {rows.map(i => <Row key={i.id} i={i} h24={h24} sel={sel} setSel={choose} />)}
          <p className="opby">Operated by Aerolyn and partner airlines Harbour Airlines and Summit Airlines.</p>
          <details className="fine"><summary>Good to know</summary><p>Fares are per adult and include taxes and charges, except local airport departure fees collected at the airport.</p><p>A booking is complete only once payment is received and a PNR is issued.</p><p>Availability on partner-operated flights is confirmed after payment.</p></details>
          <div className="end"><button className="btn" disabled={!sel} onClick={next}>Continue</button></div></>}
      </>}

      {step === 'comfort' && <><h2 className="ul">Economy comfort options</h2>
        <div className="note"><b>Changes cannot be made on this page</b><br />You can change your seat choices at the 'Select seats' page.</div>
        <div className="cards2">{[['Extra Legroom', ['Up to 39% more legroom than our standard economy seat', 'Located at the front of the Economy cabin', 'Premium headphones'], '#3c2a7a'], ['Couch Row', ['Turn three seats into a couch', 'Comfy bedding and large pillows', 'Infant pod if required'], '#7a2a8b']].map(([t, l, c]) =>
          <div className="card" key={t}><div className="img" style={{ background: `linear-gradient(135deg,${c},#d9b8e8)` }} /><h3>{t}</h3>{l.map(x => <p key={x}>{x}</p>)}</div>)}</div>
        <div className="end"><button className="btn" onClick={() => go('pax')}>Continue</button></div></>}

      {step === 'pax' && <><h2>Enter passenger details</h2><div className="note light">Sign in to autofill your details and earn Aerolyn Miles.</div>
        <div className="form"><h3>{p.first || 'Passenger'} {p.last}</h3><h4>Personal details</h4><p>Enter details exactly as shown in the passport</p>
          <label>Title*<select value={p.title} onChange={e => set('title', e.target.value)}>{['Mr', 'Ms', 'Mrs', 'Dr'].map(x => <option key={x}>{x}</option>)}</select></label>
          <Field p={p} set={set} tried={tried} k="first" label="First name" req /><Field p={p} set={set} tried={tried} k="middle" label="Middle name" /><Field p={p} set={set} tried={tried} k="last" label="Family name" req />
          <label>Gender*<select value={p.gender} onChange={e => set('gender', e.target.value)}><option>Male</option><option>Female</option><option>Another</option></select></label>
          <div className="dob"><label>Date of birth*<input placeholder="DD" maxLength={2} value={p.d} onChange={e => set('d', e.target.value.replace(/\D/g, ''))} className={tried && !p.d ? 'bad' : ''} /></label><input placeholder="MM" maxLength={2} value={p.m} onChange={e => set('m', e.target.value.replace(/\D/g, ''))} className={tried && !p.m ? 'bad' : ''} /><input placeholder="YYYY" maxLength={4} value={p.y} onChange={e => set('y', e.target.value.replace(/\D/g, ''))} className={tried && !p.y ? 'bad' : ''} /></div>
          <h4>Contact details</h4><div className="dob"><label>Mobile or landline*<input value={p.cc} onChange={e => set('cc', e.target.value)} /></label><input placeholder="Number" value={p.phone} onChange={e => set('phone', e.target.value)} className={tried && !p.phone ? 'bad' : ''} /></div>
          <Field p={p} set={set} tried={tried} k="email" label="Email address" req type="email" />
          <h4>Meal preferences</h4><label>Meal request*<select value={p.meal} onChange={e => set('meal', e.target.value)}>{['Regular', 'Vegetarian', 'Vegan', 'Gluten free', 'Halal', 'Kosher'].map(x => <option key={x}>{x}</option>)}</select></label>
          <h4>Accessible travel</h4><label>Assistance request<select value={p.assist} onChange={e => set('assist', e.target.value)}>{['No assistance required', 'Wheelchair to gate', 'Wheelchair to seat', 'Visual impairment'].map(x => <option key={x}>{x}</option>)}</select></label></div>
        {tried && !pOk && <p className="err">Complete the highlighted fields to continue (date of birth as DD / MM / YYYY, valid email).</p>}
        {msg && <p className="err" role="alert">{msg}</p>}
        <div className="end"><button className="btn" onClick={() => { setTried(true); if (pOk) createPnr() }}>Continue</button></div></>}

      {step === 'extras' && <><h2 className="ul">Extras</h2>
        {cabin !== 'BUSINESS' && <PodIbs sky={sky} onPrebook={prebook} onRemove={removeSky} />}
        <section className="x"><div className="circle bag" /><div className="xb"><h3>Add extra bags</h3><p>In case you need room for something extra</p>
          <div className="panel row"><b>{city(q.from)} to {city(q.to)}</b><button className="rnd" aria-label="Fewer bags" onClick={() => setEx({ ...ex, bags: Math.max(0, ex.bags - 1) })}>−</button><span>{ex.bags}</span><button className="rnd" aria-label="More bags" onClick={() => setEx({ ...ex, bags: ex.bags + 1 })}>+</button><small>extra bags (23kg each)</small><b>${ex.bags * BAG}</b></div></div></section>
        <section><h3>Book a rental car</h3><p className="muted">We'll hold your flights while you book. Pick up in {city(q.to)}.</p>
          <div className="cars">{CARS.map(([t, s, pr], i) => <div className="car" key={i}><b>{t}</b><small>{s}</small><span className="carimg" /><small>Estimated total</small><b className="pur">NZ${pr}</b><button className={`btn ghost ${ex.car === i ? 'sel' : ''}`} onClick={() => setEx({ ...ex, car: ex.car === i ? null : i })}>{ex.car === i ? 'Selected' : 'Select'}</button></div>)}</div></section>
        <section className="saf"><h3>Support sustainable aviation fuel (SAF) and nature projects</h3><p>Suggested contribution: <b className="grn">$44.88</b></p>
          <div className="panel row">{SAF.map(v => <label key={v} className="radio"><input type="radio" name="saf" checked={ex.saf === v} onChange={() => setEx({ ...ex, saf: v })} />${v}</label>)}</div></section>
        <section className="x"><div className="circle umb" /><div className="xb"><h3>Make your flights flexible</h3><p>If your plans change, so can your flights.</p><div className="panel">{cabin === 'ECONOMY' ? 'Flexible fares give you better change and refund conditions than Saver fares.' : `You already have flexibility for ${itin.origin} to ${itin.dest}.`}</div></div></section>
        <div className="end"><button className="btn" onClick={() => go('seats')}>Continue</button></div></>}

      {step === 'seats' && <SeatMap seats={cabinSeats} sid={sid} pick={pickSeat} mine={mine} msg={msg} p={p} cont={() => go('pay')} cabinName={CABINS[cabin]} />}

      {step === 'pay' && <><h2 className="ul">Review and pay</h2>
        <div className="pay"><div className="form"><h3>Card details</h3><Pay onPay={pay} />{info && <p className="grn" role="status">{info}</p>}{msg && <p className="err">{msg}</p>}</div>
          <aside className="panel"><h3>Price summary</h3><dl><dt>Fare ({CABINS[cabin]})</dt><dd>{usd(fare.price)}</dd><dt>Taxes & charges</dt><dd>{usd(TAX)}</dd><dt>Seat {mine?.code || '(at check-in)'}</dt><dd>{usd(mine?.price || 0)}</dd><dt>Extras & Sleeper Pod</dt><dd>{usd(extrasTotal)}</dd><dt className="tot">Total</dt><dd className="tot">{usd(total)}</dd></dl></aside></div></>}

      {step === 'done' && <div className="done"><div className="ok">✓</div><h2>You're booked</h2><p>PNR <b className="ref">{pnr.ref}</b> · Offer ID <b>{sky.offer?.offerId || '–'}</b> · Order ID <b>{res.orderNumber}</b></p><small>The same PNR, Offer ID and Order ID appear in the Airline, Bridge and IBS systems.</small>
        <div className="bp"><div><small>Passenger</small><b>{p.title} {p.first} {p.last}</b><small>Flight</small><b>{itin.segments.map(s => s.flightNo).join(' + ')} · {dayLong(itin.departAt)}</b></div><div><b className="big">{itin.origin} → {itin.dest}</b><small>Departs {clock(itin.departAt, false)} · {CABINS[cabin]} · Seat {mine?.code || 'at check-in'}</small></div></div>
        <OrderView n={res.orderNumber} /><p>Total paid {usd(res.total)}. A confirmation will be sent to {p.email}.</p><button className="btn" onClick={reset}>Book another flight</button></div>}
    </main>
    {itin && step !== 'done' && <Trip itin={itin} cabin={cabin} fare={fare} p={p} mine={mine} extrasTotal={extrasTotal} total={total} pnr={pnr} sky={sky} />}
    </div>
    <EventFeed events={events} />
    <footer><b>Aerolyn</b> · a proof of concept by TSI Private Limited<br />© 2026 TSI Private Limited. All rights reserved. Demo only: no real payments or tickets are issued.</footer>
  </>)
}

function Row({ i, h24, sel, setSel }) {
  const arrDay = dt(i.arriveAt).toLocaleDateString('en-US', { weekday: 'short', day: '2-digit' })
  return (<article className="flightcard"><div>
    <div className="fc-times"><div><b>{clock(i.departAt, h24)}</b><small>{city(i.origin)}</small></div>
      <div className="route"><span>{dur(i.durationMin)}</span><i /><span>{i.stops ? `${i.stops} stop` : 'Non-stop'}</span></div>
      <div><b>{clock(i.arriveAt, h24)}</b><small>{city(i.dest)} · {arrDay}</small></div></div>
    <div className="chips">{i.segments.map(s => <span className="chip" key={s.seq}>{s.flightNo} · {s.aircraft} · {s.airlineCode === 'AL' ? 'Aerolyn' : s.operatedBy}</span>)}
      {i.stops > 0 && <span className="chip soft">Layover in {city(i.segments[0].dest)} · {dur(i.segments[0].transitMin)}</span>}</div></div>
    <div className="tiers">{['ECONOMY', 'PREMIUM', 'BUSINESS'].map(c => { const f = i.fares[c], on = sel?.itin.id === i.id && sel.cabin === c
      return f?.available ? <button key={c} aria-pressed={on} className={`tier ${on ? 'on' : ''}`} onClick={() => setSel({ itin: i, cabin: c })}><small>{CABINS[c]}</small><b>{usd0(f.price)}</b>{f.deal && <em>Deal</em>}</button>
        : <div key={c} className="tier off"><small>{CABINS[c]}</small><span>Sold out</span></div> })}</div></article>)
}
function Logo() {   // original Aerolyn mark
  return (<svg width="30" height="30" viewBox="0 0 32 32" aria-hidden="true"><defs><linearGradient id="lg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#ff6a4d" /><stop offset="1" stopColor="#6c4cf1" /></linearGradient></defs><circle cx="16" cy="16" r="16" fill="url(#lg)" /><path d="M7 20c5-1 9-4 12-9 1 3 1 6-1 9-2 2-6 3-11 0z" fill="#fff" /></svg>)
}
function Trip({ itin, cabin, fare, p, mine, extrasTotal, total, pnr, sky }) {
  return (<aside className="trip" aria-label="Your trip"><h3>Your trip</h3>
    <div className="tripcode"><b>{itin.origin}</b><i /><b>{itin.dest}</b></div>
    <p>{city(itin.origin)} to {city(itin.dest)}<br /><small>{dayLong(itin.departAt)} · {clock(itin.departAt, false)}</small></p>
    <p><small>{itin.segments.map(s => s.flightNo).join(' + ')} · {CABINS[cabin]}<br />{p.first ? `${p.first} ${p.last}` : 'Passenger 1'}</small></p>
    {pnr && <p className="ids"><small>PNR</small> <b>{pnr.ref}</b>{sky.offer && <><br /><small>Offer ID</small> <b>{sky.offer.offerId}</b></>}</p>}
    <dl><dt>Fare</dt><dd>{usd(fare.price)}</dd><dt>Taxes & charges</dt><dd>{usd(TAX)}</dd><dt>Seat {mine?.code || ''}</dt><dd>{usd(mine?.price || 0)}</dd><dt>Extras</dt><dd>{usd(extrasTotal)}</dd><dt className="tot">Total</dt><dd className="tot">{usd(total)}</dd></dl></aside>)
}
function OrderView({ n }) {   // step 13: view order details
  const [d, setD] = useState(null)
  return (<div className="panel">{!d ? <button className="btn ghost" onClick={() => fetch(`/api/orders/${n}`).then(r => r.json()).then(setD)}>View order details</button> :
    <><h3>Order {d.order.orderNumber}</h3><small>PNR {d.pnr.ref} · Offer ID {d.offers.filter(o => o.status === 'CONFIRMED').map(o => o.offerId).join(', ') || '–'} · Order ID {d.order.orderNumber}</small><table className="ot"><tbody>{d.items.map(i => <tr key={i.id}><td>{i.type}</td><td>{i.description}</td><td>${i.amount.toFixed(2)}</td></tr>)}<tr><td colSpan={2}><b>Total</b></td><td><b>${d.order.total.toFixed(2)}</b></td></tr></tbody></table>
      <small>Payment {d.payments[0]?.reference} · {d.payments[0]?.status} · card ••••{d.payments[0]?.cardLast4}</small></>}</div>)
}

function Field({ p, set, tried, k, label, req, ...rest }) {   // module-level so the input keeps focus while typing
  return <label>{label}{req && '*'}<input value={p[k]} onChange={e => set(k, e.target.value)} className={tried && req && !p[k] ? 'bad' : ''} {...rest} /></label>
}
