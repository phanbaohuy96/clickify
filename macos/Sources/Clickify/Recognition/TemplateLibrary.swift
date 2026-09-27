import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers
import os

/// A Scenario's **Template** library: PNG files in the `templates/` folder of the Scenario's own directory.
///
/// Templates are duplicated rather than shared between Scenarios — see [ADR-0005].
@MainActor
final class TemplateLibrary {
    private static let logger = Logger(subsystem: "com.local.Clickify", category: "Templates")

    private let directory: URL
    /// Decoded images, kept between the retries of `EX-8`. Without the cache a PNG would be read and decoded
    /// again every 150 ms.
    private var cache: [String: GrayImage] = [:]

    init(directory: URL) {
        self.directory = directory
    }

    func url(for name: String) -> URL {
        directory.appendingPathComponent(name)
    }

    /// Saves a freshly cropped piece of image and returns its file name.
    func save(_ image: CGImage) throws -> String {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let name = "\(UUID().uuidString.prefix(8)).png"
        let target = url(for: name)

        guard let destination = CGImageDestinationCreateWithURL(
            target as CFURL,
            UTType.png.identifier as CFString,
            1,
            nil
        ) else {
            throw CocoaError(.fileWriteUnknown)
        }
        CGImageDestinationAddImage(destination, image, nil)
        guard CGImageDestinationFinalize(destination) else {
            throw CocoaError(.fileWriteUnknown)
        }
        return name
    }

    func loadGray(_ name: String) -> GrayImage? {
        if let cached = cache[name] { return cached }

        guard let source = CGImageSourceCreateWithURL(url(for: name) as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil),
              let gray = GrayImage(cgImage: image) else {
            Self.logger.error("Could not read template \(name, privacy: .public)")
            return nil
        }
        cache[name] = gray
        return gray
    }

    func loadImage(_ name: String) -> CGImage? {
        guard let source = CGImageSourceCreateWithURL(url(for: name) as CFURL, nil) else { return nil }
        return CGImageSourceCreateImageAtIndex(source, 0, nil)
    }

    /// Deletes the files no Step uses any more, so the Scenario directory does not grow every time the user
    /// changes a template.
    func removeUnused(keeping names: Set<String>) {
        let files = (try? FileManager.default.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: nil
        )) ?? []

        for file in files where !names.contains(file.lastPathComponent) {
            try? FileManager.default.removeItem(at: file)
            cache[file.lastPathComponent] = nil
        }
    }
}
