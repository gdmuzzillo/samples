/*
 * Created on Dec 7, 2005
 */
package decidir.sps.sac.vista.utils.info;

import java.util.List;

/**
 * @author aquereilhac
 */
public class InfoReglas{

	private String idsite;
	private String descsite;
	private String idregla;
	private int orden;
	private String estado;
	private String sitesalida;
	private String descsitesalida;
	private List<InfoReglasDetalle> detalle;
	


	public InfoReglas(String idsite, String sitesalida,  String idregla, int orden, String estado, List<InfoReglasDetalle> detalle,
			String descsite, String descsitesalida) {
		this.idsite = idsite;
		this.descsite = descsite;
		this.sitesalida = sitesalida;
		this.descsitesalida = descsitesalida;
		this.idregla= idregla;
		this.orden = orden;
		this.estado = estado;
		this.detalle = detalle;
	}

	public void setIdsite(String idsite) {
		this.idsite = idsite;
	}

	public String getIdsite() {
		return idsite;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	public String getEstado() {
		return estado;
	}
	public void setOrden(int orden) {
		this.orden = orden;
	}
	public int getOrden() {
		return orden;
	}
	public void setIdregla(String idregla) {
		this.idregla = idregla;
	}
	public String getIdregla() {
		return idregla;
	}

	public List<InfoReglasDetalle> getDetalle() {
		return detalle;
	}

	public void setDetalle(List<InfoReglasDetalle> detalle) {
		this.detalle = detalle;
	}

	public String getSitesalida() {
		return sitesalida;
	}

	public void setSitesalida(String sitesalida) {
		this.sitesalida = sitesalida;
	}

	public String getDescsitesalida() {
		return descsitesalida;
	}

	public void setDescsitesalida(String descsitesalida) {
		this.descsitesalida = descsitesalida;
	}

	public String getDescsite() {
		return descsite;
	}

	public void setDescsite(String descsite) {
		this.descsite = descsite;
	}

	
}
