# Screenshots come from the accessibility service, not MediaProjection

Recognition takes every frame with `AccessibilityService.takeScreenshot()`. **MediaProjection is
not used**, although [01](../sdd/01-scope.md) named it for A3 and
[ADR-0014](./0014-no-database.md) assumed it when it argued about performance. Both are corrected
by this record.

## Why

MediaProjection is the faster of the two and it is the wrong trade here.

**It asks the user for permission every time.** `createScreenCaptureIntent()` puts up a system
dialogue — *"Clickify will start capturing everything that's displayed on your screen"* — and
since Android 14 the consent cannot be kept: one projection, one session, one dialogue. A
**Scenario** that stops to ask for the screen before it may look at it is a **Scenario** that
cannot be started with the phone already in the game. The one promise this app makes about running
is that Start is one press and Stop is always reachable; a consent dialogue in front of Start
breaks the first half.

**It needs a foreground service type the app would have to justify.** `mediaProjection` is a
declared type with its own notification and its own review, and this app already runs as
`specialUse` and explains itself there. Two foreground service types for one feature is a lot of
platform surface for a frame.

**The speed it buys is not speed this app can use.** `takeScreenshot` is rate-limited to about one
call every 333 ms, and MediaProjection can deliver 60. But `TP-13` already caps one coarse scan at
about 40 million comparisons, which is tens to hundreds of milliseconds of arithmetic on a phone —
so the search, not the capture, is what sets the rate. A faster frame would arrive while the
previous one was still being looked at.

**And the user is already paying for the accessibility service.** `takeScreenshot` needs
`canTakeScreenshot="true"` in a configuration the user has already been walked through
([02](../sdd/02-permissions-and-onboarding.md)), with no second dialogue and no second grant.
MediaProjection would be the *third* permission this app asks for, to do something the second one
can already do.

## What it costs, stated plainly

- **About 2.5 frames a second.** Waiting for a button to appear is fine at that rate. Tracking
  something that moves is not, and this app does not try to.
- **Clickify's own floating control is in every frame** and cannot be excluded — `TP-6`. macOS
  excludes its own windows; there is no equivalent here.
- **`takeScreenshot` can be refused** by the platform while a secure window is on screen, and it
  reports it. A refused frame is treated as a search that found nothing, which means the **Step**'s
  own `onTimeout` and not a crash.

## Consequences

- `android:canTakeScreenshot="true"` joins the accessibility service configuration. It is not a new
  runtime permission and adds no new dialogue.
- The performance paragraph in [ADR-0014](./0014-no-database.md) names a hot loop that does not
  exist. Its conclusion is unaffected: **Template**s are still files, and the argument for files
  was never about frame rate.
- If a use case ever needs to follow something that moves, this is the decision to revisit, and
  the cost of revisiting it is a permission dialogue in front of Start.
