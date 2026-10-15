"""Checks an exported video of the kick test track (app/src/androidTest/assets/kick120.mp3,
a kick every 0.5 s from 0.25 s) for audio timing and for the beat pulse in the picture.

  python3 tools/check_export.py video.mp4 --start 10

--start is where the segment begins in the track. Needs ffmpeg.
Prints every kick's offset from where it belongs, the largest jump between
kicks (a gap or overlap in the audio would show there), and how many frames
after each kick the record is at its biggest.
"""
import argparse
import array
import subprocess
import tempfile

RATE = 44100
FPS = 30


def pcm(path):
    with tempfile.NamedTemporaryFile(suffix='.raw') as f:
        subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', path, '-map', '0:a', '-ac', '1',
                        '-ar', str(RATE), '-f', 'f32le', f.name], check=True)
        a = array.array('f')
        a.frombytes(open(f.name, 'rb').read())
        return a


def onsets(x, threshold=0.3, hold=0.3):
    out, i = [], 0
    while i < len(x):
        if abs(x[i]) > threshold:
            out.append(i / RATE)
            i += int(hold * RATE)
        else:
            i += 1
    return out


def disc_widths(path, w=108, h=192):
    """Width of the record along the middle row of every frame, in pixels of a small copy."""
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', path, '-vf', f'scale={w}:{h}', '-pix_fmt', 'gray',
                          '-f', 'rawvideo', '-'], check=True, capture_output=True).stdout
    widths = []
    for k in range(len(raw) // (w * h)):
        row = raw[k * w * h + (h // 2) * w: k * w * h + (h // 2 + 1) * w]
        lit = [i for i, v in enumerate(row) if v > 6]
        widths.append((lit[-1] - lit[0]) if lit else 0)
    return widths


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('video')
    ap.add_argument('--start', type=float, default=10.0)
    args = ap.parse_args()

    found = onsets(pcm(args.video))
    expected = [t for t in ((0.25 + 0.5 * n) - (args.start % 0.5) for n in range(200)) if 0 <= t < found[-1] + 0.25]
    offsets = []
    for t in found:
        nearest = min(expected, key=lambda e: abs(e - t))
        offsets.append((t - nearest) * 1000)
    print(f'kicks found: {len(found)}')
    print(f'offset from where they belong: first {offsets[0]:+.1f} ms, min {min(offsets):+.1f}, max {max(offsets):+.1f} ms')
    jumps = [b - a for a, b in zip(offsets, offsets[1:])]
    print(f'largest jump between kicks: {max(abs(j) for j in jumps):.2f} ms')

    # the record's width around each kick, averaged over all kicks, from 4 frames before to 8 after
    widths = disc_widths(args.video)
    profile = [0.0] * 13
    used = 0
    for t in found:
        f0 = int(round(t * FPS))
        if f0 - 4 >= 0 and f0 + 9 <= len(widths):
            for k in range(13):
                profile[k] += widths[f0 - 4 + k]
            used += 1
    if used:
        profile = [p / used for p in profile]
        peak = profile.index(max(profile)) - 4
        print('record width around a kick (frames -4..+8):', ' '.join(f'{p:.1f}' for p in profile))
        print(f'record is biggest {peak:+d} frames from the kick in the sound')


if __name__ == '__main__':
    main()
