ALPHA, BETA, EPS = 0.7, 0.8, 0.08
def run(a, I, dt=0.01, t_end=1200.0):
    v, w = -1.2, -0.6
    t = 0.0; above = 0.0; n = 0; peak = -1e9; prev_v = v; starts=0
    while t < t_end:
        k1v = v - v**3/3 - w + I; k1w = EPS*(v + a - BETA*w)
        v2, w2 = v + dt/2*k1v, w + dt/2*k1w
        k2v = v2 - v2**3/3 - w2 + I; k2w = EPS*(v2 + a - BETA*w2)
        v3, w3 = v + dt/2*k2v, w + dt/2*k2w
        k3v = v3 - v3**3/3 - w3 + I; k3w = EPS*(v3 + a - BETA*w3)
        v4, w4 = v + dt*k3v, w + dt*k3w
        k4v = v4 - v4**3/3 - w4 + I; k4w = EPS*(v4 + a - BETA*w4)
        prev_v = v
        v = v + dt/6*(k1v+2*k2v+2*k3v+k4v); w = w + dt/6*(k1w+2*k2w+2*k3w+k4w)
        t += dt
        if v >= 1.8:
            above += dt
            if prev_v < 1.8: starts += 1
        peak = max(peak, v)
    return above/t_end, peak, starts
print("case                duty(v>=1.8)  peak   spike_count")
for name,(a,I) in {"s=0.40,I=0":(0.30,0.0), "s=0.50,I=0":(0.20,0.0), "s=0,I=0.50":(0.7,0.5), "s=0,I=0.70":(0.7,0.7)}.items():
    duty, peak, cnt = run(a, I)
    print(f"{name:<18} {duty*100:6.1f}%      {peak:5.2f}   {cnt}")
