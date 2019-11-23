package decidir.sps.core;

import java.text.ParseException;
import java.util.Vector;

//import decidir.sps.jdbc.DBSPS;
//import decidir.sps.logger.LogSPS;
//import decidir.sps.vista.DatosTransaccion;

public class MarcaTarjeta {
	
	public static Integer idBanelco = new Integer(26);
	
	public static Integer idLPWT = new Integer(38);
	
	private Integer id;

	private String descri;

	private String codAlfaNum;

	private String urlServicio;
	
	private String sufijoPlantilla;
	
	private String verificaBin;
	
	public MarcaTarjeta(Integer idMarca, String descri, String codAlfaNum, String urlServicio, String sufijoPlantilla,String verificaBin) {
		this.id = idMarca;
		this.descri = descri;
		this.codAlfaNum = codAlfaNum;
		this.urlServicio = urlServicio;
		this.sufijoPlantilla = sufijoPlantilla;
		this.verificaBin = verificaBin;
	}

	public boolean equals(Object otraMarca) {
		return ((MarcaTarjeta) otraMarca).getId().equals(this.getId());
	}

	public String getCodAlfaNum() {
		return codAlfaNum;
	}

	public String getDescri() {
		return descri;
	}

	public String getSufijoPlantilla(){
		return sufijoPlantilla;
	}
	
	public String getUrlServicio(){
		return urlServicio;
	}
	
	public Integer getId() {
		return id;
	}

//mpaoletta	
//	public boolean validarDatos(DatosTransaccion datos) throws Throwable {
//		// Parametros a validar
//		String numero = (String) datos.getParametro("NROTARJETA");
//		String fecha = (String) datos.getParametro("VENCIMIENTO");
//		String codseguridad = (String) datos.getParametro("CODSEGURIDAD");
//
//		try {
//			// Verifica que sea un nro valido
//			for (int i = 0; i < numero.length(); i++)
//				if (!Character.isDigit(numero.charAt(i))) {
//					LogSPS.debug("El n�mero de tarjeta " + numero
//							+ " no es del tipo esperado.");
//					return false;
//				}
//
//			// Verifica que el codigo de seguridad sea un numero, salvo para el
//			// caso de tarjetas Visa, Shopping y Naranja que puede venir vacio
//			/*******************************************************************
//			 * if ((id.intValue() != 4) && (id.intValue() != 8) &&
//			 * (id.intValue() != 9)) Integer.parseInt(codseguridad);
//			 ******************************************************************/
//
//			// Valida que la fecha de vencimiento tenga el formato esperado
//			if (!isValidFecha(fecha)) {
//				LogSPS.debug("Formato de fecha inv�lido");
//				return false;
//			}
//
//			// Verifica el nro de tarjeta
//			switch (id.intValue()) {
//			case 1:
//			case 6:
//				return isValidMaster(numero);
//			case 2:
//				return isValidAmex(numero);
//			case 3:
//				return isValidDiners(numero);
//			case 4:
//				return isValidVisa(numero);
//			case 8:
//				return isValidShopping(numero);
//			case 9:
//				return isValidNaranja(numero);
//			default:
//				return isValidCreditCard(numero);
//			}
//
//		} catch (NumberFormatException e) {
//			LogSPS.debug("El c�digo de seguridad " + codseguridad
//					+ " no es del tipo esperado.");
//			throw e;
//		}
//
//	}

	public boolean isValidMaster(String number) {

		// Valido la longitud
		if (number.length() != 16)
			return false;

		int firstTwoNumbers = Integer.parseInt(number.substring(0, 2));

		if ((firstTwoNumbers <= 50 || firstTwoNumbers >= 56))
			return false;

		if (!isValidCreditCard(number))
			return false;

		return true;
	}

	public boolean isValidAmex(String number) {
		// Valido la longitud
		if (number.length() != 15)
			return false;

		int firstTwoNumbers = Integer.parseInt(number.substring(0, 2));
		if (firstTwoNumbers != 34 && firstTwoNumbers != 37)
			return false;

		if (!isValidCreditCard(number))
			return false;

		return true;
	}

	public boolean isValidDiners(String number) {

		// Valido la longitud
		if (number.length() != 14)
			return false;

		int firstTwoNumbers = Integer.parseInt(number.substring(0, 2));
		if (firstTwoNumbers != 30 && firstTwoNumbers != 36
				&& firstTwoNumbers != 38)
			return false;

		if (!isValidCreditCard(number))
			return false;

		return true;
	}

	public boolean isValidShopping(String number) {

		int firstTenNumbers = Integer.parseInt(number.substring(0, 10));
		if (firstTenNumbers != 589407279)
			return false;

		return true;
	}

	public boolean isValidNaranja(String number) {

		int r = 0;
		int j = 2;

		if (number.length() != 16)
			return false;

		int firstSixNumbers = Integer.parseInt(number.substring(0, 6));
		if (firstSixNumbers != 589562)
			return false;

		for (int i = (number.length() - 2); i >= 0; i--) {
			if ((i + 4) % 6 == 0) {
				j = 2;
			}
			r += Character.getNumericValue(number.charAt(i)) * (j++);
		}

		int x1 = 11 - (r % 11);
		if (x1 > 9) {
			x1 = 0;
		}

		if (x1 == Character.getNumericValue(number.charAt(number.length() - 1)))
			return true;
		return false;
	}

	public boolean isValidVisa(String number) {

		int firstNumber = Integer.parseInt(number.substring(0, 1));
		if (firstNumber != 4)
			return false;

		if (!isValidCreditCard(number))
			return false;

		return true;
	}

	public boolean isValidCreditCard(String number) {
		int total = 0;
		int flag = 0;

		for (int i = (number.length() - 1); i >= 0; i--) {
			if (flag == 1) {
				int digits = Character.getNumericValue(number.charAt(i)) * 2;
				if (digits > 9)
					digits -= 9;
				total += digits;
				flag = 0;
			} else {
				total = total + Character.getNumericValue(number.charAt(i));
				flag = 1;
			}
		}
		if ((total % 10) == 0) {
			return true;
		} else {
			return false;
		}
	}

	public boolean isValidFecha(String fvenc) {
		String fdt = "yyMM"; // mascara que utiliza
		try {
			java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(fdt);
			sdf.setLenient(false);
			sdf.parse(fvenc);

		} catch (ParseException e) {
			return false;
		} catch (IllegalArgumentException e) {
			return false;
		}

		return true;
	}

//	public boolean esNacional(String nroTarjeta) {
//		try {
//			if (nroTarjeta == null)
//				return true;
//			Vector<String []> rangos = DBSPS.getRangosTarjetaNacional(this);
//
//			for (int i = 0; i < rangos.size(); i++) {
//				String[] rango = rangos.elementAt(i);
//				if ((nroTarjeta.compareTo(rango[0]) >= 0)
//						&& nroTarjeta.compareTo(rango[1]) <= 0)
//					return true;
//			}
//			return false;
//		} catch (Exception e) {
//			LogSPS.error("No se pudo obtener los rangos de tarjeta nacional por un problema en la BD: "
//					+ e.getMessage());
//			return true;
//		}
//	}

	public String getVerificaBin() {
		return verificaBin;
	}

	public void setVerificaBin(String verificaBin) {
		this.verificaBin = verificaBin;
	}

}