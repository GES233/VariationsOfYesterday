ALPHA, BETA, EPS = 0.7, 0.8, 0.08
AREA, TAU = 12.0, 5.0
S, SPIKE = 1.2, 1.8

def run(cadence, a=0.7, dt=0.01, t_end=800.0):
    v, w, z, p = -1.2, -0.6, 0.0, 0.0
    t = 0.0; next_fire = 0.0
    prev_v = v; peak = -1e9; above = 0.0; below_shift = 0.0
    vmin, vmax = 9, -9
    while t < t_end:
        decay = pow(2.718281828459045, -dt/TAU)
        p = (p + z*dt/TAU)*decay; z *= decay
        k1v = v - v**3/3 - w + p; k1w = EPS*(v + a - BETA*w)
        v2, w2 = v + dt/2*k1v, w + dt/2*k1w
        k2v = v2 - v2**3/3 - w2 + p; k2w = EPS*(v2 + a - BETA*w2)
        v3, w3 = v + dt/2*k2v, w + dt/2*k2w
        k3v = v3 - v3**3/3 - w3 + p; k3w = EPS*(v3 + a - BETA*w3)
        v4, w4 = v + dt*k3v, w + dt*k3w
        k4v = v4 - v4**3/3 - w4 + p; k4w = EPS*(v4 + a - BETA*w4)
        prev_v = v
        v = v + dt/6*(k1v+2*k2v+2*k3v+k4v); w = w + dt/6*(k1w+2*k2w+2*k3w+k4w)
        t += dt
        peak = max(peak, v); vmin=min(vmin,v); vmax=max(vmax,v)
        if v >= SPIKE: above += dt
        if t >= next_fire: z += AREA/TAU; next_fire += cadence
    return peak, above/t_end, vmin, vmax, v

for c in [8, 12, 20, 30, 40, 60, 90]:
    peak, duty, vmin, vmax, vend = run(c)
    print(f"cadence={c:3.0f}s  peak={peak:6.2f}  duty(v>=1.8)={duty*100:5.1f}%  v range=[{vmin:6.2f},{vmax:6.2f}]  v_end={vend:6.2f}")
