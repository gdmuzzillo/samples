package decidir.sps.core;

//import decidir.sps.jdbc.ServidorDeObjetos;
//import decidir.sps.logger.LogSAC;
//import decidir.sps.logger.LogSPS;
import decidir.sps.util.Keyable;

public class MedioPago implements Keyable {
	private static String nombreClase = new MedioPago().getClass().getName();
	private String idMedioPago;
	private String descripcion;
	private Moneda moneda;
	private MarcaTarjeta mTarjeta;
	private Double limite;
	private Boolean validabines;
	public static String idBanelco = "41";
	public static String idTarjetaShopping = "23";
	public static String idLPWT = "53";
	private Integer backend;
	private Integer protocol;
	private Boolean annulment;
	private Boolean annulmentPreApproved;
	private Boolean refundPartialBeforeClose;
	private Boolean refundPartialBeforeCloseAnnulment;
	private Boolean refundPartialAfterClose;
	private Boolean refundPartialAfterCloseAnnulment;
	private Boolean refund;
	private Boolean refundAnnulment;
	private Boolean twoSteps;
	private String binRegex;
	private Boolean blackList;
	private Boolean whiteList;
	private Boolean validateLuhn;
	private String templateSuffix;
	private Boolean cyberSourceApiField;
	private Boolean tokenized;
	private String esAgro;

	public String getTemplateSuffix() {
		return templateSuffix;
	}

	public void setTemplateSuffix(String templateSuffix) {
		this.templateSuffix = templateSuffix;
	}

	public MedioPago() {
	}

	public Object getClave() {
		return getIdMedioPago();
	}

	public String getDescripcion() {
		return descripcion.trim();
	}

	public String getDescripcionCompleta() {
		return descripcion + "-" + moneda.getDescripcion();
	}

	public String getIdMedioPago() {
		return idMedioPago;
	}

//	public static MedioPago getMedioPago(String id) {
//		return ((MedioPago) ServidorDeObjetos.getServidorDeObjetos().getObject(
//				nombreClase, id));
//	}

	public Moneda getMoneda() {
		return moneda;
	}

	public String toString() {
		StringBuffer tostr = new StringBuffer();
		tostr.append("<IdMedioPago:" + idMedioPago);
		tostr.append("|Descripcion:" + descripcion);
		tostr.append("|Moneda:" + moneda + ">");
		return tostr.toString();
	}

	public void setDescripcion(String newDescripcion) {
		descripcion = newDescripcion;
	}

	public void setMoneda(Moneda newMoneda) {
		moneda = newMoneda;
	}

	public MedioPago(String idMedioPago, String descripcion, Moneda moneda,
			MarcaTarjeta mTarjeta, Double limite, Boolean validabines, Integer backend, 
			Integer protocol, Boolean annulment, Boolean annulmentPreApproved, Boolean refundPartialBeforeClose, Boolean refundPartialBeforeCloseAnnulment, 
			Boolean refundPartialAfterClose, Boolean refundPartialAfterCloseAnnulment, Boolean refund, Boolean refundAnnulment, Boolean twoSteps,
			String binRegex, Boolean blackList, Boolean whiteList, Boolean validateLuhn, String templateSuffix, Boolean cyberSourceApiField, Boolean tokenized, String esAgro) {
		this.idMedioPago = idMedioPago;
		this.descripcion = descripcion;
		this.moneda = moneda;
		this.mTarjeta = mTarjeta;
		this.limite = limite;
		this.backend = backend;
		this.protocol = protocol;
		this.annulment = annulment;
		this.annulmentPreApproved = annulmentPreApproved;
		this.refundPartialBeforeClose = refundPartialBeforeClose;
		this.refundPartialBeforeCloseAnnulment = refundPartialBeforeCloseAnnulment;
		this.refundPartialAfterClose = refundPartialAfterClose;
		this.refundPartialAfterCloseAnnulment = refundPartialAfterCloseAnnulment;
		this.refund = refund;
		this.refundAnnulment = refundAnnulment;
		this.twoSteps = twoSteps;
		this.binRegex = binRegex;
		this.blackList = blackList;
		this.whiteList = whiteList;
		this.validateLuhn = validateLuhn;
		this.setValidabines(validabines);
        this.templateSuffix = templateSuffix;
		this.cyberSourceApiField = cyberSourceApiField;
		this.tokenized = tokenized;
		this.esAgro = esAgro;
	}

