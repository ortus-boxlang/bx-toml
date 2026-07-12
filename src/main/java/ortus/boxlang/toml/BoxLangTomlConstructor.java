/**
 * [BoxLang]
 *
 * Copyright [2023] [Ortus Solutions, Corp]
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package ortus.boxlang.toml;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

import ortus.boxlang.runtime.dynamic.casters.DateTimeCaster;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.IStruct.TYPES;
import ortus.boxlang.runtime.types.Struct;

/**
 * Recursively converts a parsed tomlj {@link TomlTable}/{@link TomlArray} tree into BoxLang
 * types: {@link IStruct}, {@link ortus.boxlang.runtime.types.Array}, {@code DateTime}, and the
 * scalars ({@code String}, {@code Long}, {@code Double}, {@code Boolean}) that pass through as-is.
 *
 * TOML's 4 temporal kinds (offset-datetime, local-datetime, local-date, local-time) all collapse
 * to BoxLang's single DateTime type, using {@link DateTimeCaster}. Since a BoxLang DateTime always
 * carries both a date and a time, a bare TOML local-time (no date component) is anchored to
 * {@link #LOCAL_TIME_ANCHOR} rather than relying on any implicit "current date" behavior - this
 * is a deliberate, documented choice, not a value taken from the source document.
 */
public class BoxLangTomlConstructor {

	/**
	 * The epoch date used to anchor a bare TOML local-time into a BoxLang DateTime.
	 */
	public static final LocalDate LOCAL_TIME_ANCHOR = LocalDate.of( 1970, 1, 1 );

	private BoxLangTomlConstructor() {
	}

	/**
	 * Convert a parsed TOML table (typically the root {@code TomlParseResult}) into a BoxLang struct.
	 *
	 * TOML keys are case-sensitive ("Key" and "key" are distinct keys), so the resulting struct is
	 * always case-sensitive regardless of the {@code ordered} setting - only whether key
	 * declaration order is additionally preserved is configurable. Using a case-insensitive struct
	 * here would silently collide differently-cased TOML keys into a single BoxLang key.
	 *
	 * Keys are built with {@code new Key(String)} rather than the {@code Key.of(String)} factory:
	 * {@code Key.of} normalizes strings that look numeric (e.g. "01" collapses to the same key as
	 * "1"), which would silently merge two distinct TOML bare keys into one. The plain constructor
	 * preserves the exact source string.
	 *
	 * Entries are {@code put} directly onto the target struct rather than assembled in an
	 * intermediate {@code java.util.Map<Key, Object>} first: {@code Key.equals()}/{@code
	 * hashCode()} are always case-insensitive regardless of which struct type ultimately holds
	 * them (case-sensitive comparison only happens inside the struct's own storage), so an
	 * intermediate {@code HashMap}/{@code LinkedHashMap} keyed by {@code Key} would itself silently
	 * collapse differently-cased keys (e.g. "section" and "Section") before the case-sensitive
	 * struct ever saw them.
	 *
	 * @param table   The tomlj table to convert.
	 * @param ordered Whether to preserve TOML key declaration order via a linked struct.
	 */
	public static IStruct toStruct( TomlTable table, boolean ordered ) {
		IStruct target = new Struct( ordered ? TYPES.LINKED_CASE_SENSITIVE : TYPES.CASE_SENSITIVE );
		for ( Map.Entry<String, Object> entry : table.entrySet() ) {
			target.put( new Key( entry.getKey() ), convert( entry.getValue(), ordered ) );
		}
		return target;
	}

	/**
	 * Convert a parsed TOML array into a BoxLang array, recursively converting its elements.
	 */
	public static Array toArray( TomlArray array, boolean ordered ) {
		List<Object> list = new ArrayList<>( array.size() );
		for ( Object item : array.toList() ) {
			list.add( convert( item, ordered ) );
		}
		return Array.fromList( list );
	}

	private static Object convert( Object value, boolean ordered ) {
		if ( value instanceof TomlTable table ) {
			return toStruct( table, ordered );
		}
		if ( value instanceof TomlArray array ) {
			return toArray( array, ordered );
		}
		if ( value instanceof LocalTime time ) {
			return DateTimeCaster.cast( LocalDateTime.of( LOCAL_TIME_ANCHOR, time ).toString() );
		}
		if ( value instanceof LocalDate || value instanceof LocalDateTime || value instanceof OffsetDateTime ) {
			return DateTimeCaster.cast( value.toString() );
		}
		// String, Long, Double, Boolean all map directly - and tomlj enforces TOML's i64 integer
		// range at parse time, so integers are always a Long here, never a BigInteger.
		return value;
	}

}
