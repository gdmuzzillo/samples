/* vim:ts=4:sw=4:ai
 */
package legacy.decidir.sps.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateParser {
	static Locale currentLocale = new Locale("es", "AR");
	static TimeZone currentTimeZone = TimeZone.getDefault();
	static String datefmt = "dd/MM/yyyy";
	static String datefmtFull = "dd/MM/yyyy HH:mm:ss";
	static String datefmtAMPM = "dd/MM/yyyy hh:mm:ss aa";
	static SimpleDateFormat formato = new SimpleDateFormat(datefmt,
			currentLocale);
	static SimpleDateFormat formatoFull = new SimpleDateFormat(datefmtFull,
			currentLocale);
	static SimpleDateFormat formatoAMPM = new SimpleDateFormat(datefmtAMPM,
			currentLocale);

	static {
		formato.setTimeZone(currentTimeZone);
		formatoFull.setTimeZone(currentTimeZone);
		formatoAMPM.setTimeZone(currentTimeZone);
	}

	public static Date parse(String cadenaFecha) {
		try {
			return formato.parse(cadenaFecha);
		} catch (java.text.ParseException e) {
			throw new RuntimeException("Fecha inválida: " + cadenaFecha);
		}
	}

	public static Date parse(String cadenaFecha, String strFormato) {
		SimpleDateFormat fmt = new SimpleDateFormat(strFormato, currentLocale);
		fmt.setTimeZone(currentTimeZone);
		try {
			return fmt.parse(cadenaFecha);
		} catch (java.text.ParseException e) {
			throw new RuntimeException("Fecha inválida: " + cadenaFecha);
		}
	}

	public static String parse(Date Fecha) {
		return formato.format(Fecha);
	}

	public static String parse(Date Fecha, String strFormato) {
		SimpleDateFormat fmt = new SimpleDateFormat(strFormato, currentLocale);
		fmt.setTimeZone(currentTimeZone);
		return fmt.format(Fecha);
	}

	public static Date parseFechayHora(String cadenaFechaHora) {
		try {
			return formatoAMPM.parse(cadenaFechaHora);
		} catch (java.text.ParseException e) {
			throw new RuntimeException("Fecha inválida: " + cadenaFechaHora);
		}
	}

	public static String parseFechaCorrecta(Date Fecha, String strFormato) {
		return parse(Fecha, strFormato);
	}
}
