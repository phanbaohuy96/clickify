import CoreGraphics
import Foundation
import Testing
@testable import Clickify

/// The **Locked application** gate for events carrying coordinates (EX-10).
///
/// These twenty lines are the only thing stopping a Scenario from firing into a stranger's window when the user
/// accidentally drags another window on top, so they have to fail on the safe side.
struct ClickRoutingPolicyTests {
    private let target: pid_t = 1234

    @Test func withoutALockedApplicationEveryPointIsAllowed() {
        // Simple mode locks no application: there is nothing to compare against, so nothing may be blocked.
        #expect(ClickRoutingPolicy.route(
            targetProcessIdentifier: nil,
            processIdentifierAtPoint: 999
        ) == .systemEventTap)
        #expect(ClickRoutingPolicy.route(
            targetProcessIdentifier: nil,
            processIdentifierAtPoint: nil
        ) == .systemEventTap)
    }

    @Test func aPointInsideTheLockedApplicationIsAllowed() {
        #expect(ClickRoutingPolicy.route(
            targetProcessIdentifier: target,
            processIdentifierAtPoint: target
        ) == .systemEventTap)
    }

    @Test func aPointOverAnotherApplicationIsBlocked() {
        #expect(ClickRoutingPolicy.route(
            targetProcessIdentifier: target,
            processIdentifierAtPoint: 5678
        ) == nil)
    }

    @Test func anUnidentifiablePointIsBlockedRatherThanAssumedSafe() {
        // Only fire when the owner can be determined. Clicking without knowing what is under the cursor is how
        // a Scenario ends up pressing something the user never expected.
        #expect(ClickRoutingPolicy.route(
            targetProcessIdentifier: target,
            processIdentifierAtPoint: nil
        ) == nil)
    }
}
