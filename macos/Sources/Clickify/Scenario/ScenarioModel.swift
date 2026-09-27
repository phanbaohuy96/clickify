import CoreGraphics
import Foundation

/// The valid range of every numeric field in a Scenario (DM-2, DM-4, DM-6, DM-7).
///
/// Values read from disk are clamped into these ranges rather than rejected (DM-20).
enum ScenarioLimits {
    static let runCount = 1...1_000_000
    static let stepRepeatCount = 1...1_000_000
    static let delayMilliseconds = 0...3_600_000
    static let clickCount = 1...10
    static let holdMilliseconds = 0...60_000
    static let scrollDelta = -10_000...10_000

    /// The minimum yield between two consecutive events (SF-8).
    static let minimumEventGapMilliseconds = 10

    /// The gap between two press/release pairs inside one click Action (EX-17).
    static let interClickGapMilliseconds = 30

    /// The maximum number of UTF-16 units sent in one key pair when typing a string (EX-24).
    static let typingChunkUTF16Units = 20

    /// The gap between two key pairs when typing a string key by key (EX-26).
    ///
    /// About 40 keys per second: faster than a person types, comfortably slower than a frame. Games drop input
    /// that arrives inside one frame, and a dropped key is another silent loss of characters — the very thing
    /// `EX-24` exists to stop. ADR-0007's measurements of 30 ms and 60 ms are **not** evidence here: they were
    /// taken on the payload mechanism, which fails for a different reason.
    static let perKeyGapMilliseconds = 25

    /// How many intermediate points a drag has (EX-20). Many applications ignore a drag if the cursor
    /// jumps straight from start to end with nothing in between.
    static let dragIntermediateSteps = 24
    static let dragStepGapMilliseconds = 8

    /// How long to wait at most for the Locked application to come forward before typing (SF-4).
    static let activationTimeoutMilliseconds = 500

    static let recognitionThreshold = 0.50...1.0
    static let recognitionWaitMilliseconds = 0...600_000
    /// The minimum retry cadence when resolving a Template Target, so as not to burn CPU (EX-8).
    static let recognitionRetryFloorMilliseconds = 150
    static let defaultRecognitionThreshold = 0.90
}

/// What to do when the timeout expires and the target still has not been found (DM-16).
enum TimeoutBehaviour: String, CaseIterable, Identifiable, Codable, Sendable {
    case stopScenario
    case skipStep

    var id: String { rawValue }
}

/// The part of the screen narrowed down to search for the target (DM-17).
///
/// Never requires a **Locked application**: see [ADR-0006]. The window-relative form is more robust and so
/// is preferred when drawing, but if it cannot be resolved at run time it falls back to the default scope
/// instead of reporting an error.
enum SearchRegion: Equatable, Sendable {
    case screenRect(x: Double, y: Double, width: Double, height: Double)
    case windowRelative(
        corner: WindowCorner,
        dx: Double,
        dy: Double,
        width: Double,
        height: Double
    )
}

struct RecognitionSettings: Equatable, Sendable {
    var threshold: Double
    var searchRegion: SearchRegion?
    var waitMilliseconds: Int
    var onTimeout: TimeoutBehaviour

    init(
        threshold: Double = ScenarioLimits.defaultRecognitionThreshold,
        searchRegion: SearchRegion? = nil,
        waitMilliseconds: Int = 0,
        onTimeout: TimeoutBehaviour = .stopScenario
    ) {
        self.threshold = threshold.clamped(to: ScenarioLimits.recognitionThreshold)
        self.searchRegion = searchRegion
        self.waitMilliseconds = waitMilliseconds
            .clamped(to: ScenarioLimits.recognitionWaitMilliseconds)
        self.onTimeout = onTimeout
    }
}

/// The corner of the Anchor window a relative Target hangs off (DM-13).
///
/// The corner is chosen automatically when the point is recorded — the corner nearest the point — so a button
/// in the bottom-right still lands correctly when the window is enlarged, which a fixed top-left anchor cannot do.
enum WindowCorner: String, CaseIterable, Identifiable, Codable, Sendable {
    case topLeft
    case topRight
    case bottomLeft
    case bottomRight

    var id: String { rawValue }
}

enum KeyModifier: String, CaseIterable, Identifiable, Codable, Sendable {
    case command
    case option
    case control
    case shift

    var id: String { rawValue }
}

/// A key combination. Key names are stored as readable strings, not numeric codes (DM-21).
struct KeyStroke: Equatable, Codable, Sendable {
    var key: String
    var modifiers: [KeyModifier]

    init(key: String, modifiers: [KeyModifier] = []) {
        self.key = key
        // Normalised so two identical combinations always compare equal and always write the same JSON.
        self.modifiers = Array(Set(modifiers)).sorted { $0.rawValue < $1.rawValue }
    }
}

/// How many times a Scenario runs (DM-2). A Step always uses a plain integer.
enum RunCount: Equatable, Sendable {
    case times(Int)
    case untilStopped

    var totalIterations: Int? {
        switch self {
        case let .times(count): return count
        case .untilStopped: return nil
        }
    }
}

enum MouseButton: String, CaseIterable, Identifiable, Codable, Sendable {
    case left
    case right
    case center

