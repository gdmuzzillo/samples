/*
 * Created on Ene 10, 2014
 *
 */
package legacy.decidir.sps.offline;

import com.decidir.coretx.domain.DatosOffline;
import play.Logger;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Vector;

public class ProtocoloCajaDePagos {

	public final String tipoGeneracion = "9";
	public static final int idmediopago = 48; 
	public final String nombreArchivoFTP = "Transacciones-P";
	public String LogCorreo;
//	private List<TransaccionCajaDePago> transacciones;

	public ProtocoloCajaDePagos() {
	}

	public String formatFechaString(String fecha/* dd/mm/aaaa */) {
		String dia = fecha.substring(4, 6);
		String mes = fecha.substring(2, 4);
		String anio = "20" + fecha.substring(0, 2);
		return dia + "/" + mes + "/" + anio;
	}

	public String obtenerCodigo(DatosOffline datos) throws Exception {

		// estos datos se deberian sacar de una tabla que tenga el idsite el
		// medio de pago y los datos
		String codPagoPyme = completarCero((String) datos.getParametro("COD_P1"),4);
		String monto = (String) datos.getParametro("MONTO");
		String documento = (String) datos.getParametro("NRODOC");
		String fechavtoOriginal = (String) datos.getParametro("FECHAVTO");
		String fechavto = "";
		if(fechavtoOriginal!=null && !fechavtoOriginal.isEmpty())
		 fechavto = formatFechaString(fechavtoOriginal);
//		String empresa = completarCero((String) datos.getParametro("CLIENTE"),5);		
//		Site site = (Site) datos.getParametro("site");
		
//		int nroTienda = 0;
//		try{
//			nroTienda = DBSPSw.obtenerNumeroComercio(site.getIdSite(), idmediopago);
//		}catch(Exception e){
//			Logger.error(e.getMessage());
//		}

        int nroTienda = Integer.parseInt(datos.getParametro("NROTIENDA"));
				
		String empresa = completarCero(Integer.toString(nroTienda),5);		
		
		String tipoGen = (String) datos.getParametro("COD_P2");
		String nroOperacion = completarCero((String) datos.getParametro("NROOPERACION"),8);
		String moneda = (String) datos.getParametro("COD_P3");
		String recargo = (String) datos.getParametro("RECARGO");
		String fechavto2Original = (String) datos.getParametro("FECHAVTO2");
		String fechavto2 = "";
		if(fechavto2Original!=null && !fechavto2Original.isEmpty())
			fechavto2 = formatFechaString(fechavto2Original);	
		
		int comapos = monto.indexOf(".");
		if (comapos < 0)
			comapos = monto.indexOf(",");
		if (comapos > -1) {
			String parteentera = monto.substring(0, comapos);
			String partedecimal = monto.substring(comapos + 1, monto.length());
			monto = parteentera + partedecimal;
		}
		monto = completarCero(monto, 8);
		validarParametros(codPagoPyme, monto, fechavtoOriginal, empresa, tipoGen, nroOperacion, moneda, completarCero(recargo, 6), documento, fechavto2Original);
		
		Integer monto1 = new Integer(monto);
		if(recargo == null || recargo.trim().isEmpty())
			recargo = "0";
		
		Integer recargo1 = new Integer(recargo);
		if(recargo1!=0){
			if(monto1 > recargo1)
				throw new Exception("Parametro Invalido - El monto de recargo no puede ser menor al monto original");
			monto1 = recargo1 - monto1;			  
			recargo = completarCero(String.valueOf(monto1), 6);
		}else{
			recargo = completarCero(String.valueOf(recargo1), 6);	
		}	
		
		
			
		int dias = (int) dias(Integer.valueOf(fechavto.split("/")[0]), Integer.valueOf(fechavto.split("/")[1]), Integer.valueOf(fechavto.split("/")[2]));
		
		int dias2Venc;
		if(!fechavto2.isEmpty()){
			dias2Venc = (int) dias(Integer.valueOf(fechavto2.split("/")[0]), Integer.valueOf(fechavto2.split("/")[1]), Integer.valueOf(fechavto2.split("/")[2]));
			dias2Venc -=dias; 
		}else{
			dias2Venc = 0;
		}	
		
		if(dias2Venc>99)
			throw new Exception("Parametro Invalido - La cantidad de dias entre las fechas de vencimiento no puede ser mayor a 99");
		
		String dias2Ven = completarCero(String.valueOf(dias2Venc), 2);
		
		int ano = Integer.valueOf(fechavto.split("/")[2]);
		String fecha = String.valueOf(ano).substring(String.valueOf(ano).length()-2, String.valueOf(ano).length()) + completarCero(String.valueOf(dias),3);
		
		
		String codigo = codPagoPyme + monto + fecha +  empresa + tipoGen + nroOperacion + moneda + recargo + dias2Ven;		
		
		return calcularDigito(codigo, 0);
	}
	
