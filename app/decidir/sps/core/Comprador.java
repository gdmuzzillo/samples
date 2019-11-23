package decidir.sps.core;

import java.util.Date;

//import decidir.sps.jdbc.DBSPSw;
//import decidir.sps.logger.LogSPS;
//import decidir.sps.util.DateParser;
import decidir.sps.util.Documento;
//import decidir.sps.util.HashtableOrdenada;
//import decidir.sps.vista.DatosTransaccion;

public class Comprador {
	private Documento documento;
	private String email;
	private String titular;
	private String nroTarjeta;
	private String nroTarjetaVisible;
	private Integer codigoSeguridad;
	private String vencimientoTarjeta;
	private String ip;
	private String apellido;
	private String calle;
	private String piso;
	private String departamento;
	private String localidad;
	private String codpostal;
	private String provincia;
	private Date fechaDeNacimiento;
	private String nombre;
	private String numeroDePuerta;
	private String telefono;
	private String sexo;
	private String celular;
	private String pin;
	private Long iddomicilio;
	private Long idtransaccion;
	
	private final static String BLANCO = " ";

	public Comprador() {
	}

	private Comprador(String titular, String mailUsu, String nroTarjetaCompleto,
			String vencimientoTarjeta, Integer codigoSeguridad, String sexo,
			String celular, String pin) {
		this.titular = titular;
		email = mailUsu;
		nroTarjeta = nroTarjetaCompleto;
		this.vencimientoTarjeta = vencimientoTarjeta;
		this.codigoSeguridad = codigoSeguridad;
		this.sexo = sexo;
		this.celular = celular;
		this.pin = pin;
	}

