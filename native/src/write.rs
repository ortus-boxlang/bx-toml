//! Writes a value tree as TOML text, following the JVM module's `TomlWriter`: plain `key = value` lines first, then
//! `[tables]`, then `[[arrays of tables]]`, at every level; array of tables only when every element is a table.

use crate::model::{DateTimeValue, Failure, Kind, Result, Value};
use crate::zone::offset_seconds;
use chrono::{DateTime, Datelike, NaiveDate, Timelike};

#[derive(Debug, Clone)]
pub struct WriteOptions {
    /// Alphabetize keys, ignoring case, instead of keeping the incoming order.
    pub sort_keys: bool,
    /// Spaces of indentation per table level for `key = value` lines.
    pub indent: usize,
    /// `auto`, `offset-datetime`, `local-datetime`, `local-date` or `local-time`.
    pub date_time_style: String,
}

impl Default for WriteOptions {
    fn default() -> Self {
        Self { sort_keys: false, indent: 2, date_time_style: "auto".into() }
    }
}

fn fail(message: impl Into<String>) -> Failure {
    Failure::new(Kind::Serialization, message)
}

/// The TOML document for `content`, whose root must be a table.
pub fn write(content: &Value, options: &WriteOptions) -> Result<String> {
    let Value::Table(entries) = content else {
        return Err(fail(format!("TOML documents must have a struct at the root; received {}", type_name(content))));
    };
    let mut out = String::new();
    write_table(entries, &mut Vec::new(), &mut out, options)?;
    // The blank line written before the first header is not part of the document.
    Ok(out.strip_prefix('\n').map(str::to_owned).unwrap_or(out))
}

fn write_table(entries: &[(String, Value)], path: &mut Vec<String>, out: &mut String, options: &WriteOptions) -> Result<()> {
    let mut entries: Vec<&(String, Value)> = entries.iter().filter(|(_, value)| !matches!(value, Value::Null)).collect();
    if options.sort_keys {
        entries.sort_by_cached_key(|(key, _)| key.to_lowercase());
    }
    let (mut scalars, mut tables, mut table_arrays) = (Vec::new(), Vec::new(), Vec::new());
    for entry in entries {
        match &entry.1 {
            Value::Table(_) => tables.push(entry),
            value if is_table_array(value) => table_arrays.push(entry),
            _ => scalars.push(entry),
        }
    }
    let pad = " ".repeat(options.indent * path.len());
    for (key, value) in scalars {
        out.push_str(&format!("{pad}{} = {}\n", format_key(key), format_value(value, options)?));
    }
    for (key, value) in tables {
        path.push(key.clone());
        out.push_str(&format!("\n[{}]\n", format_path(path)));
        if let Value::Table(inner) = value {
            write_table(inner, path, out, options)?;
        }
        path.pop();
    }
    for (key, value) in table_arrays {
        path.push(key.clone());
        if let Value::Array(items) = value {
            for item in items {
                out.push_str(&format!("\n[[{}]]\n", format_path(path)));
                if let Value::Table(inner) = item {
                    write_table(inner, path, out, options)?;
                }
            }
        }
        path.pop();
    }
    Ok(())
}

/// A non-empty array whose every element is a table.
fn is_table_array(value: &Value) -> bool {
    matches!(value, Value::Array(items) if !items.is_empty() && items.iter().all(|item| matches!(item, Value::Table(_))))
}

fn type_name(value: &Value) -> &'static str {
    match value {
        Value::Null => "null",
        Value::Bool(_) => "boolean",
        Value::Int(_) | Value::Float(_) => "number",
        Value::Str(_) => "string",
        Value::DateTime(_) => "datetime",
        Value::Array(_) => "array",
        Value::Table(_) => "struct",
    }
}

