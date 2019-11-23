package legacy.decidir.sps.offline;

import com.decidir.coretx.domain.DatosOffline;
import decidir.sps.core.Site;
import play.Logger;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;


public class ProtocoloCobroExpress {
	
	public static final int idmediopago = 51;
	public static final int codigoDecidir = 1253;
	String isNumeric = "^[0-9]*$";

	public ProtocoloCobroExpress() {
	}

	public String obtenerCodigo(DatosOffline datos) throws Exception {

		String monto = (String) datos.getParametro("MONTO");
		String fechavtoOriginal = (String) datos.getParametro("FECHAVTO");
		String fechavto = "";
		if (fechavtoOriginal != null && !fechavtoOriginal.isEmpty()){
			fechavto = formatFechaString(fechavtoOriginal);
		}

//		String siteId = datos.getParametro("site");

		int empresaServicio = 0;
		try {
			empresaServicio = Integer.valueOf(datos.getParametro("NROTIENDA"));
		} catch (Exception e) {
			Logger.error(e.getMessage());
		}

		String empresa = completarCero(Integer.toString(empresaServicio), 4);
		
		String nroOperacion = completarCero((String) datos.getParametro("NROOPERACION"), 15);
		String recargo = (String) datos.getParametro("RECARGO");
		String fechavto2Original = (String) datos.getParametro("FECHAVTO2");
		String fechavto2 = "";
		if (fechavto2Original != null && !fechavto2Original.isEmpty()){
			fechavto2 = formatFechaString(fechavto2Original);
		}

		int comapos = monto.indexOf(".");
		if (comapos < 0){
			comapos = monto.indexOf(",");
		}
		if (comapos > -1) {
			String parteentera = monto.substring(0, comapos);
			String partedecimal = monto.substring(comapos + 1, monto.length());
			monto = parteentera + partedecimal;
		}
		monto = completarCero(monto, 8);
		
		Integer monto1 = new Integer(monto);
		if (recargo == null || recargo.trim().isEmpty()){
			recargo = "0";
		}
		
		Integer recargo1 = new Integer(recargo);
		if (recargo1 != 0) {
			if (monto1 > recargo1){
				throw new Exception("Parametro Invalido - El monto de recargo no puede ser menor al monto original");
			}
			monto1 = recargo1 - monto1;
			recargo = completarCero(String.valueOf(monto1), 6);
		} else {
			recargo = completarCero(String.valueOf(recargo1), 6);
		}
		
		String difDias = "";
		if(fechavto2Original.isEmpty()){
			
			validarParametros(monto, fechavtoOriginal, empresa, nroOperacion, recargo);
			difDias = completarCero(difDias, 2);
		}else{
			
			String difDiasVencimiento = calcularDiferenciaDias(fechavtoOriginal, fechavto2Original);
			difDiasVencimiento = completarCero(difDiasVencimiento, 2);
			validarParametros(monto, fechavtoOriginal, empresa, nroOperacion, recargo, difDiasVencimiento);
			difDias =  difDiasVencimiento;
		}
		
		fechavtoOriginal = invertirFecha(fechavtoOriginal);

		String codigo = codigoDecidir + empresa + monto + fechavtoOriginal + nroOperacion + recargo + difDias;
		
		return calcularDigito(codigo);
	}
	
	private String invertirFecha(String cadena) {

		String dia;
		String mes;
		String anio;

		anio = cadena.substring(0, 2);
		mes = cadena.substring(2, 4);
		dia = cadena.substring(4, 6);
		
		cadena = dia + mes + anio;
		
		return cadena;
	}

	private String calcularDiferenciaDias(String fechavtoOriginal, String fechavto2Original) {

		String diferencia = String.valueOf(Integer.parseInt(fechavto2Original) - Integer.parseInt(fechavtoOriginal));
		
		return diferencia;
	}

	private void validarParametros(String monto, String fechavtoOriginal,
			String empresa, String nroOperacion, String recargo) throws Exception {

		if (empresa.length() != 4)
			throw new Exception(" - Parametro Invalido - La longitud del parametro 'NROCOMERCIO' no es de 4 digitos ");
		else if (monto.length() != 8)
			throw new Exception("Parametro Invalido - La longitud del parametro 'MONTO' no es de 8 digitos ");
		else if (fechavtoOriginal.length() != 6)
			throw new Exception("Parametro Invalido - La longitud del parametro 'FECHAVTO' no es de 6 digitos ");
		else if (!isNumeric.matches(fechavtoOriginal))
			throw new Exception("Parametro Invalido - El parametro 'FECHAVTO' no es numerico ");
		else if (nroOperacion.length() != 15)
			throw new Exception("Parametro Invalido - La longitud del parametro 'NROOPERACION' no es de 15 digitos ");
		else if (!isNumeric.matches(nroOperacion))
			throw new Exception("Parametro Invalido - El parametro 'NROOPERACION' no es numerico ");
		else if (!isNumeric.matches(monto))
			throw new Exception("Parametro Invalido - El parametro 'MONTO' no es numerico ");
		else if (recargo.length() != 6)
			throw new Exception("Parametro Invalido - La longitud del parametro 'RECARGO' no es de 6 digitos ");
		else if (!isNumeric.matches(recargo))
			throw new Exception("Parametro Invalido - El parametro 'RECARGO' no es numerico ");
	}

