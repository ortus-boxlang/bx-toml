//! The MatchBox plugin: registers `tomlDeserialize`, `tomlDeserializeFile`, `tomlSerialize` and `tomlSerializeFile`
//! with the same names and arguments as the JVM module, and moves values between the VM and the TOML core.

use crate::model::{DateTimeValue, Failure, Kind, Value as Model};
use crate::write::WriteOptions;
use crate::{parse, write};
use matchbox_plugin_sdk::{
    Call, CollectionsV1, Context, DatetimeV1, ErrorsV1, HostV1, IntegersV1, Invoke, Kind as VmKind, PathsV1, SettingsV1, StructsV1,
    Text, TypesV1, Value, ValuesV1, guarded, require_collections, require_datetime, require_errors, require_integers,
    require_paths, require_settings, require_structs, require_types, require_values,
};
use std::{ffi::c_void, sync::OnceLock};

/// The capabilities the host granted at initialization.
struct Apis {
    /// Type checks and conversions of values.
    types: &'static TypesV1,
    /// Reading and building arrays.
    collections: &'static CollectionsV1,
    /// Reading and creating whole numbers.
    integers: &'static IntegersV1,
    /// Reading and building structs.
    structs: &'static StructsV1,
    /// Reading and creating date-times.
    datetime: &'static DatetimeV1,
    /// The module's `boxlang.json` settings.
    settings: &'static SettingsV1,
    /// `expandPath` resolution.
    paths: &'static PathsV1,
    /// Typed exceptions.
    errors: &'static ErrorsV1,
}

/// The capabilities, set once when the host initializes the plugin.
static APIS: OnceLock<Apis> = OnceLock::new();

/// The granted capabilities, or an error when the plugin was not initialized.
fn apis() -> Result<&'static Apis, String> {
    APIS.get().ok_or_else(|| "The TOML plugin was not initialized".to_string())
}

/// The result of a step that can fail with a typed failure.
type Outcome<T> = std::result::Result<T, Failure>;

/// A failure that is not tied to a file or a document, raised as a serialization error.
fn plain(error: String) -> Failure {
    Failure::new(Kind::Serialization, error)
}

/// What a call reads: the options struct, then the module's settings, then the built-in default.
struct Options {
    /// Whether a struct read from TOML keeps the order of its keys.
    ordered: bool,
    /// How a document is written.
    write: WriteOptions,
}

/// The first non-null value of `key` across `sources`, which are the call options and then the module settings.
fn option_value(call: &Call<'_>, a: &Apis, sources: &[Value], key: &str) -> Outcome<Option<Value>> {
    for source in sources {
        if let Some(value) = call.struct_get(a.collections, *source, key).map_err(plain)? {
            if call.kind(a.types, value).map_err(plain)? != VmKind::Null {
                return Ok(Some(value));
            }
        }
    }
    Ok(None)
}

/// A boolean from a boolean, a number (non-zero is true) or text such as true, false, yes or no.
fn read_bool(call: &Call<'_>, a: &Apis, value: Value, key: &str) -> Outcome<bool> {
    match call.kind(a.types, value).map_err(plain)? {
        VmKind::Bool => call.boolean(a.types, value).map_err(plain),
        VmKind::Number => Ok(call.number(value).map_err(plain)? != 0.0),
        VmKind::String => match call.string(value).map_err(plain)?.to_ascii_lowercase().as_str() {
            "true" | "yes" => Ok(true),
            "false" | "no" => Ok(false),
            other => Err(plain(format!("The option {key} must be true or false, not {other}"))),
        },
        _ => Err(plain(format!("The option {key} must be true or false"))),
    }
}