	public Integer getCodigoSeguridad() {
		return codigoSeguridad;
	}

//	/* deprecated */
//	public static Comprador getCompradorDelHttpRequest(DatosTransaccion datos) {
//		Comprador comprador = new Comprador();
//		Date fechaNac = null;
//		String strAux = (String) datos.getParametro("NOMBREENTARJETA");
//		
//		if (strAux != null && strAux.length() > 60)
//			strAux = strAux.substring(0, 59);
//			
//		comprador.setTitular(strAux);
//		comprador.setNombre((String) datos.getParametro("NOMBRE"));
//		comprador.setApellido((String) datos.getParametro("APELLIDO"));
//		strAux = (String) datos.getParametro("FECHANACIMIENTO");
//		if (strAux != null) {
//			try {
//				fechaNac = DateParser.parse(strAux, "ddMMyyyy");
//			} catch (Throwable throwable) {
//			}
//		}
//		comprador.setFechaDeNacimiento(fechaNac);
//		comprador.setCalle((String) datos.getParametro("CALLE"));
//		comprador.setNumeroDePuerta((String) datos.getParametro("NROPUERTA"));
//		comprador.setTelefono((String) datos.getParametro("TELEFONO"));
//		comprador.setSexo((String) datos.getParametro("SEXOTITULAR"));
//		strAux = (String) datos.getParametro("TIPODOC");
//		
//		Integer tipoDoc;
//		if (strAux == null){
//			tipoDoc = null;
//		}
//		else{
//			//SOLUCI�N TIPODOC
//			if("".equals(strAux)){
//				
//				Comprador comprador2 = (Comprador) datos.getParametro("COMPRADOR");
//				tipoDoc = comprador2.getDocumento().getTipoDocumento();
//			}else{
//				tipoDoc = new Integer(strAux);
//			}
//		}
//		comprador.setTipoDocumento(tipoDoc);
//		comprador.setNroDocumento((String) datos.getParametro("NRODOC"));
//		strAux = (String) datos.getParametro("EMAILCLIENTE");
//		if (strAux != null && strAux.length() > 80)
//			strAux = strAux.substring(0, 79);
//		comprador.setEmail(strAux);
//		comprador.setNroTarjeta((String) datos.getParametro("NROTARJETA"));
//
//		if("".equals((String) datos.getParametro("VENCIMIENTO"))){
//			comprador.setVencimientoTarjeta((String) datos.getParametro("VENCTARJETA"));
//		}else{
//			comprador.setVencimientoTarjeta((String) datos.getParametro("VENCIMIENTO"));
//		}
//		
//		strAux = (String) datos.getParametro("CODSEGURIDAD");
//		Integer cod = (strAux == null || strAux.trim().equals("") ? null
//				: new Integer(strAux));
//		comprador.setCodigoSeguridad(cod);
//		comprador.setCelular((String) datos.getParametro("CELULAR"));
//		comprador.setPin((String) datos.getParametro("PIN"));
//		return comprador;
//	}
//
//	public static Comprador getComprador(DatosTransaccion datos) {
//
//		Comprador comprador = new Comprador();
//		Date fechaNac = null;
//		String strAux = (String) datos.getParametro("NOMBREENTARJETA");
//					
//		if (strAux != null && strAux.length() > 60)
//			strAux = strAux.substring(0, 59);
//			
//		comprador.setTitular(strAux);
//		comprador.setNombre((String) datos.getParametro("NOMBRE"));
//		comprador.setApellido((String) datos.getParametro("APELLIDO"));
//		strAux = (String) datos.getParametro("FECHANACIMIENTO");
//		if (strAux != null) {
//			try {
//				fechaNac = DateParser.parse(strAux, "ddMMyyyy");
//			} catch (Throwable throwable) {
//				/* empty */
//			}
//		}
//		comprador.setFechaDeNacimiento(fechaNac);
//		comprador.setCalle((String) datos.getParametro("CALLE"));
//		comprador.setNumeroDePuerta((String) datos.getParametro("NROPUERTA"));
//		comprador.setTelefono((String) datos.getParametro("TELEFONO"));
//		comprador.setSexo((String) datos.getParametro("SEXOTITULAR"));
//		strAux = (String) datos.getParametro("TIPODOC");
//		
//		Integer tipoDoc;
//		if (strAux == null)
//			tipoDoc = null;
//		else
//			tipoDoc = new Integer(strAux);
//		comprador.setTipoDocumento(tipoDoc);
//		comprador.setNroDocumento((String) datos.getParametro("NRODOC"));
//		strAux = (String) datos.getParametro("EMAILCLIENTE");
//		if (strAux != null && strAux.length() > 80)
//			strAux = strAux.substring(0, 79);
//		comprador.setEmail(strAux);
//		comprador.setNroTarjeta((String) datos.getParametro("NROTARJETA"));
//
//		if("".equals((String) datos.getParametro("VENCIMIENTO"))){
//			comprador.setVencimientoTarjeta((String) datos.getParametro("VENCTARJETA"));
//		}else{
//			comprador.setVencimientoTarjeta((String) datos.getParametro("VENCIMIENTO"));
//		}
//		
//		strAux = (String) datos.getParametro("CODSEGURIDAD");
//		Integer cod = (strAux == null || strAux.trim().equals("") ? null
//				: new Integer(strAux));
//		comprador.setCodigoSeguridad(cod);
//		
//		return comprador;
//	}

	public Documento getDocumento() {
		if (documento == null)
			documento = new Documento();
		return documento;
	}

	public String getEmail() {
		return email == null ? "" : email;
	}

	public String getIP() {
		return ip;
	}

	public String getNroTarjeta() {
		return nroTarjeta;
	}

	public String getTitular() {
		return titular;
	}

	public String getVencimientoTarjeta() {
		return vencimientoTarjeta;
	}

	public String getSexo() {
		return sexo;
	}