fn format_value(value: &Value, options: &WriteOptions) -> Result<String> {
    Ok(match value {
        Value::Null => return Err(fail("TOML has no representation for null values")),
        Value::Bool(b) => b.to_string(),
        Value::Int(i) => i.to_string(),
        Value::Float(f) => format_float(*f),
        Value::Str(s) => format!("\"{}\"", escape(s)),
        Value::DateTime(dt) => format_datetime(dt, options)?,
        Value::Array(items) if items.is_empty() => "[]".into(),
        Value::Array(items) => {
            let parts: Result<Vec<String>> = items.iter().map(|item| format_value(item, options)).collect();
            format!("[ {} ]", parts?.join(", "))
        }
        Value::Table(entries) => {
            let mut entries: Vec<&(String, Value)> = entries.iter().filter(|(_, v)| !matches!(v, Value::Null)).collect();
            if options.sort_keys {
                entries.sort_by_cached_key(|(key, _)| key.to_lowercase());
            }
            if entries.is_empty() {
                "{}".into()
            } else {
                let parts: Result<Vec<String>> = entries
                    .iter()
                    .map(|(key, value)| Ok(format!("{} = {}", format_key(key), format_value(value, options)?)))
                    .collect();
                format!("{{ {} }}", parts?.join(", "))
            }
        }
    })
}

/// A float the way Java's `Double.toString` writes it, which is also valid TOML: `3.14`, `1.0E10`, `1.5E-7`.
pub fn format_float(value: f64) -> String {
    if value.is_nan() {
        return "nan".into();
    }
    if value.is_infinite() {
        return if value > 0.0 { "inf" } else { "-inf" }.into();
    }
    if value == 0.0 {
        return if value.is_sign_negative() { "-0.0" } else { "0.0" }.into();
    }
    let scientific = format!("{:e}", value.abs());
    let (mantissa, exponent) = scientific.split_once('e').expect("scientific notation has an exponent");
    let exponent: i32 = exponent.parse().expect("exponent is a number");
    let digits: String = mantissa.chars().filter(|c| *c != '.').collect();
    let sign = if value < 0.0 { "-" } else { "" };
    if (-3..7).contains(&exponent) {
        if exponent >= 0 {
            let integer_len = exponent as usize + 1;
            if digits.len() <= integer_len {
                format!("{sign}{digits}{}.0", "0".repeat(integer_len - digits.len()))
            } else {
                format!("{sign}{}.{}", &digits[..integer_len], &digits[integer_len..])
            }
        } else {
            format!("{sign}0.{}{digits}", "0".repeat((-exponent - 1) as usize))
        }
    } else {
        let (first, rest) = digits.split_at(1);
        format!("{sign}{first}.{}E{exponent}", if rest.is_empty() { "0" } else { rest })
    }
}

/// Escapes a basic string the way tomlj does: the short escapes, then `\uXXXX` (lowercase) for control characters and
/// everything outside printable ASCII, `\UXXXXXXXX` beyond the first plane. An apostrophe is left alone, which TOML
/// requires (tomlj writes the invalid `\'`).
pub fn escape(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    for c in text.chars() {
        match c {
            '\u{8}' => out.push_str("\\b"),
            '\t' => out.push_str("\\t"),
            '\n' => out.push_str("\\n"),
            '\u{c}' => out.push_str("\\f"),
            '\r' => out.push_str("\\r"),
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            c if (c as u32) < 0x20 || (c as u32) >= 0x7f => {
                if (c as u32) <= 0xffff {
                    out.push_str(&format!("\\u{:04x}", c as u32));
                } else {
                    out.push_str(&format!("\\U{:08x}", c as u32));
                }
            }
            c => out.push(c),
        }
    }
    out
}

fn format_key(key: &str) -> String {
    if !key.is_empty() && key.chars().all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '-') {
        key.to_string()
    } else {
        format!("\"{}\"", escape(key))
    }
}

fn format_path(path: &[String]) -> String {
    path.iter().map(|segment| format_key(segment)).collect::<Vec<_>>().join(".")
}

