# 07 — Target recognition

Slice 4. See [ADR-0001](../adr/0001-screencapturekit-and-min-macos-14.md).

## Screen capture

- **RG-1** `[done]` Use `SCScreenshotManager` (macOS 14+). `Package.swift` raises `platforms` to
  `.macOS(.v14)`.
- **RG-2** `[done]` A capture comes back in **pixels**, while `CGEvent` works in **points**. Every
  coordinate found has to be divided by the scale factor of **the display it is on** before any
  event is emitted. A machine with a retina display and an external display at once has two
  different factors inside the same coordinate space. Where that factor comes from is `RG-24`; what
  the two factors do to a **Template** cropped on one of them is `RG-25`.
- **RG-24** `[done]` The scale factor of a display is **never guessed**. It is read from `NSScreen`;
  failing that from `CGDisplayCopyDisplayMode` (`pixelWidth / width`), which is independent of
  `NSScreen`; and if both fail **that display is skipped**.

  The old code fell back to assuming 2x. On a 1x display that halves every coordinate and the click
  lands between the display's origin and the target — silently wrong, which is worse than not
  clicking. Not finding a target is a state the user can see; clicking the wrong place is not.
  See [ADR-0008](../adr/0008-match-templates-at-two-scales.md).
- **RG-3** `[done]` The capture must not be cached between `EX-8` retries — the whole point of
  retrying is to see an interface that **has changed**.

- **RG-20** `[done]` The capture **excludes Clickify's own windows**. The floating panel sits in
  the middle-top of the screen and can perfectly well cover the target being looked for.

## Cropping a Template

- **RG-4** `[done]` The user drags a frame on a full-screen overlay, extended from
  `ClickPointSelector`; the area inside the frame is captured straight into a **Template**. This
  is **entirely independent** of the **Locked application**: you can draw anywhere on any display.

  That is deliberate, not incidental. The target you need to aim at is usually **not on screen
  yet** while you are writing the Scenario — the dialog has not opened, the button only appears
  after the page loads. What the user does then is open an old screenshot in Preview and crop the
  **Template** out of that. Requiring the crop to be inside the **Locked application**'s window
  would make exactly that way of working impossible.
- **RG-5** `[done]` The overlay **must be hidden before capturing**, and we must wait about 120 ms
  for the window server to actually take it down — `orderOut` is only a request. The overlay
  paints `black.withAlphaComponent(0.10)` over the whole screen; capturing while it is still up
  produces a **Template** darkened by 10% that can never match again at run time.
- **RG-6** `[done]` The search region is also drawn by the user, and is **not required**
  (`DM-17`). How it is stored is chosen automatically in priority order:

  1. The **Scenario** has a **Locked application** and an **Anchor window** could be read while
     drawing → store it as an offset from that window's nearest corner, by the same rule as
     `DM-13`.
  2. Otherwise → store absolute screen coordinates.

  Option 1 is clearly better: move the window 40 pt and the search region follows, whereas an
  absolute region reports "not found" even though the **Template** is still on screen. But it
  **must not** become a requirement, because that would block the way of working in `RG-4`. When
  it has to fall back to option 2, the interface says plainly that the region is absolute and will
  drift if the window moves.

- **RG-17** `[done]` `template` and `text` **Target**s work **without a Locked application**.
  Locking an application only makes the search faster and less noisy (`RG-7`); it is not a
  precondition.
- **RG-7** `[done]` With no search region drawn, the search scope is: the **Anchor window** if
  there is a **Locked application** and a window can be read, otherwise all displays.

## Template matching

- **RG-8** `[done]` The algorithm is pyramid normalised cross-correlation: a coarse scan at a
  downscaled level, then refinement within ±8 pixels at native resolution.

  Scanning directly at native resolution is not viable: a 120×48 template on a 3024×1964 screen is
  roughly 5.56 million positions × 5,760 pixels ≈ **32 billion comparisons** for **one** **Target**
  resolution.

- **RG-21** `[done]` The downscale level is **not** fixed at 8 as the first specification said. It
  is the cheapest level at which the template still keeps at least half its original contrast.

  A fixed 8× downscale **wipes out** high-frequency detail — text, 1px borders, small checker
  patterns — turning a coarse template into a nearly flat array. Correlation at the coarse level
  is then meaningless, the peak lands somewhere random, and the refinement window does not even
  contain the right position. This is a bug that actually happened and has a regression test
  holding it shut.

- **RG-22** `[done]` The coarse scan keeps **8 separated candidates** (non-maximum suppression at
  half the template size) and refines all of them, rather than keeping only the highest peak. Real
  interfaces are full of repeated detail — a row of identical buttons, table rules — so the best
  spot at the coarse level is often not the right spot at native resolution.