    var id: String { rawValue }
}

/// What a Step does, kept separate from where it does it (ADR-0002).
enum StepAction: Equatable, Sendable {
    case click(button: MouseButton, count: Int, holdMilliseconds: Int)
    case scroll(deltaX: Int, deltaY: Int)
    case move
    /// Press at the Step's Target, drag through the intermediate points, release at `destination` (DM-9).
    case drag(button: MouseButton, destination: StepTarget)
    case typeText(String)
    case pressKey(KeyStroke)

    /// Keyboard events carry no coordinates, so `EX-10` cannot protect them; see `SF-4`.
    var isKeyboard: Bool {
        switch self {
        case .typeText, .pressKey: return true
        case .click, .scroll, .move, .drag: return false
        }
    }
}

/// Where an Action happens, resolved to coordinates only at run time (DM-11, DM-12).
indirect enum StepTarget: Equatable, Sendable {
    case cursor
    case screenPoint(x: Double, y: Double)
    /// Offset from one corner of the Anchor window (DM-13). Only resolvable with a Locked application (DM-18).
    case windowRelative(corner: WindowCorner, dx: Double, dy: Double)
    /// The centre of the Template found on screen (DM-14).
    case template(name: String, settings: RecognitionSettings)
    /// The centre of the piece of text found on screen (DM-15).
    case text(String, settings: RecognitionSettings)

    /// Only `windowRelative` **requires** an Anchor window. Template and text do not —
    /// see [ADR-0006].
    var needsAnchorWindow: Bool {
        if case .windowRelative = self { return true }
        return false
    }

    /// Recognition settings, if this Target has to go looking for its target.
    var recognitionSettings: RecognitionSettings? {
        switch self {
        case let .template(_, settings), let .text(_, settings): return settings
        case .cursor, .screenPoint, .windowRelative: return nil
        }
    }

    /// The Template file name this Target needs, if any.
    var templateName: String? {
        if case let .template(name, _) = self { return name }
        return nil
    }
}

struct Step: Identifiable, Equatable, Sendable {
    var id: UUID
    var action: StepAction
    var target: StepTarget
    var repeatCount: Int
    var delayMillisecondsAfter: Int

    init(
        id: UUID = UUID(),
        action: StepAction,
        target: StepTarget,
        repeatCount: Int = 1,
        delayMillisecondsAfter: Int = 100
    ) {
        self.id = id
        self.action = action
        self.target = target
        self.repeatCount = repeatCount.clamped(to: ScenarioLimits.stepRepeatCount)
        self.delayMillisecondsAfter = delayMillisecondsAfter
            .clamped(to: ScenarioLimits.delayMilliseconds)
    }
}

struct LockedApplication: Equatable, Codable, Sendable {
    var bundleIdentifier: String
    var name: String
    /// The title of the **Anchor window** at recording time, if known.
    ///
    /// A bundle id alone cannot name a window: two Chrome profiles are two processes sharing one bundle id,
    /// and one process has as many windows as it likes. Without the title, replay binds the Scenario to
    /// "the first process matching the bundle id, and whichever window it has focused" — that is, whatever
    /// window happens to be in front. Found while preparing `D10` of the manual tests: a recording made on a
    /// scratch window nearly replayed onto the user's logged-in window.
    ///
    /// It is only a **preference**, not a hard condition: window titles change constantly (a different file is
    /// opened, a tab is switched), so a title that no longer matches falls back to the old way instead of refusing to run.
    var windowTitle: String?

    init(bundleIdentifier: String, name: String, windowTitle: String? = nil) {
        self.bundleIdentifier = bundleIdentifier
        self.name = name
        self.windowTitle = windowTitle
    }
}

extension Step {
    /// Every Target this Step touches, including a drag's destination.
    var targets: [StepTarget] {
        if case let .drag(_, destination) = action { return [target, destination] }
        return [target]
    }
}

struct Scenario: Identifiable, Equatable, Sendable {
    /// The name given to a Scenario whose `scenario.json` has no `name` field.
    ///
    /// LC-11: deliberately **not** translated. This is a repair value, not a name the user chose, and it is
    /// written back to disk on the next save — so translating it would let an interface setting rewrite user
    /// data, and would give the same damaged file a different name on every machine.
    static let repairedName = "Untitled scenario"

    var id: UUID
    var name: String
    var steps: [Step]
    var runCount: RunCount
    var lockedApplication: LockedApplication?

    init(
        id: UUID = UUID(),
        name: String,
        steps: [Step] = [],
        runCount: RunCount = .times(1),
        lockedApplication: LockedApplication? = nil
    ) {
        self.id = id
        self.name = name
        self.steps = steps
        self.runCount = runCount
        self.lockedApplication = lockedApplication
    }

    /// DM-18: a window-relative Target cannot be resolved without a Locked application.
    var requiresLockedApplication: Bool {
        steps.contains { $0.targets.contains(where: \.needsAnchorWindow) }
    }

    /// The names of the Template files this Scenario uses.
    var templateNames: Set<String> {
        Set(steps.flatMap(\.targets).compactMap(\.templateName))
    }
}

extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