fn format_datetime(value: &DateTimeValue, options: &WriteOptions) -> Result<String> {
    let offset = offset_seconds(&value.zone, value.seconds)?;
    let wall = DateTime::from_timestamp(value.seconds + i64::from(offset), value.nanos)
        .ok_or_else(|| fail(format!("Date out of range: {} seconds", value.seconds)))?
        .naive_utc();
    let style = if options.date_time_style == "auto" {
        let system = offset_seconds("", value.seconds)?;
        if offset != system {
            "offset-datetime"
        } else if wall.date() == NaiveDate::from_ymd_opt(1970, 1, 1).expect("valid date") {
            "local-time"
        } else if wall.time().num_seconds_from_midnight() == 0 && wall.time().nanosecond() == 0 {
            "local-date"
        } else {
            "local-datetime"
        }
    } else {
        options.date_time_style.as_str()
    };
    let date = format!("{:04}-{:02}-{:02}", wall.year(), wall.month(), wall.day());
    let mut time = format!("{:02}:{:02}:{:02}", wall.hour(), wall.minute(), wall.second());
    if wall.nanosecond() != 0 {
        time.push('.');
        time.push_str(format!("{:09}", wall.nanosecond()).trim_end_matches('0'));
    }
    Ok(match style {
        "offset-datetime" => format!("{date}T{time}{}", format_offset(offset)),
        "local-datetime" => format!("{date}T{time}"),
        "local-date" => date,
        "local-time" => time,
        other => return Err(fail(format!("Unknown dateTimeStyle: {other}"))),
    })
}

/// `Z` for no offset, otherwise `+HH:MM` (with `:SS` only when seconds are not zero).
fn format_offset(seconds: i32) -> String {
    if seconds == 0 {
        return "Z".into();
    }
    let sign = if seconds < 0 { '-' } else { '+' };
    let total = seconds.abs();
    let mut text = format!("{sign}{:02}:{:02}", total / 3600, total % 3600 / 60);
    if total % 60 != 0 {
        text.push_str(&format!(":{:02}", total % 60));
    }
    text
}

#[cfg(test)]
mod tests {
    use super::*;

    fn table(entries: Vec<(&str, Value)>) -> Value {
        Value::Table(entries.into_iter().map(|(k, v)| (k.to_string(), v)).collect())
    }
    fn s(text: &str) -> Value {
        Value::Str(text.into())
    }

    #[test]
    fn floats_print_as_java_does() {
        for (value, expected) in [
            (3.14, "3.14"),
            (100.0, "100.0"),
            (0.001, "0.001"),
            (1234567.0, "1234567.0"),
            (12345678.0, "1.2345678E7"),
            (1e10, "1.0E10"),
            (1.5e-7, "1.5E-7"),
            (0.0001, "1.0E-4"),
            (-2.5, "-2.5"),
            (0.0, "0.0"),
            (-0.0, "-0.0"),
            (f64::INFINITY, "inf"),
            (f64::NEG_INFINITY, "-inf"),
            (f64::NAN, "nan"),
        ] {
            assert_eq!(format_float(value), expected, "{value}");
        }
    }

    #[test]
    fn strings_escape_like_tomlj() {
        assert_eq!(escape("a\"b\\c\n\t\r\u{8}\u{c}"), "a\\\"b\\\\c\\n\\t\\r\\b\\f");
        assert_eq!(escape("\u{0}\u{1b}\u{7f}é\u{2028}😀"), "\\u0000\\u001b\\u007f\\u00e9\\u2028\\U0001f600");
        assert_eq!(escape("it's"), "it's");
    }

    #[test]
    fn scalars_then_tables_then_table_arrays() {
        let doc = table(vec![
            ("servers", Value::Array(vec![table(vec![("name", s("a"))]), table(vec![("name", s("b"))])])),
            ("title", s("demo")),
            ("owner", table(vec![("name", s("Ada")), ("born", Value::Int(1815))])),
            ("ports", Value::Array(vec![Value::Int(80), Value::Int(443)])),
        ]);
        let text = write(&doc, &WriteOptions::default()).unwrap();
        assert_eq!(
            text,
            "title = \"demo\"\nports = [ 80, 443 ]\n\n[owner]\n  name = \"Ada\"\n  born = 1815\n\n[[servers]]\n  name = \"a\"\n\n[[servers]]\n  name = \"b\"\n"
        );
    }