	public String toString() {
		String strNroTarj;
		if (nroTarjeta == null)
			strNroTarj = "null";
		else if (nroTarjeta.length() >= 4)
			strNroTarj = nroTarjeta.substring(nroTarjeta.length() - 4,
					nroTarjeta.length());
		else
			strNroTarj = nroTarjeta;

		StringBuffer tostr = new StringBuffer();
		tostr.append("<Documento:" + documento);
		tostr.append("|Titular:" + titular);
		tostr.append("|Nombre:" + nombre);
		tostr.append("|Apellido:" + apellido);
		tostr.append("|FechaNacimiento:" + fechaDeNacimiento);
		tostr.append("|Calle:" + calle);
		tostr.append("|NroPuerta:" + numeroDePuerta);
		tostr.append("|Piso:" + piso);
		tostr.append("|Departamento:" + departamento);
		tostr.append("|Codpostal:" + codpostal);
		tostr.append("|Localidad:" + localidad);
		tostr.append("|Provincia:" + provincia);
		tostr.append("|Telefono:" + telefono);
		tostr.append("|Email:" + email);
		tostr.append("|Sexo:" + sexo);
		tostr.append("|NroTarjeta:" + strNroTarj + ">");
		return tostr.toString();
	}

	public void setCodigoSeguridad(Integer newCodigoSeguridad) {
		codigoSeguridad = newCodigoSeguridad;
	}

	public void setDocumento(Documento newDocumento) {
		documento = newDocumento;
	}

	public void setEmail(String newEmail) {
		email = newEmail;
	}

	public void setIP(String newIP) {
		ip = newIP;
	}

	public void setNroTarjeta(String newNroTarjeta) {
		nroTarjeta = newNroTarjeta;

		if (nroTarjeta != null) 
		{
			if (nroTarjeta.length() >= 4)
			{
				setNroTarjetaVisible(nroTarjeta.substring(nroTarjeta.length() - 4));
			}
			else
			{
				setNroTarjetaVisible(nroTarjeta);
			}
		}
		else
		{
			setNroTarjetaVisible("");
		}
		
	}

	public void setTitular(String newTitular) {
		titular = newTitular;
	}

	public void setTitular(String newNombre, String newApellido) {
		titular = (newNombre == null) ? "" : newNombre.trim();
		titular += (newApellido == null) ? "" : BLANCO + newApellido.trim();
	}

	public void setVencimientoTarjeta(String newVencimientoTarjeta) {
		vencimientoTarjeta = newVencimientoTarjeta;
	}

	public String getNroTarjetaVisible() {
		return nroTarjetaVisible;
	}

	private void setNroTarjetaVisible(String newNroTarjetaVisible) {
		nroTarjetaVisible = newNroTarjetaVisible;
	}

	public Comprador(String titular, Integer idTipoDoc, String nroDocumento,
			String ipComprador, String mailUsu, String nroTarjetaVisible,
			String nroTarjetaCompleto, String vencimientoTarjeta,
			Integer codigoSeguridad, String sexo, String celular, String pin) {
		this(titular, mailUsu, nroTarjetaCompleto, vencimientoTarjeta,
				codigoSeguridad, sexo, celular, pin);
		setNroTarjetaVisible(nroTarjetaVisible);
		setDocumento(new Documento(idTipoDoc, nroDocumento));
		ip = ipComprador;
	}

