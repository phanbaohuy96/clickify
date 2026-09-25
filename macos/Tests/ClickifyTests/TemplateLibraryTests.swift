import CoreGraphics
import Foundation
import Testing
@testable import Clickify

/// The **Template** library: real disk writes into a temporary directory. This is I/O, so it can really fail —
/// PNG encoding, conversion to greyscale, and cleaning up unused files (ST-12, RG-19).
@MainActor
struct TemplateLibraryTests {
    private func makeDirectory() -> URL {
        let directory = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("ClickifyTemplates-\(UUID().uuidString)")
        return directory
    }

    /// A checkerboard image: the pixel values differ sharply, so any distortion in the PNG round trip shows immediately.
    private func makeImage(width: Int, height: Int) -> CGImage {
        var bytes = [UInt8](repeating: 0, count: width * height)
        for y in 0..<height {
            for x in 0..<width {
                bytes[y * width + x] = (x / 2 + y / 2) % 2 == 0 ? 20 : 230
            }
        }
        let context = CGContext(
            data: &bytes,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width,
            space: CGColorSpaceCreateDeviceGray(),
            bitmapInfo: CGImageAlphaInfo.none.rawValue
        )!
        return context.makeImage()!
    }

    @Test func savingThenLoadingReturnsTheSamePixels() throws {
        let directory = makeDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }
        let library = TemplateLibrary(directory: directory)
        let image = makeImage(width: 8, height: 6)

        // The directory does not exist yet: `save` has to create it rather than throw.
        let name = try library.save(image)
        let loaded = try #require(library.loadGray(name))

        #expect(loaded.width == 8)
        #expect(loaded.height == 6)
        let original = try #require(GrayImage(cgImage: image))
        // PNG is lossless; any difference could only come from a colour-space conversion.
        for index in original.pixels.indices {
            #expect(abs(loaded.pixels[index] - original.pixels[index]) < 0.02)
        }
    }

    @Test func aMissingTemplateReturnsNilInsteadOfCrashing() {
        let directory = makeDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }
        let library = TemplateLibrary(directory: directory)

        // A hand-copied Scenario missing a file is a real situation; the runner must report not-found, not crash.
        #expect(library.loadGray("does-not-exist.png") == nil)
        #expect(library.loadImage("does-not-exist.png") == nil)
    }

    @Test func cleaningUpRemovesOnlyTheTemplatesNoStepUsesAnyMore() throws {
        let directory = makeDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }
        let library = TemplateLibrary(directory: directory)

        let kept = try library.save(makeImage(width: 8, height: 6))
        let dropped = try library.save(makeImage(width: 8, height: 6))

        library.removeUnused(keeping: [kept])

        #expect(FileManager.default.fileExists(atPath: library.url(for: kept).path))
        #expect(!FileManager.default.fileExists(atPath: library.url(for: dropped).path))
    }

    @Test func aRemovedTemplateIsNotStillServedFromTheCache() throws {
        let directory = makeDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }
        let library = TemplateLibrary(directory: directory)

        let name = try library.save(makeImage(width: 8, height: 6))
        #expect(library.loadGray(name) != nil)  // loads into the cache

        library.removeUnused(keeping: [])

        // Without clearing the cache the runner would keep matching against a deleted image — silently wrong and hard to track down.
        #expect(library.loadGray(name) == nil)
    }
}
