package decidir.sps.core;

import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

//import decidir.sps.com.protocolos.FactoryProtocolo;
//import decidir.sps.com.protocolos.Protocolo;
//import decidir.sps.jdbc.DBSPS;
//import decidir.sps.logger.LogSPS;
import decidir.sps.sac.vista.utils.info.InfoSite;
import decidir.sps.util.Pais;

public class Site extends Comercio {
	private String idSite;
	private String descripcionSite;
	private String razonSocial;
	private String direccion;
	private String codigoPostal;
	private Integer pais;
	private Integer tipoActividad;
	private String mail;
	private String DNS;
	private String IP;
	private String URLPost;
	private String replyMail;
	private char enviarResuOnLine;
	private Boolean mandarMailAUsuario;
	private Boolean mandarMailASite;
	private Boolean utilizaFirma;
	private Boolean encripta;
	private String tipoEncripcion;
	private String publicKey;
	private Integer estadoTienda;
	private Boolean enviaMedioDePago;
	private List<Cuenta> lasCuentas;
	private Boolean usaUrlDinamica;
	private Boolean validaRangoNroTarjeta;
	private char reutilizaTransaccion;
	private Integer timeoutcompra;
	private String idtemplates;
	private String transaccionesdistribuidas;
	private String montoporcent;
	private String tienereglas;
	private List<InfoSite> subSites;
	private Boolean mandarMailOperaciones;
	private Integer versionResumen;
	private VerazidLogin verazidLogin;
	private String idvalidator;
	private String tipoid;
	private String sinservicioid;
	private String pasaid;
	private String agregador;
	private String flagCS;
	private SiteModeloCS modeloCS;
	private String cierreUnificado;
	private String mensajeria;
	private String mid;
	private String securityKey;
	private String securityKeyExpirationDate;
	private SiteRubro rubro;
	private String autorizaseguir;
	private String csreversiontimeout;
	private Boolean flagclavehash;
	private String clavehash;
    private Boolean validaOrigen;
    private Boolean retornaTarjetaEnc;
	private Date fechaUsoHash;
	private String parentSiteId;
	private String mensajeriaMPOS;
	private Boolean isTokenized;
	private Integer timeToLive;

	public Date getFechaUsoHash() {
		return fechaUsoHash;
	}

	public void setFechaUsoHash(Date fechaUsoHash) {
		this.fechaUsoHash = fechaUsoHash;
	}

    public Boolean getValidaOrigen() {
        return validaOrigen;
    }

    public void setValidaOrigen(Boolean validaOrigen) {
        this.validaOrigen = validaOrigen;
    }

	public Site(String id) {
		idSite = id;
	}

	public Site(String idComercio, String descComercio, String id, String desc,
			Boolean mandarMailAUsuario, Boolean mandarMailASite, String email) {
		super(idComercio, descComercio);
		this.idSite = id;
		this.descripcionSite = desc;
		this.mail = email;
		this.mandarMailAUsuario = mandarMailAUsuario;
		this.mandarMailASite = mandarMailASite;
	}

