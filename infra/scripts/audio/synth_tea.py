"""Modal synthesis of a metal teaspoon striking a thin Turkish tea glass (ince belli).
Placeholder pending owner approval by ear. Deterministic (fixed seed)."""
import sys, wave, numpy as np

SR = 44100
rng = np.random.default_rng(1923)

# Glass partials (Hz), relative amplitude, T60 decay (s). Slight inharmonicity built in.
# Upper modes decay faster; tea in the glass damps everything a little.
MODES = [
    (2210.0, 1.00, 0.55),
    (3405.0, 0.55, 0.40),
    (5120.0, 0.35, 0.26),
    (7630.0, 0.20, 0.16),
    (9870.0, 0.08, 0.09),
]

def hit(dur, pitch=1.0, gain=1.0, brightness=1.0):
    n = int(dur * SR); t = np.arange(n) / SR
    y = np.zeros(n)
    for f, a, t60 in MODES:
        f *= pitch
        a *= gain * (brightness ** (f / 4000.0))
        tau = t60 / 6.91
        # each glass mode is a near-degenerate doublet (wall asymmetry) -> gentle beating
        split = rng.uniform(1.5, 4.0)
        ph1, ph2 = rng.uniform(0, 2 * np.pi, 2)
        y += a * np.exp(-t / tau) * (0.6 * np.sin(2*np.pi*(f - split/2)*t + ph1)
                                    + 0.4 * np.sin(2*np.pi*(f + split/2)*t + ph2))
    # spoon contact: ~2.5 ms high-passed noise burst
    m = int(0.0025 * SR)
    burst = rng.standard_normal(m)
    burst = np.diff(burst, prepend=0.0)            # crude high-pass
    burst *= np.exp(-np.arange(m) / (m / 4)) * 0.35 * gain
    y[:m] += burst
    # 1 ms attack ramp to avoid a digital click
    a = int(0.001 * SR); y[:a] *= np.linspace(0, 1, a)
    return y

def place(total, events):
    out = np.zeros(int(total * SR))
    for start, sig in events:
        i = int(start * SR); j = min(len(out), i + len(sig))
        out[i:j] += sig[:j - i]
    return out

def swirl(total, level):
    """Very faint liquid swish under the stir: band-limited noise, slow AM."""
    n = int(total * SR); t = np.arange(n) / SR
    x = rng.standard_normal(n)
    # one-pole low-pass then subtract a heavier low-pass -> band ~300-2500 Hz
    def lp(sig, fc):
        a = np.exp(-2*np.pi*fc/SR); o = np.empty_like(sig); acc = 0.0
        for k in range(len(sig)):
            acc = (1-a)*sig[k] + a*acc; o[k] = acc
        return o
    b = lp(x, 2500) - lp(x, 300)
    env = 0.5 * (1 - np.cos(2*np.pi*t/total)) * (0.6 + 0.4*np.sin(2*np.pi*3.6*t))
    return level * b * env

def write(path, y):
    y = y / (np.max(np.abs(y)) + 1e-9) * 0.89
    pcm = (y * 32767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())

if __name__ == '__main__':
    out_dir = sys.argv[1]
    # Notification: three stir clinks, accelerating like a real stir, ±4% pitch variance
    p = 1.0 + rng.uniform(-0.04, 0.04, 3)
    stir = place(1.25, [
        (0.000, hit(1.0, p[0], 1.00, 1.00)),
        (0.235, hit(1.0, p[1], 0.62, 0.90)),
        (0.430, hit(0.82, p[2], 0.85, 0.95)),
    ]) + swirl(1.25, 0.035)
    write(f'{out_dir}/synth_tea_stir_raw.wav', stir)
    # UI send clink: one light tap, fast decay
    c = hit(0.14, 1.03, 1.0, 1.05)
    c *= np.exp(-np.arange(len(c)) / (0.045 * SR))
    write(f'{out_dir}/synth_ui_clink_raw.wav', c)
