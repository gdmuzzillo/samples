package decidir.sps.core;

//import decidir.sps.jdbc.ServidorDeObjetos;
import decidir.sps.util.Keyable;

public class Moneda implements Keyable {
	private static String nombreClase = new Moneda().getClass().getName();
	private String descripcion;
	private String simbolo;
	private String idMoneda;
	private String codigoIsoNum;
	private String codigoIsoAlfaNum;

	public Moneda() {
	}

	public Moneda(String idMoneda, String descripcion, String simbolo) {
		this.idMoneda = idMoneda;
		this.descripcion = descripcion;
		this.simbolo = simbolo;
	}

	public Moneda(String idMoneda, String descripcion, String simbolo,
			String codigoIsoNum, String codigoIsoAlfaNum) {
		this.idMoneda = idMoneda;
		this.descripcion = descripcion;
		this.simbolo = simbolo;
		this.codigoIsoNum = codigoIsoNum;
		this.codigoIsoAlfaNum = codigoIsoAlfaNum;
	}

	public Object getClave() {
		return getIdMoneda();
	}

	public String getcodigoIsoAlfaNum() {
		return codigoIsoAlfaNum;
	}

	public String getCodigoIsoNum() {
		return codigoIsoNum;
	}

	public String getDescripcion() {
		return descripcion.trim();
	}

	public String getIdMoneda() {
		return idMoneda;
	}

//	public static Moneda getMoneda(String id) {
//		return ((Moneda) ServidorDeObjetos.getServidorDeObjetos().getObject(
//				nombreClase, id));
//	}

	public String getSimbolo() {
		return simbolo;
	}

	public String toString() {
		StringBuffer tostr = new StringBuffer();
		tostr.append("<IdMoneda:" + idMoneda);
		tostr.append("|Descripcion:" + descripcion + ">");
		return tostr.toString();
	}

	public void setDescripcion(String newDescripcion) {
		descripcion = newDescripcion;
	}

	public void setIdMoneda(String newIdMoneda) {
		idMoneda = newIdMoneda;
	}

	public void setSimbolo(String newSimbolo) {
		simbolo = newSimbolo;
	}
}