# ⚡︎ BoxLang Module: TOML Support

```
|:------------------------------------------------------:|
| ⚡︎ B o x L a n g ⚡︎
| Dynamic : Modular : Productive
|:------------------------------------------------------:|
```

<blockquote>
	Copyright Since 2023 by Ortus Solutions, Corp
	<br>
	<a href="https://www.boxlang.io">www.boxlang.io</a> |
	<a href="https://www.ortussolutions.com">www.ortussolutions.com</a>
</blockquote>

<p>&nbsp;</p>

This module brings full [TOML](https://toml.io) parsing and serialization to BoxLang applications: `tomlDeserialize()`, `tomlDeserializeFile()`, `tomlSerialize()`, and `tomlSerializeFile()`. Parsing is powered by [tomlj](https://github.com/tomlj/tomlj) (a hardened, spec-compliant TOML parser); serialization is hand-written in this module.

## Installation

```bash
box install bx-toml
```

## Quick Start

```js
data = tomlDeserialize( '
	title = "bx-toml"

	[owner]
	name = "Ortus Solutions"
' );

writeOutput( data.title ); // bx-toml
writeOutput( data.owner.name ); // Ortus Solutions

toml = tomlSerialize( data );
```

## BIF Reference

### `tomlDeserialize( toml, [options] )`

Parses a TOML string into a BoxLang struct.

| Argument | Type   | Required | Description |
|----------|--------|----------|--------------|
| `toml`   | string | yes      | The TOML string to parse. |
| `options`| struct | no       | Per-call overrides of this module's settings - see [Module Settings](#module-settings). |

### `tomlDeserializeFile( path, [charset], [options] )`

Reads and parses a TOML file into a BoxLang struct.

| Argument  | Type   | Required | Description |
|-----------|--------|----------|--------------|
| `path`    | string | yes      | The path to the TOML file. |
| `charset` | string | no       | The charset to read the file with. Defaults to the system default charset. |
| `options` | struct | no       | Per-call overrides of this module's settings. |

### `tomlSerialize( content, [filepath], [charset], [options] )`

Converts a BoxLang struct into a TOML string, or writes it directly to a file if `filepath` is provided.

| Argument   | Type   | Required | Description |
|------------|--------|----------|--------------|
| `content`  | any    | yes      | The struct to convert to TOML. TOML documents always have a struct at the root. |
| `filepath` | string | no       | If provided, the TOML is written here instead of being returned. |
| `charset`  | string | no       | The charset to write the file with. Defaults to the system default charset. |
| `options`  | struct | no       | Per-call overrides of this module's settings - see [Module Settings](#module-settings). |

### `tomlSerializeFile( content, filepath, [charset], [options] )`

The explicit, filepath-required counterpart to `tomlSerialize()`, for symmetry with `tomlDeserializeFile()`.

| Argument   | Type   | Required | Description |
|------------|--------|----------|--------------|
| `content`  | any    | yes      | The struct to convert to TOML. |
| `filepath` | string | yes      | The path to write the TOML to. |
| `charset`  | string | no       | The charset to write the file with. Defaults to the system default charset. |
| `options`  | struct | no       | Per-call overrides of this module's settings. |

## Module Settings

Configure module-wide defaults in your `boxlang.json`:

```json
{
	"modules": {
		"bxtoml": {
			"settings": {
				"specVersion": "1.0",
				"ordered": true,
				"sortKeys": false,
				"indent": 2,
				"dateTimeStyle": "auto"
			}
		}
	}
}
```

| Setting         | Default  | Description |
|-----------------|----------|--------------|
| `specVersion`   | `"1.0"`  | `"1.0"` or `"1.1"` (best-effort - see [TOML Spec Version Support](#toml-spec-version-support)). |
| `ordered`       | `true`   | Whether `tomlDeserialize()`/`tomlDeserializeFile()` return tables as ordered (linked) structs that preserve TOML key declaration order. |
| `sortKeys`      | `false`  | Whether `tomlSerialize()`/`tomlSerializeFile()` alphabetize keys instead of preserving the incoming struct's iteration order. |
| `indent`        | `2`      | Cosmetic indentation width (spaces). TOML has no semantic indentation requirement, so this does not affect parseability. |
| `dateTimeStyle` | `"auto"` | How a BoxLang DateTime is re-emitted on serialize: `"auto"`, `"offset-datetime"`, `"local-datetime"`, `"local-date"`, or `"local-time"`. See [Known Limitations](#known-limitations). |

Any setting can be overridden per-call via the `options` argument on any of the 4 BIFs, e.g. `tomlSerialize( data, options={ sortKeys: true } )`.

## Type Mapping

| TOML type                              | BoxLang type |
|-----------------------------------------|--------------|
| String                                   | `String` |
| Integer                                  | `Long` (BoxLang's numeric auto-promotion handles the rest - see below) |
| Float                                    | `Double` |
| Boolean                                  | `Boolean` |
| Array                                    | `Array` |
| Table                                    | `Struct` (ordered/case-sensitive - see below) |
| Array of tables                          | `Array` of `Struct` |
| Inline table                             | `Struct` |
| Offset date-time, local date-time, local date, local time | `DateTime` (see [Known Limitations](#known-limitations)) |

**Integers:** TOML integers are 64-bit signed (i64). BoxLang numbers auto-promote across `Integer`/`Long`/`BigDecimal`/`BigInteger` as needed, so there's no equivalent of other TOML libraries' "int64 mode" setting to worry about on the read side. On the *write* side, a `BigDecimal`/`BigInteger` that doesn't fit losslessly into TOML's integer or float range raises a `TomlSerializationException` rather than silently losing precision.

**Keys are case-sensitive:** TOML keys are case-sensitive by spec (`"Key"` and `"key"` are distinct), so tables always deserialize into case-sensitive structs, independent of the `ordered` setting.

## TOML Spec Version Support

- **TOML 1.0.0** is fully supported and is the default (`specVersion: "1.0"`), validated against the [official toml-test conformance suite](https://github.com/toml-lang/toml-test) (the TOML-1.0.0-scoped subset is vendored under `src/test/resources/toml-test` and run on every build).
- **TOML 1.1.0** (`specVersion: "1.1"`) is *not yet ratified* upstream, and as of tomlj 1.1.1 - the parser this module wraps - its "development spec" mode (`TomlVersion.HEAD`) accepts exactly the same grammar as 1.0.0. In practice, `specVersion: "1.1"` currently behaves identically to `"1.0"`; none of the 1.1-track syntax (optional seconds in datetimes, trailing commas/multi-line inline tables, the `\e`/`\xHH` string escapes) is implemented yet. This is tracked as a fast-follow, contingent on tomlj (or a safe, targeted normalization pass) actually supporting it.

## Known Limitations

- **No comment preservation.** Parsing and re-serializing a TOML document does not preserve the original comments or formatting - the data model is value-only, matching how `tomlDeserialize()`/`tomlSerialize()` work for JSON.
- **DateTime round-trip is lossy.** TOML has 4 distinct temporal kinds (offset-datetime, local-datetime, local-date, local-time), but BoxLang has a single `DateTime` type. All 4 collapse into one on parse. On serialize, the `dateTimeStyle: "auto"` heuristic tries to pick a sensible TOML form back out (based on whether the DateTime's offset differs from the system default, and whether its time-of-day is midnight), but this is a best-effort heuristic, not a guarantee - a bare TOML local-date and a midnight UTC offset-datetime are indistinguishable after parsing and will round-trip to the same form. Force a specific form with the `dateTimeStyle` option if you need deterministic output.
- **Two known upstream tomlj gaps.** tomlj 1.1.1 accepts a UTC offset with a 1-digit minute component (e.g. `+09:9`), which the TOML grammar requires to be 2 digits. This is a parser-level limitation this module cannot work around without hand-rolling its own validation pass, and is excluded (with an explicit, documented reason) from the vendored conformance suite run.

## Conformance

This module is tested against the official [toml-test](https://github.com/toml-lang/toml-test) conformance suite (TOML 1.0.0-scoped subset, 210 valid + 497 invalid fixtures, 2 fixtures excluded for the documented tomlj gap above) on every build - see `src/test/java/ortus/boxlang/toml/OfficialConformanceTest.java` and `src/test/resources/toml-test/README.md` for the pinned source commit.

## Ortus Sponsors

BoxLang is a professional open-source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com). Ortus Patreons get many benefits like a cfcasts account, a FORGEBOX Pro account and so much more. If you are interested in becoming a sponsor, please visit our patronage page: [https://patreon.com/ortussolutions](https://patreon.com/ortussolutions)

### THE DAILY BREAD

> "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