	public void copiarValoresNoNulos(Comprador c2) {
		String strAux = c2.getTitular();
		if (strAux != null && !strAux.equals(""))
			setTitular(strAux);
		strAux = c2.getNombre();
		if (strAux != null && !strAux.equals(""))
			setNombre(strAux);
		strAux = c2.getApellido();
		if (strAux != null && !strAux.equals(""))
			setApellido(strAux);
		Date fechaNac = c2.getFechaDeNacimiento();
		if (fechaNac != null)
			setFechaDeNacimiento(fechaNac);
		strAux = c2.getCalle();
		if (strAux != null && !strAux.equals(""))
			setCalle(strAux);
		strAux = c2.getNumeroDePuerta();
		if (strAux != null && !strAux.equals(""))
			setNumeroDePuerta(strAux);
		strAux = c2.getTelefono();
		if (strAux != null && !strAux.equals(""))
			setTelefono(strAux);
		Integer tipoDoc = c2.getDocumento().getTipoDocumento();
		if (tipoDoc != null)
			setTipoDocumento(tipoDoc);
		strAux = c2.getDocumento().getNroDocumento();
		if (strAux != null)
			setNroDocumento(strAux);
		strAux = c2.getEmail();
		if (strAux != null && !strAux.equals(""))
			setEmail(strAux);
		strAux = c2.getNroTarjeta();
		if (strAux != null && !strAux.equals(""))
			setNroTarjeta(strAux);
		strAux = c2.getNroTarjetaVisible();
		if (strAux != null && !strAux.equals(""))
			setNroTarjetaVisible(strAux);
		strAux = c2.getVencimientoTarjeta();
		if (strAux != null && !strAux.equals(""))
			setVencimientoTarjeta(strAux);
		Integer cod = c2.getCodigoSeguridad();
		if (cod != null)
			setCodigoSeguridad(cod);
		strAux = c2.getSexo();
		if (strAux != null && !strAux.equals(""))
			setSexo(strAux);
		strAux = c2.getCelular();
		if (strAux != null)
			setCelular(strAux);
		strAux = c2.getPin();
		if (strAux != null)
			setPin(strAux);
		strAux = c2.getPiso();
		if (strAux != null && !strAux.equals(""))
			setPiso(strAux);
		strAux = c2.getDepartamento();
		if (strAux != null && !strAux.equals(""))
			setDepartamento(strAux);
		strAux = c2.getLocalidad();
		if (strAux != null && !strAux.equals(""))
			setLocalidad(strAux);
		strAux = c2.getCodPostal();
		if (strAux != null && !strAux.equals(""))
			setCodPostal(strAux);
		strAux = c2.getProvincia();
		if (strAux != null && !strAux.equals(""))
			setProvincia(strAux);
	}

	public String getAnoVencimientoTarjeta() {
		return vencimientoTarjeta.substring(2, 4);
	}

	public String getApellido() {
		return apellido == null ? "" : apellido.trim();
	}

	public String getCalle() {
		return calle == null ? "" : calle.trim();
	}

	public String getNroPuerta() {
 		return numeroDePuerta == null ? "" : numeroDePuerta.trim();
 	}
 	
	public Date getFechaDeNacimiento() {
		return fechaDeNacimiento;
	}

//	public HashtableOrdenada<String, Object> getHashTags() {
//		HashtableOrdenada<String, Object> ht = new HashtableOrdenada<String, Object>();
//		String strFecha = null;
//		String[] strSelectedTags = new String[4];
//		String[] strSelectedTagsSexo = new String[3];
//		try {
//			strFecha = DateParser.parse(getFechaDeNacimiento(), "ddMMyyyy");
//		} catch (Throwable throwable) {
//			/* empty */
//		}
//		ht.put("nombreentarjeta", getTitular() == null ? "" : getTitular());
//		ht.put("nombre", getNombre() == null ? "" : getNombre());
//		ht.put("apellido", getApellido() == null ? "" : getApellido());
//		ht.put("fechanacimiento", strFecha == null ? "" : strFecha);
//		ht.put("calle", getCalle() == null ? "" : getCalle());
//		ht.put("nropuerta", getNumeroDePuerta() == null ? ""
//				: getNumeroDePuerta());
//		ht.put("telefono", getTelefono() == null ? "" : getTelefono());
//		
//		for (int i = 0; i < strSelectedTags.length; i++)
//			strSelectedTags[i] = "";
//		Integer tipoDoc = getDocumento().getTipoDocumento();
//		
//		if (tipoDoc != null)
//			strSelectedTags[tipoDoc.intValue() - 1] = "selected";
//		for (int i = 0; i < strSelectedTags.length; i++)
//			ht.put("selectedtipodoc" + (i + 1), strSelectedTags[i]);
//		
//		
//		for (int i = 0; i < strSelectedTagsSexo.length; i++)
//			strSelectedTagsSexo[i] = "";
//		String sexo = getSexo();
//		
//		if (sexo != null)
//			strSelectedTagsSexo[Integer.parseInt(sexo) - 1] = "selected";
//		for (int i = 0; i < strSelectedTagsSexo.length; i++)
//			ht.put("selectedsexotitular" + (i + 1), strSelectedTagsSexo[i]);
//		
//		
//		
//		ht.put("nrodoc", (getDocumento().getNroDocumento() == null ? ""
//				: getDocumento().getNroDocumento()));
//		ht.put("emailcliente", getEmail() == null ? "" : getEmail());
//		return ht;
//	}

