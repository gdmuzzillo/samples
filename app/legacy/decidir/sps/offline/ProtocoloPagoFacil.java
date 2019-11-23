/*
 * Created on Dec 16, 2005
 *
 */
package legacy.decidir.sps.offline;

import com.decidir.coretx.domain.DatosOffline;
import play.Logger;

/**
 * @author aquereilhac
 *
 */
public class ProtocoloPagoFacil {

	public static final int idmediopago = 25;

	public ProtocoloPagoFacil() {}
    
	public String obtenerCodigo(DatosOffline datos) throws Exception {

		// estos datos se deberian sacar de una tabla que tenga el idsite el
		// medio de pago y los datos
		String cod_p1 = (String) datos.getParametro("COD_P1");
		String cod_p2 = (String) datos.getParametro("COD_P2");
		String cod_p3 = (String) datos.getParametro("COD_P3");
		String cod_p4 = (String) datos.getParametro("COD_P4");

		String cliente = (String) datos.getParametro("CLIENTE");
		String fechavto = (String) datos.getParametro("FECHAVTO");
		String monto = (String) datos.getParametro("MONTO");
		String recargo = (String) datos.getParametro("RECARGO");
		String documento = (String) datos.getParametro("NRODOC");

		int comapos = monto.indexOf(".");
		if (comapos < 0)
			comapos = monto.indexOf(",");
		if (comapos > -1) {
			String parteentera = monto.substring(0, comapos);
			String partedecimal = monto.substring(comapos + 1, monto.length());
			monto = parteentera + partedecimal;
		}

		int montol = monto.length();
		for (int i = 0; i < (8 - montol); i++) {
			monto = "0" + monto;
		}

		int recargol = recargo.length();
		for (int i = 0; i < (7 - recargol); i++) {
			recargo = "0" + recargo;
		}

		// validaciones
		validarParametros(monto, cliente, fechavto, recargo, documento, cod_p1,
				cod_p2, cod_p3, cod_p4);

		int documentol = documento.length();
		for (int i = 0; i < (8 - documentol); i++)
			documento = "0" + documento;
		
		String codigo = cod_p1 + cod_p2 + cliente + documento + fechavto
				+ monto + cod_p3 + recargo + cod_p4;

		return calcularDigito(codigo);
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
				dig += codigo.charAt(idx);
				sum_p = sum_p + Integer.parseInt(dig);
			} else {
				String dig = "";
				dig += codigo.charAt(idx);
				sum_i = sum_i + Integer.parseInt(dig);
			}
			idx++;
		}
		resto = (sum_i * 3 + sum_p) % 10;

		result = (new Integer(((10 - resto) % 10))).toString();

		Logger.debug("ProtocoloPagoFacil>>ObtenerCodigo codigo PagoFacil "
				+ codigo + result);

		return codigo + result;
	}

	protected void validarParametros(String monto, String cliente,
			String fechavto, String recargo, String documento, String cod_p1,
			String cod_p2, String cod_p3, String cod_p4) throws Exception {
        String isNumeric = "^[0-9]*$";

		if (monto.length() != 8)
			throw new Exception(
					"La longitud del parametro 'MONTO' no es de 8 d�gitos ");
		else if (cliente.length() != 8)
			throw new Exception(
					"La longitud del parametro 'CLIENTE' no es de 8 d�gitos ");
		else if (fechavto.length() != 6)
			throw new Exception(
					"La longitud del parametro 'FECHAVTO' no es de 8 d�gitos ");
		else if (recargo.length() != 7)
			throw new Exception(
					"La longitud del parametro 'RECARGO' no es de 7 d�gitos ");
		else if (documento.length() < 4)
			throw new Exception(
					"La longitud del parametro 'NRODOC' debe ser mayor a 4 d�gitos ");
		else if (cod_p1.length() != 3)
			throw new Exception(
					"La longitud del parametro 'COD_P1' no es de 3 d�gitos ");
		else if (cod_p2.length() != 4)
			throw new Exception(
					"La longitud del parametro 'COD_P2' no es de 4 d�gitos ");
		else if (cod_p3.length() != 2)
			throw new Exception(
					"La longitud del parametro 'COD_P3' no es de 2 d�gitos ");
		else if (cod_p4.length() != 3)
			throw new Exception(
					"La longitud del parametro 'COD_P4' no es de 3 d�gitos ");
		else if (!monto.matches(isNumeric))
			throw new Exception(
					"El parametro 'MONTO' no es num�rico ");
		else if (!cliente.matches(isNumeric))
			throw new Exception(
					"El parametro 'CLIENTE' no es num�rico ");
		else if (!fechavto.matches(isNumeric))
			throw new Exception(
					"El parametro 'FECHAVTO' no es num�rico ");
		else if (!documento.matches(isNumeric))
			throw new Exception(
					"El parametro 'NRODOC' no es num�rico ");
		else if (!recargo.matches(isNumeric))
			throw new Exception(
					"El parametro 'RECARGO' no es num�rico ");
		else if (!cod_p1.matches(isNumeric))
			throw new Exception(
					"El parametro 'COD_P1' no es num�rico ");
		else if (!cod_p2.matches(isNumeric))
			throw new Exception(
					"El parametro 'COD_P2' no es num�rico ");
		else if (!cod_p3.matches(isNumeric))
			throw new Exception(
					"El parametro 'COD_P3' no es num�rico ");
		else if (!cod_p4.matches(isNumeric))
			throw new Exception(
					"El parametro 'COD_P4' no es num�rico ");
	}
}
