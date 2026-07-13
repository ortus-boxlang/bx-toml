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

/**
 * Explicit, filepath-required counterpart to {@link TomlSerialize} for symmetry with
 * {@code tomlDeserializeFile()}.
 */
@BoxBIF
public class TomlSerializeFile extends BIF {

	private static TomlParser parser = TomlParser.getInstance();

	/**
	 * Constructor
	 */
	public TomlSerializeFile() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.ANY, Key.content ),
		    new Argument( true, Argument.STRING, Key.filepath ),
		    new Argument( false, Argument.STRING, Key.charset, Charset.defaultCharset().toString() ),
		    new Argument( false, Argument.STRUCT, KeyDictionary.options, Struct.EMPTY )
		};
	}

	/**
	 * Converts a BoxLang struct into a TOML document and writes it to a file.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.content The struct to convert to TOML. TOML documents always have a struct at the root.
	 *
	 * @argument.filepath The path to the file to write the TOML to.
	 *
	 * @argument.charset The charset to use when writing the file. Defaults to the system default charset.
	 *
	 * @argument.options A struct of options overriding this module's configured settings for this
	 *                   call: specVersion, sortKeys, indent, dateTimeStyle.
	 *
	 * @return void
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		parser.serializeToFile(
		    context,
		    arguments.get( Key.content ),
		    arguments.getAsString( Key.filepath ),
		    arguments.getAsString( Key.charset ),
		    ( IStruct ) arguments.get( KeyDictionary.options )
		);
		return null;
	}

}