	public boolean equals(Object otroMedioPago) {		
		return getIdMedioPago().equals(
				((MedioPago) otroMedioPago).getIdMedioPago());
	}

	public MarcaTarjeta getMarcaTarjeta() {
		return mTarjeta;
	}

	public Double getLimite() {
		return limite;
	}

	public void setLimite(Double limite) {
		this.limite = limite;
	}

	public Boolean getValidabines() {
		return validabines;
	}

	public void setValidabines(Boolean validabines) {
		this.validabines = validabines;
	}

	public Integer getBackend() {
		return backend;
	}

	public void setBackend(Integer backend) {
		this.backend = backend;
	}

	public Integer getProtocol() {
		return protocol;
	}

	public void setProtocol(Integer protocol) {
		this.protocol = protocol;
	}

	public Boolean getAnnulment() {
		return annulment;
	}

	public void setAnnulment(Boolean annulment) {
		this.annulment = annulment;
	}
	
	public Boolean getAnnulmentPreApproved() {
		return annulmentPreApproved;
	}

	public void setAnnulmentPreApproved(Boolean annulmentPreApproved) {
		this.annulmentPreApproved = annulmentPreApproved;
	}

	public Boolean getRefundPartialBeforeClose() {
		return refundPartialBeforeClose;
	}

	public void setRefundPartialBeforeClose(Boolean refundPartialBeforeClose) {
		this.refundPartialBeforeClose = refundPartialBeforeClose;
	}

	public Boolean getRefundPartialBeforeCloseAnnulment() {
		return refundPartialBeforeCloseAnnulment;
	}

	public void setRefundPartialBeforeCloseAnnulment(
			Boolean refundPartialBeforeCloseAnnulment) {
		this.refundPartialBeforeCloseAnnulment = refundPartialBeforeCloseAnnulment;
	}

	public Boolean getRefundPartialAfterClose() {
		return refundPartialAfterClose;
	}

	public void setRefundPartialAfterClose(Boolean refundPartialAfterClose) {
		this.refundPartialAfterClose = refundPartialAfterClose;
	}

	public Boolean getRefundPartialAfterCloseAnnulment() {
		return refundPartialAfterCloseAnnulment;
	}

	public void setRefundPartialAfterCloseAnnulment(
			Boolean refundPartialAfterCloseAnnulment) {
		this.refundPartialAfterCloseAnnulment = refundPartialAfterCloseAnnulment;
	}

	public Boolean getRefund() {
		return refund;
	}

	public void setRefund(Boolean refund) {
		this.refund = refund;
	}

	public Boolean getRefundAnnulment() {
		return refundAnnulment;
	}

	public void setRefundAnnulment(Boolean refundAnnulment) {
		this.refundAnnulment = refundAnnulment;
	}
	
	public Boolean getTwoSteps() {
		return twoSteps;
	}

	public void setTwoSteps(Boolean twoSteps) {
		this.twoSteps = twoSteps;
	}

	public String getBinRegex() {
		return binRegex;
	}

	public void setBinRegex(String binRegex) {
		this.binRegex = binRegex;
	}


	public Boolean getBlackList() {
		return blackList;
	}

	public void setBlackList(Boolean blackList) {
		this.blackList = blackList;
	}

	public Boolean getWhiteList() {
		return whiteList;
	}

	public void setWhiteList(Boolean whiteList) {
		this.whiteList = whiteList;
	}

	public Boolean getValidateLuhn() {
		return validateLuhn;
	}

	public void setValidateLuhn(Boolean validateLuhn) {
		this.validateLuhn = validateLuhn;
	}

	public Boolean getCyberSourceApiField() {
		return cyberSourceApiField;
	}

	public void setCyberSourceApiField(Boolean cyberSourceApiField) {
		this.cyberSourceApiField = cyberSourceApiField;
	}

	public Boolean getTokenized() {
		return tokenized;
	}

	public void setTokenized(Boolean tokenized) {
		this.tokenized = tokenized;
	}

	public void setFlagAgro(String esAgro) { this.esAgro = esAgro;	}

	public String esAgro() { return esAgro;	}
	
}