	public String completarCero(String cadena, int tamano){
		int longitud = cadena.length();
		for (int i = 0; i < (tamano - longitud); i++)
			cadena = "0" + cadena;
		return cadena;
	}
	
	private String calcularDigito(String cadena, int termino) throws Exception {

		try{
			if(termino!=2){
				int longitud = cadena.length();
				Vector<Integer> paso1 = new Vector();
				Vector<Integer> paso2 = new Vector();
				paso1.add(1);
				paso1.add(3);
				paso1.add(5);
				paso1.add(7);
				paso1.add(9);
				int[] secuencia = {3,5,7,9};
				int y=0;		
				for(int i=5; i<longitud; i++){			
					if(y<4)
					{
						paso1.add(secuencia[y]);
						y++;
					}else{					
						y=0;
						paso1.add(secuencia[y]);
						y++;
					}
				}
								
				int suma=0;
				int sumaTotal=0;
				for(int i=0; i<longitud; i++){	
					suma=0;	
					suma=Integer.parseInt(String.valueOf(cadena.charAt(i))) * paso1.get(i);
					paso2.add(suma);
					sumaTotal += suma; 
				}			
				int paso4 = 0;
				paso4 = sumaTotal / 2;				
				int paso5 = 0;
				paso5 = paso4 % 10;				
				cadena +=paso5;				
				termino++;
				return calcularDigito(cadena, termino);
			}else{
				return cadena;
			}						
		}catch (Exception e){
			Logger.error("ProtocoloCajaDePagos>>calcularDigito "+e.getMessage());
			throw e;
		}	
		
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

	protected void validarParametros(String codPagoPyme, String monto, String fechavto,
			String empresa, String tipoGen, String nroOperacion, String moneda,
			String recargo, String documento, String fechavto2) throws Exception {

		String isNumeric = "^[0-9]*$";

		if (codPagoPyme.length() != 4)
			throw new Exception("Parametro Invalido - La longitud del parametro 'COD_P1' no es de 4 digitos ");
		else if (monto.length() != 8)
			throw new Exception("Parametro Invalido - La longitud del parametro 'MONTO' no es de 8 digitos ");
		else if (fechavto.length() != 6)
			throw new Exception("Parametro Invalido - La longitud del parametro 'FECHAVTO' no es de 6 digitos ");
		else if (!fechavto.matches(isNumeric))
			throw new Exception("Parametro Invalido - El parametro 'FECHAVTO' no es numerico ");
		else if (tipoGen.length() != 1)
			throw new Exception("Parametro Invalido - La longitud del parametro 'COD_P2' no es de 1 digito ");
		else if (nroOperacion.length() != 8)
			throw new Exception("Parametro Invalido - La longitud del parametro 'NROOPERACION' no es de 8 digitos ");
		else if (moneda.length() != 1)
			throw new Exception("Parametro Invalido - La longitud del parametro 'MONEDA' no es de 1 digitos ");
		else if (!monto.matches(isNumeric))
			throw new Exception("Parametro Invalido - El parametro 'MONTO' no es numerico ");
		else if (!nroOperacion.matches(isNumeric))
			throw new Exception("Parametro Invalido - El parametro 'NROOPERACION' no es numerico ");
		else if (!recargo.matches(isNumeric))
			throw new Exception("Parametro Invalido - El parametro 'RECARGO' no es numerico ");
		else if (documento.length() < 4)
			throw new Exception("Parametro Invalido - La longitud del parametro 'NRODOC' debe ser mayor a 4 digitos ");
		else if (!codPagoPyme.matches(isNumeric))
			throw new Exception("Parametro Invalido - El parametro 'COD_P1' no es numerico ");
		else if (!tipoGen.matches(isNumeric))
			throw new Exception("Parametro Invalido - El parametro 'COD_P2' no es numerico ");
		else if (!moneda.matches(isNumeric))
			throw new Exception("Parametro Invalido - El parametro 'COD_P3' no es numerico ");
	}
	
}
