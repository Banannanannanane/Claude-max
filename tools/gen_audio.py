#!/usr/bin/env python3
"""Génère la musique et les ambiances du jeu (Python pur, sans dépendance).

Usage : python3 tools/gen_audio.py  (écrit dans game/audio/)
Fichiers : music.wav (boucle), ambiance.wav (vent + oiseaux, boucle), rain.wav (pluie, boucle),
step.wav (pas dans l'herbe), golden.wav (brin doré), recycle.wav (recyclage).
"""
import math
import os
import random
import struct
import wave

OUT = os.path.join(os.path.dirname(__file__), "..", "game", "audio")
random.seed(42)


def write(name, samples, rate):
    peak = max(1e-6, max(abs(s) for s in samples))
    k = 0.89 / peak
    with wave.open(os.path.join(OUT, name), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s * k)) * 32767)) for s in samples))
    print(name, len(samples) / rate, "s")


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def lowpass(x, a):
    y = 0.0
    out = []
    for v in x:
        y += a * (v - y)
        out.append(y)
    return out


def seamless(x, rate, fade=1.0):
    """Fondu de la fin vers le début pour une boucle sans clic."""
    n = int(fade * rate)
    head = x[:n]
    body = x[n:]
    for i in range(n):
        t = i / n
        body[len(body) - n + i] = body[len(body) - n + i] * (1 - t) + head[i] * t
    return body


# ---------------------------------------------------------------- musique
def music():
    rate = 22050
    bpm = 84
    beat = 60 / bpm
    bar = beat * 4
    # Dmaj9 - Bm9 - Gmaj9 - A6sus : 2 mesures chacun, 2 tours (le second avec mélodie variée)
    chords = [
        [50, 57, 62, 66, 69, 73],
        [47, 54, 59, 62, 66, 69],
        [43, 50, 55, 59, 62, 66],
        [45, 52, 57, 61, 64, 66],
    ]
    seg = bar * 2
    total = seg * len(chords) * 2
    n = int(total * rate)
    out = [0.0] * n
    # nappes : chaque accord avec une enveloppe douce, circulaire pour boucler
    for ci in range(len(chords) * 2):
        ch = chords[ci % len(chords)]
        start = ci * seg
        att, rel = 0.9, 1.6
        dur = seg + rel
        for note in ch[1:]:
            f = midi(note)
            for det in (-0.12, 0.12):
                ff = f * 2 ** (det / 12)
                ph = random.random() * math.tau
                for i in range(int(dur * rate)):
                    t = i / rate
                    env = min(1.0, t / att) * (1.0 if t < seg else max(0.0, 1 - (t - seg) / rel))
                    v = math.sin(math.tau * ff * t + ph) * 0.5 + math.sin(math.tau * ff * 2 * t + ph) * 0.08
                    out[(int(start * rate) + i) % n] += v * env * 0.05
        # basse
        f = midi(ch[0] - 12)
        for b in range(2):
            for k in (0, 2.5):
                s0 = start + b * bar + k * beat
                for i in range(int(beat * 1.6 * rate)):
                    t = i / rate
                    env = min(1.0, t / 0.02) * math.exp(-t * 1.6)
                    out[(int(s0 * rate) + i) % n] += math.sin(math.tau * f * t) * env * 0.22
    # mélodie : pluck pentatonique (ré majeur), notes sur les croches, clairsemée
    scale = [62, 64, 66, 69, 71, 74, 76, 78, 81]
    rnd = random.Random(7)
    idx = 4
    for step in range(int(total / (beat / 2))):
        if rnd.random() < 0.38:
            idx = max(0, min(len(scale) - 1, idx + rnd.choice([-2, -1, -1, 1, 1, 2])))
            f = midi(scale[idx])
            s0 = step * beat / 2
            for i in range(int(1.4 * rate)):
                t = i / rate
                env = min(1.0, t / 0.004) * math.exp(-t * 3.2)
                v = math.sin(math.tau * f * t) + 0.3 * math.sin(math.tau * f * 3 * t) * math.exp(-t * 8)
                out[(int(s0 * rate) + i) % n] += v * env * 0.07
    # léger écho
    d = int(beat * 0.75 * rate)
    for i in range(n):
        out[i] += out[(i - d) % n] * 0.25
    write("music.wav", out, rate)


