package decidir.sps.core;


public class Motivo {
	private Integer idMotivo;
	private Integer idProtocolo;
	private Integer idTipoOperacion;
	private String descripcion;
	private String descripcion_display;
	private String idMotivoTarjeta;
	private String infoAdicional;
	
	public static final Integer MOTIVO_ERROR_DESCONOCIDO = new Integer(10008);
	public static final Integer MOTIVO_ERROR_CUENTAINVALIDA = new Integer(10009);
	public static final Integer MOTIVO_ERROR_NOHAYRESULTADOS = new Integer(10010);
	public static final Integer MOTIVO_ERROR_ESTADOINVALIDO = new Integer(10011);
	public static final Integer MOTIVO_ERROR_CIERREENCURSO = new Integer(10012);
	public static final Integer IDDATOSINVALIDOS = new Integer(10005);
	public static final Integer WSDATOSINVALIDOS = new Integer(10018);
	public static final Integer WSLOGINERROR = new Integer(10019);
	public static final Integer WSIDVALERROR = new Integer(10020);
	public static final Integer WSIDVALERRORSS = new Integer(10021);
	public static final Integer WSERRORIMPORTE = new Integer(10022);
	public static final Integer MOTIVO_ERROR_CS = new Integer(10025);
	public static final Integer MOTIVO_RECHAZO_CS = new Integer(10026);
	public static final Integer TRX_ORIGINAL_INEXISTENTE = new Integer(302);
	
	public Motivo() {

	}

	public Motivo(Integer idMotivo, String descripcion) {
		this.idMotivo = idMotivo;
		this.descripcion = descripcion;
	}

	public Motivo(Integer idMotivo) {
		this.idMotivo = idMotivo;
	}

	public boolean equals(Motivo otroMotivo) {
		return idMotivo.equals(otroMotivo.getId());
	}

	public String getDescripcion() {
		return descripcion;
	}

	public String getIdMotivoTarjeta(){
		return idMotivoTarjeta;
	}
	
	public Integer getId() {
		return idMotivo;
	}

	public static Motivo getMotivoAmexErrorEnConstruccion1100() {
		return new Motivo(new Integer(10003), "ErrorEnConstruccion1100");
	}

	public static Motivo getMotivoAmexErrorEnConstruccion1110() {
		return new Motivo(new Integer(10004), "ErrorEnConstruccion1110");
	}

	public static Motivo getMotivoAmexErrorEnConstruccion1420() {
		return new Motivo(new Integer(10003), "ErrorEnConstruccion1420");
	}

	public static Motivo getMotivoAmexErrorEnConstruccion1430() {
		return new Motivo(new Integer(10004), "ErrorEnConstruccion1430");
	}

	public static Motivo getMotivoAmexImposibleInicializarServer() {
		return new Motivo(new Integer(10000), "ImposibleInicializarServer");
	}

	public static Motivo getMotivoAmexOtrosProblemasAutorizacion() {
		return new Motivo(new Integer(10002), "OtrosProblemasAutorizacion");
	}

	public static Motivo getMotivoAmexOtrosProblemasReversion() {
		return new Motivo(new Integer(10002), "OtrosProblemasReversion");
	}

	public static Motivo getMotivoAmexRespuestaCloseNoOK() {
		return new Motivo(new Integer(10004), "Respuesta close No OK");
	}

	public static Motivo getMotivoAmexRespuestaOpenNoOK() {
		return new Motivo(new Integer(10001), "Respuesta open No OK");
	}

	public static Motivo getMotivoAmexSinRespuestaClose() {
		return new Motivo(new Integer(10003), "Sin respuesta en el Batch-Close");
	}
	
	public static Motivo getMotivoAmexSinRespuestaDataCapture() {
		return new Motivo(new Integer(10002),
				"Sin respuesta en el Data-Capture");
	}

	public static Motivo getMotivoPagoCuponInsufficientBalance(){
		return new Motivo(new Integer(7), "El monto de los cupones ingresados no alcanza el monto de la operaci�n.");
	}
	
	public static Motivo getMotivoPagoCuponInvalidMerchant(){
		return new Motivo(new Integer(1), "El IdMerchant es inv�lido.");
	}

	public static Motivo getMotivoPagoCuponInvalidMerchantPassword(){
		return new Motivo(new Integer(2), "La Password del Merchant inv�lido.");
	}

	public static Motivo getMotivoPagoCuponInvalidCurrency(){
		return new Motivo(new Integer(3), "El tipo de moneda es inv�lido.");
	}
	
	public static Motivo getMotivoPagoCuponInvalidAmount(){
		return new Motivo(new Integer(4), "El importe es inv�lido.");
	}
	
	public static Motivo getMotivoPagoCuponInvalidToken(){
		return new Motivo(new Integer(5), "Los datos del token son inv�lidos.");
	}

	public static Motivo getMotivoPagoCuponMaxAmountExceeded(){
		return new Motivo(new Integer(6), "El importe de la compra excede el importe autorizado por el sitio.");
	}
	
