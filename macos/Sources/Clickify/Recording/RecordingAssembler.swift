import CoreGraphics
import Foundation

/// Builds a **Scenario** out of the Steps that were inferred (RC-13, RC-14).
///
/// A pure function, split off from event capture: this is where it is decided whether a recording is usable
/// straight away or forces the user to fix every step by hand, so it has to be verifiable.
enum RecordingAssembler {
    /// Why a finished recording is not quite usable as it is (RC-14).
    ///
    /// LC-6: an enum rather than a sentence, so the warning is translated when it is drawn and not when the
    /// recording ended.
    enum Warning: Equatable {
        case spansSeveralApplications(Int)
        case someStepsAreAbsolute(absolute: Int, total: Int)

        var text: String {
            switch self {
            case let .spansSeveralApplications(count):
                return localized(.recordWarningMultipleApplications, count)
            case let .someStepsAreAbsolute(absolute, total):
                return localized(.recordWarningAbsoluteSteps, absolute, total)
            }
        }
    }

    struct Result: Equatable {
        var scenario: Scenario
        /// The warning shown after recording ends; `nil` means the recording is usable as it is.
        var warning: Warning?
    }

    /// The single process the whole session touched, or `nil` if it spans several applications.
    ///
    /// One Step whose process could not be determined disqualifies the whole session: raising Targets to
    /// window-relative on a guess is worse than keeping absolute coordinates and saying so plainly.
    static func singleProcessIdentifier(in steps: [RecordedStep]) -> pid_t? {
        guard !steps.isEmpty, steps.allSatisfy({ $0.processIdentifier != nil }) else { return nil }
        let identifiers = Set(steps.compactMap(\.processIdentifier))
        return identifiers.count == 1 ? identifiers.first : nil
    }

    static func scenario(
        named name: String,
        from recorded: [RecordedStep],
        lockedApplication: LockedApplication?,
        scenarioIdentifier: UUID = UUID()
    ) -> Result {
        var anchoredCount = 0
        let steps = recorded.map { step -> Step in
            let target = convert(step.location, frame: step.windowFrame, isLocked: lockedApplication != nil)
            if target.needsAnchorWindow { anchoredCount += 1 }

            var action = step.action
            if case let .drag(button, _) = step.action, let endLocation = step.endLocation {
                action = .drag(
                    button: button,
                    destination: convert(
                        endLocation,
                        frame: step.windowFrame,
                        isLocked: lockedApplication != nil
                    )
                )
            }

            return Step(
                action: action,
                target: target,
                repeatCount: 1,
                delayMillisecondsAfter: step.delayMillisecondsAfter
            )
        }

        let scenario = Scenario(
            id: scenarioIdentifier,
            name: name,
            steps: steps,
            runCount: .times(1),
            lockedApplication: lockedApplication
        )

        return Result(scenario: scenario, warning: warning(for: recorded, anchoredCount: anchoredCount))
    }

    private static func convert(
        _ point: CGPoint,
        frame: CGRect?,
        isLocked: Bool
    ) -> StepTarget {
        // RC-13: only raise to relative when there is both a Locked application and a window frame read while recording.
        guard isLocked, let frame, frame.width > 0, frame.height > 0 else {
            return .screenPoint(x: point.x, y: point.y)
        }
        let offset = WindowAnchor.offset(for: point, in: frame)
        return .windowRelative(corner: offset.corner, dx: offset.dx, dy: offset.dy)
    }

    private static func warning(for recorded: [RecordedStep], anchoredCount: Int) -> Warning? {
        guard !recorded.isEmpty else { return nil }

        let identifiers = Set(recorded.compactMap(\.processIdentifier))
        if identifiers.count > 1 {
            // RC-14: say so plainly rather than letting the user find out when the scenario fires wide.
            return .spansSeveralApplications(identifiers.count)
        }
        if anchoredCount < recorded.count {
            return .someStepsAreAbsolute(absolute: recorded.count - anchoredCount, total: recorded.count)
        }
        return nil
    }
}
