# Compose runs in the Overlay, and `BaseScreen` does not

The **Overlay** is where most of this app's interface lives — **Marker**s, the floating control, the
per-**Step** panel — and an **Overlay** is a view attached to `WindowManager`, not an Activity.
Compose refuses to run there until the view is given a `LifecycleOwner`, a `SavedStateRegistryOwner`
and a `ViewModelStoreOwner` by hand. We wire them by hand and run Compose anyway, behind a
`BaseOverlayScreen` in `:core` that sits beside the template's `BaseScreen` rather than reusing it.

Recorded because the app departs here from the template it started on, in the template's strongest
area. Everything `android-base-structure` offers for screens — `BaseScreen`, `AppScaffold`,
`MessageEffect` snackbars, `@Serializable` `Route`s and Navigation Compose — assumes an Activity, and
therefore does not reach the surface that matters most.

## Why not classic Views in the Overlay

Using the old toolkit for the **Overlay** and Compose only in the Activity avoids all the lifecycle
wiring, and it is what most apps in this category do. It was rejected because the cost lands
repeatedly and in the wrong place: every widget, every theme token and every string would be built
twice, the heavier half of the interface would sit on the older toolkit, and **A4** — five interface
languages — would have to be solved twice instead of once. One Compose tree with one theme and one
string source makes that slice a single problem.

## The cost, stated plainly

Hand-wiring those three owners is where this goes wrong if it goes wrong. A `ViewModelStore` that is
not cleared when the window is removed leaks its ViewModels, once per opening and closing of the
floating control, which is many times an hour. This is a documented pattern and not an invention,
but it must be written **once**, correctly, in `:core`, and never re-implemented per feature. It
belongs under test.

## Consequences

- `:core` gains an overlay window host — owners, theming, and teardown — beside the Activity-side
  `BaseScreen`, and `:app` uses one or the other depending on which surface it is building.
- Together with [ADR-0014] (no Room) and the absence of any network layer (Clickify talks to no
  server, so Retrofit and OkHttp go too), what survives of the template is `:domain` as pure Kotlin,
  `:data` over files, Hilt, and the quality and test toolchain. That is still the reason to start
  from it; the screen scaffolding is not.