# ---------------------------------------------------------------- ambiances
def ambiance():
    rate = 22050
    dur = 14.0
    n = int((dur + 1.0) * rate)
    brown = []
    y = 0.0
    for i in range(n):
        y = 0.995 * y + random.uniform(-1, 1) * 0.05
        brown.append(y)
    wind = lowpass(brown, 0.08)
    out = []
    for i, v in enumerate(wind):
        t = i / rate
        g = 0.55 + 0.45 * math.sin(math.tau * t / 7.0) * math.sin(math.tau * t / 3.1 + 1)
        out.append(v * g)
    # oiseaux
    for c in range(9):
        s0 = random.uniform(0.3, dur - 1.0)
        f0 = random.uniform(2600, 4200)
        for k in range(random.randint(2, 4)):
            st = s0 + k * random.uniform(0.11, 0.18)
            ln = random.uniform(0.06, 0.12)
            for i in range(int(ln * rate)):
                t = i / rate
                f = f0 * (1 + 0.25 * math.sin(math.pi * t / ln))
                env = math.sin(math.pi * t / ln) ** 2
                j = int(st * rate) + i
                if j < n:
                    out[j] += math.sin(math.tau * f * t) * env * 0.12
    write("ambiance.wav", seamless(out, rate), rate)


def rain():
    rate = 22050
    dur = 6.0
    n = int((dur + 1.0) * rate)
    hiss = lowpass([random.uniform(-1, 1) for _ in range(n)], 0.35)
    out = [v * 0.5 for v in hiss]
    for d in range(900):
        s0 = random.randint(0, n - 400)
        a = random.uniform(0.05, 0.25)
        for i in range(300):
            out[s0 + i] += random.uniform(-1, 1) * a * math.exp(-i / 40)
    write("rain.wav", seamless(lowpass(out, 0.5), rate), rate)


def step():
    rate = 44100
    n = int(0.12 * rate)
    x = [random.uniform(-1, 1) * math.exp(-i / (0.025 * rate)) for i in range(n)]
    x = lowpass(x, 0.18)
    thump = [math.sin(math.tau * 90 * i / rate) * math.exp(-i / (0.02 * rate)) * 0.6 for i in range(n)]
    write("step.wav", [a + b for a, b in zip(x, thump)], rate)


def golden():
    rate = 44100
    notes = [74, 78, 81, 86, 90, 93]
    n = int(1.8 * rate)
    out = [0.0] * n
    for k, m in enumerate(notes):
        f = midi(m)
        s0 = int(k * 0.07 * rate)
        for i in range(n - s0):
            t = i / rate
            env = math.exp(-t * 2.2) * min(1, t / 0.003)
            out[s0 + i] += (math.sin(math.tau * f * t) + 0.4 * math.sin(math.tau * f * 2.01 * t)) * env * 0.3
    # scintillement
    for i in range(n):
        t = i / rate
        out[i] += math.sin(math.tau * 5200 * t) * random.random() * math.exp(-t * 3) * 0.05
    write("golden.wav", out, rate)


def recycle():
    rate = 44100
    n = int(1.6 * rate)
    out = []
    ph = 0.0
    noise = lowpass([random.uniform(-1, 1) for _ in range(n)], 0.1)
    for i in range(n):
        t = i / rate
        f = 120 + 900 * (t / 1.6) ** 2
        ph += math.tau * f / rate
        env = math.sin(math.pi * min(1, t / 1.6)) ** 1.5
        out.append((math.sin(ph) * 0.4 + noise[i] * 1.5) * env)
    write("recycle.wav", out, rate)


if __name__ == "__main__":
    music()
    ambiance()
    rain()
    step()
    golden()
    recycle()
