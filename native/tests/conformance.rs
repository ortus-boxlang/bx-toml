//! The vendored toml-test suite (TOML 1.0.0), the same fixtures the JVM module's `OfficialConformanceTest` runs:
//! every valid document must parse to the tagged value in its `.json` file, and every invalid one must be rejected.

use bx_toml_native::model::Value;
use bx_toml_native::parse::parse;
use chrono::DateTime;
use serde_json::Value as Json;
use std::{fs, path::{Path, PathBuf}};

/// The directory holding the vendored toml-test fixtures.
fn fixtures() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../src/test/resources/toml-test/tests")
}

/// Every file with `extension` under `dir`, searching subdirectories, in sorted order.
fn files(dir: &Path, extension: &str) -> Vec<PathBuf> {
    let mut found = Vec::new();
    let mut stack = vec![dir.to_path_buf()];
    while let Some(next) = stack.pop() {
        for entry in fs::read_dir(&next).unwrap_or_else(|e| panic!("{}: {e}", next.display())) {
            let path = entry.unwrap().path();
            if path.is_dir() {
                stack.push(path);
            } else if path.extension().is_some_and(|e| e == extension) {
                found.push(path);
            }
        }
    }
    found.sort();
    found
}

/// Compares a parsed value with the toml-test tagged JSON: `{"type": "integer", "value": "42"}` for scalars.
fn matches(expected: &Json, actual: &Value, path: &str) -> Result<(), String> {
    let mismatch = |what: &str| Err(format!("{path}: {what}: expected {expected}, got {actual:?}"));
    match expected {
        Json::Array(items) => {
            let Value::Array(values) = actual else { return mismatch("not an array") };
            if items.len() != values.len() {
                return mismatch("array length");
            }
            items.iter().zip(values).enumerate().try_for_each(|(i, (e, a))| matches(e, a, &format!("{path}[{i}]")))
        }
        Json::Object(map) if map.len() == 2 && map.contains_key("type") && map.contains_key("value") => {
            let kind = map["type"].as_str().unwrap_or_default();
            let text = map["value"].as_str().unwrap_or_default();
            match (kind, actual) {
                ("string", Value::Str(s)) if s == text => Ok(()),
                ("bool", Value::Bool(b)) if b.to_string() == text => Ok(()),
                ("integer", Value::Int(i)) if i.to_string() == text => Ok(()),
                ("float", Value::Float(f)) => {
                    let ok = match text {
                        "nan" | "+nan" | "-nan" => f.is_nan(),
                        "inf" | "+inf" => *f == f64::INFINITY,
                        "-inf" => *f == f64::NEG_INFINITY,
                        other => other.parse::<f64>().is_ok_and(|e| e == *f && e.is_sign_negative() == f.is_sign_negative()),
                    };
                    if ok { Ok(()) } else { mismatch("float") }
                }
                ("datetime", Value::DateTime(dt)) => match DateTime::parse_from_rfc3339(text) {
                    Ok(e) if e.timestamp() == dt.seconds && e.timestamp_subsec_nanos() == dt.nanos => Ok(()),
                    _ => mismatch("datetime"),
                },
                // These read in the system zone, so only the kind is checked.
                ("datetime-local" | "date-local" | "time-local", Value::DateTime(_)) => Ok(()),
                _ => mismatch("type"),
            }
        }
        Json::Object(map) => {
            let Value::Table(entries) = actual else { return mismatch("not a table") };
            if map.len() != entries.len() {
                return mismatch("table size");
            }
            for (key, expected) in map {
                match entries.iter().find(|(k, _)| k == key) {
                    Some((_, value)) => matches(expected, value, &format!("{path}.{key}"))?,
                    None => return Err(format!("{path}: missing key {key}")),
                }
            }
            Ok(())
        }
        _ => mismatch("unexpected expectation"),
    }
}

/// Every valid document parses to the tagged value in its `.json` file.
#[test]
fn valid_documents_parse_to_the_expected_values() {
    let valid = fixtures().join("valid");
    let mut failures = Vec::new();
    let documents = files(&valid, "toml");
    assert!(documents.len() > 100, "fixtures not found under {}", valid.display());
    for path in &documents {
        let name = path.strip_prefix(&valid).unwrap().display().to_string();
        let expected: Json = serde_json::from_str(&fs::read_to_string(path.with_extension("json")).unwrap()).unwrap();
        match parse(&fs::read_to_string(path).unwrap()) {
            Ok(actual) => {
                if let Err(error) = matches(&expected, &actual, "$") {
                    failures.push(format!("{name}: {error}"));
                }
            }
            Err(error) => failures.push(format!("{name}: rejected: {}", error.message)),
        }
    }
    assert!(failures.is_empty(), "{} of {} valid documents failed:\n{}", failures.len(), documents.len(), failures.join("\n"));
}

/// Every invalid document is a parse error.
#[test]
fn invalid_documents_are_rejected() {
    let invalid = fixtures().join("invalid");
    let mut accepted = Vec::new();
    let documents = files(&invalid, "toml");
    assert!(documents.len() > 100, "fixtures not found under {}", invalid.display());
    for path in &documents {
        // Some invalid documents are not valid UTF-8, which the file read rejects.
        let Ok(text) = fs::read_to_string(path) else { continue };
        if parse(&text).is_ok() {
            accepted.push(path.strip_prefix(&invalid).unwrap().display().to_string());
        }
    }
    assert!(accepted.is_empty(), "{} of {} invalid documents were accepted:\n{}", accepted.len(), documents.len(), accepted.join("\n"));
}
