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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.tomlj.Toml;

import ortus.boxlang.runtime.scopes.Key;
import ortus.boxlang.runtime.types.Array;
import ortus.boxlang.runtime.types.DateTime;
import ortus.boxlang.runtime.types.IStruct;
import ortus.boxlang.toml.exceptions.TomlSerializationException;
import ortus.boxlang.toml.util.TomlOptions;

/**
 * Hand-written TOML serializer that walks a BoxLang {@link IStruct}/{@link Array} tree and emits
 * valid TOML text. tomlj (the parsing library this module uses) has no public writer, so this
 * class owns serialization entirely - though it reuses tomlj's {@link Toml#tomlEscape(String)}
 * utility for spec-correct string escaping rather than reimplementing that logic.
 *
 * TOML requires that within any table, all direct {@code key = value} pairs be written before any
 * {@code [table]}/{@code [[array-of-tables]]} sub-header, recursively - this class enforces that
 * ordering regardless of the incoming struct's own key order.
 */
public final class TomlWriter {

	private static final Pattern BARE_KEY = Pattern.compile( "[A-Za-z0-9_-]+" );

	private TomlWriter() {
	}

	/**
	 * Serialize a BoxLang value to a TOML document. The root value must be a struct - TOML
	 * documents are always a table.
	 */
	public static String write( Object content, TomlOptions options ) {
		if ( ! ( content instanceof IStruct ) ) {
			throw new TomlSerializationException(
			    "TOML documents must have a struct at the root; received "
			        + ( content == null ? "null" : content.getClass().getName() )
			);
		}
		StringBuilder out = new StringBuilder();
		writeTable( ( IStruct ) content, new ArrayList<>(), out, options );
		// Drop the single leading blank line written before the first [table]/[[array]] header, if any.
		return out.length() > 0 && out.charAt( 0 ) == '\n' ? out.substring( 1 ) : out.toString();
	}

	private static void writeTable( IStruct table, List<String> path, StringBuilder out, TomlOptions options ) {
		List<Map.Entry<Key, Object>> entries = new ArrayList<>( table.entrySet() );
		if ( options.sortKeys ) {
			entries.sort( ( a, b ) -> a.getKey().getName().compareToIgnoreCase( b.getKey().getName() ) );
		}

		List<Map.Entry<Key, Object>>	scalars		= new ArrayList<>();
		List<Map.Entry<Key, Object>>	subTables	= new ArrayList<>();
		List<Map.Entry<Key, Object>>	tableArrays	= new ArrayList<>();

		for ( Map.Entry<Key, Object> entry : entries ) {
			Object value = entry.getValue();
			if ( value == null ) {
				continue;
			}
			if ( value instanceof IStruct ) {
				subTables.add( entry );
			} else if ( isTableArray( value ) ) {
				tableArrays.add( entry );
			} else {
				scalars.add( entry );
			}
		}

		String pad = " ".repeat( options.indent * path.size() );
		for ( Map.Entry<Key, Object> entry : scalars ) {
			out.append( pad )
			    .append( formatKey( entry.getKey().getName() ) )
			    .append( " = " )
			    .append( formatValue( entry.getValue(), options ) )
			    .append( '\n' );
		}

		for ( Map.Entry<Key, Object> entry : subTables ) {
			List<String> newPath = withSegment( path, entry.getKey().getName() );
			out.append( '\n' ).append( '[' ).append( formatPath( newPath ) ).append( ']' ).append( '\n' );
			writeTable( ( IStruct ) entry.getValue(), newPath, out, options );
		}

		for ( Map.Entry<Key, Object> entry : tableArrays ) {
			List<String>	newPath	= withSegment( path, entry.getKey().getName() );
			Array			arr		= ( Array ) entry.getValue();
			for ( Object element : arr ) {
				out.append( '\n' ).append( "[[" ).append( formatPath( newPath ) ).append( "]]" ).append( '\n' );
				writeTable( ( IStruct ) element, newPath, out, options );
			}
		}
	}

