"""Slow-system probe for the U-shaped remnant arc (design note 11 ruling 12).

Unlike the fast-subsystem probes in this folder, this one integrates the FULL
slow system exactly as MandateState does, plus the calming stock c0 with its
45-minute leak (ruling 11, no source blocks in the worldgen field):

    dv/dt = v - v^3/3 - w
    dw/dt = eps*(v + a - beta*w),   a = alpha - depth*(1-M) - k*s
    ds/dt = gamma*(u - recovery(s) - rho*c0 + eta*q)
    dq/dt = lambda*(max(|v| - 1.8, 0) - q)
    dc0/dt = -c0/tau

Fixed parameters mirror the game's frozen values (StressParam / MandateParam /
CalmingParam). The field is a three-tier disturbance cone:
  core     r <  2   u = u_core
  remnant  2-4      u = u_remnant
  ring     4-5      u = ambient, c0 present (no source -> leaks)
  outer    >= 5     u = ambient, c0 = 0

Usage: python tools/lab/fhn_slow.py
Prints, per (u_remnant, u_core) candidate and per radial sample, the arc of
s(t): when the beat is lost, the minimum s, whether it recovers, and when the
cell reaches the acid line (s >= 0.6).
"""

import math
import sys

ALPHA, BETA, EPS = 0.7, 0.8, 0.08
GAMMA, DELTA0, SHALF, RHO, ETA, LAMBDA = 0.01, 0.3, 0.15, 1.0, 1.0, 0.2
VSPIKE = 1.8
DEPTH, K = 1.2, 1.0
TAU_C0 = 2700.0
ACID = 0.6
ONSET = 0.265  # s_onset = alpha - a_c

DT = 0.05
T_END = 4 * 3600.0  # 4 simulated hours


def recovery(s):
    return DELTA0 / (1.0 + (s / SHALF) ** 2) * s


def deriv(v, w, s, q, u, c0):
    a = ALPHA - DEPTH * 0.0 - K * s  # M = 1
    dv = v - v ** 3 / 3.0 - w
    dw = EPS * (v + a - BETA * w)
    ds = GAMMA * (u - recovery(s) - RHO * c0 + ETA * q)
    dq = LAMBDA * (max(abs(v) - VSPIKE, 0.0) - q)
    return dv, dw, ds, dq


def step(v, w, s, q, c0, u):
    k1v, k1w, k1s, k1q = deriv(v, w, s, q, u, c0)
    k2v, k2w, k2s, k2q = deriv(v + DT / 2 * k1v, w + DT / 2 * k1w,
                             s + DT / 2 * k1s, q + DT / 2 * k1q, u, c0)
    k3v, k3w, k3s, k3q = deriv(v + DT / 2 * k2v, w + DT / 2 * k2w,
                             s + DT / 2 * k2s, q + DT / 2 * k2q, u, c0)
    k4v, k4w, k4s, k4q = deriv(v + DT * k3v, w + DT * k3w,
                             s + DT * k3s, q + DT * k3q, u, c0)
    v2 = v + DT / 6 * (k1v + 2 * k2v + 2 * k3v + k4v)
    w2 = w + DT / 6 * (k1w + 2 * k2w + 2 * k3w + k4w)
    s2 = s + DT / 6 * (k1s + 2 * k2s + 2 * k3s + k4s)
    q2 = q + DT / 6 * (k1q + 2 * k2q + 2 * k3q + k4q)
    s2 = min(max(s2, 0.0), 1.0)
    c0n = c0 * math.exp(-DT / TAU_C0) if c0 > 0 else 0.0
    return v2, w2, s2, q2, c0n


def run(s0, u, c0_0, label):
    v, w, s, q, c0 = -1.2, -0.6, s0, 0.0, c0_0
    t = 0.0
    last_spike = None
    periods = []
    first_fall = None       # time s drops below onset
    bottom = s0
    recovered = None        # time s climbs back above onset after the fall
    acid_at = None          # time s reaches the acid line
    beats = 0               # spike RISING edges only
    was_high = False
    last_beat = 0.0
    while t < T_END:
        was_above = s >= ONSET
        v, w, s, q, c0 = step(v, w, s, q, c0, u)
        t += DT
        bottom = min(bottom, s)
        if was_above and s < ONSET and first_fall is None:
            first_fall = t
        if first_fall is not None and recovered is None and s >= ONSET and bottom < ONSET:
            recovered = t
        if acid_at is None and s >= ACID:
            acid_at = t
        high = v >= VSPIKE
        if high and not was_high:
            beats += 1
            last_beat = t
            if last_spike is not None:
                periods.append(t - last_spike)
            last_spike = t
        was_high = high
    per = sum(periods[-5:]) / len(periods[-5:]) if periods else None
    print(f"{label:<22} u={u:5.3f} s0={s0:.3f} | fall={fmt(first_fall)} "
          f"bottom={bottom:.3f} up={fmt(recovered)} acid={fmt(acid_at)} "
          f"beats={beats:5d} lastbeat={fmt(last_beat)} "
          f"period={('--' if per is None else f'{per:6.1f}')}")


def fmt(t):
    return "  --  " if t is None else f"{t / 60.0:5.1f}m"


def rescue(u, s0=0.60, c0_hold=0.04, minutes=60.0):
    """Anchored-rescue check (ruling 13): an intact 0.04 calming stock
    (dose + trickle cap) held against the core source. Declining s means the
    burned core can be walked back down; still rising means the rescue chain
    is broken at this u_core.
    """
    v, w, s, q, c0 = -1.2, -0.6, s0, 0.0, c0_hold
    t, s0_t = 0.0, s0
    while t < minutes * 60.0:
        v, w, s, q, c0 = step(v, w, s, q, c0, u)
        c0 = c0_hold  # sources hold the stock at the cap
        t += DT
    print(f"rescue  u_core={u:5.3f} s0={s0_t:.2f} c0={c0_hold:.2f} -> "
          f"s({minutes:.0f}m)={s:.3f}  {'OK (falls)' if s < s0_t else 'FAILS (still rising)'}")


def main():
    peak, fall = 0.349, 0.0209
    print(f"field: peak={peak:.3f} falloff={fall:.4f} onset={ONSET} acid={ACID}")
    print(f"clock: dt={DT}s, horizon={T_END / 3600.0:.0f}h, tau_c0={TAU_C0:.0f}s\n")
    for u_core in [0.034, 0.036, 0.038, 0.040]:
        rescue(u_core)
    print()
    sample = [
        ("core    r=0", 0.0, peak, 0.03),
        ("remnant r=2", 2.0, peak - 2.0 * fall, 0.03),
        ("remnant r=3", 3.0, peak - 3.0 * fall, 0.03),
        ("ring    r=4.5", 4.5, peak - 4.5 * fall, 0.03),
        ("outer   r=6", 6.0, peak - 6.0 * fall, 0.0),
    ]
    for u_remnant in [0.031, 0.032, 0.033, 0.034, 0.035]:
        for u_core in [0.038, 0.040, 0.042]:
            print(f"== u_remnant={u_remnant:.3f}  u_core={u_core:.3f} ==")
            for label, r, s0, c0_0 in sample:
                u = u_core if r < 2 else (u_remnant if r < 4 else 0.002)
                run(s0, u, c0_0, label)
            print()


if __name__ == "__main__":
    sys.exit(main())
