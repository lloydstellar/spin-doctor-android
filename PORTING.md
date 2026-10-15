# Porting Spin Doctor

This is for anyone who wants to build Spin Doctor for another platform, an
iPhone version first of all. It describes what the app does to make a video,
with the numbers, so you do not have to dig them out of the Kotlin code. The
code is the reference when this file and the code disagree, and the files are
named in every section.

There are two reference implementations:

- this Android app (Kotlin), and
- the website, whose renderer is plain JavaScript on a 2D canvas: [lloydstellar.nl/vinyl/js/app.js](https://lloydstellar.nl/vinyl/js/app.js), function `renderVinylFrame()` and everything it calls. The Android renderer is a line by line port of it. If you know the canvas API, the JavaScript can be the easier one to read.

Where the app deliberately differs from the website (spin up, output sizes,
limits) the app is the one to follow. See "Differences from the website" in
the [README](README.md).

## The pipeline

For every track of a release:

1. Decode the chosen segment (start to end, 5 to 60 seconds) to 16-bit PCM, at the file's own sample rate and channel count.
2. Apply the spin up to the PCM if it is on (`audio/ClipAudio.kt`). This is the sound that goes into the video.
3. Build the beat envelope from that PCM if beat pulse is on (`audio/BeatEnvelope.kt`).
4. Bake the disc once for this size and style (`render/DiscArt.kt`) and the blurred background once (`render/Backdrop.kt`).
5. Render `ceil(duration * 30)` frames. Frame `i` is at `t = i / 30` seconds.
6. Encode H.264 (High profile where available, VBR, 1 second keyframe interval, BT.709 limited range) and AAC-LC at 192 kbit/s into an MP4. Video bitrate is 6, 7 or 8 Mbit/s for square, 4:5 and 9:16. The first audio sample must line up with the first frame; on Android that means dropping the encoder's 2048 samples of priming (`export/Mp4Encoder.kt`). Check how your encoder handles priming; a few dozen milliseconds of offset are audible against the kick.

On iOS the natural tools are AVAssetReader for decoding, Core Graphics (or
Metal) for drawing, and AVAssetWriter with a pixel buffer adaptor for
encoding. Use what you like, as long as the frames come out the same.

## Output sizes

| Format | Size |
|---|---|
| Square, 1:1 | 1080 x 1080 |
| Vertical, 9:16 | 1080 x 1920 |
| Portrait, 4:5 | 1080 x 1350 |

Every length in a frame is a fraction of the frame's width, height or the
disc radius, so a renderer that gets one size right gets them all right.

## One frame

`render/VinylRenderer.kt`, `render()`. Coordinates have y pointing down and
angles in radians, positive is clockwise on screen. If your canvas has y up
(Core Graphics without a flip), flip it first or every angle below turns the
other way.

Order of drawing:

1. **Background.** A solid colour, or the background image (see Backdrop below).
2. **Measure the text block** and work out how far the record has to move up or down to make room (`render/Overlay.kt`, `measureOverlay()` and `discOffsetForOverlay()`).
3. **The disc** at the centre of the frame plus that offset.
4. **The text block.**

### Disc size and motion

```
radius   = min(width, height) * 0.7 * (zoom / 70) * (1 + pulse) / 2
rotation = played(t) * rpm / 60 * 2π
pulse    = envelope(t) * amount        amount: 0, 0.012, 0.028 or 0.05
```

`zoom` runs from 70 to 150 (default 70), `rpm` from 15 to 80 (default 33).
`played(t)` is `t` without spin up; with spin up see below.

While the record turns, its centre wobbles on a small circle as a real
record does around the spindle: the disc and label are shifted by
`(cos(rotation), sin(rotation)) * radius * 0.005` before they are drawn.
The centre hole is drawn without that shift.

Drawing the disc, from the centre `(cx, cy)`:

1. A soft shadow: a radial gradient from radius `0.92 r` to `1.12 r`, centred slightly away from the light (`drawShadow`).
2. Apply the wobble.
3. The baked disc bitmap, rotated by `rotation`.
4. The baked sheen bitmap, **not** rotated: the light stays where it is while the grooves turn under it.
5. Rotate by `rotation`, clip to a circle of `0.33 r`, and draw the label: the circle the user chose from the artwork, scaled to fill it. Then the label edge: a darkening radial gradient at the rim, a faint linear light, a dark hairline and a faint light ring (`drawLabelDetail`).
6. If the centre hole is on, a hole of radius `0.024 r` with a soft rim (`drawSpindleHole`).

Without a cover the label is left out and the disc shows through.

### The baked disc

`render/DiscArt.kt`. Two bitmaps per size and style, baked once and reused for
every frame. The beat pulse only scales them, so a pulsing record never
rebakes.

Radii as a fraction of the disc radius: label 0.33, locked groove 0.352,
dead wax starts at 0.40, music runs from 0.40 to 0.97, rim from 0.98 to 1.
The music area has three bands, split at 0.775 and 0.575, with a smooth gap
of 0.0035 either side. The light comes from `atan2(-0.2, -0.6)`, left and a
little above.

The disc bitmap (everything that turns) is drawn at twice the size when the
radius is under 700 px. It has, in order: the body gradient (near black for
black vinyl, the chosen colour scaled down towards the rim otherwise), the
splatter or marble pattern, the satin bands, hairline grooves every
`max(0.8, 0.0017 r)` px whose alpha wanders with a few sines plus noise, ten
wider soft rings, the track separators, the lead-in, the dead wax with
lead-out and locked groove, a short scratched matrix number, wear (scratches
and dust) and the rim bevel.

The sheen bitmap (the light that stays put) has a side light, a sweep
gradient "fan" over the music area and the dead wax that peaks along the
light's axis, a glint on the glossy rings, a soft gloss spot on the dead wax,
and shading on the far side with a rim light. For coloured vinyl the light is
at 65% of its strength on black.

All the randomness comes from **mulberry32** with fixed seeds (`render/Random.kt`):
`0xA11D15C` for the grooves and wear, `0x5EED1234` for splatter and marble.
The order of the random calls matters: one call more or less and every
splatter lands somewhere else. Port it with 32-bit wrapping integer
arithmetic (`&+`, `&*` in Swift) and check the first few values against
`WebsiteParityTest`.

### Splatter and marble

`render/Patterns.kt`. Both are drawn into a square of twice the radius and
clipped to the disc.

Splatter: nine faint tints of the mixed colour, 14 large shards with halos,
110 medium pieces (half with a halo), and 900 crumbs streaked along the
radius. Each shard is a wobbly polygon of 9 to 14 points, fuller towards the
centre, stretched along its angle.

Marble is computed per pixel at `min(800, max(256, 2r))` pixels square:
3D value noise on a cylinder (so there is no seam where the angle wraps),
domain warped, mixed from the base colour through the mid colour to the
accent, with veins, darker pockets and smoke. The noise table is shuffled with
the same PRNG. The `ValueNoise3` gradient table is stored as 32-bit floats,
as in the website's `Float32Array`; keep that or the marble shifts slightly.

### Backdrop

`render/Backdrop.kt`. The image is scaled to cover the frame, centred, then
blurred with a Gaussian of sigma `blur * frameHeight / 1080` (blur runs 0 to
20, default 5). Pixels outside the image count as transparent, so the edges
fade, and the result is put on black. Three box blurs approximate the
Gaussian.

### Text and badge

`render/Overlay.kt`. Up to three lines, centred: artist (always upper case,
letter spaced), title, and a badge pill. The block sits below the record by
default, or above it.

| Style | Title font | Artist font | Title size | Artist size |
|---|---|---|---|---|
| Clean | system sans, bold | system sans, medium | 0.056 h | 0.027 h |
| Techno | Orbitron bold, upper case | Share Tech Mono | 0.05 h | 0.025 h |
| Mono | Share Tech Mono, upper case | Share Tech Mono | 0.052 h | 0.026 h |

`h` is the frame height. A line that is wider than `0.88 w` shrinks in one
step to fit, but not below `max(9, 0.018 h)`. Letter spacing on the artist
is 0.18, 0.22 or 0.2 of the font size. The badge text is `0.024 h` with
padding `0.62` of its size above and below and `1.05` of its size at the
sides, and its ink is near black or white depending on the badge colour's
luma. Artist and title have a dark outline of `0.14` of their size, and everything has a soft shadow.

Line heights are `1.15` of the size for the artist and `1.2` for the title,
with a gap of `0.016 h` between blocks. The text sits `0.03 h` from the
record and at least `0.045 h` from the frame edge. If it does not fit, the
record moves out of the way, as far as there is room.

Text positions are measured against the radius without pulse, so the text
stays still while the record pumps.

## Spin up

`model/SpinUp.kt` and `audio/ClipAudio.kt`. The record starts from standstill
and reaches full speed after `T` seconds (0.3 to 6, default 1):

```
x        = t / T
speed(t) = 1 - (1 - x)²                      for t < T, then 1
played(t)= T * (x - (1 - (1 - x)³) / 3)      for t < T, then 2T/3 + (t - T)
```

`played` is the integral of `speed`: how many seconds of music have been
played, and how far the record has turned in full-speed seconds. The
rotation uses `played(t)`.

The sound follows the same curve. For output sample `i` at `t = i / rate`,
read the segment at position `played(t) * rate` with cubic (Catmull-Rom)
interpolation and multiply by `speed(t)`. So it starts silent, rises from a
rumble to normal pitch, and the level follows the speed like a cartridge. After
the ramp, samples are copied unchanged. The read position is shifted by less
than one sample so that it lands on a whole sample exactly when the ramp
ends; without that the rest of the clip would be resampled for nothing.

The output has the same length as the segment. Because the ramp plays only
`2T/3` seconds of music in `T` seconds, the clip ends `T/3` seconds earlier in
the music than the segment does.

The preview plays exactly this processed sound and uses the audio clock to
place the record, so preview and video match.

## Beat pulse

`audio/BeatEnvelope.kt`, built from the processed sound (spin up included):

1. Split the PCM into `ceil(seconds * 30)` equal frames.
2. Run the first channel through a one-pole low pass at 150 Hz: `alpha = 1 - exp(-2π * 150 / rate)`.
3. Take the RMS of the filtered signal per frame.
4. Divide by the 95th percentile of those values and clamp to 1.
5. Gate: `max(0, (v - 0.35) / 0.65)`, then raise to the power 1.3.
6. Smooth with a fast attack and slow release: `s += (v - s) * (v > s ? 0.6 : 0.12)`.

Sample it at a frame's time with linear interpolation. Segments shorter than
0.1 s, or silent ones, have no envelope.

## Checking your port

- **Unit level.** `app/src/test/.../WebsiteParityTest.kt` has the mulberry32 sequence, title parsing, badge labels and a beat envelope compared against `tools/parity/beat-envelope.mjs`, which runs the website's own function. `SpinUpTest.kt` checks the curve.
- **Frames.** `tools/parity/scenes.json` describes a set of scenes (finishes, formats, text, badge, pulse). Render them with your port, render them with `tools/parity/render-web.sh` from the website, and compare. The Android app is within 0.5 to 1.6% RMSE of the website; most of what is left is fonts and anti-aliasing.
- **Video.** `tools/check_export.py` checks an exported video of `app/src/androidTest/assets/kick120.mp3` (a 120 BPM kick): every kick at the right time, no gaps in the sound, and the beat pulse peaking on the kick. Export a clip starting at 10 s and run `python3 tools/check_export.py video.mp4 --start 10`.

## Name and artwork

The code is Apache 2.0. The name Spin Doctor, the icon and the artwork are
not, see [NOTICE](NOTICE). If you want to release an iPhone version under the
Spin Doctor name, get in touch through
[lloydstellar.nl/contact.html](https://lloydstellar.nl/contact.html) first and
we can work it out.
