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
package ortus.boxlang.toml.bifs;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.toml.BaseIntegrationTest;

/**
 * Integration tests for tomlDeserializeFile() / tomlSerializeFile(), exercised through real
 * BoxLang source against files on disk.
 */
public class TomlFileBifsTest extends BaseIntegrationTest {

	@Test
	@DisplayName( "tomlSerializeFile() writes a file that tomlDeserializeFile() reads back" )
	public void testWriteThenReadFile( @TempDir Path dir ) throws IOException {
		Path target = dir.resolve( "out.toml" );

		// @formatter:off
		runtime.executeSource(
		    """
			data = { title: "bx-toml", owner: { name: "Ortus" } };
			tomlSerializeFile( data, "%s" );
			result = tomlDeserializeFile( "%s" );
			""".formatted( toBoxLangPath( target ), toBoxLangPath( target ) ),
		    context
		);
		// @formatter:on

		assertThat( Files.exists( target ) ).isTrue();
		IStruct result = ( IStruct ) variables.get( "result" );
		assertThat( result.getAsString( ortus.boxlang.runtime.scopes.Key.of( "title" ) ) ).isEqualTo( "bx-toml" );
	}

	@Test
	@DisplayName( "tomlDeserializeFile() honors an explicit charset" )
	public void testExplicitCharset( @TempDir Path dir ) throws IOException {
		Path target = dir.resolve( "charset.toml" );
		Files.writeString( target, "name = \"café\"\n", StandardCharsets.ISO_8859_1 );

		// @formatter:off
		runtime.executeSource(
		    """
			result = tomlDeserializeFile( "%s", "ISO-8859-1" );
			""".formatted( toBoxLangPath( target ) ),
		    context
		);
		// @formatter:on

		IStruct result = ( IStruct ) variables.get( "result" );
		assertThat( result.getAsString( ortus.boxlang.runtime.scopes.Key.of( "name" ) ) ).isEqualTo( "café" );
	}

	private static String toBoxLangPath( Path path ) {
		return path.toAbsolutePath().toString().replace( "\\", "\\\\" );
	}

}
