import Testing
import CoreGraphics
@testable import Clickify

@Test func acceptsValidSettings() throws {
    let result = SettingsValidator.validate(intervalText: "250", repeatText: "12")
    let settings = try result.get()

    #expect(settings == SimpleModeSettings(intervalMilliseconds: 250, repeatCount: 12))
}

@Test func rejectsIntervalsBelowMinimum() {
    let result = SettingsValidator.validate(intervalText: "9", repeatText: "1")

    #expect(result == .failure(.intervalOutOfRange))
}

@Test func rejectsNonNumericRepeatCount() {
    let result = SettingsValidator.validate(intervalText: "100", repeatText: "abc")

    #expect(result == .failure(.invalidRepeatCount))
}

@Test func applicationLockRequiresASelection() {
    let error = ApplicationLockValidator.validate(
        isEnabled: true,
        selectedBundleIdentifier: "",
        isApplicationRunning: false
    )

    #expect(error == .missingSelection)
}

@Test func applicationLockRejectsAnApplicationThatIsNotRunning() {
    let error = ApplicationLockValidator.validate(
        isEnabled: true,
        selectedBundleIdentifier: "com.example.Target",
        isApplicationRunning: false
    )

    #expect(error == .applicationNotRunning)
}

@Test func disabledApplicationLockDoesNotRequireASelection() {
    let error = ApplicationLockValidator.validate(
        isEnabled: false,
        selectedBundleIdentifier: "",
        isApplicationRunning: false
    )

    #expect(error == nil)
}

@Test func cursorTargetReadsThePositionAtResolutionTime() throws {
    nonisolated(unsafe) var cursor = CGPoint(x: 30, y: 40)
    let resolver = TargetResolver(currentCursorPoint: { cursor })

    #expect(try resolver.resolve(.cursor, anchorWindowFrame: nil) == CGPoint(x: 30, y: 40))

    // DM-19: resolved again on every repetition, so the sequence follows the user's hand.
    cursor = CGPoint(x: 11, y: 12)
    #expect(try resolver.resolve(.cursor, anchorWindowFrame: nil) == CGPoint(x: 11, y: 12))
}

@Test func screenPointTargetIgnoresTheCursor() throws {
    let resolver = TargetResolver(currentCursorPoint: { CGPoint(x: 30, y: 40) })

    #expect(try resolver.resolve(.screenPoint(x: 50, y: 60), anchorWindowFrame: nil) == CGPoint(x: 50, y: 60))
}

@Test func lockedApplicationUsesSystemRoutingOnlyWhenItOwnsThePoint() {
    #expect(
        ClickRoutingPolicy.route(
            targetProcessIdentifier: 100,
            processIdentifierAtPoint: 100
        ) == .systemEventTap
    )
    #expect(
        ClickRoutingPolicy.route(
            targetProcessIdentifier: 100,
            processIdentifierAtPoint: 200
        ) == nil
    )
}
