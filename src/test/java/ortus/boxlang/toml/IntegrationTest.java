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

/**
 * This loads the module and runs an integration test on the module.
 */
public class IntegrationTest extends BaseIntegrationTest {

	@DisplayName( "Test the module loads in BoxLang" )
	@Test
	public void testModuleLoads() {
		assertThat( moduleService.getRegistry().containsKey( moduleName ) ).isTrue();
	}

	@DisplayName( "Test tomlDeserialize and tomlSerialize round trip via BIFs" )
	@Test
	public void testDeserializeSerializeRoundTrip() {
		// @formatter:off
		runtime.executeSource(
		    """
			toml = 'title = "bx-toml"
			[owner]
			name = "Ortus"';
			data = tomlDeserialize( toml );
			result = tomlSerialize( data );
			""",
		    context
		);
		// @formatter:on

		IStruct data = ( IStruct ) variables.get( "data" );
		assertThat( data.getAsString( ortus.boxlang.runtime.scopes.Key.of( "title" ) ) ).isEqualTo( "bx-toml" );

		String tomlOut = ( String ) variables.get( result );
		assertThat( tomlOut ).contains( "title" );
		assertThat( tomlOut ).contains( "[owner]" );
	}
}
