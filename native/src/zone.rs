//! Time zones for the date and time values a TOML document can hold.

use crate::model::{Failure, Kind, Result};
use chrono::{DateTime, Local, NaiveDateTime, Offset, TimeZone, Utc};

/// The offset from UTC, in seconds, that `zone` has at `instant` (Unix seconds).
///
/// An empty zone is the system zone. `UTC`, `Z`, `GMT` and `+HH:MM` style offsets are read directly; anything
/// else is an IANA name such as `America/New_York`.
pub fn offset_seconds(zone: &str, instant: i64) -> Result<i32> {
    let utc = DateTime::<Utc>::from_timestamp(instant, 0)
        .ok_or_else(|| Failure::new(Kind::Serialization, format!("Date out of range: {instant} seconds")))?;
    if zone.is_empty() {
        return Ok(Local.offset_from_utc_datetime(&utc.naive_utc()).fix().local_minus_utc());
    }
    if ["utc", "z", "gmt", "ut"].iter().any(|name| zone.eq_ignore_ascii_case(name)) {
        return Ok(0);
    }
    if zone.starts_with(['+', '-']) {
        return parse_offset(zone).ok_or_else(|| Failure::new(Kind::Serialization, format!("Unknown time zone: {zone}")));
    }
    let tz: chrono_tz::Tz = zone
        .parse()
        .map_err(|_| Failure::new(Kind::Serialization, format!("Unknown time zone: {zone}")))?;
    Ok(tz.offset_from_utc_datetime(&utc.naive_utc()).fix().local_minus_utc())
}

/// `+HH`, `+HH:MM`, `+HHMM` or `+HH:MM:SS`, every part two digits.
fn parse_offset(text: &str) -> Option<i32> {
    let sign = if text.starts_with('-') { -1 } else { 1 };
    let body = &text[1..];
    let parts: Vec<&str> = if body.contains(':') {
        body.split(':').collect()
    } else {
        body.as_bytes().chunks(2).map(|chunk| std::str::from_utf8(chunk).unwrap_or("")).collect()
    };
    if parts.is_empty() || parts.len() > 3 || parts.iter().any(|p| p.len() != 2 || !p.chars().all(|c| c.is_ascii_digit())) {
        return None;
    }
    let number = |index: usize| parts.get(index).map_or(Some(0), |p| p.parse::<i32>().ok());
    let (hours, minutes, seconds) = (number(0)?, number(1)?, number(2)?);
    if hours > 18 || minutes > 59 || seconds > 59 {
        return None;
    }
    Some(sign * (hours * 3600 + minutes * 60 + seconds))
}

/// The zone name for a TOML offset: `UTC` for `Z`, otherwise `+HH:MM`.
pub fn offset_name(minutes: i32) -> String {
    if minutes == 0 {
        return "UTC".into();
    }
    let sign = if minutes < 0 { '-' } else { '+' };
    format!("{sign}{:02}:{:02}", minutes.abs() / 60, minutes.abs() % 60)
}

/// Unix seconds for a wall-clock time in the system zone. A time skipped by a clock change moves forward an hour.
pub fn local_to_instant(wall: NaiveDateTime) -> Result<i64> {
    let resolve = |wall: NaiveDateTime| Local.from_local_datetime(&wall).earliest().map(|dt| dt.timestamp());
    resolve(wall)
        .or_else(|| resolve(wall + chrono::Duration::hours(1)))
        .ok_or_else(|| Failure::new(Kind::Parse, format!("Local time does not exist in this time zone: {wall}")))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fixed_offsets_and_names_resolve() {
        assert_eq!(offset_seconds("UTC", 0).unwrap(), 0);
        assert_eq!(offset_seconds("z", 0).unwrap(), 0);
        assert_eq!(offset_seconds("+05:30", 0).unwrap(), 19800);
        assert_eq!(offset_seconds("-0800", 0).unwrap(), -28800);
        assert_eq!(offset_seconds("+02", 0).unwrap(), 7200);
        // 2024-01-15 is winter and 2024-07-15 summer in New York.
        assert_eq!(offset_seconds("America/New_York", 1_705_276_800).unwrap(), -18000);
        assert_eq!(offset_seconds("America/New_York", 1_721_001_600).unwrap(), -14400);
    }

    #[test]
    fn unknown_zones_fail() {
        assert!(offset_seconds("Mars/Olympus", 0).is_err());
        assert!(offset_seconds("+25:00", 0).is_err());
        assert!(offset_seconds("+1:2", 0).is_err());
    }

    #[test]
    fn names_for_offsets() {
        assert_eq!(offset_name(0), "UTC");
        assert_eq!(offset_name(330), "+05:30");
        assert_eq!(offset_name(-480), "-08:00");
    }
}
