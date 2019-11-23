package decidir.sps.util;

public class Pais {
	private static Integer idArgentina = new Integer(0);
	private static Integer idMexico = new Integer(1);
	private static Integer idBrasil = new Integer(2);
	private static Integer idChile = new Integer(3);
	private Integer idPais;
	private String descripcion;
	private String codigoIsoNum;
	private String codigoIsoAlfaNum;

	public Pais() {
	}

	public Pais(Integer idPais, String descripcion) {
		this.idPais = idPais;
		this.descripcion = descripcion;
	}

	public Pais(Integer idPais, String descripcion, String codigoIsoNum,
			String codigoIsoAlfaNum) {
		this.idPais = idPais;
		this.descripcion = descripcion;
		this.codigoIsoNum = codigoIsoNum;
		this.codigoIsoAlfaNum = codigoIsoAlfaNum;
	}

	public boolean equals(Object other) {
		if (other instanceof Pais) {
			Pais pais = (Pais) other;
			return idPais.equals(pais.idPais);
		}
		return false;
	}

	public boolean esArgentina() {
		return idPais.equals(idArgentina);
	}

	public boolean esBrasil() {
		return idPais.equals(idBrasil);
	}

	public boolean esChile() {
		return idPais.equals(idChile);
	}

	public boolean esMexico() {
		return idPais.equals(idMexico);
	}

	public static Pais getArgentina() {
		return new Pais(idArgentina, "Argentina");
	}

	public static Pais getBrasil() {
		return new Pais(idBrasil, "Brasil");
	}

	public static Pais getChile() {
		return new Pais(idChile, "Chile");
	}

	public String getcodigoIsoAlfaNum() {
		return codigoIsoAlfaNum;
	}

	public String getCodigoIsoNum() {
		return codigoIsoNum;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public Integer getIdPais() {
		return idPais;
	}

	public static Pais getMexico() {
		return new Pais(idMexico, "Mexico");
	}

	public String toString() {
		StringBuffer tostr = new StringBuffer();
		tostr.append("<IdPais:" + idPais);
		tostr.append("|Descripcion:" + descripcion + ">");
		return tostr.toString();
	}

	public void setDescripcion(String newDescripcion) {
		descripcion = newDescripcion;
	}

	public void setIdPais(Integer newIdPais) {
		idPais = newIdPais;
	}
}