/// The options for a call: the per-call struct, then the module settings, then the defaults.
fn options(call: &Call<'_>, a: &Apis, per_call: Value) -> Outcome<Options> {
    let mut sources = Vec::new();
    match call.kind(a.types, per_call).map_err(plain)? {
        VmKind::Null => {}
        VmKind::Struct => sources.push(per_call),
        _ => return Err(plain("The options argument must be a struct".into())),
    }
    // A plugin that no module supplied has no settings, which is not an error here.
    if let Ok(settings) = call.module_settings(a.settings) {
        sources.push(settings);
    }
    let mut result = Options { ordered: true, write: WriteOptions::default() };
    if let Some(value) = option_value(call, a, &sources, "ordered")? {
        result.ordered = read_bool(call, a, value, "ordered")?;
    }
    if let Some(value) = option_value(call, a, &sources, "sortKeys")? {
        result.write.sort_keys = read_bool(call, a, value, "sortKeys")?;
    }
    if let Some(value) = option_value(call, a, &sources, "indent")? {
        let indent = call.integer(a.integers, value).map_err(|_| plain("The option indent must be a whole number".into()))?;
        result.write.indent = usize::try_from(indent).map_err(|_| plain("The option indent must not be negative".into()))?;
    }
    if let Some(value) = option_value(call, a, &sources, "dateTimeStyle")? {
        result.write.date_time_style = call.string(value).map_err(plain)?;
    }
    Ok(result)
}

/// A value read from the VM, ready to write as TOML.
fn from_vm(call: &Call<'_>, a: &Apis, value: Value) -> Outcome<Model> {
    Ok(match call.kind(a.types, value).map_err(plain)? {
        VmKind::Null => Model::Null,
        VmKind::Bool => Model::Bool(call.boolean(a.types, value).map_err(plain)?),
        VmKind::Number => number(call.number(value).map_err(plain)?)?,
        VmKind::String => Model::Str(call.string(value).map_err(plain)?),
        VmKind::Array => {
            let length = call.array_len(a.collections, value).map_err(plain)?;
            let mut items = Vec::with_capacity(length);
            for index in 0..length {
                items.push(from_vm(call, a, call.array_get(a.collections, value, index).map_err(plain)?)?);
            }
            Model::Array(items)
        }
        VmKind::Struct => {
            let mut entries = Vec::new();
            for key in call.struct_keys(a.collections, value).map_err(plain)? {
                let inner = call.struct_get(a.collections, value, &key).map_err(plain)?.unwrap_or_default_null(call)?;
                entries.push((key, from_vm(call, a, inner)?));
            }
            Model::Table(entries)
        }
        VmKind::Binary => return Err(plain("Cannot serialize a value of type binary to TOML".into())),
        VmKind::Other => {
            if call.is_datetime(a.datetime, value).map_err(plain)? {
                let (seconds, nanos, zone) = call.datetime_get(a.datetime, value).map_err(plain)?;
                Model::DateTime(DateTimeValue { seconds, nanos, zone })
            } else {
                return Err(plain("Cannot serialize a value of this type to TOML".into()));
            }
        }
    })
}

/// MatchBox numbers are not told apart as integers and decimals, so a whole number is a TOML integer and any other is a float.
fn number(n: f64) -> Outcome<Model> {
    if n.is_finite() && n.fract() == 0.0 {
        if n >= -9_223_372_036_854_775_808.0 && n < 9_223_372_036_854_775_808.0 {
            return Ok(Model::Int(n as i64));
        }
        return Err(plain(format!("Integer value out of TOML's 64-bit integer range: {n}")));
    }
    Ok(Model::Float(n))
}

