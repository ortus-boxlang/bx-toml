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
public class TomlSerialize extends BIF {

	private static TomlParser parser = TomlParser.getInstance();

	/**
	 * Constructor
	 */
	public TomlSerialize() {
		super();
		declaredArguments = new Argument[] {
		    new Argument( true, Argument.ANY, Key.content ),
		    new Argument( false, Argument.STRING, Key.filepath ),
		    new Argument( false, Argument.STRING, Key.charset, Charset.defaultCharset().toString() ),
		    new Argument( false, Argument.STRUCT, KeyDictionary.options, Struct.EMPTY )
		};
	}

	/**
	 * Converts a BoxLang struct into a TOML string, or writes it directly to a file.
	 *
	 * @param context   The context in which the BIF is being invoked.
	 * @param arguments Argument scope for the BIF.
	 *
	 * @argument.content The struct to convert to TOML. TOML documents always have a struct at the root.
	 *
	 * @argument.filepath The path to the file to write the TOML to. If not provided, the TOML is
	 *                    returned as a string.
	 *
	 * @argument.charset The charset to use when writing the file. Defaults to the system default charset.
	 *
	 * @argument.options A struct of options overriding this module's configured settings for this
	 *                   call: specVersion, sortKeys, indent, dateTimeStyle.
	 *
	 * @return The TOML string, or null if a filepath was provided.
	 */
	public Object _invoke( IBoxContext context, ArgumentsScope arguments ) {
		String	filePath	= arguments.getAsString( Key.filepath );
		Object	content		= arguments.get( Key.content );
		String	charset		= arguments.getAsString( Key.charset );
		IStruct	options		= ( IStruct ) arguments.get( KeyDictionary.options );

		if ( filePath == null ) {
			return parser.serialize( context, content, options );
		}

		parser.serializeToFile( context, content, filePath, charset, options );
		return null;
	}

}
