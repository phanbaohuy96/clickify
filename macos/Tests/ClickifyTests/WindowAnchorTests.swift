import CoreGraphics
import Foundation
import Testing
@testable import Clickify

/// DM-13: this is exactly the situation that led to "anchor to the nearest corner" rather than always the
/// top-left. A button in the bottom-right has to follow the bottom-right corner when the window is enlarged.
@Test func aBottomRightPointStaysAtTheBottomRightWhenTheWindowGrows() {
    let small = CGRect(x: 0, y: 0, width: 800, height: 600)
    let point = CGPoint(x: 760, y: 560)

    let offset = WindowAnchor.offset(for: point, in: small)
    #expect(offset == WindowAnchor.Offset(corner: .bottomRight, dx: -40, dy: -40))

    let grown = CGRect(x: 0, y: 0, width: 1200, height: 900)
    #expect(WindowAnchor.resolve(offset, in: grown) == CGPoint(x: 1160, y: 860))

    // Anchored to the top-left it would stay at (760, 560) — drifting into the middle of the window.
    let topLeftOffset = WindowAnchor.Offset(corner: .topLeft, dx: 760, dy: 560)
    #expect(WindowAnchor.resolve(topLeftOffset, in: grown) == point)
}

@Test func movingAWindowMovesEveryAnchoredPointWithIt() {
    let frame = CGRect(x: 700, y: 300, width: 800, height: 600)
    let point = CGPoint(x: 820, y: 388)

    let offset = WindowAnchor.offset(for: point, in: frame)
    let moved = frame.offsetBy(dx: 40, dy: 0)

    #expect(WindowAnchor.resolve(offset, in: moved) == CGPoint(x: 860, y: 388))
}

@Test func eachQuadrantPicksItsOwnCorner() {
    let frame = CGRect(x: 0, y: 0, width: 100, height: 100)
    let expected: [(CGPoint, WindowCorner)] = [
        (CGPoint(x: 10, y: 10), .topLeft),
        (CGPoint(x: 90, y: 10), .topRight),
        (CGPoint(x: 10, y: 90), .bottomLeft),
        (CGPoint(x: 90, y: 90), .bottomRight)
    ]

    for (point, corner) in expected {
        #expect(WindowAnchor.offset(for: point, in: frame).corner == corner)
    }
}

@Test func resolvingAnOffsetIsTheInverseOfTakingIt() {
    let frame = CGRect(x: 120, y: 80, width: 640, height: 480)
    for point in [CGPoint(x: 130, y: 90), CGPoint(x: 700, y: 500), CGPoint(x: 400, y: 300)] {
        let offset = WindowAnchor.offset(for: point, in: frame)
        #expect(WindowAnchor.resolve(offset, in: frame) == point)
    }
}