/// Turns a missing value into a BoxLang null.
trait OrNull {
    /// The value, or a null when there is none.
    fn unwrap_or_default_null(self, call: &Call<'_>) -> Outcome<Value>;
}
impl OrNull for Option<Value> {
    /// The value, or a null when there is none.
    fn unwrap_or_default_null(self, call: &Call<'_>) -> Outcome<Value> {
        match self {
            Some(value) => Ok(value),
            None => call.null_new().map_err(plain),
        }
    }
}

/// A parsed value as a VM value. Tables are case-sensitive, and ordered unless the `ordered` option says otherwise.
fn to_vm(call: &Call<'_>, a: &Apis, value: &Model, ordered: bool) -> Outcome<Value> {
    Ok(match value {
        Model::Null => call.null_new(),
        Model::Bool(b) => call.bool_new(*b),
        Model::Int(i) => call.int_new(a.integers, *i),
        Model::Float(f) => call.number_new(*f),
        Model::Str(s) => call.string_new(s),
        Model::DateTime(dt) => call.datetime_new(a.datetime, dt.seconds, dt.nanos, &dt.zone),
        Model::Array(items) => {
            let array = call.array_new(a.collections).map_err(plain)?;
            for item in items {
                let converted = to_vm(call, a, item, ordered)?;
                call.array_push(a.collections, array, converted).map_err(plain)?;
            }
            Ok(array)
        }
        Model::Table(entries) => {
            let table = call.struct_new_with(a.structs, ordered, true).map_err(plain)?;
            for (key, inner) in entries {
                let converted = to_vm(call, a, inner, ordered)?;
                call.struct_set(table, key, converted).map_err(plain)?;
            }
            Ok(table)
        }
    }
    .map_err(plain)?)
}

/// The native backend reads and writes UTF-8 only.
fn check_charset(call: &Call<'_>, a: &Apis, value: Value) -> Outcome<()> {
    if call.kind(a.types, value).map_err(plain)? == VmKind::Null {
        return Ok(());
    }
    let name = call.string(value).map_err(plain)?;
    match name.to_ascii_lowercase().replace('_', "-").as_str() {
        "utf-8" | "utf8" | "" => Ok(()),
        _ => Err(Failure::new(Kind::Io, format!("Unsupported charset: {name}. The native TOML backend reads and writes UTF-8"))),
    }
}

/// The text of argument `index`, or `None` when it is missing or null.
fn argument_text(call: &Call<'_>, a: &Apis, index: usize) -> Outcome<Option<String>> {
    let value = call.argument(index).map_err(plain)?;
    if call.kind(a.types, value).map_err(plain)? == VmKind::Null {
        return Ok(None);
    }
    Ok(Some(call.string(value).map_err(plain)?))
}

/// Parses TOML text into a BoxLang value.
fn deserialize(call: &Call<'_>, a: &Apis, text: &str, options: &Options) -> Outcome<Value> {
    let parsed = parse::parse(text)?;
    to_vm(call, a, &parsed, options.ordered)
}

/// Writes a BoxLang value as TOML text.
fn serialize(call: &Call<'_>, a: &Apis, content: Value, options: &Options) -> Outcome<String> {
    write::write(&from_vm(call, a, content)?, &options.write)
}

/// Resolves `path` the way `expandPath` does.
fn resolve_path(call: &Call<'_>, a: &Apis, path: &str) -> Outcome<String> {
    call.expand_path(a.paths, path).map_err(|e| Failure::new(Kind::Io, e))
}

// tomlDeserialize( toml, options )
/// `tomlDeserialize( text, options )`: parses TOML text.
fn toml_deserialize(call: &Call<'_>, a: &Apis) -> Outcome<Value> {
    let text = call.string(call.argument(0).map_err(plain)?).map_err(plain)?;
    let options = options(call, a, call.argument(1).map_err(plain)?)?;
    deserialize(call, a, &text, &options)
}

// tomlDeserializeFile( path, charset, options )
/// `tomlDeserializeFile( path, options )`: parses a TOML file.
fn toml_deserialize_file(call: &Call<'_>, a: &Apis) -> Outcome<Value> {
    let path = call.string(call.argument(0).map_err(plain)?).map_err(plain)?;
    check_charset(call, a, call.argument(1).map_err(plain)?)?;
    let options = options(call, a, call.argument(2).map_err(plain)?)?;
    let resolved = resolve_path(call, a, &path)?;
    let text = std::fs::read_to_string(&resolved)
        .map_err(|error| Failure::new(Kind::Io, format!("Error reading TOML file: {path} ({error})")))?;
    deserialize(call, a, &text, &options)
}

// tomlSerialize( content, filepath, charset, options ): the text, or null when it was written to filepath
/// `tomlSerialize( content, options )`: writes a value as TOML text.
fn toml_serialize(call: &Call<'_>, a: &Apis) -> Outcome<Value> {
    let content = call.argument(0).map_err(plain)?;
    let file = argument_text(call, a, 1)?;
    check_charset(call, a, call.argument(2).map_err(plain)?)?;
    let options = options(call, a, call.argument(3).map_err(plain)?)?;
    let text = serialize(call, a, content, &options)?;
    match file {
        None => call.string_new(&text).map_err(plain),
        Some(path) => {
            write_file(call, a, &path, &text)?;
            call.null_new().map_err(plain)
        }
    }
}

// tomlSerializeFile( content, filepath, charset, options )
/// `tomlSerializeFile( path, content, options )`: writes a value to a TOML file.
fn toml_serialize_file(call: &Call<'_>, a: &Apis) -> Outcome<Value> {
    let content = call.argument(0).map_err(plain)?;
    let path = call.string(call.argument(1).map_err(plain)?).map_err(plain)?;
    check_charset(call, a, call.argument(2).map_err(plain)?)?;
    let options = options(call, a, call.argument(3).map_err(plain)?)?;
    let text = serialize(call, a, content, &options)?;
    write_file(call, a, &path, &text)?;
    call.null_new().map_err(plain)
}

/// Writes `text` to `path`, resolved the way `expandPath` does.
fn write_file(call: &Call<'_>, a: &Apis, path: &str, text: &str) -> Outcome<()> {
    let resolved = resolve_path(call, a, path)?;
    std::fs::write(&resolved, text).map_err(|error| Failure::new(Kind::Io, format!("Error writing TOML file: {path} ({error})")))
}

/// Runs a function body, turning its failure into the BoxLang exception the JVM module throws.
unsafe fn run(
    ctx: Context,
    api: *const ValuesV1,
    args: *const Value,
    len: usize,
    out: *mut Value,
    body: fn(&Call<'_>, &Apis) -> Outcome<Value>,
) -> i32 {
    unsafe {
        guarded(ctx, api, out, || {
            let call = Call::new(ctx, api, args, len);
            let a = apis()?;
            body(&call, a).map_err(|failure| call.error_typed(a.errors, failure.kind.exception_type(), &failure.message, ""))
        })
    }
}

/// Defines the extern "C" entry point for a plugin function, running `$body` under `run`.
macro_rules! entry {
    ($name:ident, $body:path) => {
        unsafe extern "C" fn $name(
            ctx: Context,
            api: *const ValuesV1,
            _: *mut c_void,
            args: *const Value,
            len: usize,
            out: *mut Value,
        ) -> i32 {
            unsafe { run(ctx, api, args, len, out, $body) }
        }
    };
}
entry!(deserialize_entry, toml_deserialize);
entry!(deserialize_file_entry, toml_deserialize_file);
entry!(serialize_entry, toml_serialize);
entry!(serialize_file_entry, toml_serialize_file);

/// Initializes the plugin: requires the capabilities, then registers the four functions.
///
/// # Safety
/// Called by the MatchBox host with its own pointers.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn matchbox_plugin_init_v1(host: *const HostV1, ctx: Context) -> i32 {
    macro_rules! need {
        ($call:expr) => {
            match unsafe { $call } {
                Ok(api) => api,
                Err(status) => return status,
            }
        };
    }
    if unsafe { require_values(host, ctx) }.is_err() {
        return 1;
    }
    let granted = Apis {
        types: need!(require_types(host, ctx)),
        collections: need!(require_collections(host, ctx)),
        integers: need!(require_integers(host, ctx)),
        structs: need!(require_structs(host, ctx)),
        datetime: need!(require_datetime(host, ctx)),
        settings: need!(require_settings(host, ctx)),
        paths: need!(require_paths(host, ctx)),
        errors: need!(require_errors(host, ctx)),
    };
    let _ = APIS.set(granted);
    let host = unsafe { &*host };
    let functions: [(&str, Invoke); 4] = [
        (r#"{"name":"tomlDeserialize","arguments":[{"name":"toml","required":true},{"name":"options","default":null}]}"#, deserialize_entry),
        (
            r#"{"name":"tomlDeserializeFile","arguments":[{"name":"path","required":true},{"name":"charset","default":"UTF-8"},{"name":"options","default":null}]}"#,
            deserialize_file_entry,
        ),
        (
            r#"{"name":"tomlSerialize","arguments":[{"name":"content","required":true},{"name":"filepath","default":null},{"name":"charset","default":"UTF-8"},{"name":"options","default":null}]}"#,
            serialize_entry,
        ),
        (
            r#"{"name":"tomlSerializeFile","arguments":[{"name":"content","required":true},{"name":"filepath","required":true},{"name":"charset","default":"UTF-8"},{"name":"options","default":null}]}"#,
            serialize_file_entry,
        ),
    ];
    for (metadata, invoke) in functions {
        if unsafe { (host.register)(ctx, Text::new(metadata), invoke) } != 0 {
            return 1;
        }
    }
    0
}
