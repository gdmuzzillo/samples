/*
 * Created on Dec 2, 2005
 *
 */
package decidir.sps.sac.vista.utils.info;

/**
 * @author aquereilhac
 */
public class InfoSite {

	private String idsite;
	private String site_descri;
	private boolean reciberesultadoslote;
	private String mailresumenlote;
	private float porcentaje;
	private String activo;
	private String idValidator;
	private String tipoId;
	private String pasaId;
	private String sinServicioId;

	public InfoSite(String idsite, String site_descri,
			String reciberesultadoslote, String mailresumenlote) {

		this.idsite = (idsite != null ? idsite : "");
		this.site_descri = (site_descri != null ? site_descri : "");
		if (reciberesultadoslote == null || !reciberesultadoslote.equals("S"))
			this.reciberesultadoslote = false;
		else
			this.reciberesultadoslote = true;
		this.mailresumenlote = mailresumenlote;
	}

	public InfoSite(String idsite, String site_descri,
			float porcentaje, String activo, String idValidator, String tipoId, String pasaId, String sinServicioId) {

		this.idsite = (idsite != null ? idsite : "");
		this.site_descri = (site_descri != null ? site_descri : "");
		this.porcentaje = porcentaje;
		this.activo = activo;
		this.idValidator = idValidator;
		this.tipoId = tipoId;
		this.pasaId = pasaId;
		this.sinServicioId = sinServicioId;
	}
	
	public String getIdSite() {
		return this.idsite;
	}

	public String getSiteDescri() {
		return this.site_descri;
	}

	public boolean recibeResultadosCierreLote() {
		return reciberesultadoslote;
	}

	public String getMailResumenLote() {
		return mailresumenlote;
	}

	public float getPorcentaje() {
		return porcentaje;
	}

	public String getActivo() {
		return activo;
	}

	public void setIdValidator(String idValidator) {
		this.idValidator = idValidator;
	}

	public String getIdValidator() {
		return idValidator;
	}

	public void setTipoId(String tipoId) {
		this.tipoId = tipoId;
	}

	public String getTipoId() {
		return tipoId;
	}

	public void setPasaId(String pasaId) {
		this.pasaId = pasaId;
	}

	public String getPasaId() {
		return pasaId;
	}

	public void setSinServicioId(String sinServicioId) {
		this.sinServicioId = sinServicioId;
	}

	public String getSinServicioId() {
		return sinServicioId;
	}
	
}
