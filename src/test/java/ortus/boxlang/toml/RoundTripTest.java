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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;

/**
 * Parse -> serialize -> parse again -> deep-equal tests over representative, real-world-shaped
 * TOML documents, plus an explicit pin of the documented lossy case from collapsing TOML's 4
 * temporal kinds into a single BoxLang DateTime (see BoxLangTomlConstructor/TomlWriter).
 */
public class RoundTripTest extends BaseIntegrationTest {

	private static final String CARGO_LIKE = """
	                                         name = "bx-toml"
	                                         version = "1.0.0"
	                                         authors = [ "Ortus Solutions" ]

	                                         [dependencies]
	                                         tomlj = "1.1.1"

	                                         [[bin]]
	                                         name = "main"
	                                         path = "src/main.bx"

	                                         [[bin]]
	                                         name = "cli"
	                                         path = "src/cli.bx"

	                                         [profile]
	                                         debug = true
	                                         opt-level = 3
	                                         ratio = 0.5
	                                         """;

	@Test
	@DisplayName( "A Cargo.toml-shaped document round-trips to an equal struct" )
	public void testCargoLikeRoundTrip() {
		TomlParser	parser	= TomlParser.getInstance();
		IStruct		first	= parser.deserialize( context, CARGO_LIKE, Struct.EMPTY );
		String		toml	= parser.serialize( context, first, Struct.EMPTY );
		IStruct		second	= parser.deserialize( context, toml, Struct.EMPTY );

		assertThat( second ).isEqualTo( first );
	}

	@Test
	@DisplayName( "Deeply nested tables round-trip to an equal struct" )
	public void testDeepNestingRoundTrip() {
		TomlParser	parser	= TomlParser.getInstance();
		String		toml	= String.join( "\n", "[a.b.c.d.e]", "value = 42", "" );
		IStruct		first	= parser.deserialize( context, toml, Struct.EMPTY );
		IStruct		second	= parser.deserialize( context, parser.serialize( context, first, Struct.EMPTY ), Struct.EMPTY );

		assertThat( second ).isEqualTo( first );
	}

	@Test
	@DisplayName( "Known limitation: a bare local-date and a midnight offset-datetime are not distinguishable after round-trip" )
	public void testDateTimeCollapseIsLossyAsDocumented() {
		TomlParser	parser			= TomlParser.getInstance();
		String		toml			= String.join( "\n", "d = 2024-01-01", "t = 2024-01-01T00:00:00Z", "" );
		IStruct		parsedOnce		= parser.deserialize( context, toml, Struct.EMPTY );
		IStruct		options			= Struct.of( "dateTimeStyle", "local-date" );
		String		reserialized	= parser.serialize( context, parsedOnce, options );

		// Both values collapse to the same BoxLang DateTime shape. An explicit style keeps this
		// assertion independent of the host operating system's default timezone.
		assertThat( reserialized ).doesNotContain( "T00:00:00" );
		long dateLines = reserialized.lines().filter( line -> line.matches( "[dt] = \\d{4}-\\d{2}-\\d{2}" ) ).count();
		assertThat( dateLines ).isEqualTo( 2 );
	}

}
