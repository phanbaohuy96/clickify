// Draws Clickify's application icon and writes `Resources/Clickify.icns`.
//
// Why a script and not a checked-in picture: the same mark also ships as an Android vector
// (`android/app/src/main/res/drawable/ic_launcher_foreground.xml`), and two hand-exported images
// drift apart the first time one of them is touched. The proportions below are the Android ones,
// expressed as fractions of the icon's rounded square, so a change here and a change there can be
// compared by reading them.
//
// macOS is not Android: a launcher masks an adaptive icon for you, and macOS does not. The rounded
// square, its inset, and the transparent corners all have to be drawn, which is what `plate` does.
//
//     swift tools/icon/make-icon.swift        # from macos/
//
// Requires `iconutil`, which ships with macOS.

import AppKit
import Foundation

// MARK: - The design, as fractions of the plate (the rounded square, not the canvas)

/// Apple's grid: the plate is 824pt of a 1024pt canvas, with a 185.4pt corner radius.
let plateSideFraction = 824.0 / 1024.0
let plateCornerFraction = 185.4 / 824.0

/// The mark. Taken from the Android viewport (arc radius 24.75, stroke 10.5, dot 8, of 108) and
/// scaled up by 1.224, because an adaptive icon reserves a margin that a macOS plate does not.
let arcRadiusFraction = 24.75 / 108.0 * 1.224
let arcStrokeFraction = 10.5 / 108.0 * 1.224
let dotRadiusFraction = 8.0 / 108.0 * 1.224
/// Half of the C's opening. 20° each side of due east, cut square.
let gapHalfAngle = 20.0 * .pi / 180.0

let amber = CGColor(red: 0xE8 / 255.0, green: 0xB3 / 255.0, blue: 0x3C / 255.0, alpha: 1)
let plateInner = CGColor(red: 0x24 / 255.0, green: 0x28 / 255.0, blue: 0x30 / 255.0, alpha: 1)
let plateMid = CGColor(red: 0x18 / 255.0, green: 0x1B / 255.0, blue: 0x21 / 255.0, alpha: 1)
let plateOuter = CGColor(red: 0x10 / 255.0, green: 0x12 / 255.0, blue: 0x16 / 255.0, alpha: 1)

// MARK: - Drawing

func draw(size: Int) -> CGImage {
    let side = CGFloat(size)
    let context = CGContext(
        data: nil,
        width: size,
        height: size,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: CGColorSpace(name: CGColorSpace.sRGB)!,
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    )!

    let plateSide = side * plateSideFraction
    let plate = CGRect(
        x: (side - plateSide) / 2,
        y: (side - plateSide) / 2,
        width: plateSide,
        height: plateSide
    )
    let shape = CGPath(
        roundedRect: plate,
        cornerWidth: plateSide * plateCornerFraction,
        cornerHeight: plateSide * plateCornerFraction,
        transform: nil
    )

    // The plate. A radial gradient with its light source up and to the left, so the surface reads as
    // a surface; a flat fill or a 45° fade is what every other icon does.
    context.saveGState()
    context.addPath(shape)
    context.clip()
    let gradient = CGGradient(
        colorsSpace: CGColorSpace(name: CGColorSpace.sRGB)!,
        colors: [plateInner, plateMid, plateOuter] as CFArray,
        locations: [0, 0.55, 1]
    )!
    context.drawRadialGradient(
        gradient,
        startCenter: CGPoint(x: plate.minX + plateSide * 0.35, y: plate.minY + plateSide * 0.70),
        startRadius: 0,
        endCenter: CGPoint(x: plate.minX + plateSide * 0.35, y: plate.minY + plateSide * 0.70),
        endRadius: plateSide * 0.89,
        options: [.drawsAfterEndLocation]
    )
    context.restoreGState()

    let centre = CGPoint(x: plate.midX, y: plate.midY)

    // The C. Drawn from the lower lip anticlockwise round to the upper one, so the 320° of arc is
    // the part that exists and the 40° gap is the part that does not.
    context.setStrokeColor(amber)
    context.setLineWidth(plateSide * arcStrokeFraction)
    context.setLineCap(.butt)
    context.addArc(
        center: centre,
        radius: plateSide * arcRadiusFraction,
        startAngle: -gapHalfAngle,
        endAngle: gapHalfAngle,
        clockwise: true
    )
    context.strokePath()

    // The tap.
    context.setFillColor(amber)
    context.addArc(
        center: centre,
        radius: plateSide * dotRadiusFraction,
        startAngle: 0,
        endAngle: 2 * .pi,
        clockwise: false
    )
    context.fillPath()

    return context.makeImage()!
}

// MARK: - Writing

let project = URL(filePath: #filePath)
    .deletingLastPathComponent()   // icon
    .deletingLastPathComponent()   // tools
    .deletingLastPathComponent()   // macos
let iconset = project.appending(path: "Resources/Clickify.iconset")

try? FileManager.default.removeItem(at: iconset)
try FileManager.default.createDirectory(at: iconset, withIntermediateDirectories: true)

/// `iconutil` recognises exactly these names, and silently ignores anything else in the folder.
let variants: [(name: String, size: Int)] = [
    ("icon_16x16", 16), ("icon_16x16@2x", 32),
    ("icon_32x32", 32), ("icon_32x32@2x", 64),
    ("icon_128x128", 128), ("icon_128x128@2x", 256),
    ("icon_256x256", 256), ("icon_256x256@2x", 512),
    ("icon_512x512", 512), ("icon_512x512@2x", 1024),
]

for variant in variants {
    let image = draw(size: variant.size)
    let url = iconset.appending(path: "\(variant.name).png")
    let destination = CGImageDestinationCreateWithURL(url as CFURL, "public.png" as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else { fatalError("could not write \(url.path())") }
}

let iconutil = Process()
iconutil.executableURL = URL(filePath: "/usr/bin/iconutil")
iconutil.arguments = [
    "--convert", "icns",
    "--output", project.appending(path: "Resources/Clickify.icns").path(),
    iconset.path(),
]
try iconutil.run()
iconutil.waitUntilExit()
guard iconutil.terminationStatus == 0 else { fatalError("iconutil failed") }

// The .iconset is a build product of this script, not a source: leaving it behind would invite
// somebody to edit a PNG by hand and have it silently overwritten on the next run.
try FileManager.default.removeItem(at: iconset)
print(project.appending(path: "Resources/Clickify.icns").path())
