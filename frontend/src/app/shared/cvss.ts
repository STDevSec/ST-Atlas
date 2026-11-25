/**
 * CVSS 3.1 base score, mirroring the backend (which is authoritative). Used only for an
 * instant preview while typing; the stored score always comes from the server.
 */
export function cvss31(vector: string): number | null {
  const m = /^CVSS:3\.1\/AV:([NALP])\/AC:([LH])\/PR:([NLH])\/UI:([NR])\/S:([UC])\/C:([NLH])\/I:([NLH])\/A:([NLH])$/.exec(vector.trim());
  if (!m) return null;
  const [, avS, acS, prS, uiS, sS, cS, iS, aS] = m;
  const changed = sS === 'C';
  const av = { N: 0.85, A: 0.62, L: 0.55, P: 0.2 }[avS]!;
  const ac = acS === 'L' ? 0.77 : 0.44;
  const pr = prS === 'N' ? 0.85 : prS === 'L' ? (changed ? 0.68 : 0.62) : (changed ? 0.5 : 0.27);
  const ui = uiS === 'N' ? 0.85 : 0.62;
  const cia = (v: string) => (v === 'H' ? 0.56 : v === 'L' ? 0.22 : 0);
  const iss = 1 - (1 - cia(cS)) * (1 - cia(iS)) * (1 - cia(aS));
  const impact = changed ? 7.52 * (iss - 0.029) - 3.25 * Math.pow(iss - 0.02, 15) : 6.42 * iss;
  if (impact <= 0) return 0;
  const exploit = 8.22 * av * ac * pr * ui;
  const raw = changed ? Math.min(1.08 * (impact + exploit), 10) : Math.min(impact + exploit, 10);
  const int = Math.round(raw * 100000);
  return int % 10000 === 0 ? int / 100000 : (Math.floor(int / 10000) + 1) / 10;
}
