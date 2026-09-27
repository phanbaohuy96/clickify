import AppKit
import Foundation

enum ClickTargetMode: String, CaseIterable, Identifiable, Sendable {
    case cursor
    case fixedPoint

    var id: String { rawValue }
}

struct RunningApplicationOption: Identifiable, Hashable, Sendable {
    let bundleIdentifier: String
    let name: String

    var id: String { bundleIdentifier }

    @MainActor
    static func processIdentifier(forBundleIdentifier bundleIdentifier: String) -> pid_t? {
        guard !bundleIdentifier.isEmpty else { return nil }
        return NSWorkspace.shared.runningApplications.first {
            $0.bundleIdentifier == bundleIdentifier && !$0.isTerminated
        }?.processIdentifier
    }

    /// Running applications with a user interface, excluding Clickify itself.
    @MainActor
    static func current() -> [RunningApplicationOption] {
        var seenBundleIdentifiers = Set<String>()
        return NSWorkspace.shared.runningApplications
            .filter { application in
                application.activationPolicy == .regular
                    && !application.isTerminated
                    && application.processIdentifier != ProcessInfo.processInfo.processIdentifier
                    && application.bundleIdentifier != nil
            }
            .compactMap { application -> RunningApplicationOption? in
                guard let bundleIdentifier = application.bundleIdentifier,
                      seenBundleIdentifiers.insert(bundleIdentifier).inserted else { return nil }
                return RunningApplicationOption(
                    bundleIdentifier: bundleIdentifier,
                    name: application.localizedName ?? bundleIdentifier
                )
            }
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }
}

enum ApplicationLockValidationError: LocalizedError, Equatable {
    case missingSelection
    case applicationNotRunning

    var errorDescription: String? {
        switch self {
        case .missingSelection:
            return localized(.errorLockMissingSelection)
        case .applicationNotRunning:
            return localized(.errorLockNotRunning)
        }
    }
}

enum ApplicationLockValidator {
    static func validate(
        isEnabled: Bool,
        selectedBundleIdentifier: String,
        isApplicationRunning: Bool
    ) -> ApplicationLockValidationError? {
        guard isEnabled else { return nil }
        guard !selectedBundleIdentifier.isEmpty else { return .missingSelection }
        guard isApplicationRunning else { return .applicationNotRunning }
        return nil
    }
}

struct SimpleModeSettings: Equatable, Sendable {
    let intervalMilliseconds: Int
    let repeatCount: Int

    /// LC-9: the limits are **arguments** to the message, never digits written inside a translation. Baked in,
    /// changing them here would leave five languages stating a number the code no longer enforces.
    static let intervalRange = 10...3_600_000
    static let repeatRange = 1...1_000_000
}

enum SettingsValidationError: LocalizedError, Equatable {
    case invalidInterval
    case intervalOutOfRange
    case invalidRepeatCount
    case repeatCountOutOfRange

    var errorDescription: String? {
        switch self {
        case .invalidInterval:
            return localized(.errorIntervalNotAnInteger)
        case .intervalOutOfRange:
            return localized(
                .errorIntervalOutOfRange,
                localizedNumber(SimpleModeSettings.intervalRange.lowerBound),
                localizedNumber(SimpleModeSettings.intervalRange.upperBound)
            )
        case .invalidRepeatCount:
            return localized(.errorRepeatNotAnInteger)
        case .repeatCountOutOfRange:
            return localized(
                .errorRepeatOutOfRange,
                localizedNumber(SimpleModeSettings.repeatRange.lowerBound),
                localizedNumber(SimpleModeSettings.repeatRange.upperBound)
            )
        }
    }
}

enum SettingsValidator {
    static func validate(
        intervalText: String,
        repeatText: String
    ) -> Result<SimpleModeSettings, SettingsValidationError> {
        guard let interval = Int(intervalText) else {
            return .failure(.invalidInterval)
        }
        guard SimpleModeSettings.intervalRange.contains(interval) else {
            return .failure(.intervalOutOfRange)
        }
        guard let repeatCount = Int(repeatText) else {
            return .failure(.invalidRepeatCount)
        }
        guard SimpleModeSettings.repeatRange.contains(repeatCount) else {
            return .failure(.repeatCountOutOfRange)
        }

        return .success(
            SimpleModeSettings(
                intervalMilliseconds: interval,
                repeatCount: repeatCount
            )
        )
    }
}
