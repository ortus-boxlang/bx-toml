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

import java.nio.charset.Charset;

import ortus.boxlang.runtime.bifs.BIF;
import ortus.boxlang.runtime.bifs.BoxBIF;
import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.ArgumentsScope;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Argument;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.Struct;
import ortus.boxlang.toml.TomlParser;
import ortus.boxlang.toml.util.KeyDictionary;

@BoxBIF
public class TomlDeserializeFile extends BIF {

	private static TomlParser parser = TomlParser.getInstance();

	/**
	 * Constructor
	 */
	public TomlDeserializeFile() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.STRING, Key.path ),
		    new Argument( false, Argument.STRING, Key.charset, Charset.defaultCharset().toString() ),
		    new Argument( false, Argument.STRUCT, KeyDictionary.options, Struct.EMPTY )
		};
	}

	/**
	 * Parses a TOML file into a BoxLang struct.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.path The path to the TOML file to parse.
	 *
	 * @argument.charset The charset to use when reading the file. Defaults to the system default charset.
	 *
	 * @argument.options A struct of options overriding this module's configured settings for this
	 *                   call: specVersion, ordered.
	 *
	 * @return The parsed BoxLang struct.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		return parser.deserializeFromFile(
		    context,
		    arguments.getAsString( Key.path ),
		    arguments.getAsString( Key.charset ),
		    ( IStruct ) arguments.get( KeyDictionary.options )
		);
	}

}
