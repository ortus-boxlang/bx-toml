//! Reads TOML text into a value tree, following the JVM module's `TomlParser` and `BoxLangTomlConstructor`.

use crate::model::{DateTimeValue, Failure, Kind, Result, Value};
use crate::zone::{local_to_instant, offset_name};
use chrono::{NaiveDate, NaiveDateTime, NaiveTime};

fn fail(message: impl Into<String>) -> Failure {
    Failure::new(Kind::Parse, message)
}

/// Parses a TOML document. A leading byte order mark is dropped and CRLF line endings become LF, as on the JVM,
/// so the same text reads the same on every platform. TOML 1.0 and the draft 1.1 setting read alike.
pub fn parse(text: &str) -> Result<Value> {
    let text = text.strip_prefix('\u{feff}').unwrap_or(text);
    // One byte order mark may start the file. A second one is not TOML, though the parser below would skip it.
    if text.starts_with('\u{feff}') {
        return Err(fail("TOML parse error(s):\n  unexpected byte order mark (line 1, column 1)"));
    }
    let normalized;
    let text = if text.contains('\r') {
        normalized = text.replace("\r\n", "\n");
        normalized.as_str()
    } else {
        text
    };
    let table: toml::Table = toml::from_str(text).map_err(|error| {
        let (line, column) = error.span().map_or((1, 1), |span| position(text, span.start));
        fail(format!("TOML parse error(s):\n  {} (line {line}, column {column})", error.message()))
    })?;
    convert_table(table)
}

/// 1-based line and column of a byte offset.
fn position(text: &str, offset: usize) -> (usize, usize) {
    let before = &text[..offset.min(text.len())];
    let line = before.matches('\n').count() + 1;
    let column = before.rsplit('\n').next().map_or(0, |last| last.chars().count()) + 1;
    (line, column)
}

fn convert_table(table: toml::Table) -> Result<Value> {
    table.into_iter().map(|(key, value)| Ok((key, convert(value)?))).collect::<Result<Vec<_>>>().map(Value::Table)
}

fn convert(value: toml::Value) -> Result<Value> {
    Ok(match value {
        toml::Value::String(s) => Value::Str(s),
        toml::Value::Integer(i) => Value::Int(i),
        toml::Value::Float(f) => Value::Float(f),
        toml::Value::Boolean(b) => Value::Bool(b),
        toml::Value::Datetime(dt) => Value::DateTime(convert_datetime(&dt)?),
        toml::Value::Array(items) => Value::Array(items.into_iter().map(convert).collect::<Result<_>>()?),
        toml::Value::Table(table) => convert_table(table)?,
    })
}

/// A TOML date or time as a point in time. An offset date-time keeps its offset. A local date-time or date reads in the
/// system zone, and a local time on 1970-01-01, as the JVM module does.
fn convert_datetime(value: &toml::value::Datetime) -> Result<DateTimeValue> {
    let date = match value.date {
        Some(d) => NaiveDate::from_ymd_opt(i32::from(d.year), u32::from(d.month), u32::from(d.day))
            .ok_or_else(|| fail(format!("Invalid date: {}", value)))?,
        None => NaiveDate::from_ymd_opt(1970, 1, 1).expect("valid date"),
    };
    let time = match value.time {
        Some(t) => NaiveTime::from_hms_nano_opt(u32::from(t.hour), u32::from(t.minute), u32::from(t.second), t.nanosecond)
            .ok_or_else(|| fail(format!("Invalid time: {}", value)))?,
        None => NaiveTime::MIN,
    };
    let wall = NaiveDateTime::new(date, time);
    Ok(match value.offset {
        Some(toml::value::Offset::Z) => DateTimeValue { seconds: wall.and_utc().timestamp(), nanos: time_nanos(&wall), zone: "UTC".into() },
        Some(toml::value::Offset::Custom { minutes }) => DateTimeValue {
            seconds: wall.and_utc().timestamp() - i64::from(minutes) * 60,
            nanos: time_nanos(&wall),
            zone: offset_name(i32::from(minutes)),
        },
        None => DateTimeValue { seconds: local_to_instant(wall)?, nanos: time_nanos(&wall), zone: String::new() },
    })
}