	public static Motivo getMotivoPagoCuponMaxQtyPurchase(){
		return new Motivo(new Integer(16), "La cantidad de cupones ingresados excede el m�ximo permitido.");
	}
	
	public static Motivo getMotivoPagoCuponInvalidCupon(){
		return new Motivo(new Integer(8), "Uno de los cupones ingresados es inv�lido.");
	}

	public static Motivo getMotivoPagoCuponInvalidCuponConsumido(){
		return new Motivo(new Integer(9), "Uno de los cupones ingresados est� en estado consumido.");
	}

	public static Motivo getMotivoPagoCuponInvalidCuponDistribuido(){
		return new Motivo(new Integer(10), "Uno de los cupones ingresados est� en estado distribuido.");
	}

	public static Motivo getMotivoPagoCuponInvalidCuponAnulado(){
		return new Motivo(new Integer(11), "Uno de los cupones ingresados est� en estado anulado.");
	}

	public static Motivo getMotivoPagoCuponInvalidCuponSuspendido(){
		return new Motivo(new Integer(12), "Uno de los cupones ingresados est� en estado suspendido.");
	}

	public static Motivo getMotivoPagoCuponInvalidCuponVencido(){
		return new Motivo(new Integer(13), "Uno de los cupones ingresados est� en estado vencido.");
	}

	public static Motivo getMotivoPagoCuponInvalidCuponPassword(){
		return new Motivo(new Integer(14), "La Password de al menos un cup�n es inv�lido.");
	}
	public static Motivo getMotivoAmexSinRespuestaOpen() {
		return new Motivo(new Integer(10000), "Sin respuesta en el Batch-Open");
	}

	public static Motivo getMotivoAmexTimeOut() {
		return new Motivo(new Integer(10001), "TimeOut");
	}
	
	public static Motivo getMotivoTimeOutCompra() {
		return new Motivo(new Integer(-1), "TimeOut Compra");
	}

	public static Motivo getMotivoAprovado() {
		return new Motivo(new Integer(0), "Aprovado");
	}

	public static Motivo getMotivoErrorDefaultAmex() {
		return new Motivo(new Integer(9999), "error desconocido");
	}

	public static Motivo getMotivoErrorDefaultParaProtocolo(
			Integer codigoProtocolo) {
		if (codigoProtocolo.equals(Protocolos.codigoProtocoloMoset))
			return getMotivoErrorDefaultVisanet();
		if (codigoProtocolo.equals(Protocolos.codigoProtocoloSet))
			return getMotivoErrorDefaultVisanet();
		if (codigoProtocolo.equals(Protocolos.codigoProtocoloPCuenta))
			return getMotivoErrorDefaultPCuenta();
		if (codigoProtocolo.equals(Protocolos.codigoProtocoloAmex))
			return getMotivoErrorDefaultAmex();
		return getMotivoErrorDefault();
	}

	public static Motivo getMotivoErrorDefaultPCuenta() {
		return new Motivo(new Integer(99), "Venta no autorizada");
	}

	public static Motivo getMotivoErrorDefaultVisanet() {
		return new Motivo(new Integer(99), "Venda n\u00e3o autorizada");
	}

	public static Motivo getMotivoIndefinido() {
		return new Motivo(new Integer(-1), "");
	}

	public String toString() {
		StringBuffer tostr = new StringBuffer();
		tostr.append("<IdMotivo:" + idMotivo);
		tostr.append("|Descripcion:" + descripcion + ">");
		return tostr.toString();
	}

	public void setDescripcion(String descripcion) {
		this.descripcion = descripcion;
	}

	public void setIdMotivoTarjeta(String idMotivoTarjeta){
		this.idMotivoTarjeta = idMotivoTarjeta;
	}
	
	public void setId(Integer idMotivo) {
		this.idMotivo = idMotivo;
	}

	public static Motivo getMotivoVisanetAnulacionNoOK() {
		return new Motivo(new Integer(1), "Operacion no OK");
	}

	public static Motivo getMotivoVisanetAnulacionOK() {
		return new Motivo(new Integer(0), "Operacion OK");
	}

	public static Motivo getMotivoVisanetCierreDeLote(int id) {
		Integer codigo = new Integer(id);
		if (id == 0)
			return new Motivo(codigo, "capturado com sucesso");
		if (id == 1)
			return new Motivo(codigo, "autorizacao negada");
		if (id == 3)
			return new Motivo(codigo, "captura ja efetuada");
		if (id == 99)
			return new Motivo(codigo, "autorizacao inexistente");
		return (new Motivo(new Integer(999),
				"Se recibi\u00f3 desde Back-end codigo de error desconocido"));
	}

	public static Motivo getMotivoVisanetCierreDeLoteErrorEnComunicacion() {
		return new Motivo(new Integer(1000), "Error en comunicacion");
	}

	public static Motivo getMotivoVisanetCierreDeLoteNoOK() {
		return new Motivo(new Integer(9999),
				"alguna captura no recibi\u00f3 respuesta");
	}