	public Site(String idSite, String descripcionSite, String razonSocial,
			String direccion, String codigoPostal, Integer idPais,
			Integer idTipoActividad, String mail, String DNS, String IP,
			String URLPost, String replyMail, char enviarResuOnLine,
			Boolean mandarMailAUsuario, Boolean mandarMailASite, Boolean utilizaFirma, Boolean encripta, String tipoEncripcion,
			String publicKey, Boolean usaUrlDinamica, Boolean enviaMedioDePago,
			Boolean autorizaAmexEnDosPasos, Boolean autorizaDinersEnDosPasos,
			Boolean autorizaVisaEnDosPasos, Boolean validaRangoNroTarjeta,
			char reutilizaTransaccion, Integer timeoutcompra, Boolean mandarMailOperaciones,
			Integer versionResumen, VerazidLogin verazidLogin, String transaccionesdistribuidas,
			String montoporcent, String tienereglas, String idvalidator, String tipoid, 
			String sinservicioid, String pasaid, String agregador, String cierreunificado, String mensajeria,
			String flagCS, SiteModeloCS modeloCS, String mid, String securityKey, String securityKeyExpirationDate,
			SiteRubro rubro, String autorizaseguir, String csreversiontimeout, 
			List<Cuenta> cuentas, List<InfoSite> subsites, Boolean flagclavehash, String clavehash, Boolean validaOrigen,
			Boolean retornaTarjetaEnc, Date fechaUsoHash, String parentSiteId, String mensajeriaMPOS, Boolean isTokenized,
			Integer timeToLive)
			throws java.sql.SQLException {


		this.idSite = idSite;
		this.descripcionSite = descripcionSite;
		this.razonSocial = razonSocial;
		this.direccion = direccion;
		this.codigoPostal = codigoPostal;
		this.pais = idPais; //mpaoletta DBSPS.getPais(idPais);
		this.tipoActividad = idTipoActividad; //mpaoletta DBSPS.getTipoActividad(idTipoActividad);
		this.mail = mail;
		this.DNS = DNS;
		this.IP = IP;
		this.URLPost = URLPost;
		this.replyMail = replyMail;
		this.enviarResuOnLine = enviarResuOnLine;
		this.mandarMailAUsuario = mandarMailAUsuario;
		this.mandarMailASite = mandarMailASite;
		this.utilizaFirma = utilizaFirma;
		this.encripta = encripta;
        this.tipoEncripcion = tipoEncripcion;
		this.publicKey = publicKey;
		this.usaUrlDinamica = usaUrlDinamica;
		this.enviaMedioDePago = enviaMedioDePago;
		this.validaRangoNroTarjeta = validaRangoNroTarjeta != null ? validaRangoNroTarjeta: Boolean.FALSE;
		this.reutilizaTransaccion = reutilizaTransaccion;
		this.timeoutcompra = timeoutcompra != null ? timeoutcompra: new Integer(0);
		this.mandarMailOperaciones = mandarMailOperaciones;
		this.versionResumen = versionResumen == null? 1 : versionResumen;
		this.verazidLogin = verazidLogin;
		this.transaccionesdistribuidas = transaccionesdistribuidas;
		this.montoporcent = montoporcent;
		this.tienereglas = tienereglas;
		this.idvalidator = idvalidator;
		this.tipoid = tipoid;
		this.sinservicioid = sinservicioid;
		this.pasaid = pasaid;
		this.agregador = agregador;
		this.cierreUnificado = cierreunificado;
		this.mensajeria = mensajeria;
		this.flagCS = flagCS;
		this.modeloCS = modeloCS;
		this.mid = mid;
		this.securityKey = securityKey;
		this.securityKeyExpirationDate = securityKeyExpirationDate;
		this.setRubro(rubro);
		this.setAutorizaseguir(autorizaseguir);
		this.setCsreversiontimeout(csreversiontimeout);
		this.lasCuentas = cuentas;
		this.subSites = subsites;
		this.flagclavehash = flagclavehash;
		this.clavehash = clavehash;
        this.validaOrigen = validaOrigen;
        this.retornaTarjetaEnc = retornaTarjetaEnc;
		this.fechaUsoHash = fechaUsoHash;
		this.mensajeriaMPOS = mensajeriaMPOS;
		this.isTokenized = isTokenized;
		this.setParentSiteId(parentSiteId);
		this.timeToLive = timeToLive;
	}

	public Boolean encripta() {
		return encripta;
	}

	public String getCodigoPostal() {
		return codigoPostal;
	}

/*mpaoletta	
	public Cuenta getCuenta(Integer idProtocolo, String idMedioPago)
			throws Throwable {
		Protocolo protocolo = FactoryProtocolo.crearProtocolo(idProtocolo);
		String idBackEnd = protocolo.getBackEnd().getIdBackEnd();
		return getCuenta(idProtocolo, idBackEnd, idMedioPago);
	}

	public Cuenta getCuenta(Integer idProtocolo, String idBackEnd,
			String idMedioPago) {
		Enumeration<Cuenta> e = getCuentas().elements();
		while (e.hasMoreElements()) {
			Cuenta cuenta = e.nextElement();
			if (cuenta.permiteTrabajarConMedioPago(idProtocolo, idBackEnd,
					idMedioPago) &&  cuenta.estaHabilitada())
				return cuenta;
		}
		return null;
	}

	public Cuenta getCuenta(MarcaTarjeta marcatarjeta, Moneda moneda) {
		Enumeration<Cuenta> e = getCuentas().elements();
		
		while (e.hasMoreElements()) {
			Cuenta cuenta = e.nextElement();
			
			if (cuenta.getMarcaTarjeta().getId().equals(marcatarjeta.getId())
					&& cuenta.getMoneda().getIdMoneda().equals(
							moneda.getIdMoneda()) &&  cuenta.estaHabilitada())
				return cuenta;
		}
		return null;
	}
*/	

