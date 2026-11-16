"""Probe: does a sustained p-channel drive (I) replace stress (s) as the
'excitation' dial? Uses the project's exact fast subsystem:

    dv/dt = v - v^3/3 - w + I
    dw/dt = eps*(v + a - beta*w),   a = alpha - k*s   (M = 1)

Hopf point: a_c = beta*(vc - vc^3/3) - vc, vc = -sqrt(1-eps*beta).
"""

ALPHA, BETA, EPS = 0.7, 0.8, 0.08
SECTION, SPIKE = 1.2, 1.8


def hopf():
    vc = -(1.0 - EPS * BETA) ** 0.5
    return BETA * (vc - vc ** 3 / 3.0) - vc


def run(a, I, dt=0.02, t_end=2400.0):
    v, w = -1.2, -0.6
    t = 0.0
    prev_v, prev_t = v, 0.0
    cross, peak = [], -1e9
    while t < t_end:
        k1v = v - v ** 3 / 3.0 - w + I
        k1w = EPS * (v + a - BETA * w)
        v2, w2 = v + dt / 2 * k1v, w + dt / 2 * k1w
        k2v = v2 - v2 ** 3 / 3.0 - w2 + I
        k2w = EPS * (v2 + a - BETA * w2)
        v3, w3 = v + dt / 2 * k2v, w + dt / 2 * k2w
        k3v = v3 - v3 ** 3 / 3.0 - w3 + I
        k3w = EPS * (v3 + a - BETA * w3)
        v4, w4 = v + dt * k3v, w + dt * k3w
        k4v = v4 - v4 ** 3 / 3.0 - w4 + I
        k4w = EPS * (v4 + a - BETA * w4)
        prev_v, prev_t = v, t
        v = v + dt / 6 * (k1v + 2 * k2v + 2 * k3v + k4v)
        w = w + dt / 6 * (k1w + 2 * k2w + 2 * k3w + k4w)
        t += dt
        if prev_v <= SECTION < v:
            cross.append(prev_t + (SECTION - prev_v) / (v - prev_v) * dt)
        peak = max(peak, v)
    periods = [b - a2 for a2, b in zip(cross, cross[1:])]
    if len(periods) >= 3:
        periods = periods[2:]  # ignore transient
    per = sum(periods) / len(periods) if periods else None
    return per, peak, len(cross)


ac = hopf()
print(f"a_c (Hopf) = {ac:.4f};  burnout knee = {BETA*(1-1/3)-1:.4f}")
print(f"onset stress at I=0  (predicted s*) = {(ALPHA-ac):.4f}")
print(f"onset drive at s=0   (predicted I*) = {(ALPHA-ac)/BETA:.4f}\n")

print("== s sweep, I = 0 ==")
print(f"{'s':>6} {'a':>7} {'period':>8} {'peak v':>7} {'spikes':>7}")
for s in [0.00, 0.10, 0.20, 0.26, 0.27, 0.30, 0.40, 0.50, 0.60, 0.70]:
    per, peak, n = run(ALPHA - s, 0.0)
    print(f"{s:6.2f} {ALPHA-s:7.3f} {('--' if per is None else f'{per:8.2f}')} {peak:7.3f} {n:7d}")

print("\n== I sweep, s = 0 (a = 0.7) ==")
print(f"{'I':>6} {'a-I*beta':>9} {'period':>8} {'peak v':>7} {'spikes':>7} {'flag'}")
for I in [0.00, 0.20, 0.30, 0.33, 0.34, 0.40, 0.50, 0.60, 0.70, 0.90, 1.20]:
    per, peak, n = run(ALPHA, I)
    flag = "genuine" if peak >= SPIKE else ("osc" if per else "rest")
    print(f"{I:6.2f} {ALPHA-I*BETA:9.3f} {('--' if per is None else f'{per:8.2f}')} {peak:7.3f} {n:7d} {flag}")

print("\n== 2-D grid: period (top) / peak v (bottom) ==")
Is = [0.0, 0.35, 0.45, 0.55, 0.7]
ss = [0.0, 0.2, 0.4, 0.6]
print("        " + "".join(f"I={i:<8.2f}" for i in Is))
for s in ss:
    row_p, row_k = "", ""
    for I in Is:
        per, peak, n = run(ALPHA - s, I)
        row_p += f"{('--' if per is None else f'{per:.1f}'):<10}"
        row_k += f"{peak:<10.2f}"
    print(f"s={s:<4.1f} {row_p}")
    print(f"       {row_k}")
