// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "Clickify",
    platforms: [
        .macOS(.v14)
    ],
    products: [
        .executable(name: "Clickify", targets: ["Clickify"])
    ],
    targets: [
        .executableTarget(
            name: "Clickify",
            linkerSettings: [
                .linkedFramework("AppKit"),
                .linkedFramework("ApplicationServices"),
                .linkedFramework("Carbon"),
                .linkedFramework("ScreenCaptureKit"),
                .linkedFramework("Vision"),
                .linkedFramework("ServiceManagement")
            ]
        ),
        .testTarget(
            name: "ClickifyTests",
            dependencies: ["Clickify"]
        )
    ]
)
