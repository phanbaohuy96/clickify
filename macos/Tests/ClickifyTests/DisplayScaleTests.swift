import CoreGraphics
import Foundation
import Testing
@testable import Clickify

/// `RG-2`, `RG-25`: converting captured **pixels** into `CGEvent` **points**, and finding a Template that was
/// cropped on a display of the other scale.
///
/// Both are the coordinate arithmetic the manual tests cannot reach: `C2` passed on a single 2x built-in
/// display, where `frame.minX`, `frame.minY` and the scale are all exactly the values that hide a mistake —
/// origin zero and one uniform factor. `C3` and `C4`, written to catch that, could not be run.

/// A greyscale `CGImage` from raw `0…1` values, so a synthetic haystack can be handed to `CapturedImage`.
private func image(of gray: GrayImage) -> CGImage {
    var bytes = gray.pixels.map { UInt8(max(0, min(255, $0 * 255))) }
    let context = CGContext(
        data: &bytes,
        width: gray.width,
        height: gray.height,
        bitsPerComponent: 8,
        bytesPerRow: gray.width,
        space: CGColorSpaceCreateDeviceGray(),
        bitmapInfo: CGImageAlphaInfo.none.rawValue
    )!
    return context.makeImage()!
}

/// A deterministically noisy background with one patch pasted at exactly one spot.
private func haystack(
    width: Int,
    height: Int,
    patch: GrayImage,
    at origin: (x: Int, y: Int)
) -> GrayImage {
    var pixels = [Float](repeating: 0, count: width * height)
    for index in pixels.indices {
        pixels[index] = Float((index * 37) % 100) / 400
    }
    for y in 0..<patch.height {
        for x in 0..<patch.width {
            pixels[(origin.y + y) * width + origin.x + x] = patch[x, y]
        }
    }
    return GrayImage(width: width, height: height, pixels: pixels)
}

private func checkerboard(width: Int, height: Int, cell: Int = 3) -> GrayImage {
    var pixels = [Float](repeating: 0, count: width * height)
    for y in 0..<height {
        for x in 0..<width {
            pixels[y * width + x] = (x / cell + y / cell) % 2 == 0 ? 0.95 : 0.05
        }
    }
    return GrayImage(width: width, height: height, pixels: pixels)
}

private func captured(_ gray: GrayImage, frame: CGRect, scale: Double) -> CapturedImage {
    CapturedImage(image: image(of: gray), frame: frame, scale: scale)
}

// MARK: - RG-2, the pixel-to-point conversion

@Test func aPixelOnTheMainRetinaDisplayBecomesHalfItsPointPosition() {
    let main = CapturedImage(
        image: image(of: checkerboard(width: 4, height: 4)),
        frame: CGRect(x: 0, y: 0, width: 1512, height: 982),
        scale: 2
    )

    #expect(main.screenPoint(fromPixel: CGPoint(x: 600, y: 400)) == CGPoint(x: 300, y: 200))
}

/// The case `C3` and `C4` exist for: a 1x display at a non-zero origin. A 1x display divides by nothing, and
/// the origin has to be added on top — the failure the `?? 2` fallback in `scale(of:)` used to cause was
/// exactly this, dividing a 1x coordinate by 2.
@Test func aPixelOnAOneXDisplayToTheRightKeepsItsSizeAndGainsTheOrigin() {
    let external = CapturedImage(
        image: image(of: checkerboard(width: 4, height: 4)),
        frame: CGRect(x: 1512, y: 0, width: 1920, height: 1080),
        scale: 1
    )

    #expect(external.screenPoint(fromPixel: CGPoint(x: 600, y: 400)) == CGPoint(x: 2112, y: 400))
}

/// A display placed above or to the left of the main one has a negative origin, which must be added, not
/// clamped away.
@Test func aPixelOnADisplayAtANegativeOriginStaysNegative() {
    let above = CapturedImage(
        image: image(of: checkerboard(width: 4, height: 4)),
        frame: CGRect(x: -1920, y: -1080, width: 1920, height: 1080),
        scale: 1
    )

    #expect(above.screenPoint(fromPixel: CGPoint(x: 100, y: 100)) == CGPoint(x: -1820, y: -980))
}

/// Two displays of different scales in one coordinate space: the same pixel offset has to land in two
/// different places. This is the sentence in `RG-2` that a single-display machine can never exercise.
@Test func theSamePixelOffsetOnTwoScalesGivesTwoDifferentPoints() {
    let retina = CapturedImage(
        image: image(of: checkerboard(width: 4, height: 4)),
        frame: CGRect(x: 0, y: 0, width: 1512, height: 982),
        scale: 2
    )
    let external = CapturedImage(
        image: image(of: checkerboard(width: 4, height: 4)),
        frame: CGRect(x: 1512, y: 0, width: 1920, height: 1080),
        scale: 1
    )

    #expect(retina.screenPoint(fromPixel: CGPoint(x: 200, y: 100)) == CGPoint(x: 100, y: 50))
    #expect(external.screenPoint(fromPixel: CGPoint(x: 200, y: 100)) == CGPoint(x: 1712, y: 100))
}