	public List<InfoSite> getSubSites() {
//		if (subSites == null) {
//			try {
//				subSites = DBSPS.getSubSites(this);
//			} catch (Throwable t) {
//				LogSPS.error("Error obteniendo los subSites del Site. ", t);
//			}
//		}
		return subSites;
	}

	public List<Cuenta> getCuentas() {
//		if (lasCuentas == null) {
//			try {
//				lasCuentas = DBSPS.getCuentas(this);
//			} catch (Throwable t) {
//				LogSPS.error("Error obteniendo las cuentas del Site. ", t);
//			}
//		}
		return lasCuentas;
	}
	
	public String getDescripcionSite() {
		return descripcionSite;
	}

	public String getDireccion() {
		return direccion;
	}

	public String getDNS() {
		return DNS;
	}

	public Integer getEstadoTienda() {
		return estadoTienda;
	}

	public String getIdSite() {
		return idSite;
	}

	public String getIP() {
		return IP;
	}

	public String getMail() {
		return mail;
	}

	public Boolean getMandarMailAUsuario() {
		return mandarMailAUsuario;
	}
	
	public Boolean getMandarMailASite() {
		return mandarMailASite;
	}

	public Boolean getMandarMailOperaciones(){
		return mandarMailOperaciones;
	}
	
	public Integer getVersionResumen() {
		return versionResumen;
	}
	
	public int getPais() {
		return pais;
	}

	public String getPublikKey() {
		return publicKey;
	}

	public String getRazonSocial() {
		return razonSocial;
	}

	public String getReplyMail() {
		return replyMail;
	}

	public int getTipoActividad() {
		return tipoActividad;
	}

	public String getURLPost() {
		return URLPost;
	}

	public String toString() {
		StringBuffer tostr = new StringBuffer();
		tostr.append("IdSite: " + idSite);
		tostr.append("|Descripcion:" + descripcionSite);
		tostr.append("|RazonSocial:" + razonSocial);
		tostr.append("|Direccion:" + direccion);
		tostr.append("|CodigoPostal:" + codigoPostal);
		tostr.append("|Pais:" + codigoPostal);
		tostr.append("|Email:" + mail);
		tostr.append("|URLPost:" + URLPost);
		tostr.append("|UsaURLDinamica:" + usaUrlDinamica);
		tostr.append("|EnviaMedioDePago:" + enviaMedioDePago);
		tostr.append("|ResultadosOnLine:" + enviarResuOnLine);
		tostr.append("|mandarMailAUsuario:" + mandarMailAUsuario);
		tostr.append("|mandarMailASite:" + mandarMailASite);
		tostr.append("|Encripta:" + encripta);
		tostr.append("|UtilizaFirma:" + utilizaFirma);
		return tostr.toString();
	}

	void setCodigoPostal(String newCodigoPostal) {
		codigoPostal = newCodigoPostal;
	}

	void setDireccion(String newDireccion) {
		direccion = newDireccion;
	}

	public void setDNS(String newDNS) {
		DNS = newDNS;
	}

	public void setEncripta(Boolean encri) {
		encripta = encri;
	}

	public void setEstadoTienda(Integer estado) {
		estadoTienda = estado;
	}

	public void setIP(String newIP) {
		IP = newIP;
	}

	public void setPais(int newPais) {
		pais = newPais;
	}

	public void setPublicKey(String pk) {
		publicKey = pk;
	}

	void setRazonSocial(String newRazonSocial) {
		razonSocial = newRazonSocial;
	}

	public void setReplyMail(String newMail) {
		replyMail = newMail;
	}

	public void setURLPost(String URL) {
		URLPost = URL;
	}

	public void setUtilizaFirma(Boolean frm) {
		utilizaFirma = frm;
	}

	public Boolean utilizaFirma() {
		return utilizaFirma;
	}

	public Boolean reutilizaTransaccion() {
		return Boolean.valueOf(reutilizaTransaccion != 'N');
	}

