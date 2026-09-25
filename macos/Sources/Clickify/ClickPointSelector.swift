import AppKit

/// Full-screen transparent overlays that capture one click without passing it
/// through to the underlying application.
@MainActor
final class ClickPointSelector {
    private var windows: [NSWindow] = []
    private var keyMonitor: Any?
    private var completion: ((CGPoint?) -> Void)?

    func start(completion: @escaping (CGPoint?) -> Void) {
        self.completion = completion
        NSApp.activate(ignoringOtherApps: true)

        for screen in NSScreen.screens {
            let window = PointSelectionWindow(
                contentRect: screen.frame,
                styleMask: .borderless,
                backing: .buffered,
                defer: false
            )
            window.setFrame(screen.frame, display: true)
            window.level = .screenSaver
            window.backgroundColor = .clear
            window.isOpaque = false
            window.hasShadow = false
            window.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
            window.contentView = PointSelectionView(
                onSelect: { [weak self] point in self?.finish(with: point) },
                onCancel: { [weak self] in self?.finish(with: nil) }
            )
            window.orderFrontRegardless()
            windows.append(window)
        }

        windows.first?.makeKey()
        keyMonitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { [weak self] event in
            if event.keyCode == 53 {
                self?.finish(with: nil)
                return nil
            }
            return event
        }
    }

    private func finish(with point: CGPoint?) {
        guard let completion else { return }
        self.completion = nil

        if let keyMonitor {
            NSEvent.removeMonitor(keyMonitor)
            self.keyMonitor = nil
        }

        for window in windows {
            window.orderOut(nil)
            window.contentView = nil
        }
        windows.removeAll()
        completion(point)
    }

}

private final class PointSelectionWindow: NSWindow {
    override var canBecomeKey: Bool { true }
}

private final class PointSelectionView: NSView {
    private let onSelect: (CGPoint) -> Void
    private let onCancel: () -> Void

    init(onSelect: @escaping (CGPoint) -> Void, onCancel: @escaping () -> Void) {
        self.onSelect = onSelect
        self.onCancel = onCancel
        super.init(frame: .zero)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override var acceptsFirstResponder: Bool { true }

    override func resetCursorRects() {
        addCursorRect(bounds, cursor: .crosshair)
    }

    override func mouseDown(with event: NSEvent) {
        let point = event.cgEvent?.location ?? CGEvent(source: nil)?.location ?? .zero
        onSelect(point)
    }

    override func keyDown(with event: NSEvent) {
        if event.keyCode == 53 {
            onCancel()
        } else {
            super.keyDown(with: event)
        }
    }

    override func draw(_ dirtyRect: NSRect) {
        NSColor.black.withAlphaComponent(0.10).setFill()
        dirtyRect.fill()

        let text = localized(.overlayPickPoint)
        let attributes: [NSAttributedString.Key: Any] = [
            .font: NSFont.systemFont(ofSize: 18, weight: .semibold),
            .foregroundColor: NSColor.white
        ]
        let attributedText = NSAttributedString(string: text, attributes: attributes)
        let textSize = attributedText.size()
        let panelRect = NSRect(
            x: bounds.midX - textSize.width / 2 - 22,
            y: bounds.midY - textSize.height / 2 - 14,
            width: textSize.width + 44,
            height: textSize.height + 28
        )

        NSColor.black.withAlphaComponent(0.72).setFill()
        NSBezierPath(roundedRect: panelRect, xRadius: 12, yRadius: 12).fill()
        attributedText.draw(
            at: NSPoint(
                x: panelRect.midX - textSize.width / 2,
                y: panelRect.midY - textSize.height / 2
            )
        )
    }
}
