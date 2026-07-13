# Project Guidelines

## Purpose

`bx-toml` is the BoxLang TOML module. It exposes `tomlDeserialize()`,
`tomlDeserializeFile()`, `tomlSerialize()`, and `tomlSerializeFile()` for TOML
1.0.0 parsing and serialization, with best-effort TOML 1.1 opt-in behavior.

## Architecture

- BoxLang module metadata and lifecycle configuration live in
	`src/main/bx/ModuleConfig.bx`.
- BoxLang-facing functions belong in `src/main/bx` and their Java BIF
	implementations belong in `src/main/java/ortus/boxlang/toml/bifs`.
- `TomlParser.java` wraps `org.tomlj:tomlj` for parsing. `TomlWriter.java` is
	the hand-written serializer; preserve its type mapping, ordering, numeric
	precision, and date-time behavior when changing serialization.
- `BoxLangTomlConstructor.java` and the classes under `util` own conversion
	between TOML values and BoxLang values. Keep TOML keys case-sensitive.
- Module-specific exceptions belong under `exceptions` and should retain the
	existing BoxLang exception behavior.
- The module is loaded in its own class loader. Do not rely on shared static
	state, IDE-only resources, or accidental classpath leakage.

## Testing

- Java tests live under `src/test/java/ortus/boxlang/toml` and use JUnit 5,
	Mockito, and Google Truth.
- `OfficialConformanceTest` runs the vendored TOML 1.0.0 `toml-test` fixtures
	under `src/test/resources/toml-test`. Preserve the fixture exclusions and
	their documented upstream `tomlj` rationale unless parser support changes.
- `IntegrationTest`, `RoundTripTest`, and `TomlWriterTest` cover the public
	module behavior and serializer details. Add focused tests beside the
	affected behavior when changing parser, writer, conversion, or BIF code.
- Use `./gradlew test` for the full test suite. Test output is intentionally
	verbose so conformance failures include their fixture context.

## Build And Packaging

- Use the Gradle wrapper from the repository root. The project currently
	targets JDK 21 and BoxLang 1.15.0; versions are declared in
	`gradle.properties`.
- Run `./gradlew spotlessCheck` for Java formatting and
	`./gradlew test` for behavioral validation. Run `./gradlew build` before
	changes that affect distribution packaging.
- `shadowJar` creates the module jar, merges service files, and triggers
	`createModuleStructure`; the assembled module is written to `build/module`
	and zipped under `build/distributions`.
- Service-loader entries are generated during the build. Preserve the
	`serviceLoader` configuration in `build.gradle` when adding BoxLang runtime
	integrations.
- The build deliberately removes `src/main/resources` from the IDE/test
	classpath to avoid BoxLang class-loading conflicts. Do not undo that setup
	casually; resources still participate in jar/build outputs as configured.
- If the local BoxLang jar is unavailable, run `./gradlew downloadBoxLang` to
	place the configured test dependency under `src/test/resources/libs`.

## Conventions

- Follow `.editorconfig` and the Ortus Java formatter configured by
	`.ortus-java-style.xml`. Keep existing tab indentation in Gradle and Java
	files.
- Keep `box.json`, `settings.gradle`, `gradle.properties`, and module metadata
	aligned when changing the module name, version, BoxLang minimum version, or
	distribution identifiers.
- Preserve the public BIF signatures and documented defaults in `readme.md`.
	Update documentation and focused tests when behavior or options change.
- Keep edits scoped to the TOML module; do not treat this repository as a
	reusable template or change generated build artifacts by hand.
