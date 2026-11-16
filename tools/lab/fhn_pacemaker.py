"""Can a train of alpha-kernel pulses (the bell) be a pacemaker on a REST cell?

Rest cell (a = 0.7, s = 0, M = 1) is non-oscillatory. Bell pulse = alpha kernel:
  z += area/tau  at firing;  dz/dt = -z/tau;  dp/dt = (z-p)/tau
  dv/dt = v - v^3/3 - w + p ;  dw/dt = eps*(v + a - beta*w)
Detect genuine spikes: v upcross 1.2 and peak >= 1.8 within 5 s.
"""

ALPHA, BETA, EPS = 0.7, 0.8, 0.08
AREA, TAU = 12.0, 5.0
SECTION, SPIKE, CONFIRM = 1.2, 1.8, 5.0


def run(cadence, a=0.7, dt=0.01, t_end=1200.0):
    v, w, z, p = -1.2, -0.6, 0.0, 0.0
    t = 0.0
    next_fire = 0.0
    crossings = []          # candidate upcross times
    spikes = []             # confirmed spike times
    cand = None
    peak = -1e9
    prev_v = v
    excursions = 0
    while t < t_end:
        # closed-form kernel decay over dt, then RK4 on the (v,w) pair
        decay = pow(2.718281828459045, -dt / TAU)
        p = (p + z * dt / TAU) * decay
        z *= decay
        k1v = v - v**3/3 - w + p;  k1w = EPS*(v + a - BETA*w)
        v2, w2 = v + dt/2*k1v, w + dt/2*k1w
        k2v = v2 - v2**3/3 - w2 + p; k2w = EPS*(v2 + a - BETA*w2)
        v3, w3 = v + dt/2*k2v, w + dt/2*k2w
        k3v = v3 - v3**3/3 - w3 + p; k3w = EPS*(v3 + a - BETA*w3)
        v4, w4 = v + dt*k3v, w + dt*k3w
        k4v = v4 - v4**3/3 - w4 + p; k4w = EPS*(v4 + a - BETA*w4)
        prev_v = v
        v = v + dt/6*(k1v + 2*k2v + 2*k3v + k4v)
        w = w + dt/6*(k1w + 2*k2w + 2*k3w + k4w)
        t += dt
        if prev_v <= SECTION < v:
            cand = t
            peak = v
            crossings.append(t)
        elif cand is not None:
            peak = max(peak, v)
            if peak >= SPIKE and t - cand <= CONFIRM:
                spikes.append(cand)
                cand = None
            elif t - cand > CONFIRM:
                cand = None
        # fire the bell on cadence
        if t >= next_fire:
            z += AREA / TAU
            next_fire += cadence
    periods = [b - a2 for a2, b in zip(spikes, spikes[1:])]
    mean = sum(periods)/len(periods) if periods else None
    return len(spikes), mean, len([c for c in crossings])


print(f"{'cadence':>8} {'#spikes':>8} {'mean period':>12}  verdict")
for cadence in [8.0, 12.0, 20.0, 30.0, 40.0, 50.0, 60.0, 90.0]:
    n, per, ncross = run(cadence)
    v = "1 spike per ring (paced)" if per and abs(per - cadence) < 3 else (
        "self-sustained" if n > 0 and per and per < cadence * 0.75 else
        ("dropped beats" if per and per > cadence * 1.3 else "no beat"))
    print(f"{cadence:8.1f} {n:8d} {('--' if per is None else f'{per:12.2f}')}  {v}")
