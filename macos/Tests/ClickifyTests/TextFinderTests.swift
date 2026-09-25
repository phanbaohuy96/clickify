import AppKit
import CoreText
import Testing
@testable import Clickify

/// Draws a line of black text on a white background to give Vision a real image to read.
///
/// This is one of the few places that uses a real dependency rather than a fake: what has to be checked is
/// **which piece Vision returns the bounding box of**, so replacing Vision with a fake would check nothing.
private func imageOfText(_ text: String, width: Int = 1000, height: Int = 180) -> CGImage {
    let context = CGContext(
        data: nil,
        width: width,
        height: height,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    )!
    context.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
    context.fill(CGRect(x: 0, y: 0, width: width, height: height))

    let font = CTFontCreateWithName("Helvetica" as CFString, 72, nil)
    let attributed = NSAttributedString(
        string: text,
        attributes: [
            .font: font,
            .foregroundColor: CGColor(red: 0, green: 0, blue: 0, alpha: 1)
        ]
    )
    context.textPosition = CGPoint(x: 40, y: 60)
    CTLineDraw(CTLineCreateWithAttributedString(attributed), context)
    return context.makeImage()!
}

/// RG-15: the Target returned has to be the centre of the **matched piece of text**, not of the whole line.
///
/// Vision folds a whole line into one observation. Taking `observation.boundingBox` makes looking for `"Lưu"` in
/// the line `"Lưu   ⌘S"` click into the gap in the middle. Found while running `C9` of the manual tests: the
/// click landed exactly in the centre of the line, 83 points away from the word being aimed at.
@Test func twoWordsOnOneLineGiveTwoDifferentBoxes() throws {
    let image = imageOfText("ZUKAMI QWERTY")

    let first = try #require(TextFinder.find("ZUKAMI", in: image))
    let second = try #require(TextFinder.find("QWERTY", in: image))

    // Taking the whole line's box would make these two boxes **equal** and collapse the assertion below.
    #expect(first.boundingBox.midX < second.boundingBox.midX)
    #expect(second.boundingBox.minX > first.boundingBox.maxX - 1)

    // Each box has to be well narrower than the line, rather than covering both words.
    let lineWidth = second.boundingBox.maxX - first.boundingBox.minX
    #expect(first.boundingBox.width < lineWidth * 0.7)
    #expect(second.boundingBox.width < lineWidth * 0.7)
}

/// RG-14: case does not matter.
@Test func caseDoesNotChangeWhatIsFound() throws {
    let image = imageOfText("ZUKAMI QWERTY")

    let upper = try #require(TextFinder.find("ZUKAMI", in: image))
    let lower = try #require(TextFinder.find("zukami", in: image))

    #expect(abs(upper.boundingBox.midX - lower.boundingBox.midX) < 2)
    #expect(abs(upper.boundingBox.midY - lower.boundingBox.midY) < 2)
}

@Test func textThatIsNotOnTheImageIsNotFound() {
    #expect(TextFinder.find("KHONGCOCHUNAY", in: imageOfText("ZUKAMI QWERTY")) == nil)
}
