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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.IStruct.TYPES;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.toml.exceptions.TomlSerializationException;
import ortus.boxlang.toml.util.TomlOptions;

/**
 * Unit tests for {@link TomlWriter} edge cases that aren't already covered by the official
 * conformance suite (which only exercises parsing): array-of-tables detection boundaries, key
 * quoting, and numeric range handling on the write side.
 */
public class TomlWriterTest extends BaseIntegrationTest {

	private TomlOptions defaultOptions() {
		return TomlOptions.resolve( context, Struct.EMPTY );
	}

	@Test
	@DisplayName( "A non-empty array of all-struct elements writes as [[array-of-tables]]" )
	public void testArrayOfTables() {
		IStruct	root	= Struct.linkedOf(
		    "servers", Array.fromList( java.util.List.of(
		        Struct.linkedOf( "name", "alpha" ),
		        Struct.linkedOf( "name", "beta" )
		    ) )
		);
		String	toml	= TomlWriter.write( root, defaultOptions() );
		assertThat( toml ).contains( "[[servers]]" );
		assertThat( toml ).doesNotContain( "servers = [" );
	}

	@Test
	@DisplayName( "An empty array writes as an inline empty array, not an array-of-tables" )
	public void testEmptyArrayIsInline() {
		IStruct	root	= Struct.linkedOf( "items", Array.EMPTY );
		String	toml	= TomlWriter.write( root, defaultOptions() );
		assertThat( toml.trim() ).isEqualTo( "items = []" );
	}

	@Test
	@DisplayName( "A mixed array (structs and scalars) writes inline, not as an array-of-tables" )
	public void testMixedArrayIsInline() {
		IStruct	root	= Struct.linkedOf(
		    "items", Array.fromList( java.util.List.of( "a", Struct.linkedOf( "k", 1L ) ) )
		);
		String	toml	= TomlWriter.write( root, defaultOptions() );
		assertThat( toml ).doesNotContain( "[[items]]" );
		assertThat( toml ).contains( "{ k = 1 }" );
	}

	@Test
	@DisplayName( "Keys that aren't bare TOML identifiers are quoted and escaped" )
	public void testKeyQuoting() {
		IStruct	root	= Struct.linkedOf( "with space", "v", "plain_key", "v2" );
		String	toml	= TomlWriter.write( root, defaultOptions() );
		assertThat( toml ).contains( "\"with space\" = \"v\"" );
		assertThat( toml ).contains( "plain_key = \"v2\"" );
	}

	@Test
	@DisplayName( "Scalars are written before sub-tables regardless of struct insertion order" )
	public void testScalarsBeforeSubTables() {
		IStruct	root	= Struct.linkedOf(
		    "table", Struct.linkedOf( "nested", "v" ),
		    "scalar", "first-in-struct-but-must-write-after-header"
		);
		String	toml	= TomlWriter.write( root, defaultOptions() );
		assertThat( toml.indexOf( "scalar" ) ).isLessThan( toml.indexOf( "[table]" ) );
	}

	@Test
	@DisplayName( "sortKeys alphabetizes keys within a table" )
	public void testSortKeys() {
		IStruct		root	= Struct.linkedOf( "b", 1L, "a", 2L );
		TomlOptions	sorted	= TomlOptions.resolve( context, Struct.of( "sortKeys", true ) );
		String		toml	= TomlWriter.write( root, sorted );
		assertThat( toml.indexOf( "a = " ) ).isLessThan( toml.indexOf( "b = " ) );
	}

	@Test
	@DisplayName( "A BigInteger within long range serializes as a plain integer" )
	public void testBigIntegerInRange() {
		IStruct	root	= Struct.linkedOf( "n", BigInteger.valueOf( 42L ) );
		String	toml	= TomlWriter.write( root, defaultOptions() );
		assertThat( toml.trim() ).isEqualTo( "n = 42" );
	}

	@Test
	@DisplayName( "A BigInteger out of TOML's 64-bit range throws TomlSerializationException" )
	public void testBigIntegerOutOfRangeThrows() {
		IStruct root = Struct.linkedOf( "n", BigInteger.valueOf( Long.MAX_VALUE ).add( BigInteger.TEN ) );
		assertThrows( TomlSerializationException.class, () -> TomlWriter.write( root, defaultOptions() ) );
	}

	@Test
	@DisplayName( "A whole-number BigDecimal serializes as a plain integer" )
	public void testWholeBigDecimalSerializesAsInteger() {
		IStruct	root	= Struct.linkedOf( "n", new BigDecimal( "100" ) );
		String	toml	= TomlWriter.write( root, defaultOptions() );
		assertThat( toml.trim() ).isEqualTo( "n = 100" );
	}

	@Test
	@DisplayName( "A fractional BigDecimal serializes as a TOML float" )
	public void testFractionalBigDecimalSerializesAsFloat() {
		IStruct	root	= Struct.linkedOf( "n", new BigDecimal( "3.5" ) );
		String	toml	= TomlWriter.write( root, defaultOptions() );
		assertThat( toml.trim() ).isEqualTo( "n = 3.5" );
	}

	@Test
	@DisplayName( "A non-struct root throws TomlSerializationException" )
	public void testNonStructRootThrows() {
		assertThrows( TomlSerializationException.class, () -> TomlWriter.write( "not a struct", defaultOptions() ) );
	}

	@Test
	@DisplayName( "Null-valued struct entries are silently skipped, not serialized" )
	public void testNullValuesAreSkipped() {
		IStruct root = new Struct( TYPES.LINKED );
		root.put( ortus.boxlang.runtime.scopes.Key.of( "present" ), "yes" );
		root.put( ortus.boxlang.runtime.scopes.Key.of( "absent" ), null );
		String toml = TomlWriter.write( root, defaultOptions() );
		assertThat( toml ).doesNotContain( "absent" );
		assertThat( toml ).contains( "present" );
	}

}
