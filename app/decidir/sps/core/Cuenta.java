package decidir.sps.core;

import java.sql.SQLException;

import org.apache.commons.lang3.StringUtils;

import decidir.sps.util.Utilities;

//import org.apache.commons.lang.StringUtils;

//import decidir.sps.com.protocolos.FactoryProtocolo;
//import decidir.sps.com.protocolos.Protocolo;
//import decidir.sps.jdbc.DBSPS;
//import decidir.sps.util.Utilities;

public class Cuenta {
	private Site site;
	private String idMedioPago;
//	private Protocolo protocolo;
	private Integer protocoloId;
//	private BackEnd backEnd;
	private String backEndId;
	private String nroId;
	private Boolean estaHabilitadaParaOperarConPlanN;
	private Boolean habilitado;
	private String numeroDeTerminal;
	private Boolean utilizaAutenticacionExterna;
	private Boolean autorizaEnDosPasos;
	private String planCuotas;
	private Boolean pasaAutenticacionExterna;
	private Boolean pasaAutenticacionExternaSinServicio;
	private String formatoNroTarjetaVisible;
	private String password;
	private Boolean pagoDiferidoHabilitado;
	private Boolean aceptaSoloNacional = false;
	private String tipoPlantilla;
	private String nroIdDestinatario;
	private Integer porcentajesuperior;
	private Integer porcentajeinferior; 

	public Cuenta(){
		
	}
	
	public Cuenta(Site t, String idMedioPago) throws SQLException {
		this.site = t;
		this.idMedioPago = idMedioPago;
	}

//	public BackEnd getBackEnd() {
//		return backEnd;
//	}

	public String getIdMedioPago() {
		return idMedioPago;
	}

	public String getNroId() {
		return nroId;
	}

//	public Protocolo getProtocolo() {
//		return protocolo;
//	}

	public Site getSite() {
		return site;
	}

	public Boolean permiteOperarConPlanN() {
		return estaHabilitadaParaOperarConPlanN;
	}

//	public boolean permiteTrabajarConMedioPago(Integer idProtocolo,
//			String idBackEnd, String idMedioPago) {
//		if (idProtocolo.equals(getProtocolo().getIdProtocolo())
//				&& idBackEnd.equals(getBackEnd().getIdBackEnd())
//				&& idMedioPago.equals(getMedioPago().getIdMedioPago()))
//			return true;
//		return false;
//	}

	public String toString() {
		StringBuffer tostr = new StringBuffer();
		tostr.append("<Site:" + site);
		tostr.append("|MedioPago:" + idMedioPago);
//		tostr.append("|Protocolo:" + protocolo);
//		tostr.append("|Backend:" + backEnd);
		tostr.append("|PlanN:" + estaHabilitadaParaOperarConPlanN);
		tostr.append("|NumeroTerminal:" + numeroDeTerminal);
		tostr.append("|Habilitada:" + estaHabilitada() + ">");
		return tostr.toString();
	}

//	public void setBackEnd(BackEnd newBackEnd) {
//		backEnd = newBackEnd;
//	}
//
//	public void setMedioPago(MedioPago newMedioPago) throws SQLException {
//		medioPago = newMedioPago;
//		if (medioPago != null && site != null
//				&& medioPago.getIdMedioPago() != null) {
//			Integer idProtocolo = DBSPS.obtenerIdProtocolo(medioPago, site);
//			protocolo = FactoryProtocolo.crearProtocolo(idProtocolo);
//		}
//	}

	public void setNroId(String newNroId) {
		nroId = newNroId;
	}

//	public void setProtocolo(Protocolo newProtocolo) {
//		protocolo = newProtocolo;
//	}

	public Cuenta(Site site, String idMedioPago, int protocoloId, String backEndId,
			/*Protocolo protocolo, BackEnd backEnd, */
			String nroId, String password,
			Boolean estaHabilitadaParaOperarConPlanN, Boolean habilitado,
			String numeroDeTerminal, Boolean autorizaEnDosPasos, Integer porcentajeinferior, Integer porcentajesuperior,
			Boolean utilizaAutenticacionExterna, String planCuotas, Boolean pasaAutenticacionExterna,
			Boolean pasaAutenticacionExternaSinServicio, String formatoNroTarjetaVisible,
			Boolean pagoDiferidoHabilitado, Boolean aceptaSoloNacional, String tipoPlantilla,
			String nroIdDestinatario) {

		this.site = site;
		this.idMedioPago = idMedioPago;
		this.protocoloId = protocoloId;
		this.backEndId = backEndId;
//		this.protocolo = protocolo;
//		this.backEnd = backEnd;
		this.nroId = nroId;
		this.password = password;
		this.estaHabilitadaParaOperarConPlanN = estaHabilitadaParaOperarConPlanN;
		this.habilitado = habilitado;
		this.numeroDeTerminal = numeroDeTerminal;
		this.autorizaEnDosPasos = autorizaEnDosPasos;
		this.porcentajeinferior = porcentajeinferior;
		this.porcentajesuperior = porcentajesuperior;
		this.utilizaAutenticacionExterna = utilizaAutenticacionExterna;
		this.planCuotas = planCuotas;
		this.pasaAutenticacionExterna = pasaAutenticacionExterna;
		this.pasaAutenticacionExternaSinServicio = pasaAutenticacionExternaSinServicio;
		this.formatoNroTarjetaVisible = formatoNroTarjetaVisible;
		this.pagoDiferidoHabilitado = pagoDiferidoHabilitado;
		this.aceptaSoloNacional = aceptaSoloNacional;
		this.tipoPlantilla = tipoPlantilla;
		this.nroIdDestinatario = nroIdDestinatario;
	}

