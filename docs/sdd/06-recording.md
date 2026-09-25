# 06 — Recording

Slice 3. See [ADR-0003](../adr/0003-no-keyboard-capture-when-recording.md) (no keyboard capture)
and [ADR-0004](../adr/0004-recordings-keep-real-timing.md) (no timing cap).

## Recording session

- **RC-1** `[done]` A **Recording session** starts and ends with the global `⌥⌘R` shortcut, **not**
  with an on-screen button — clicking a button would land in the recording itself.
- **RC-2** `[done]` Any event **landing in one of Clickify's own windows** is excluded from the
  recording, including the tail of that gesture (mouse up, drag), not just the press.

  The condition is **position**, not "which application is frontmost". The frontmost-application
  test is wrong in **both directions**, and both were measured on the real app:

  - **Missed.** The floating panel is an `NSPanel` of kind `.nonactivatingPanel`, so clicking it
    does **not** bring Clickify to the front — which let the "Finish" click land in the
    recording as a spurious click Step right where the button is.
  - **Falsely caught, and worse.** The user opens the popover and presses "Record": the popover
    closes but Clickify is **still the frontmost application**, so the first click on the
    destination app is treated as our own and swallowed. That means **every recording started from
    the popover lost its first operation**, silently. Measured: three clicks produced two Steps,
    and the missing one was always the first.

  Along with this, the **owning application** of a gesture has to be asked by coordinate whenever
  the frontmost application is Clickify itself. Attributing it to ourselves makes the whole
  session look like it spans two applications (`RC-14`) and loses **Anchor window**-relative
  Targets entirely (`RC-13`).
- **RC-3** `[done]` While recording, the floating panel shows the number of **Step**s recorded and
  the `⌥⌘R` reminder to finish.
- **RC-4** `[done]` A **Recording session** watches the mouse only: `mouseDown`, `mouseUp`,
  `mouseDragged`, `scrollWheel`.

## Inferring Actions

- **RC-5** `[done]` `down` then `up` within 400 ms, moving ≤ 3 pt → `click(button, 1, 0)`.
- **RC-6** `[done]` `down` then `up` after **more than** 400 ms, moving ≤ 3 pt → `click(button, 1,
  holdMs = the real hold time)`.
- **RC-7** `[done]` Two `click`s with the same button, closer together than the system
  double-click interval and within 5 pt → folded into `click(button, 2, 0)`. Three make
  `count = 3`.
- **RC-8** `[done]` `down` → `dragged` beyond 3 pt → `up` → `drag`, keeping **only the start and
  end points**.

  The original specification said to keep downsampled intermediate points. Dropped: the `drag`
  **Action** has nowhere to store a path (`DM-9`), and the runner already reconstructs the path by
  interpolation (`EX-20`), which is what applications actually need. Storing the real path only
  helps freehand drawing, which is out of scope.
- **RC-9** `[done]` A burst of `scrollWheel` events less than 150 ms apart → folded into a single
  `scroll` with the summed delta. Without folding, one trackpad scroll becomes about 100 **Step**s.

  The "same direction" condition from the first version was dropped: inertial trackpad scrolling
  usually **changes sign** at the end of a burst, so testing direction would cut one user
  operation into two **Step**s. Folding purely by time gap is both simpler and a better guess.

## Timing

- **RC-10** `[done]` A **Step**'s delay is the real gap between that **Step** ending and the next
  **Step** starting, **uncapped and unrounded**.
- **RC-11** `[done]` The last **Step** has a delay of `0`.

## Generating Targets

- **RC-12** `[done]` For each operation, ask Accessibility which process owns that point.
- **RC-13** `[done]` Every operation in the session belonging to the **same** application → make
  that application the **Locked application** and generate `windowRelative` **Target**s (`DM-13`).
- **RC-14** `[done]` Operations spanning several applications → generate `screenPoint` **Target**s,
  set no **Locked application**, and show a warning that the coordinates will drift if the window
  moves.
- **RC-15** `[done]` A **Recording session** never generates a **Template** **Target** on its own.
  Raising a **Step** to `template` is a deliberate user action in the editor window.

## Event capture mechanism

- **RC-18** `[done]` The `CGEventTap` is attached to the **main run loop**, not to a separate
  thread. That is what makes asking Accessibility inside the callback legal. In exchange the
  callback has to be fast: the system disables the tap if it runs too long, so
  `tapDisabledByTimeout` has to be caught and the tap **re-enabled**, rather than silently ceasing
  to record mid-session.
- **RC-19** `[done]` The process owning a gesture is determined by the **frontmost application**
  (`frontmostApplication`), not by `AXUIElementCopyElementAtPosition`. Asking AX by coordinate is
  more accurate in theory but costs tens of milliseconds with some applications, enough to trip
  `RC-18`. Clicking a window brings it to the front, so in practice the two agree.
- **RC-20** `[done]` Only the **start point** of a gesture samples the process and the **Anchor
  window** frame; `dragged` events inherit the previous event's values. Sampling at every drag
  step would violate `RC-18`.

## Result

- **RC-16** `[done]` Ending a **Recording session** creates a new saved **Scenario**, named by
  default after the application and the time of recording, then opens the editor window on that
  **Scenario**.

  The name has to be **unique**: the timestamp only goes down to the minute, so two recordings in
  the same minute produce exactly the same name and the picker shows two identical rows. Measured
  while running session D: six recordings, three colliding pairs. The ordinary save path (`save`)
  still leaves the name **untouched**, because it is also the path that records every character
  the user types while renaming.
- **RC-17** `[done]` A **Recording session** that captured no **Step** creates no **Scenario**.
- **RC-20** `[done]` The timestamp in that default name is `yyyy-MM-dd HH:mm`, formatted with
  `en_US_POSIX` and **never** with the user's locale.

  The name is the **sort key**: `ScenarioStore` orders the list with
  `name.localizedCaseInsensitiveCompare`. The previous `"dd/MM HH:mm"` sorted by day-of-month, so a
  recording made on `01/10` came out ahead of one made on `17/09` and the list of recordings was in
  no useful order.

  Freezing the format is not only about ordering. A name is written into `scenario.json` and stays
  there, so deriving it from an interface setting would name the same recording differently on two
  machines, and would bring the wrong ordering back in every locale that puts the day first. Only
  the **word** in the name is translated (`LC-11`); the digits are data.

## Verification

- **RC-21** `[done]` Everything `ScenarioRecorder` asks the operating system — its own process,
  the frontmost application, the **Anchor window** frame, the application name, the double-click
  interval, the clock — goes through `RecordingEnvironment`, the same way as
  `ScenarioSystemBridge`. Without that seam, `RC-2`, `RC-13`, `RC-14` and `RC-20` could only be
  checked by hand with a real `CGEventTap`, which means not checkable in `swift test` at all: a
  tap requires the Accessibility permission, which TCC does not grant to SwiftPM's test binary.
  Tests inject prebuilt `CGEvent`s straight into `ScenarioRecorder.handle`. **Creating** a
  `CGEvent` needs no permission; only **posting** one does.
