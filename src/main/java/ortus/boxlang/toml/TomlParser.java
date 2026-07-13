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

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.runtime.types.exceptions.BoxIOException;
import ortus.boxlang.runtime.util.FileSystemUtil;
import ortus.boxlang.toml.exceptions.TomlParseException;
import ortus.boxlang.toml.util.TomlOptions;

/**
 * Singleton facade for parsing and serializing TOML, mirroring the shape of bx-yaml's
 * {@code YamlParser}. Parsing is delegated to tomlj; serialization is hand-written - see
 * {@link TomlWriter}.
 */
public final class TomlParser {

	private static final TomlParser instance = new TomlParser();

	private TomlParser() {
	}

	public static TomlParser getInstance() {
		return instance;
	}

	/**
	 * Deserialize a TOML string into a BoxLang struct.
	 *
	 * @param context The context of execution.
	 * @param toml    The TOML string to parse.
	 * @param options Per-call options overriding the module's configured settings.
	 *
	 * @return The parsed BoxLang struct.
	 */
	public IStruct deserialize( IBoxContext context, String toml, IStruct options ) {
		TomlOptions resolved = TomlOptions.resolve( context, options );
		// Strip a leading UTF-8 BOM (U+FEFF), if present - TOML documents are UTF-8 text and don't
		// expect one, and tomlj treats it as invalid leading input rather than ignoring it.
		if ( !toml.isEmpty() && toml.charAt( 0 ) == '﻿' ) {
			toml = toml.substring( 1 );
		}
		// Normalize CRLF to LF so that TOML parsing produces consistent string values
		// regardless of the line-ending style that the OS or VCS checkout introduced.
		// TOML 1.0.0 §1: "Newline means LF (0x0A) or CRLF (0x0D 0x0A)" - both are valid
		// document line endings, but tomlj preserves CRLF verbatim inside multiline strings
		// whereas the toml-test conformance fixtures always expect LF.
		if ( toml.indexOf( '\r' ) >= 0 ) {
			toml = toml.replace( "\r\n", "\n" );
		}
		TomlParseResult result;
		try {
			result = Toml.parse( toml, resolved.toTomlVersion() );
		} catch ( RuntimeException | AssertionError unexpected ) {
			// tomlj's ANTLR-generated parser can hit an internal assertion/exception on some
			// malformed input (e.g. truncated unicode escapes) instead of reporting a graceful
			// TomlParseError. Surface it to BoxLang code as a normal, catchable parse error rather
			// than an uncaught error from a third-party library.
			throw new TomlParseException( "malformed input rejected by the underlying parser", unexpected );
		}
		if ( result.hasErrors() ) {
			throw new TomlParseException( result.errors() );
		}
		return BoxLangTomlConstructor.toStruct( result, resolved.ordered );
	}

	/**
	 * Deserialize a TOML file into a BoxLang struct.
	 *
	 * @param context The context of execution.
	 * @param path    The path to the TOML file.
	 * @param charset The charset to use when reading the file.
	 * @param options Per-call options overriding the module's configured settings.
	 *
	 * @return The parsed BoxLang struct.
	 */
	public IStruct deserializeFromFile( IBoxContext context, String path, String charset, IStruct options ) {
		Path filePath = FileSystemUtil.expandPath( context, path ).absolutePath();
		try {
			String content = Files.readString( filePath, Charset.forName( charset ) );
			return deserialize( context, content, options );
		} catch ( IOException e ) {
			throw new BoxIOException( "Error reading TOML file: " + path, e );
		}
	}

	/**
	 * Serialize a BoxLang value into a TOML string.
	 *
	 * @param context The context of execution.
	 * @param content The value to serialize - must be a struct at the root.
	 * @param options Per-call options overriding the module's configured settings.
	 *
	 * @return The TOML string.
	 */
	public String serialize( IBoxContext context, Object content, IStruct options ) {
		TomlOptions resolved = TomlOptions.resolve( context, options );
		return TomlWriter.write( content, resolved );
	}

	/**
	 * Serialize a BoxLang value directly to a file destination path.
	 *
	 * @param context The context of execution.
	 * @param content The value to serialize - must be a struct at the root.
	 * @param path    The absolute path to serialize the value to.
	 * @param charset The charset to use when writing the file.
	 * @param options Per-call options overriding the module's configured settings.
	 */
	public void serializeToFile( IBoxContext context, Object content, String path, String charset, IStruct options ) {
		String	filePath	= FileSystemUtil.expandPath( context, path ).absolutePath().toString();
		String	toml		= serialize( context, content, options );
		try ( Writer writer = Files.newBufferedWriter( Path.of( filePath ), Charset.forName( charset ) ) ) {
			writer.write( toml );
		} catch ( IOException e ) {
			throw new BoxIOException( "Error writing TOML file: " + path, e );
		}
	}

}
