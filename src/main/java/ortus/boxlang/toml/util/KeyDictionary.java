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

import ortus.boxlang.runtime.scopes.Key;

/**
 * This class is used to store all the keys used in the dictionary for this module.
 */
public class KeyDictionary {

	public static final Key	moduleName		= new Key( "bxtoml" );

	public static final Key	TOML			= new Key( "toml" );
	public static final Key	options			= new Key( "options" );
	public static final Key	specVersion		= new Key( "specVersion" );
	public static final Key	ordered			= new Key( "ordered" );
	public static final Key	sortKeys		= new Key( "sortKeys" );
	public static final Key	indent			= new Key( "indent" );
	public static final Key	dateTimeStyle	= new Key( "dateTimeStyle" );

}