    #[test]
    fn sort_keys_ignores_case_and_indent_is_configurable() {
        let doc = table(vec![("b", Value::Int(1)), ("A", Value::Int(2)), ("t", table(vec![("z", Value::Int(3)), ("y", Value::Int(4))]))]);
        let options = WriteOptions { sort_keys: true, indent: 4, ..Default::default() };
        assert_eq!(write(&doc, &options).unwrap(), "A = 2\nb = 1\n\n[t]\n    y = 4\n    z = 3\n");
    }

    #[test]
    fn nulls_are_skipped_in_tables_but_not_in_arrays() {
        let doc = table(vec![("a", Value::Null), ("b", Value::Int(1))]);
        assert_eq!(write(&doc, &WriteOptions::default()).unwrap(), "b = 1\n");
        let bad = table(vec![("a", Value::Array(vec![Value::Null]))]);
        assert!(write(&bad, &WriteOptions::default()).unwrap_err().message.contains("null"));
    }

    #[test]
    fn mixed_and_empty_arrays_are_inline() {
        let doc = table(vec![
            ("empty", Value::Array(vec![])),
            ("mixed", Value::Array(vec![Value::Int(1), table(vec![("k", s("v"))]), Value::Array(vec![Value::Bool(true)])])),
        ]);
        assert_eq!(write(&doc, &WriteOptions::default()).unwrap(), "empty = []\nmixed = [ 1, { k = \"v\" }, [ true ] ]\n");
    }

    #[test]
    fn keys_are_quoted_when_needed() {
        let doc = table(vec![("plain-key_1", Value::Int(1)), ("has space", Value::Int(2)), ("t.x", table(vec![("é", Value::Int(3))]))]);
        assert_eq!(
            write(&doc, &WriteOptions::default()).unwrap(),
            "plain-key_1 = 1\n\"has space\" = 2\n\n[\"t.x\"]\n  \"\\u00e9\" = 3\n"
        );
    }

    #[test]
    fn the_root_must_be_a_table() {
        let error = write(&Value::Array(vec![]), &WriteOptions::default()).unwrap_err();
        assert_eq!(error.kind, Kind::Serialization);
        assert!(error.message.starts_with("TOML documents must have a struct at the root; received array"));
    }

    #[test]
    fn dates_in_every_style() {
        // 2024-03-05T06:07:08.5 in +05:30, which is 00:37:08.5Z.
        let dt = Value::DateTime(DateTimeValue { seconds: 1_709_599_028, nanos: 500_000_000, zone: "+05:30".into() });
        let style = |name: &str| {
            let options = WriteOptions { date_time_style: name.into(), ..Default::default() };
            write(&table(vec![("d", dt.clone())]), &options).unwrap()
        };
        assert_eq!(style("offset-datetime"), "d = 2024-03-05T06:07:08.5+05:30\n");
        assert_eq!(style("local-datetime"), "d = 2024-03-05T06:07:08.5\n");
        assert_eq!(style("local-date"), "d = 2024-03-05\n");
        assert_eq!(style("local-time"), "d = 06:07:08.5\n");
        assert!(write(&table(vec![("d", dt)]), &WriteOptions { date_time_style: "bogus".into(), ..Default::default() }).is_err());
        let utc = Value::DateTime(DateTimeValue { seconds: 0, nanos: 0, zone: "UTC".into() });
        let options = WriteOptions { date_time_style: "offset-datetime".into(), ..Default::default() };
        assert_eq!(write(&table(vec![("d", utc)]), &options).unwrap(), "d = 1970-01-01T00:00:00Z\n");
    }

    #[test]
    fn auto_style_picks_offset_when_it_differs_from_the_system_zone() {
        let far = Value::DateTime(DateTimeValue { seconds: 1_709_599_028, nanos: 0, zone: "+13:45".into() });
        let text = write(&table(vec![("d", far)]), &WriteOptions::default()).unwrap();
        assert!(text.ends_with("+13:45\n"), "{text}");
    }
}
