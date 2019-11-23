package legacy.decidir.sps.util;

import java.util.StringTokenizer;

import com.decidir.coretx.domain.Site;


public class AnalizeDns {
	
	private static String getRefer(String referer) {
		String refOut = referer;
		try {
//			refOut = request.getHeader("Referer");
			if (refOut != null)
				refOut = refOut.trim();
			else
				return null;
			int end = refOut.indexOf("/", 10);
			int begin = 0;
			if (end != -1)
				refOut = refOut.substring(begin, end);
		} catch (NullPointerException nullpointerexception) {
			throw new NullPointerException("Can't refer the last page");
		}
		return refOut;
	}

	public static boolean isClient(String referer, Site site, String urlOrigen)
//	public static boolean isClient(HttpServletRequest request, String nroTienda)
			throws Throwable {
		String UrlFile = site.url();// DBSPS.getDNSTiendaDecidir(nroTienda);
		String UrlNet = getRefer(referer);
		
		if (UrlNet == null || UrlNet.equals("http://localhost:8080")){
			return true;
		}
			
		
		StringTokenizer st = new StringTokenizer(UrlFile, ";");
		while (st.hasMoreElements()) {
			String urlBD = st.nextToken();
			if (UrlNet != null && urlBD != null
					&& UrlNet.trim().equalsIgnoreCase(urlBD.trim())){
				return true;
			}
			else{
				if (urlOrigen != null && urlBD != null
						&& urlOrigen.trim().equalsIgnoreCase(urlBD.trim())){
					return true;
				}
			}
		}
		return false;
	}
}
