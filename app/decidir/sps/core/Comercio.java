package decidir.sps.core;

public abstract class Comercio {
	private String idComercio;
	private String descripcion;

	public Comercio() {
		/* empty */
	}

	public Comercio(String id, String desc) {
		idComercio = id;
		descripcion = desc;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public String getIdComercio() {
		return idComercio;
	}

	public String setDescripcionCliente(String descripcion) {
		return this.descripcion;
	}

	public void setIdComercio(String idComercio) {
		this.idComercio = idComercio;
	}
}