	private void validarParametros(String monto, String fechavtoOriginal, String empresa, String nroOperacion, 
			String recargo, String difDiasVencimiento) throws Exception {
		
		if (empresa.length() != 4)
			throw new Exception("Parametro Invalido - La longitud del parametro 'NROCOMERCIO' no es de 4 digitos ");
		else if (monto.length() != 8)
			throw new Exception("Parametro Invalido - La longitud del parametro 'MONTO' no es de 8 digitos ");
		else if (fechavtoOriginal.length() != 6)
			throw new Exception("Parametro Invalido - La longitud del parametro 'FECHAVTO' no es de 6 digitos ");
		else if (!fechavtoOriginal.matches(isNumeric))
			throw new Exception("Parametro Invalido - El parametro 'FECHAVTO' no es numerico ");
		else if (nroOperacion.length() != 15)
			throw new Exception("Parametro Invalido - La longitud del parametro 'NROOPERACION' no es de 15 digitos ");
		else if (!nroOperacion.matches(isNumeric))
			throw new Exception("Parametro Invalido - El parametro 'NROOPERACION' no es numerico ");
		else if (!monto.matches(isNumeric))
			throw new Exception("Parametro Invalido - El parametro 'MONTO' no es numerico ");
		else if (recargo.length() != 6)
			throw new Exception("Parametro Invalido - La longitud del parametro 'RECARGO' no es de 6 digitos ");
		else if (!recargo.matches(isNumeric))
			throw new Exception("Parametro Invalido - El parametro 'RECARGO' no es numerico ");
		else if (difDiasVencimiento.length() != 2)
			throw new Exception("Parametro Invalido - La longitud del parametro 'difDiasVencimiento' no es de 2 digitos ");
		else if (!difDiasVencimiento.matches(isNumeric))
			throw new Exception("Parametro Invalido - El parametro 'difDiasVencimiento' no es numerico ");
	}

	protected String calcularDigito(String codigo) {

		int idx = 0;
		String result = "";
		int resto = 0;
		int sum_p = 0;
		int sum_i = 0;

		while (idx < 49) {
			if (idx % 2 != 0) {
				String dig = "";
				dig += codigo.indexOf(idx);
				sum_p = sum_p + Integer.parseInt(dig);
			} else {
				String dig = "";
				dig += codigo.indexOf(idx);
				sum_i = sum_i + Integer.parseInt(dig);
			}
			idx++;
		}
		resto = (sum_i * 3 + sum_p) % 10;

		result = (new Integer(((10 - resto) % 10))).toString();

		Logger.debug("ProtocoloCobroExpress>>ObtenerCodigo codigo CobroExpress " + codigo + result);

		Logger.info("code : " + codigo + result);
		
		return codigo + result;
	}
	
	public String formatFechaString(String fecha/* dd/mm/aaaa */) {
		String dia = fecha.substring(4, 6);
		String mes = fecha.substring(2, 4);
		String anio = "20" + fecha.substring(0, 2);
		return dia + "/" + mes + "/" + anio;
	}
	
	public String completarCero(String cadena, int tamano){
		int longitud = cadena.length();
		for (int i = 0; i < (tamano - longitud); i++){
			cadena = "0" + cadena;
		}
		return cadena;
	}
	
	public static long dias(int dia, int mes, int ano){
		try{
			final long MILLSECS_PER_DAY = 24 * 60 * 60 * 1000; //Milisegundos al d�a 
		    		 
		    Calendar fechaVen = new GregorianCalendar(ano, mes-1, dia);
		    Date fechaVenDate = new Date(fechaVen.getTimeInMillis()); 			    
			mes = 01;			
			dia = 0; 	
			
			Calendar calendar = new GregorianCalendar(ano, mes-1, dia); 
			Date fechaInicio = new Date(calendar.getTimeInMillis());			
			long diferencia = ( fechaVenDate.getTime() - fechaInicio.getTime() ) / MILLSECS_PER_DAY; 
			return diferencia;
		}catch(Exception e){
			return 0;
		}
	}
}