	public static Motivo getMotivoVisanetCierreDeLoteOK() {
		return new Motivo(new Integer(10000),
				"Todas las capturas recibieron respuesta");
	}

	public Motivo(Integer idMotivo, Integer idProtocolo, Integer tipoOperacion, String descripcion,
			String descripcion_display) {
		this.idMotivo = idMotivo;
		this.idProtocolo = idProtocolo;
		this.idTipoOperacion = tipoOperacion;
		this.descripcion = descripcion;
		this.descripcion_display = descripcion_display;
	}

	public void copiar(Motivo motivo) {
		descripcion = motivo.descripcion;
		descripcion_display = motivo.descripcion_display;
		idMotivo = motivo.idMotivo;
	}

	public String getDescripcion_display() {
		return descripcion_display;
	}

	public static Motivo getMotivoErrorDefault() {
		return new Motivo(new Integer(9999), "error desconocido");
	}

	public static Motivo getMotivoVisaArgentinaErrorAlConectarConModuloDeComunicaciones() {
		return (new Motivo(new Integer(10000),
				"Error al conectar con el m\u00f3dulo de comunicaciones"));
	}
	
	
	

	public static Motivo getMotivoVisaArgentinaErrorAlRecibirDeModuloDeComunicaciones() {
		return new Motivo(new Integer(10001),
				"Error al recibir del m\u00f3dulo de comunicaciones");
	}

	public static Motivo getMotivoVisaArgentinaErrorDeTimeOutDelSocket() {
		return new Motivo(new Integer(10003), "Error de time out del socket");
	}

	public static Motivo getMotivoVisaArgentinaErrorDeAutenticacion() {
		return new Motivo(new Integer(10004), "Error de autenticacion");
	}
	
	public static Motivo getMotivoVbVRechazo() {
		return new Motivo(new Integer(10016), "Error de autenticacion");
	}

	public static Motivo getMotivoVisaArgentinaErrorReportadoPorModuloDeComunicaciones() {
		return new Motivo(new Integer(10002),
				"Error reportado por el m\u00f3dulo de comunicaciones");
	}

	public static Motivo getMotivoVisaArgentinaOperacionNoReversada() {
		return new Motivo(new Integer(9998), "Operaci\u00f3n no reversada");
	}

	public static Motivo getMotivoVisaArgentinaOperacionReversada() {
		return new Motivo(new Integer(9997), "Operaci\u00f3n reversada");
	}

	public static Motivo getMotivoMonederoArgentinaErrorAlConectarConModuloDeComunicaciones() {
		return (new Motivo(new Integer(10000),
				"Error al conectar con el m\u00f3dulo de comunicaciones"));
	}
	
	public void setDescripcion_display(String newDescripcion_display) {
		descripcion_display = newDescripcion_display;
	}
		
 	public static Motivo getMotivoErrorConexion() {
 		return (new Motivo(new Integer(10000),
 		"Error al conectar con el m\u00f3dulo de comunicaciones"));
 	}
 	
 	public static Motivo getMotivoTweenSCMErroneo() {
 		return (new Motivo(new Integer(10012), "Validaci\u00f3n no alcanzada, c\u00f3digo Celular ID no corresponde a la transacci\u00f3n"));
 	}

 	public static Motivo getMotivoTweenSCMVencido() {
 		return (new Motivo(new Integer(10013), "Validaci\u00f3n no alcanzada, c\u00f3digo Celular ID expir\u00f3"));
 	}
 	
 	public static Motivo getMotivoTweenUsuarioInexistente() {
 		return (new Motivo(new Integer(10014), "Usuario de webservice inexistente"));
 	}
 	
 	public static Motivo getMotivoIdValidatorSinSerevicio() {
 		return (new Motivo(new Integer(WSIDVALERRORSS), "IdValidator sin servicio"));
 	}

 	public static Motivo getMotivoTweenSCMYaUtilizado() {
 		return (new Motivo(new Integer(10015), "Celular ID ya utilizado"));
 	}
	
 	public static Motivo getMotivoErrorImporteDevolucion() {
		return new Motivo(new Integer(WSERRORIMPORTE), "Error En Importe");
	}
 	
 	
 	public void setInfoAdicional(String infoAdicional) {
 		this.infoAdicional = infoAdicional;
 	}
 	
 	public String getInfoAdicional() {
 		return infoAdicional;
 	}

	public Integer getIdProtocolo() {
		return idProtocolo;
	}

	public Integer getIdTipoOperacion() {
		return idTipoOperacion;
	}

// 	public static Motivo getMotivoRechazoPorFraude(int idProtocolo){
// 		try {
//			return DBSPS.getMotivo(Integer.valueOf(idProtocolo), TipoOperacion.Autorizacion, MOTIVO_RECHAZO_CS);
//		} catch (SQLException e) {
//			LogSPS.info("Excepcion obteniendo motivo Rechazo por Fraude: " + e.getLocalizedMessage());
//			e.printStackTrace();
//			return new Motivo(new Integer(MOTIVO_RECHAZO_CS), "Rechazo por fraude");			
//		}
// 	}
 	
 	
 	
}