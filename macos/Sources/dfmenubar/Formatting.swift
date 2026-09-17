import Foundation

/// Human-readable, scale-appropriate formatting for the stats card — picks
/// the coarsest unit that keeps the number readable, growing from minutes
/// up through years, and from MB up through PB, as the totals grow over
/// weeks/months of use.
enum Formatting {
    static func duration(minutes totalMinutes: Int) -> String {
        if totalMinutes < 60 {
            return "\(totalMinutes)m"
        }
        let hours = totalMinutes / 60
        if hours < 24 {
            let mins = totalMinutes % 60
            return mins > 0 ? "\(hours)h \(mins)m" : "\(hours)h"
        }
        let days = hours / 24
        if days < 30 {
            let remHours = hours % 24
            return remHours > 0 ? "\(days)d \(remHours)h" : "\(days)d"
        }
        let months = days / 30
        if months < 12 {
            let remDays = days % 30
            return remDays > 0 ? "\(months)mo \(remDays)d" : "\(months)mo"
        }
        let years = days / 365
        let remMonths = (days % 365) / 30
        return remMonths > 0 ? "\(years)y \(remMonths)mo" : "\(years)y"
    }

    static func dataSize(mb totalMB: Double) -> String {
        let kb1024 = 1024.0
        if totalMB < kb1024 {
            return String(format: "%.1f MB", totalMB)
        }
        let gb = totalMB / kb1024
        if gb < kb1024 {
            return String(format: "%.2f GB", gb)
        }
        let tb = gb / kb1024
        if tb < kb1024 {
            return String(format: "%.2f TB", tb)
        }
        let pb = tb / kb1024
        return String(format: "%.2f PB", pb)
    }
}