	public boolean autorizanDosPasos(Cuenta cuenta) {
		return cuenta.autorizaEnDosPasos();
	}

// mpaoletta
//	/**
//	 * Obtiene la cuenta de la DB pasando el site y el medio de pago.
//	 */
//	public Cuenta getCuenta(MedioPago medioPago) throws java.sql.SQLException, GeneralSecurityException {
//		Cuenta cuenta = DBSPS.getCuenta(this, medioPago);
//		return cuenta;
//	}

	public Boolean getEnviaMedioDePago() {
		return enviaMedioDePago;
	}

	public Boolean getUsaUrlDinamica() {
		return usaUrlDinamica;
	}

	public Integer getTimeoutCompra() {
		return this.timeoutcompra;
	}

	public boolean recibeResultados() {
		if (recibeResultadosBackground() || recibeResultadosPostBrowser())
			return true;
		return false;
	}

	public boolean recibeResultadosBackground() {
		if (enviarResuOnLine == 'B' || enviarResuOnLine == 'b')
			return true;
		return false;
	}
	
	public boolean recibeResultadosBackgroundExterno() {
		if (enviarResuOnLine == 'E' || enviarResuOnLine == 'e')
			return true;
		return false;
	}

	public boolean recibeResultadosPostBrowser() {
		if (enviarResuOnLine == 'S' || enviarResuOnLine == 's')
			return true;
		return false;
	}

	public void setEnviarResuOnLine(char enviar) {
		enviarResuOnLine = enviar;
	}

	public char getEnviarResuOnLine() {
		return enviarResuOnLine;
	}

	//mpaoletta
//	public boolean validarNroTarjeta(String nroTarjeta, Integer idMarcaTarjeta) {
//		return validaRangoNroTarjeta.booleanValue() == false ? true
//				: validarRangoNroTarjeta(nroTarjeta, idMarcaTarjeta);
//	}
//
//	private boolean validarRangoNroTarjeta(String nroTarjeta,
//			Integer idMarcatarjeta) {
//		try {
//			Vector<String []> rangos = DBSPS.getRangosPermitidosTarjeta(idSite, idMarcatarjeta);
//			for (int i = 0; i < rangos.size(); i++) {
//				String[] rango = rangos.elementAt(i);
//				if ((nroTarjeta.compareTo(rango[0]) >= 0) && nroTarjeta.compareTo(rango[1]) <= 0){
//					LogSPS.info("Valido Rango >> " + rango[0] + " >> "+ rango[1]);
//					return true;
//				}
//					
//			}
//		} catch (Exception e) {
//			LogSPS.error("Site>>validarRangoNroTarjeta", e);
//			return true;
//		}
//		return false;
//	}
//	
//	private Protocolo obtenerProtocolo(String nroTarjeta,
//			Integer idMedioPago) {
//		
////		try {
////			Vector<String []> rangos = DBSPS.getRangosMedioPago(idSite, idMedioPago);
////			for (int i = 0; i < rangos.size(); i++) {
////				String[] rango = rangos.elementAt(i);
////				if ((nroTarjeta.compareTo(rango[0]) >= 0) && nroTarjeta.compareTo(rango[1]) <= 0){
////					LogSPS.info("Valido Rango >> " + rango[0] + " >> "+ rango[1]);
////					return true;
////				}
////					
////			}
////		} catch (Exception e) {
////			LogSPS.error("Site>>validarRangoNroTarjeta", e);
////			return true;
////		}
//		return null;
//	}
	
	public void setIdTemplates(String idtemplates) {
		this.idtemplates = idtemplates;
	}
	
	public String getIdTemplates() {
		return idtemplates;
	}

	public VerazidLogin getVerazidLogin() {
		return verazidLogin;
	}
	
	public boolean getValidaRangoNroTarjeta() {
		return validaRangoNroTarjeta.booleanValue();
	}
	
	public String getTransaccionesDistribuidas(){
		return transaccionesdistribuidas;
	}
	public String getMontoPorcent(){
		return montoporcent!=null ? montoporcent : "";
	}

	public void setTienereglas(String tienereglas) {
		this.tienereglas = tienereglas;
	}

	public String getTienereglas() {
		return tienereglas;
	}
	public void setIdvalidator(String idvalidator) {
		this.idvalidator = idvalidator;
	}

	public String getIdvalidator() {
		return idvalidator;
	}

