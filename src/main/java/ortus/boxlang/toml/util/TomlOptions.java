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
package ortus.boxlang.toml.util;

import org.tomlj.TomlVersion;

import ortus.boxlang.runtime.context.IBoxContext;
import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.IStruct;

/**
 * Resolves the effective set of TOML options for a single parse/serialize call.
 * Precedence: per-call {@code options} struct &gt; the module's configured {@code settings} &gt; hardcoded default.
 */
public class TomlOptions {

	public final String		specVersion;
	public final boolean	ordered;
	public final boolean	sortKeys;
	public final int		indent;
	public final String		dateTimeStyle;

	private TomlOptions( String specVersion, boolean ordered, boolean sortKeys, int indent, String dateTimeStyle ) {
		this.specVersion	= specVersion;
		this.ordered		= ordered;
		this.sortKeys		= sortKeys;
		this.indent			= indent;
		this.dateTimeStyle	= dateTimeStyle;
	}

	/**
	 * Resolve the effective options for a call, given the per-call options struct (may be empty, never null)
	 * and the invoking context (used to look up this module's configured settings).
	 */
	public static TomlOptions resolve( IBoxContext context, IStruct perCall ) {
		IStruct moduleSettings = context.getModuleSettings( KeyDictionary.moduleName );
		return new TomlOptions(
		    stringOpt( perCall, moduleSettings, KeyDictionary.specVersion, "1.0" ),
		    boolOpt( perCall, moduleSettings, KeyDictionary.ordered, true ),
		    boolOpt( perCall, moduleSettings, KeyDictionary.sortKeys, false ),
		    intOpt( perCall, moduleSettings, KeyDictionary.indent, 2 ),
		    stringOpt( perCall, moduleSettings, KeyDictionary.dateTimeStyle, "auto" )
		);
	}

	/**
	 * Maps the resolved {@link #specVersion} onto tomlj's version enum.
	 *
	 * TOML 1.1.0 is not yet a ratified spec upstream, and tomlj has no dedicated {@code V1_1_0}
	 * constant for it. {@code TomlVersion.HEAD} is tomlj's own best-effort development-spec parser -
	 * empirically verified (as of tomlj 1.1.1) to accept exactly the same grammar as
	 * {@code V1_0_0}, with none of the 1.1-track syntax (optional seconds in datetimes, trailing
	 * commas/multi-line inline tables, the {@code \e}/{@code \xHH} escapes) yet implemented. Rather
	 * than hand-rolling a text pre-processing pass to bridge that gap - which risks silently
	 * corrupting string content it wasn't meant to touch - {@code specVersion: "1.1"} is accepted
	 * today but currently behaves identically to 1.0.0. See the README's TOML Spec Version Support
	 * section. If tomlj ships real 1.1 grammar support in a future release, this is the only line
	 * that needs to change.
	 */
	public TomlVersion toTomlVersion() {
		return "1.1".equals( specVersion ) ? TomlVersion.HEAD : TomlVersion.V1_0_0;
	}

	private static String stringOpt( IStruct perCall, IStruct moduleSettings, Key key, String fallback ) {
		if ( perCall.containsKey( key ) ) {
			return perCall.getAsString( key );
		}
		if ( moduleSettings != null && moduleSettings.containsKey( key ) ) {
			return moduleSettings.getAsString( key );
		}
		return fallback;
	}

	private static boolean boolOpt( IStruct perCall, IStruct moduleSettings, Key key, boolean fallback ) {
		if ( perCall.containsKey( key ) ) {
			return perCall.getAsBoolean( key );
		}
		if ( moduleSettings != null && moduleSettings.containsKey( key ) ) {
			return moduleSettings.getAsBoolean( key );
		}
		return fallback;
	}

	private static int intOpt( IStruct perCall, IStruct moduleSettings, Key key, int fallback ) {
		if ( perCall.containsKey( key ) ) {
			return perCall.getAsInteger( key );
		}
		if ( moduleSettings != null && moduleSettings.containsKey( key ) ) {
			return moduleSettings.getAsInteger( key );
		}
		return fallback;
	}

}