// MARK: - RG-25, matching a Template cropped at the other scale

/// The failure this whole change exists for: a Template cropped at 2x, searched for on a 1x display.
/// The native pass must find nothing — proving the test is not vacuous — and the cross-scale pass must find it.
@Test func aTwoXTemplateIsNotFoundOnAOneXDisplayUntilTheSecondPass() {
    let atTwoX = checkerboard(width: 48, height: 36, cell: 6)
    let atOneX = atTwoX.downsampled(by: 2)
    let screen = haystack(width: 400, height: 300, patch: atOneX, at: (x: 150, y: 90))
    let display = [captured(screen, frame: CGRect(x: 1512, y: 0, width: 400, height: 300), scale: 1)]

    let native = ScreenTargetRecognizer.search(atTwoX, in: display, threshold: 0.9, crossScale: false)
    #expect(native == nil)

    let crossScale = ScreenTargetRecognizer.search(atTwoX, in: display, threshold: 0.9, crossScale: true)
    // Centre of a 24×18 patch at (150,90) on a 1x display starting at x=1512.
    #expect(crossScale == CGPoint(x: 1512 + 162, y: 99))
}

/// The other direction: a Template cropped at 1x, searched for on a 2x display. Here it is the **haystack**
/// that shrinks, so the point found has to be multiplied back up by 2 before it means anything.
@Test func aOneXTemplateIsFoundOnATwoXDisplayAndScaledBackUp() {
    let atTwoX = checkerboard(width: 48, height: 36, cell: 6)
    let atOneX = atTwoX.downsampled(by: 2)
    let screen = haystack(width: 400, height: 300, patch: atTwoX, at: (x: 120, y: 80))
    let display = [captured(screen, frame: CGRect(x: 0, y: 0, width: 200, height: 150), scale: 2)]

    #expect(ScreenTargetRecognizer.search(atOneX, in: display, threshold: 0.9, crossScale: false) == nil)

    let crossScale = ScreenTargetRecognizer.search(atOneX, in: display, threshold: 0.9, crossScale: true)
    // The 48×36 patch at (120,80) has its centre at pixel (144,98); at scale 2 that is point (72,49).
    let point = try! #require(crossScale)
    #expect(abs(point.x - 72) <= 1)
    #expect(abs(point.y - 49) <= 1)
}

/// Same display, same scale — the ordinary case, and the one that must not be made slower or less accurate.
/// The native pass answers it, so `RG-25`'s second pass is never reached.
@Test func aTemplateCroppedOnTheDisplayItRunsOnIsFoundByTheNativePass() {
    let patch = checkerboard(width: 24, height: 18, cell: 3)
    let screen = haystack(width: 400, height: 300, patch: patch, at: (x: 137, y: 88))
    let display = [captured(screen, frame: CGRect(x: 0, y: 0, width: 200, height: 150), scale: 2)]

    let native = ScreenTargetRecognizer.search(patch, in: display, threshold: 0.9, crossScale: false)

    // Centre of a 24×18 patch at (137,88) is pixel (149,97); at scale 2 that is point (74.5,48.5).
    #expect(native == CGPoint(x: 74.5, y: 48.5))
}

/// A Template that is not on screen at all must stay not-found through **both** passes. Without this the
/// second pass would be free to invent a match out of a blurrier comparison.
@Test func aTemplateThatIsAbsentIsNotFoundByEitherPass() {
    let present = checkerboard(width: 24, height: 18, cell: 3)
    let absent = checkerboard(width: 24, height: 18, cell: 7)
    let screen = haystack(width: 400, height: 300, patch: present, at: (x: 137, y: 88))
    let display = [captured(screen, frame: .zero, scale: 2)]

    #expect(ScreenTargetRecognizer.search(absent, in: display, threshold: 0.9, crossScale: false) == nil)
    #expect(ScreenTargetRecognizer.search(absent, in: display, threshold: 0.9, crossScale: true) == nil)
}

/// `RG-10` has to keep holding across displays: the highest score wins, wherever it is.
@Test func theHighestScoringDisplayWinsWhenTwoAreSearched() {
    let patch = checkerboard(width: 24, height: 18, cell: 3)
    let blurred = haystack(width: 400, height: 300, patch: patch.downsampled(by: 2), at: (x: 40, y: 40))
    let exact = haystack(width: 400, height: 300, patch: patch, at: (x: 137, y: 88))

    let displays = [
        captured(blurred, frame: CGRect(x: 0, y: 0, width: 200, height: 150), scale: 2),
        captured(exact, frame: CGRect(x: 1512, y: 0, width: 400, height: 300), scale: 1),
    ]

    let found = ScreenTargetRecognizer.search(patch, in: displays, threshold: 0.9, crossScale: false)

    #expect(found == CGPoint(x: 1512 + 149, y: 97))
}