- **RG-23** `[Slice 2]` `[done]` After cropping a **Template**, the default **Search region** hugs
  the area just drawn rather than the whole screen. The padding is half the Template's longer
  side, floored at 48 points and capped at 160, then clipped to the screen.

  The reason: the primary use case is games, where what you need to aim at almost always stays
  where it was cropped. Scanning the whole screen is both slower and more likely to grab an
  identical patch somewhere else — exactly the failure `I2` of the manual tests exposes. The
  suggested region is stored by the same path as a hand-drawn one (`RG-6`): relative to the
  **Anchor window** when the Scenario has a locked application, absolute otherwise. Pressing
  **Clear** returns to searching the whole screen.

- **RG-18** `[done]` The choice of downscale level is bounded above by a **cost ceiling** of about
  40 million comparisons for one coarse scan. This is in direct tension with `RG-21`: keeping
  contrast wants less downscaling, scanning wants more. When no level satisfies both, **the ceiling
  wins** — a less accurate match beats freezing the interface for seconds on one **Step**.
  Narrowing the **Search region** is how the user buys that accuracy back.

- **RG-25** `[done]` A **Template** is matched at **two scales**, and the second one is only tried
  when the first finds nothing above the threshold on **any** display. The first attempt is the
  **Template** at its native pixel size. The second compensates for a **Template** cropped on a
  display of the other scale: on a 2x display the **haystack** is downsampled by 2 and the point
  found multiplied by 2; on a 1x display the **Template** is downsampled by 2.

  A **Template** is stored as raw captured pixels, so the same button is 272×86 pixels cropped at
  2x and 136×43 at 1x, and `RG-8` scans at native size only. Without this a **Template** cropped on
  one display never matches on a display of the other scale, and it fails **silently** — as a
  timeout indistinguishable from a target that is not on screen.

  Native-first is what keeps this free: cropping and running on the same display never reaches the
  second attempt, so `C13` is unaffected. Each attempt is its own coarse scan and each stays inside
  the `RG-18` ceiling. Two attempts are enough rather than a sweep because the scale of the display
  being searched is known and the candidate set for the **Template** is `{1, 2}`.
  See [ADR-0008](../adr/0008-match-templates-at-two-scales.md) for why the crop scale is not
  recorded per **Template** instead.

- **RG-26** `[done]` The overlay used to crop a **Template** or pick a point covers **every**
  display, and the rectangle it draws is converted with the **main** display's `maxY` as the origin
  of the y flip, not the `maxY` of the display being drawn on.

  Converting a `CGEvent` coordinate into a view's own y-up coordinates is anchored to the main
  display, which is what `CGEvent` measures from; `screen.frame.maxY` happens to equal it on the
  main display and is a different number on any other.

  **Not demonstrated.** `C18` ran the two formulas against each other on a two-display arrangement
  whose frames predict a 98-point divergence — built-in `(0, 0, 1512, 982)`, external
  `(1512, 0, 1920, 1080)` — and the drawn rectangle came out **pixel-identical** both ways
  (`x 125…399, y 157…339`, measured by differencing two captures). Attempts to instrument the
  drawing code from inside the app failed. So the reasoning above is sound on paper and the defect
  it describes was **never reproduced**; this requirement records a conversion that is correct by
  construction, not a bug that was observed and fixed.

  Whichever formula is used, the region actually cropped is unaffected — it comes straight from
  `CGEvent.location` in `mouseUp` — so nothing here can corrupt a **Search region** or a
  **Template**.

- **RG-9** `[done]` The match score is a real number `0…1`. The default threshold is `0.90`,
  adjustable per **Step**.
- **RG-10** `[done]` When several places clear the threshold, take the highest score. On a tie take
  the topmost, then the leftmost, so the result is deterministic.
- **RG-11** `[done]` The **Target** returned is the **centre** of the matched area.
- **RG-12** `[done]` Apple has no template-matching API — no `matchTemplate`,
  MetalPerformanceShaders has no cross-correlation, Vision can only localise text. The SDK has
  been checked. Do not go looking again.

## Finding by text

- **RG-13** `[done]` Use `VNRecognizeTextRequest` with `recognitionLevel = .accurate`, languages
  being the system language plus English.
- **RG-14** `[done]` Matching is case-insensitive and ignores leading and trailing whitespace.
- **RG-15** `[done]` The **Target** returned is the centre of the bounding box of the **matched
  piece of text** — not of the whole line Vision read. Vision folds a whole line into one
  observation, so taking `observation.boundingBox` clicks the middle of the line: looking for
  `"Lưu"` in the line `"Lưu   ⌘S"` fires into the gap. It has to take
  `candidate.boundingBox(for: range)` for the matched range. Found while running `C9` of the
  manual tests: the click landed exactly in the centre of the line, 83 points away from the word
  being aimed at.
- **RG-16** `[done]` When several pieces match, take the one with the highest confidence; on a tie
  follow the `RG-10` rule.