fn time_nanos(wall: &NaiveDateTime) -> u32 {
    use chrono::Timelike;
    wall.nanosecond()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn get<'a>(value: &'a Value, key: &str) -> &'a Value {
        match value {
            Value::Table(entries) => &entries.iter().find(|(k, _)| k == key).unwrap_or_else(|| panic!("no key {key}")).1,
            other => panic!("not a table: {other:?}"),
        }
    }

    #[test]
    fn scalars_tables_and_arrays_keep_order_and_case() {
        let doc = parse("Zed = 1\naLpha = \"x\"\n[Owner]\nname = \"Ada\"\nborn = 1815\nratio = 0.5\nok = true\n[[Servers]]\nn = 1\n[[Servers]]\nn = 2\n").unwrap();
        let Value::Table(entries) = &doc else { panic!() };
        assert_eq!(entries.iter().map(|(k, _)| k.as_str()).collect::<Vec<_>>(), ["Zed", "aLpha", "Owner", "Servers"]);
        assert_eq!(get(get(&doc, "Owner"), "born"), &Value::Int(1815));
        assert_eq!(get(get(&doc, "Owner"), "ratio"), &Value::Float(0.5));
        assert_eq!(get(get(&doc, "Owner"), "ok"), &Value::Bool(true));
        let Value::Array(servers) = get(&doc, "Servers") else { panic!() };
        assert_eq!(servers.len(), 2);
    }

    #[test]
    fn a_bom_and_crlf_are_normalized() {
        let doc = parse("\u{feff}a = \"x\"\r\nb = \"\"\"l1\r\nl2\"\"\"\r\n").unwrap();
        assert_eq!(get(&doc, "a"), &Value::Str("x".into()));
        assert_eq!(get(&doc, "b"), &Value::Str("l1\nl2".into()));
    }

    #[test]
    fn only_one_leading_byte_order_mark_is_allowed() {
        assert!(parse("\u{feff}a = 1\n").is_ok());
        assert!(parse("\u{feff}\u{feff}a = 1\n").is_err());
    }

    #[test]
    fn offset_date_times_keep_their_instant_and_offset() {
        let doc = parse("z = 2024-03-05T06:07:08.5Z\no = 2024-03-05T06:07:08+05:30\n").unwrap();
        assert_eq!(get(&doc, "z"), &Value::DateTime(DateTimeValue { seconds: 1_709_618_828, nanos: 500_000_000, zone: "UTC".into() }));
        assert_eq!(get(&doc, "o"), &Value::DateTime(DateTimeValue { seconds: 1_709_599_028, nanos: 0, zone: "+05:30".into() }));
    }

    #[test]
    fn local_values_read_in_the_system_zone() {
        let doc = parse("dt = 2024-03-05T06:07:08\nd = 2024-03-05\nt = 06:07:08\n").unwrap();
        let wall = |key: &str| match get(&doc, key) {
            Value::DateTime(dt) => {
                assert_eq!(dt.zone, "");
                chrono::DateTime::from_timestamp(dt.seconds, dt.nanos).unwrap().with_timezone(&chrono::Local).naive_local().to_string()
            }
            other => panic!("{other:?}"),
        };
        assert_eq!(wall("dt"), "2024-03-05 06:07:08");
        assert_eq!(wall("d"), "2024-03-05 00:00:00");
        assert_eq!(wall("t"), "1970-01-01 06:07:08");
    }

    #[test]
    fn errors_say_where() {
        let error = parse("a = 1\nb = = 2\n").unwrap_err();
        assert_eq!(error.kind, Kind::Parse);
        assert!(error.message.starts_with("TOML parse error(s):\n  "), "{}", error.message);
        assert!(error.message.contains("(line 2, column"), "{}", error.message);
        assert!(parse("a = 1\na = 2\n").is_err());
    }
}
