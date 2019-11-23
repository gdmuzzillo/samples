package decidir.sps.core;

public class TipoActividad {
	private Integer idTipoActividad;
	private String descripcion;

	public TipoActividad() {
		
	}
	
	public TipoActividad(Integer idTipoActividad, String descripcion) {
		this.idTipoActividad = idTipoActividad;
		this.descripcion = descripcion;
	}

	
	
	public void setIdTipoActividad(Integer idTipoActividad) {
		this.idTipoActividad = idTipoActividad;
	}

	public void setDescripcion(String descripcion) {
		this.descripcion = descripcion;
	}

	public String getDescripcion() {
		return descripcion;
	}

	public Integer getIdTipoActividad() {
		return idTipoActividad;
	}

	public String toString() {
		StringBuffer tostr = new StringBuffer();
		tostr.append("<IdTipoActividad:" + idTipoActividad);
		tostr.append("|Descripcion:" + descripcion + ">");
		return tostr.toString();
	}
}
