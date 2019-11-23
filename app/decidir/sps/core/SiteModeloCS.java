package decidir.sps.core;

public class SiteModeloCS {
	private int id_modelo;
	private String descripcion;
	
	public SiteModeloCS(int id_modelo, String descripcion){
		this.id_modelo = id_modelo;
		this.descripcion = descripcion;
	}
	
	public int getId_modelo() {
		return id_modelo;
	}
	public void setId_modelo(int id_modelo) {
		this.id_modelo = id_modelo;
	}
	public String getDescripcion() {
		return descripcion;
	}
	public void setDescripcion(String descripcion) {
		this.descripcion = descripcion;
	}

}