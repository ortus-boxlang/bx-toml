//! The values TOML can hold, independent of the MatchBox VM, so parsing and writing can be tested on their own.

/// A point in time and the zone it is shown in, as the VM's `datetime.v1` capability carries it.
#[derive(Debug, Clone, PartialEq)]
pub struct DateTimeValue {
    /// Whole seconds since 1970-01-01T00:00:00Z.
    pub seconds: i64,
    /// The nanosecond part, below 1,000,000,000.
    pub nanos: u32,
    /// `UTC`, `+HH:MM`, an IANA name, or empty for "the system zone".
    pub zone: String,
}

/// A TOML value, or a value about to be written as TOML. A table keeps the order of its keys.
#[derive(Debug, Clone, PartialEq)]
pub enum Value {
    /// A null. TOML has none, so a table skips it and an array refuses it.
    Null,
    /// A boolean.
    Bool(bool),
    /// A whole number, within TOML's 64-bit range.
    Int(i64),
    /// A number with a fraction, or an infinity or NaN.
    Float(f64),
    /// Text.
    Str(String),
    /// A point in time. Every TOML date, time and date-time reads as one.
    DateTime(DateTimeValue),
    /// An array, which is written as an array of tables when every element is a table.
    Array(Vec<Value>),
    /// Keys in insertion order, with the spelling they were written in.
    Table(Vec<(String, Value)>),
}

/// Which BoxLang exception a failure becomes.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Kind {
    /// The text is not valid TOML.
    Parse,
    /// The value cannot be written as TOML.
    Serialization,
    /// A file could not be read or written.
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

/// A failure to read or write TOML: what kind it was, and the message a script sees.
#[derive(Debug, Clone, PartialEq)]
pub struct Failure {
    /// Which exception the failure becomes.
    pub kind: Kind,
    /// The text of the exception.
    pub message: String,
}

impl Failure {
    /// A failure of `kind` with `message`.
    pub fn new(kind: Kind, message: impl Into<String>) -> Self {
        Self { kind, message: message.into() }
    }
}

/// The result of reading or writing TOML.
pub type Result<T> = std::result::Result<T, Failure>;