	public void setTipoid(String tipoid) {
		this.tipoid = tipoid;
	}

	public String getTipoid() {
		return tipoid;
	}

	public String getSinservicioid() {
		return sinservicioid;
	}

	public void setSinservicioid(String sinservicioid) {
		this.sinservicioid = sinservicioid;
	}

	public void setPasaid(String pasaid) {
		this.pasaid = pasaid;
	}

	public String getPasaid() {
		return pasaid;
	}
	
	public String getAgregador() {
		return agregador;
	}

	public String getCierreUnificado() {
		return cierreUnificado;
	}

	public void setCierreUnificado(String cierreUnificado) {
		this.cierreUnificado = cierreUnificado;
	}

	public String getMensajeria() {
		return mensajeria;
	}

	public void setMensajeria(String mensajeria) {
		this.mensajeria = mensajeria;
	}

	public String getFlagCS() {
		return flagCS;
	}

	public void setFlagCS(String flagCS) {
		this.flagCS = flagCS;
	}
	
	public boolean hasCS(){
		return flagCS.equals("S") || flagCS.equals("A");
	}
	
	public boolean admiteAnulacionesAutomaticas(){
		return flagCS.equals("A");
	}

	public SiteModeloCS getModeloCS() {
		return modeloCS;
	}

	public void setModeloCS(SiteModeloCS modeloCS) {
		this.modeloCS = modeloCS;
	}

	public String getMid() {
		return mid;
	}

	public void setMid(String mid) {
		this.mid = mid;
	}

	public String getSecurityKey() {
		return securityKey;
	}

	public void setSecurityKey(String securityKey) {
		this.securityKey = securityKey;
	}

	public String getSecurityKeyExpirationDate() {
		return securityKeyExpirationDate;
	}

	public void setSecurityKeyExpirationDate(String securityKeyExpirationDate) {
		this.securityKeyExpirationDate = securityKeyExpirationDate;
	}

	public SiteRubro getRubro() {
		return rubro;
	}

	public void setRubro(SiteRubro rubro) {
		this.rubro = rubro;
	}

	public String getAutorizaseguir() {
		return autorizaseguir;
	}

	public void setAutorizaseguir(String autorizaseguir) {
		this.autorizaseguir = autorizaseguir;
	}
	
	public boolean autorizaSeguirAnteErrorValidacionCybersource(){
		return this.autorizaseguir.toLowerCase().equals("s");
	}
	
	public String getCsreversiontimeout() {
		return csreversiontimeout;
	}

	public void setCsreversiontimeout(String csreversiontimeout) {
		this.csreversiontimeout = csreversiontimeout;
	}
	
	public boolean reversarTransaccionAnteTimeoutCybersource(){
		return this.csreversiontimeout.toLowerCase().equals("s");
	}

    public String getTipoEncripcion() {
        return tipoEncripcion;
    }

    public void setTipoEncripcion(String tipoEncripcion) {
        this.tipoEncripcion = tipoEncripcion;
    }

	public Boolean getFlagclavehash() {
		return flagclavehash;
	}

	public void setFlagclavehash(Boolean flagclavehash) {
		this.flagclavehash = flagclavehash;
	}

	public String getClavehash() {
		return clavehash;
	}

	public void setClavehash(String clavehash) {
		this.clavehash = clavehash;
	}

	public String getParentSiteId() {
		return parentSiteId;
	}

	public void setParentSiteId(String parentSiteId) {
		this.parentSiteId = parentSiteId;
	}

	public Boolean getRetornaTarjetaEnc() {
		return retornaTarjetaEnc;
	}

	public void setRetornaTarjetaEnc(Boolean retornaTarjetaEnc) {
		this.retornaTarjetaEnc = retornaTarjetaEnc;
	}

	public String getMensajeriaMPOS() {
		return mensajeriaMPOS;
	}

	public void setMensajeriaMPOS(String mensajeriaMPOS) {
		this.mensajeriaMPOS = mensajeriaMPOS;
	}

	public Boolean getIsTokenized() {
		return isTokenized;
	}

	public void setIsTokenized(Boolean isTokenized) {
		this.isTokenized = isTokenized;
	}

	public Integer getTimeToLive() {
		return timeToLive;
	}

	public void setTimeToLive(Integer timeToLive) {
		this.timeToLive = timeToLive;
	}
}