	public String getMesVencimientoTarjeta() {
		return vencimientoTarjeta.substring(0, 2);
	}

	public String getNombre() {
		return nombre == null ? "" : nombre.trim();
	}

	public String getNumeroDePuerta() {
		String salida = "";
		if (numeroDePuerta != null) {
			if (numeroDePuerta.trim().length()>10){
				salida = numeroDePuerta.trim().substring(0,10);
			}else{
				salida = numeroDePuerta.trim();
			}
		}
		return salida;
	}

	public String getTelefono() {
		return telefono == null ? "" : telefono.trim();
	}

	public String getPiso() {
		return piso == null ? "" : piso.trim();
	}

	public String getDepartamento() {
		return departamento == null ? "" : departamento.trim();
	}

	public String getLocalidad() {
		return localidad == null ? "" : localidad.trim();
	}

	public String getCodPostal() {
		return codpostal == null ? "" : codpostal.trim();
	}

	public String getProvincia() {
		return provincia == null ? "" : provincia.trim();
	}

	public Long getIdDomicilio() {
		return iddomicilio == null ? new Long(0) : iddomicilio;
	}

	public void setApellido(String newApellido) {
		apellido = newApellido;
	}

	public void setCalle(String newCalle) {
		calle = newCalle;
	}

	public void setPiso(String newPiso) {
		piso = newPiso;
	}

	public void setDepartamento(String newDepartamento) {
		departamento = newDepartamento;
	}

	public void setLocalidad(String newLocalidad) {
		localidad = newLocalidad;
	}

	public void setCodPostal(String newCodpostal) {
		codpostal = newCodpostal;
	}

	public void setProvincia(String newProvincia) {
		provincia = newProvincia;
	}

	public void setIdDomicilio(Long newIddomicilio) {
		iddomicilio = newIddomicilio;
	}

	public void setFechaDeNacimiento(Date newFechaDeNacimiento) {
		fechaDeNacimiento = newFechaDeNacimiento;
	}

	public void setNombre(String newNombre) {
		nombre = newNombre;
	}

	public void setNroDocumento(String nroDoc) {
		getDocumento().setNroDocumento(nroDoc);
	}

	public void setNumeroDePuerta(String newNumeroDePuerta) {
		numeroDePuerta = newNumeroDePuerta;
	}

	public void setTelefono(String newTelefono) {
		telefono = newTelefono;
	}

//	public void setTipoDocumento(String newTipoDoc) {
//		try {
//			Integer tipoDoc = DBSPSw.getIdTipoDocumento(newTipoDoc);
//			setTipoDocumento(tipoDoc);
//		} catch (Exception e) {
//			LogSPS.getLog().error("Comprador>>setTipoDocumento", e);
//		}
//	}

	public void setTipoDocumento(Integer tipoDoc) {
		getDocumento().setTipoDocumento(tipoDoc);
	}

	public void setSexo(String sexo) {
		if (sexo != null && !sexo.equals(""))
			this.sexo = sexo;
	}

	public void setCelular(String celular) {
		if (celular != null && !celular.equals(""))
			this.celular = celular;
	}

	public void setPin(String pin) {
		if (pin != null && !pin.equals(""))
			this.pin = pin;
	}

	public String getCelular() {
		return (celular != null) ? celular : "";
	}

	public String getPin() {
		return (pin != null) ? pin : "";
	}

	public Long getIdtransaccion() {
		return idtransaccion == null ? new Long(0) : idtransaccion;
	}

	public void setIdtransaccion(Long idtransaccion) {
		this.idtransaccion = idtransaccion;
	}
}
