import CoreGraphics
import Foundation

/// A single-channel greyscale image, used as the input to template matching.
///
/// A type of its own rather than a `CGImage` directly: the matching algorithm accesses pixels millions of times
/// so it has to be a flat contiguous array, and keeping it out of Core Graphics lets tests build images by hand.
struct GrayImage: Equatable, Sendable {
    let width: Int
    let height: Int
    /// Brightness `0…1`, laid out row by row.
    var pixels: [Float]

    init(width: Int, height: Int, pixels: [Float]) {
        precondition(pixels.count == width * height, "Pixel count does not match the dimensions")
        self.width = width
        self.height = height
        self.pixels = pixels
    }

    subscript(x: Int, y: Int) -> Float {
        pixels[y * width + x]
    }

    var isEmpty: Bool { width <= 0 || height <= 0 }

    /// Downscales by averaging each `factor × factor` cell.
    func downsampled(by factor: Int) -> GrayImage {
        guard factor > 1 else { return self }
        let newWidth = width / factor
        let newHeight = height / factor
        guard newWidth > 0, newHeight > 0 else { return self }

        var result = [Float](repeating: 0, count: newWidth * newHeight)
        let divisor = Float(factor * factor)

        for y in 0..<newHeight {
            for x in 0..<newWidth {
                var total: Float = 0
                for offsetY in 0..<factor {
                    let row = (y * factor + offsetY) * width + x * factor
                    for offsetX in 0..<factor {
                        total += pixels[row + offsetX]
                    }
                }
                result[y * newWidth + x] = total / divisor
            }
        }

        return GrayImage(width: newWidth, height: newHeight, pixels: result)
    }

    /// Converts any `CGImage` to greyscale, independent of the source colour space.
    init?(cgImage: CGImage) {
        let width = cgImage.width
        let height = cgImage.height
        guard width > 0, height > 0 else { return nil }

        var bytes = [UInt8](repeating: 0, count: width * height)
        guard let context = CGContext(
            data: &bytes,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width,
            space: CGColorSpaceCreateDeviceGray(),
            bitmapInfo: CGImageAlphaInfo.none.rawValue
        ) else { return nil }

        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        self.init(
            width: width,
            height: height,
            pixels: bytes.map { Float($0) / 255 }
        )
    }
}
