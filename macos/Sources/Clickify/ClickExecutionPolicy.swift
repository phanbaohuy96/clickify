import CoreGraphics
import Foundation

enum ClickEventRoute: Equatable {
    case systemEventTap
}

/// Decides whether an event with coordinates may be emitted, based on the Locked application (EX-10).
///
/// Applies only to events **with coordinates**. Keyboard events have none, so they are protected
/// another way; see `SF-4`.
enum ClickRoutingPolicy {
    static func route(
        targetProcessIdentifier: pid_t?,
        processIdentifierAtPoint: pid_t?
    ) -> ClickEventRoute? {
        guard let targetProcessIdentifier else { return .systemEventTap }
        guard processIdentifierAtPoint == targetProcessIdentifier else { return nil }
        return .systemEventTap
    }
}
