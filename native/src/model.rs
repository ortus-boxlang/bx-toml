//! The values TOML can hold, independent of the MatchBox VM, so parsing and writing can be tested on their own.

/// A point in time and the zone it is shown in, as the VM's `datetime.v1` capability carries it.
#[derive(Debug, Clone, PartialEq)]
pub struct DateTimeValue {
    /// Whole seconds since 1970-01-01T00:00:00Z.
    pub seconds: i64,
    pub nanos: u32,
    /// `UTC`, `+HH:MM`, an IANA name, or empty for "the system zone".
    pub zone: String,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Value {
    Null,
    Bool(bool),
    Int(i64),
    Float(f64),
    Str(String),
    DateTime(DateTimeValue),
    Array(Vec<Value>),
    /// Keys in insertion order, with the spelling they were written in.
    Table(Vec<(String, Value)>),
}

/// Which BoxLang exception a failure becomes.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Kind {
    Parse,
    Serialization,
    Io,
}

impl Kind {
    /// The exception type scripts see. The JVM module's exceptions are `BoxRuntimeException`s, whose `type` is `Application`
    /// (their Java class tells them apart, which MatchBox's `catch` cannot match), so `catch ( Application e )` and
    /// `e.type` behave the same on both backends. The message prefix says which failure it was.
    pub fn exception_type(self) -> &'static str {
        let _ = self;
        "Application"
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct Failure {
    pub kind: Kind,
    pub message: String,
}

impl Failure {
    pub fn new(kind: Kind, message: impl Into<String>) -> Self {
        Self { kind, message: message.into() }
    }
}

pub type Result<T> = std::result::Result<T, Failure>;
