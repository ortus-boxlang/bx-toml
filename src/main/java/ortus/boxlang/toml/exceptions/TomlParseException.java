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
package ortus.boxlang.toml.exceptions;

import java.util.List;

import org.tomlj.TomlParseError;

import ortus.boxlang.runtime.types.exceptions.BoxRuntimeException;

/**
 * Thrown when a TOML document fails to parse. Wraps every error tomlj reported for the document
 * (TOML documents can contain multiple syntax errors) into a single, readable message with
 * line/column detail for each.
 */
public class TomlParseException extends BoxRuntimeException {

	public TomlParseException( List<TomlParseError> errors ) {
		super( buildMessage( errors ) );
	}

	/**
	 * Used when tomlj's parser itself throws or asserts on malformed input instead of reporting a
	 * graceful {@code TomlParseError} - see {@code TomlParser.deserialize}.
	 */
	public TomlParseException( String message, Throwable cause ) {
		super( "TOML parse error: " + message, cause );
	}

	private static String buildMessage( List<TomlParseError> errors ) {
		StringBuilder sb = new StringBuilder( "TOML parse error(s):" );
		for ( TomlParseError error : errors ) {
			// TomlParseError#toString() already embeds the "(line X, column Y)" detail.
			sb.append( "\n  " ).append( error.toString() );
		}
		return sb.toString();
	}

}