	public boolean estaHabilitada() {
		if (habilitado == null)
			return false;
		return habilitado.booleanValue();
	}

//	public MarcaTarjeta getMarcaTarjeta() {
//		return medioPago.getMarcaTarjeta();
//	}
//
//	public Moneda getMoneda() {
//		return medioPago.getMoneda();
//	}
	
	public void  setNumeroDeTerminal(String numeroDeTerminal) {
		this.numeroDeTerminal=numeroDeTerminal;
	}
	public String getNumeroDeTerminal() {
		return numeroDeTerminal == null ? "0" : numeroDeTerminal;
	}

	public String getPlanCuotas() {
		return planCuotas;
	}

//	public boolean permiteOperarConMarcaTarjeta(MarcaTarjeta marcaTarjeta) {
//		return getMarcaTarjeta().equals(marcaTarjeta);
//	}

	public String getPassword(){
		return password;
	}
	
	/*public boolean utilizaVbV() {
		if (utilizaVbV == null)
			return false;
		return utilizaVbV.booleanValue();
	}*/

	public boolean utilizaAutenticacionExterna() {
		if (utilizaAutenticacionExterna == null)
			return false;
		return utilizaAutenticacionExterna.booleanValue();
	}
	
	public boolean autorizaEnDosPasos() {
		if (autorizaEnDosPasos == null)
			return false;
		return autorizaEnDosPasos.booleanValue();
	}

	public Boolean esAutorizaEnDosPasos() {
		return autorizaEnDosPasos;
	}

	/*public boolean pasaVBV() {
		if (pasavbv == null)
			return false;
		return pasavbv.booleanValue();
	}*/
	
	public boolean pasaAutenticacionExterna(){
		if (pasaAutenticacionExterna == null)
			return false;
		return pasaAutenticacionExterna.booleanValue();
	}
	public boolean pasaVBVSinServicio() {
		return ((pasaAutenticacionExternaSinServicio == null) ? true
				: pasaAutenticacionExternaSinServicio.booleanValue());
	}

	public String getFormatoNroTarjetaVisible() {
		return formatoNroTarjetaVisible;
	}
	
//	public String getNroTarjetaVisible(String nroTarjeta) {
//		
//		if(medioPago.getIdMedioPago().equals(MedioPago.idTarjetaShopping)){
//			if (formatoNroTarjetaVisible == null || formatoNroTarjetaVisible.equals(""))
//				return null;
//			
//			//Comprueba si debe mostrarse el n�mero completo de la tarjeta # x16 o # x19 
//			if(formatoNroTarjetaVisible.equals("################") || formatoNroTarjetaVisible.equals("###################")){
//				return nroTarjeta;
//			}
//			
//			//Obtiene el primer caracter distinto a # que se utiliza como m�scara
//			String strMask = Utilities.getSubstring(formatoNroTarjetaVisible, "[^#]");
//			if(strMask.length()==0) strMask = "X";
//			
//			//Obtiene los primeros 6 d�gitos y los �ltimos 4. En el medio coloca el strMask
//			String firstDigits = nroTarjeta.substring(0, Math.min(nroTarjeta.length(), 6));
//			String lastDigits  = nroTarjeta.substring(Math.max(0, nroTarjeta.length() - 4));
//			
//			return firstDigits + StringUtils.leftPad(lastDigits, nroTarjeta.length() - firstDigits.length(), strMask);
//		}
//		
//		String nroTarjetaVisible = Utilities.formatStringConMascara(nroTarjeta, formatoNroTarjetaVisible);
//		return nroTarjetaVisible;
//	}
	
	public boolean pagoDiferidoHabilitado() {
		if (pagoDiferidoHabilitado == null)
			return false;
		return pagoDiferidoHabilitado.booleanValue();
	}
	
	public Boolean getAceptaSoloNacional() {
		return aceptaSoloNacional;
	}

	public String getTipoPlantilla() {
		return tipoPlantilla;
	}

	public void setTipoPlantilla(String tipoPlantilla) {
		this.tipoPlantilla = tipoPlantilla;
	}

	public String getNroIdDestinatario() {
		return nroIdDestinatario;
	}

	public void setNroIdDestinatario(String nroIdDestinatario) {
		this.nroIdDestinatario = nroIdDestinatario;
	}
	
	
	public void setBackEndId(String id) {
		this.backEndId = id;
	}
	
	public String getBackEndId() {
		return this.backEndId;
	}
	
	public void setProtocoloId(Integer id) {
		this.protocoloId = id;
	}
	
	public Integer getProtocoloId() {
		return this.protocoloId;
	}

	public Integer getPorcentajesuperior() {
		return porcentajesuperior;
	}

	public void setPorcentajesuperior(Integer porcentajesuperior) {
		this.porcentajesuperior = porcentajesuperior;
	}

	public Integer getPorcentajeinferior() {
		return porcentajeinferior;
	}

	public void setPorcentajeinferior(Integer porcentajeinferior) {
		this.porcentajeinferior = porcentajeinferior;
	}
	
	
	
}
