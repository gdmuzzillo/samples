package decidir.sps.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class Utilities {
	
	
	public static String getSubstring(String _string, String _regexp)
	{
		Pattern p = Pattern.compile(_regexp, Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(_string);
		
		if(m.find()){
			return m.group(0);
		}
		
		return "";
	}

	public static String formatStringConMascara(String _string, String _formato ){
		if (_formato == null || _formato.equals(""))
			return "";
		String _formatstring = "";
		for (int i = 0; i < _formato.length(); i++) {
			if (_formato.charAt(i) == '#') {
				if (i < _string.length())
					_formatstring += _string.charAt(i);
			} else
				_formatstring += _formato.charAt(i);
		}
		return _formatstring;
	}
	
}