	/**
	 * A non-empty array where every element is a struct is written as a TOML array-of-tables
	 * ({@code [[...]]}). Anything else (empty arrays, or arrays mixing scalars/structs/nested
	 * arrays) is written as a plain inline array.
	 */
	private static boolean isTableArray( Object value ) {
		if ( ! ( value instanceof Array arr ) || arr.isEmpty() ) {
			return false;
		}
		for ( Object element : arr ) {
			if ( ! ( element instanceof IStruct ) ) {
				return false;
			}
		}
		return true;
	}

	private static String formatValue( Object value, TomlOptions options ) {
		if ( value == null ) {
			throw new TomlSerializationException( "TOML has no representation for null values" );
		}
		if ( value instanceof String s ) {
			return formatString( s );
		}
		if ( value instanceof Boolean b ) {
			return b.toString();
		}
		if ( value instanceof DateTime dt ) {
			return formatDateTime( dt, options );
		}
		if ( value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte ) {
			return value.toString();
		}
		if ( value instanceof BigInteger bi ) {
			return formatBigInteger( bi );
		}
		if ( value instanceof BigDecimal bd ) {
			return formatBigDecimal( bd );
		}
		if ( value instanceof Double || value instanceof Float ) {
			return formatFloat( ( ( Number ) value ).doubleValue() );
		}
		if ( value instanceof Array arr ) {
			return formatInlineArray( arr, options );
		}
		if ( value instanceof IStruct st ) {
			return formatInlineTable( st, options );
		}
		throw new TomlSerializationException( "Cannot serialize a value of type " + value.getClass().getName() + " to TOML" );
	}

	private static String formatInlineArray( Array arr, TomlOptions options ) {
		StringBuilder	content	= new StringBuilder();
		boolean			first	= true;
		for ( Object element : arr ) {
			if ( !first ) {
				content.append( ", " );
			}
			content.append( formatValue( element, options ) );
			first = false;
		}
		return content.isEmpty() ? "[]" : "[ " + content + " ]";
	}

	private static String formatInlineTable( IStruct st, TomlOptions options ) {
		List<Map.Entry<Key, Object>> entries = new ArrayList<>( st.entrySet() );
		if ( options.sortKeys ) {
			entries.sort( ( a, b ) -> a.getKey().getName().compareToIgnoreCase( b.getKey().getName() ) );
		}
		StringBuilder	content	= new StringBuilder();
		boolean			first	= true;
		for ( Map.Entry<Key, Object> entry : entries ) {
			if ( entry.getValue() == null ) {
				continue;
			}
			if ( !first ) {
				content.append( ", " );
			}
			content.append( formatKey( entry.getKey().getName() ) ).append( " = " ).append( formatValue( entry.getValue(), options ) );
			first = false;
		}
		return content.isEmpty() ? "{}" : "{ " + content + " }";
	}

	private static String formatString( String s ) {
		return "\"" + Toml.tomlEscape( s ) + "\"";
	}

	private static String formatFloat( double d ) {
		if ( Double.isNaN( d ) ) {
			return "nan";
		}
		if ( Double.isInfinite( d ) ) {
			return d > 0 ? "inf" : "-inf";
		}
		// Java's Double.toString always includes a decimal point or exponent, matching TOML's
		// requirement that a float be visually distinguishable from an integer.
		return Double.toString( d );
	}

	private static final BigInteger	LONG_MIN	= BigInteger.valueOf( Long.MIN_VALUE );
	private static final BigInteger	LONG_MAX	= BigInteger.valueOf( Long.MAX_VALUE );

	private static String formatBigInteger( BigInteger bi ) {
		if ( bi.compareTo( LONG_MIN ) >= 0 && bi.compareTo( LONG_MAX ) <= 0 ) {
			return bi.toString();
		}
		throw new TomlSerializationException( "Integer value out of TOML's 64-bit integer range: " + bi );
	}

	private static String formatBigDecimal( BigDecimal bd ) {
		try {
			BigInteger exact = bd.toBigIntegerExact();
			if ( exact.compareTo( LONG_MIN ) >= 0 && exact.compareTo( LONG_MAX ) <= 0 ) {
				return exact.toString();
			}
			throw new TomlSerializationException( "Integer value out of TOML's 64-bit integer range: " + bd );
		} catch ( ArithmeticException notAWholeNumber ) {
			double d = bd.doubleValue();
			if ( Double.isInfinite( d ) ) {
				throw new TomlSerializationException( "Numeric value out of TOML's representable range: " + bd );
			}
			return formatFloat( d );
		}
	}

	/**
	 * Emits a TOML date/time literal for a BoxLang DateTime. TOML has 4 distinct temporal kinds
	 * but BoxLang collapses all of them into a single DateTime on parse (see
	 * {@link BoxLangTomlConstructor}), so the exact original TOML kind cannot always be recovered.
	 * When {@code dateTimeStyle} is "auto" (the default), a heuristic is used:
	 *
	 * <ul>
	 * <li>If the DateTime's offset differs from the system default zone's offset at that instant,
	 * it was very likely an explicit offset in the source - emit offset-datetime.</li>
	 * <li>Otherwise, if the date matches {@link BoxLangTomlConstructor#LOCAL_TIME_ANCHOR} (the
	 * anchor used for bare TOML local-times on parse), emit local-time.</li>
	 * <li>Otherwise, if the time-of-day is exactly midnight, emit local-date.</li>
	 * <li>Otherwise, emit local-datetime.</li>
	 * </ul>
	 *
	 * This is a best-effort heuristic, not a guarantee - see the README's "Known Limitations"
	 * section. {@code dateTimeStyle} can be forced to bypass it entirely.
	 */
	private static String formatDateTime( DateTime dt, TomlOptions options ) {
		String style = options.dateTimeStyle;
		if ( "auto".equals( style ) ) {
			style = inferDateTimeStyle( dt );
		}
		return switch ( style ) {
			case "offset-datetime" -> dt.getWrapped().toOffsetDateTime().format( DateTimeFormatter.ISO_OFFSET_DATE_TIME );
			case "local-datetime" -> dt.getWrapped().toLocalDateTime().format( DateTimeFormatter.ISO_LOCAL_DATE_TIME );
			case "local-date" -> dt.toLocalDate().format( DateTimeFormatter.ISO_LOCAL_DATE );
			case "local-time" -> dt.toLocalTime().format( DateTimeFormatter.ISO_LOCAL_TIME );
			default -> throw new TomlSerializationException( "Unknown dateTimeStyle: " + style );
		};
	}

	private static String inferDateTimeStyle( DateTime dt ) {
		ZoneOffset	actualOffset	= dt.getOffset();
		ZoneOffset	localOffset		= ZoneId.systemDefault().getRules().getOffset( dt.toInstant() );
		if ( !actualOffset.equals( localOffset ) ) {
			return "offset-datetime";
		}
		if ( dt.toLocalDate().equals( BoxLangTomlConstructor.LOCAL_TIME_ANCHOR ) ) {
			return "local-time";
		}
		if ( dt.toLocalTime().toSecondOfDay() == 0 && dt.toLocalTime().getNano() == 0 ) {
			return "local-date";
		}
		return "local-datetime";
	}

	private static String formatKey( String key ) {
		return BARE_KEY.matcher( key ).matches() ? key : "\"" + Toml.tomlEscape( key ) + "\"";
	}

	private static String formatPath( List<String> path ) {
		StringBuilder sb = new StringBuilder();
		for ( int i = 0; i < path.size(); i++ ) {
			if ( i > 0 ) {
				sb.append( '.' );
			}
			sb.append( formatKey( path.get( i ) ) );
		}
		return sb.toString();
	}

	private static List<String> withSegment( List<String> path, String segment ) {
		List<String> newPath = new ArrayList<>( path );
		newPath.add( segment );
		return newPath;
	}

}
