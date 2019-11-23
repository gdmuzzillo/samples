package legacy.decidir.sps.domain;

import java.security.GeneralSecurityException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.sql.DataSource;

import com.decidir.coretx.domain.Banco;
import com.decidir.encrypt.CryptoData;

import decidir.sps.core.*;
import org.slf4j.Logger;

import decidir.sps.sac.vista.utils.info.InfoSite;
import decidir.sps.util.Pais;

public class LegacyDBSPS {

	private DataSource dataSource;
	private Logger logger;

	public LegacyDBSPS(Logger logger, DataSource datasource) {
		this.dataSource = datasource;
		this.logger  = logger;
	}



	static private String StringSelectCuenta = "MPT.nroid MPT_nroId, "
			+ "MPT.password MPT_password, "
			+ "MPT.enc_keyid MPT_enc_keyid, "
			+ "MPT.idmediopago MPT_idMedioPago, "
			+ "MPT.idbackend MPT_idBackend, "
			+ "MPT.idprotocolo MPT_idProtocolo, "
			+ "MPT.plann MPT_habilitadaPlanN, "
			+ "MPT.habilitado MPT_habilitado, "
			+ "MPT.nroterminal MPT_nroTerminal, "
			+ "MP.descri MP_descripcion, "
			+ "MP.limite MP_limite, "
			+ "MP.validabines MP_validaBines, "
			+ "MP.idmarcatarjeta MP_idMarcaTarjeta, "
			+ "MP.bin_regex MP_bin_regex, "
			+ "MP.validate_luhn MP_validate_luhn, "
			+ "MO.idmoneda MO_idMoneda, "
			+ "MO.descri MO_decripcion, "
			+ "MO.simbolo MO_simbolo, "
			+ "MO.idmonedaisoalfa MO_codigoIsoAlfaNum, "
			+ "MO.idmonedaisonum MO_codigoIsoNum, "
			+ "PR.descri PR_descripcion, " + "BK.descri BK_descripcion, "
			+ "MPT.autorizaendospasos MPT_autorizaEnDosPasos, "
			+ "MPT.porcentajesuperior MPT_porcentajesuperior, "
			+ "MPT.porcentajeinferior MPT_porcentajeinferior, "
			+ "MPT.utilizavbv MPT_utilizaVbV, "
			+ "MPT.pasavbv MPT_pasaVbV, "
			+ "MPT.pasavbvsinservicio MPT_pasaVbVSinServicio, "
			+ "MPT.plancuotas MPT_planCuotas, "
			+ "MPT.formatonrotarjetavisible MPT_formatonrotarjetavisible, "
			+ "MPT.pagodiferidohabilitado MPT_pagodiferidohabilitado, "
			+ "MPT.solonacional MPT_solonacional, "
			+ "MPT.tipoplantilla MPT_tipoplantilla, "
			+ "MPT.idsite MPT_idsite, "
			+ "MPT.nroiddestinatario MPT_nroiddestinatario, "
			+ "MB.idbackend MB_idbackend, "
			+ "MB.idprotocolo MB_idprotocolo ";


	public Site obtenerSiteDecidir(String idSite) throws SQLException {
		final String sql = "SELECT cli.idtienda, cli.descri, r.nombre AS nombreRubro, m.descripcion as nombreModelo, hs.idestadosite AS habilitado, sm.site_id AS parent_site_id, " + StringSelectSite
				+ "FROM spsclientes cli, habilitacionsite hs, spssites "
				+ "LEFT JOIN spssites_rubros AS r ON r.idrubro = spssites.rubro "
				+ "LEFT JOIN spssites_modelo AS m ON m.idmodelo = spssites.modelocs "
				+ "LEFT JOIN site_merchant AS sm ON sm.merchant_id = spssites.idsite "
				+ "WHERE cli.idtienda = spssites.idtienda "
				+ "AND spssites.idsite = hs.idsite "
				+ "AND spssites.idsite = ?";
		final DBA dba = new DBA(logger, dataSource);
		try
		{
			final ResultSet rs = dba.select(sql, new Object[] { idSite },
					new int[] { Types.VARCHAR });

			if (rs.next())
			{
				final String idCliente = rs.getString(1);
				final String descripcionCliente = rs.getString(2);
				final Site site = cargarSite(rs);
				site.setIdComercio(idCliente);
				site.setDescripcionCliente(descripcionCliente);
				site.setEstadoTienda(rs.getInt(5));
				return site;
			}
			else
			{
				logger.warn("No se encontro el site con id " + idSite);
				return null;
			}
		}
		finally
		{
			if(dba != null)
			{
				dba.cerrar();
			}
		}
	}
	
	public List<Site> obtenerSitesDecidir(List<String> siteIds) throws SQLException {
		final List<Site> result = new ArrayList<Site>();
		if (siteIds.isEmpty()) return result;
		StringBuilder sb = new StringBuilder();
		final Iterator<String> it = siteIds.iterator();
		sb = sb.append("'");
		sb = sb.append(it.next());		
		while (it.hasNext()) {
			sb = sb.append("','").append(it.next());
		}
		sb = sb.append("'");
		final String sql = "SELECT cli.idtienda, cli.descri, r.nombre AS nombreRubro, m.descripcion as nombreModelo, hs.idestadosite AS habilitado, sm.site_id AS parent_site_id, " + StringSelectSite
				+ "FROM spsclientes cli, habilitacionsite hs, spssites "
				+ "LEFT JOIN spssites_rubros AS r ON r.idrubro = spssites.rubro "
				+ "LEFT JOIN spssites_modelo AS m ON m.idmodelo = spssites.modelocs "
				+ "LEFT JOIN site_merchant AS sm ON sm.merchant_id = spssites.idsite "
				+ "WHERE cli.idtienda = spssites.idtienda "
				+ "AND spssites.idsite = hs.idsite "
				+ "AND spssites.idsite IN ( " + sb.toString() +" )";
		final DBA dba = new DBA(logger, dataSource);
		try
		{
			final ResultSet rs = dba.select(sql);
			
			while (rs.next())
			{
				final String idCliente = rs.getString(1);
				final String descripcionCliente = rs.getString(2);
				final Site site = cargarSite(rs);
				site.setIdComercio(idCliente);
				site.setDescripcionCliente(descripcionCliente);
				site.setEstadoTienda(rs.getInt(5));
				result.add(site);
			}
		}
		finally
		{
			if(dba != null)
			{
				dba.cerrar();
			}
		}
		return result;
	}
	
	

	public List<Encrypted> obtenerEncriptacionDefaultSiteDecidir() throws SQLException {
		List<Encrypted> encrypteds = new ArrayList<Encrypted>();
		final String sql = "select idTipoenc, claveEnc from tipoenc";
		final DBA dba = new DBA(logger, dataSource);
		try
		{
			final ResultSet rs = dba.select(sql, new Object[] {}, new int[] {});
			while (rs.next()) {
				Encrypted encrypted = new Encrypted();
				encrypted.setPublicKey(rs.getString("claveEnc"));
				encrypted.setType(rs.getString("idTipoenc"));
				encrypteds.add(encrypted);
			}
			return encrypteds;
		}
		finally
		{
			if(dba != null)
			{
				dba.cerrar();
			}
		}
	}

	private Site cargarSite(ResultSet rs) throws SQLException {

		final String matriz = rs.getString("SIT_matriz");
		final String usuario = rs.getString("SIT_usuario");
		final String password = rs.getString("SIT_password");
		final String sucursal = rs.getString("SIT_sucursal");
		final String sector = rs.getString("SIT_sector");

		String sReutiliza;
		if (rs.getString("SIT_reutilizaTransaccion")==null){
			sReutiliza = "N";
		}else{
			sReutiliza = rs.getString("SIT_reutilizaTransaccion");
		}

		final VerazidLogin verazidLogin =
			(matriz == null && usuario == null && password == null && sucursal == null && sector == null)?
					null : new VerazidLogin(matriz, usuario, password, sucursal, sector);

//		int idPais = DBA.rs2Int(rs, "SIT_idPais");
//		Pais pais = getPais(idPais);
//		int idTipoActividad = DBA.rs2Int(rs, "SIT_idTipoActividad");
//		TipoActividad tipoActividad = getTipoActividad(idTipoActividad);

		// TODO
		List<Cuenta> cuentas = new ArrayList<Cuenta>();
		List<InfoSite> subsites = new ArrayList<InfoSite>();

		String SIT_enviarResuOnLine = rs.getString("SIT_enviarResuOnLine");
		char SIT_enviarResuOnLineChar = SIT_enviarResuOnLine != null && SIT_enviarResuOnLine.length() > 0 ? SIT_enviarResuOnLine.charAt(0) : 'N';
		String retornaTarjetaEnc = rs.getString("SIT_retornaTarjetaEnc");

		return new Site(rs.getString("SIT_idSite"),
				rs.getString("SIT_descripcionSite"),
				rs.getString("SIT_razonSocial"),
				rs.getString("SIT_direccion"),
				rs.getString("SIT_codigoPostal"),
				DBA.rs2Int(rs, "SIT_idPais"),
				DBA.rs2Int(rs, "SIT_idTipoActividad"),
				rs.getString("SIT_mail"),
				rs.getString("SIT_DNS"),
				rs.getString("SIT_IP"),
				rs.getString("SIT_URLPost"),
				rs.getString("SIT_replyMail"),
				SIT_enviarResuOnLineChar,
				DBA.rs2Bool(rs,	"SIT_mandarMailAUsuario"),
				DBA.rs2Bool(rs,	"SIT_mandarMailASite"),
				DBA.rs2Bool(rs, "SIT_utilizaFirma"),
				DBA.rs2Bool(rs, "SIT_encripta"),
				rs.getString("SIT_tipoEncripcion"),
				rs.getString("SIT_publicKey"),
				DBA.rs2Bool(rs, "SIT_usaURLDinamica"),
				DBA.rs2Bool(rs, "SIT_enviaMedioDePago"),
				DBA.rs2Bool(rs, "SIT_autorizaAmexEnDosPasos"),
				DBA.rs2Bool(rs, "SIT_autorizaDinersEnDosPasos"),
				DBA.rs2Bool(rs, "SIT_autorizaVisaEnDosPasos"),
				DBA.rs2Bool(rs,"SIT_validaRangoNroTarjeta"),
				sReutiliza.charAt(0),
				DBA.rs2Int(rs,"SIT_timeoutcompra"),
				DBA.rs2Bool(rs, "SIT_mandarMailOperaciones"),
				DBA.rs2Int(rs,"SIT_versionResumen"),
				verazidLogin,
				rs.getString("SIT_transaccionesdistribuidas"),
				rs.getString("SIT_montoporcent"),
				rs.getString("SIT_tienereglas"),
				rs.getString("SIT_idvalidator"),
				rs.getString("SIT_tipoid"),
				rs.getString("SIT_sinservicioid"),
				rs.getString("SIT_pasaid"),
				rs.getString("SIT_agregador"),
				rs.getString("SIT_cierreunificado"),
				rs.getString("SIT_Mensajeria"),
				rs.getString("SIT_flagCS"),
				new SiteModeloCS(rs.getInt("SIT_modeloCS"), rs.getString("nombreModelo")),
				rs.getString("SIT_mid"),
				rs.getString("SIT_securityKey"),
				rs.getString("SIT_securityKeyExpirationDate"),
				new SiteRubro(rs.getInt("SIT_rubro"), rs.getString("nombreRubro")),
				rs.getString("SIT_autorizaseguir"),
				rs.getString("SIT_csreversiontimeout"),
				cuentas,
				subsites,
				DBA.rs2Bool(rs, "SIT_flagClaveHash"),
				rs.getString("SIT_claveHash"),
				DBA.rs2Bool(rs, "SIT_validaOrigen"),
				retornaTarjetaEnc != null && retornaTarjetaEnc.equalsIgnoreCase("S"),
				rs.getDate("SIT_fechaUsoHash"),
				rs.getString("parent_site_id"),
				rs.getString("SIT_mensajeria_mpos"),
				DBA.rs2Bool(rs, "SIT_tokenized"),
				DBA.rs2Int(rs,"SIT_timeToLive"));

	}


	public List<Cuenta> getCuentas(Site site) throws SQLException, GeneralSecurityException {
		Vector<Cuenta> cuentas = new Vector<Cuenta>();
		String sql = "SELECT " + StringSelectCuenta + ", "
		+ "EXISTS (SELECT 1 FROM rangos_mediopago rm  WHERE rm.blacklist =  1 AND rm.idmediopago = mb.idmediopago) AS blacklist, "
		+ "EXISTS (SELECT 1 FROM rangos_mediopago rm WHERE rm.blacklist =  0 AND rm.idmediopago = mb.idmediopago) AS whitelist "
		+"FROM "
		+"spsmonedas MO, spsprotocolos PR, spsbackend BK , "
		+"spsmedpagotienda MPT, "
		+"spsmediopago MP, "
		+ "mediopago_backend MB "
		+"WHERE MPT.idmediopago = MP.idmediopago "
		+"AND MP.idmoneda = MO.idmoneda "
		+"AND MPT.idprotocolo = PR.idprotocolo "
		+"AND MPT.idbackend = BK.idbackend AND MPT.idsite = ? "
		+"AND MP.idmediopago = MB.idmediopago "
		+ "ORDER BY MPT_idmediopago, MO_idmoneda";

		DBA dba = new DBA(logger, dataSource);
		try
		{
			ResultSet rs = dba.select(sql, new Object[] { site.getIdSite()},
					new int[] { Types.VARCHAR });

			while (rs.next())
				cuentas.addElement(cargarCuenta(site, rs));
		}
		finally
		{
			if(dba != null)
			{
				dba.cerrar();
			}
		}

		return cuentas;
	}


	/**
	 * Dado un ResultSet, crea y devuelve una cuenta cargando con los datos del
	 * ResultSet y setea el site de la cuenta devuelta con el objeto "site"
	 */
	private Cuenta cargarCuenta(Site site, ResultSet rs)
			throws SQLException, GeneralSecurityException {
		Cuenta cuenta;
//		Protocolo protocolo;
//		BackEnd backEnd;
		Moneda moneda;

		moneda = new Moneda(rs.getString("MO_idMoneda"),
				rs.getString("MO_decripcion"), rs.getString("MO_simbolo"),
				rs.getString("MO_codigoIsoNum"),
				rs.getString("MO_codigoIsoAlfaNum"));

		String idMedioPago = rs.getString("MPT_idMedioPago");

		int protocoloId = DBA.rs2Int(rs, "MPT_idProtocolo");
		String backEndId = rs.getString("MPT_idBackend");

		String password = "";
//		if(rs.getBytes("MPT_password")!=null)
//			 password = new String(Encripcion.desencriptar(rs.getBytes("MPT_password"), rs.getInt("MPT_enc_keyid")));

		final String soloNacional = rs.getString("MPT_solonacional");

		String nroIdDestinatario = rs.getString("MPT_nroiddestinatario");
		nroIdDestinatario = nroIdDestinatario == null ? "" : nroIdDestinatario ;

		cuenta = new Cuenta(site, idMedioPago,
				protocoloId, backEndId,
				/*protocolo, backEnd,*/
				rs.getString("MPT_nroId"), password, DBA.rs2Bool(rs,
						"MPT_habilitadaPlanN"), DBA.rs2Bool(rs,
						"MPT_habilitado"), rs.getString("MPT_nroTerminal"),
				DBA.rs2Bool(rs, "MPT_autorizaEnDosPasos"),
				rs.getInt("MPT_porcentajeinferior"),
				rs.getInt("MPT_porcentajesuperior"),
				DBA.rs2Bool(rs, "MPT_utilizaVbV"), rs.getString("MPT_planCuotas"),
				DBA.rs2Bool(rs, "MPT_pasavbv"), DBA.rs2Bool(rs,
						"MPT_pasavbvsinservicio"),
				rs.getString("MPT_formatonrotarjetavisible"),

				DBA.rs2Bool(rs, "MPT_pagodiferidohabilitado"),
		soloNacional != null && soloNacional.equalsIgnoreCase("S"),
		rs.getString("MPT_tipoplantilla"),
		nroIdDestinatario);

		return cuenta;
	}


	public Pais getPais(Integer id) throws SQLException {
		Pais pais = null;
		String sql = "SELECT spspais.idpais idPais, "
				+ "spspais.descri descripcion, "
				+ "spspais.idpaisisonum codigoIsoNum, "
				+ "spspais.idpaisisoalfa codigoIsoAlfaNum "
				+ "FROM spspais WHERE spspais.idpais = ?";

		DBA dba = new DBA(logger, dataSource);
		try
		{
			ResultSet rs = dba.select(sql, new Object[] { id },
					new int[] { Types.SMALLINT });
			if (rs.next())
				pais = new Pais(DBA.rs2Int(rs, "idPais"),
						rs.getString("descripcion"), rs.getString("codigoIsoNum"),
						rs.getString("codigoIsoAlfaNum"));
			return pais;
		}
		finally
		{
			if(dba != null)
			{
				dba.cerrar();
			}
		}
	}


	public TipoActividad getTipoActividad(Integer id)
			throws SQLException {
		TipoActividad ta = null;
		String sql = "SELECT ta.idtipoactividad idTipoActividad, "
				+ "ta.descri descripcion FROM tipoactividad ta "
				+ "WHERE ta.idtipoactividad = ?";

		DBA dba = new DBA(logger, dataSource);
		try
		{
			ResultSet rs = dba.select(sql, new Object[] { id },
					new int[] { Types.SMALLINT });
			if (rs.next())
				ta = new TipoActividad(DBA.rs2Int(rs, "idTipoActividad"),
						rs.getString("descripcion"));
			return ta;
		}
		finally
		{
			if(dba != null)
			{
				dba.cerrar();
			}
		}
	}



	private static final String StringSelectSite = "spssites.idsite SIT_idSite, "

			+ "spssites.descri SIT_descripcionSite, "
			+ "spssites.razonsocial SIT_razonSocial, "
			+ "spssites.direccion SIT_direccion, "
			+ "spssites.codpos SIT_codigoPostal, "
			+ "spssites.idpais SIT_idPais, "
			+ "spssites.idtipoactividad SIT_idTipoActividad, "
			+ "spssites.mail SIT_mail, "
			+ "spssites.idtienda SIT_idTienda, "
			+ "spssites.url SIT_DNS, "
			+ "spssites.ip SIT_IP, "
			+ "spssites.urlpost SIT_URLPost, "
			+ "spssites.replymail SIT_replyMail, "
			+ "spssites.reciberesuonline SIT_enviarResuOnLine, "
			+ "spssites.flgmailusu SIT_mandarMailAUsuario, "
			+ "spssites.flgmailsite SIT_mandarMailASite, "
			+ "spssites.flgfirma SIT_utilizaFirma, "
			+ "spssites.flgencripta SIT_encripta, "
			+ "spssites.tipoencripcion SIT_tipoEncripcion, "
			+ "spssites.publickey SIT_publicKey, "
			+ "spssites.flagclavehash SIT_flagClaveHash, "
			+ "spssites.clavehash SIT_claveHash, "
			+ "spssites.usaurldinamica SIT_usaURLDinamica, "
			+ "spssites.enviamediodepago SIT_enviaMedioDePago, "
			+ "spssites.autorizaamexendospasos SIT_autorizaAmexEnDosPasos, "
			+ "spssites.autorizadinersendospasos SIT_autorizaDinersEnDosPasos, "
			+ "spssites.autorizavisaendospasos SIT_autorizaVisaEnDosPasos, "
			+ "spssites.validarangonrotarjeta SIT_validaRangoNroTarjeta, "
			+ "spssites.reutilizartransaccion SIT_reutilizaTransaccion, "
			+ "spssites.timeoutcompra SIT_timeoutcompra, "
			+ "spssites.mandarMailOperaciones SIT_mandarMailOperaciones, "
	        + "spssites.versionresumen SIT_versionResumen, "
		    + "spssites.matriz SIT_matriz, "
		    + "spssites.usuario SIT_usuario, "
		    + "spssites.password SIT_password, "
		    + "spssites.sucursal SIT_sucursal, "
		    + "spssites.sector SIT_sector, "
			+ "spssites.transaccionesdistribuidas SIT_transaccionesdistribuidas, "
			+ "spssites.montoporcent SIT_montoporcent, "
			+ "spssites.tienereglas SIT_tienereglas, "
			+ "spssites.idvalidator SIT_idvalidator, "
			+ "spssites.tipoid SIT_tipoid, "
			+ "spssites.sinservicioid SIT_sinservicioid, "
			+ "spssites.pasaid SIT_pasaid, "
			+ "spssites.agregador SIT_agregador, "
			+ "spssites.cierreunificado SIT_cierreunificado, "
			+ "spssites.mensajeria SIT_Mensajeria, "
			+ "spssites.flagcs SIT_flagCS, "
			+ "spssites.modelocs SIT_modeloCS, "
			+ "spssites.mid SIT_mid, "
			+ "spssites.securitykey SIT_securityKey, "
			+ "spssites.securitykeyexpirationdate SIT_securityKeyExpirationDate, "
			+ "spssites.rubro SIT_rubro, "
			+ "spssites.autorizaseguir SIT_autorizaseguir, "
			+ "spssites.csreversiontimeout SIT_csreversiontimeout, "
			+ "spssites.validaorigen SIT_validaOrigen, "
			+ "spssites.retornatarjetaenc SIT_retornaTarjetaEnc, "
			+ "spssites.fechausohash SIT_fechaUsoHash, "
			+ "spssites.mensajeria_mpos SIT_mensajeria_mpos, "
			+ "spssites.tokenized SIT_tokenized, "
			+ "spssites.time_to_live SIT_timeToLive ";

	public List<Moneda> getMonedas() {


		DBA dba = new DBA(logger, dataSource);
			try {

			// Monedas
			String sql = "SELECT mo.idmoneda MO_idMoneda, mo.descri MO_decripcion, "
				+ "mo.simbolo MO_simbolo, mo.idmonedaisoalfa MO_codigoIsoAlfaNum, "
				+ "mo.idmonedaisonum MO_codigoIsoNum "
				+ "FROM spsmonedas mo";
			//"ORDER BY MO_idMoneda";

			List<Moneda> vector = new ArrayList<Moneda>();
			ResultSet rs = dba.select(sql);
			while (rs.next()) {
				Moneda moneda = new Moneda(rs.getString("MO_idMoneda"),
						rs.getString("MO_decripcion"),
						rs.getString("MO_simbolo"),
						rs.getString("MO_codigoIsoNum"),
						rs.getString("MO_codigoIsoAlfaNum"));
				vector.add(moneda);
			}
			return vector;
		}
		catch(SQLException e) {
			throw new RuntimeException(e);
		}finally {
			if(dba != null) try {dba.cerrar();} catch(Exception e){logger.error("Error cerrando conexion con la base", e);}
		}
	}

	private Map<String, Moneda> mapaMonedas() {

		List<Moneda> monedas = getMonedas();
		Map<String, Moneda> mapa = new HashMap<String, Moneda>();
		for(Moneda moneda : monedas) {
			mapa.put(moneda.getIdMoneda(), moneda);
		}
		return mapa;
	}


	public List<MedioPago> getMediosPago() {

		Map<String, Moneda> mapaMonedas = mapaMonedas();

		DBA dba = new DBA(logger, dataSource);
		try {


			// Medios de pago
			String sql = "SELECT mp.idmediopago MP_idMedioPago, "
					+ "mp.descri        MP_descripcion, "
					+ "mp.idmoneda      MP_idMoneda, "
					+ "mp.idmarcatarjeta MP_idMarcaTarjeta, "
					+ "mp.limite MP_limite, "
					+ "mp.validabines MP_validaBines, "
					+ "mp.bin_regex MP_bin_regex, "
					+ "mp.validate_luhn MP_validate_luhn, "
					+ "mp.cybersourceapifield MP_cybersourceapifield, "
					+ "mp.tokenized MP_tokenized, "
					+ "mp.esAgro MP_esAgro, "
					+ "mb.idbackend MB_idbackend, "
					+ "mb.idprotocolo MB_idprotocolo, "
					+ "mpo.annulment MPO_annulment, "
					+ "mpo.annulment_pre_approved MPO_annulment_pre_approved, "
					+ "mpo.refund_partial_beforeclose MPO_refund_partial_beforeclose, "
					+ "mpo.refund_partial_beforeclose_annulment MPO_refund_partial_beforeclose_annulment, "
					+ "mpo.refund_partial_afterclose MPO_refund_partial_afterclose, "
					+ "mpo.refund_partial_afterclose_annulment MPO_refund_partial_afterclose_annulment, "
					+ "mpo.refund MPO_refund, "
					+ "mpo.refund_annulment MPO_refund_annulment, "
					+ "mpo.two_steps MPO_two_steps, "
					+ "mt.sufijoplantilla MT_template_suffix, "
					+ "EXISTS (SELECT 1 FROM rangos_mediopago rm  WHERE rm.blacklist =  1 AND rm.idmediopago = mb.idmediopago) AS blacklist, "
					+ "EXISTS (SELECT 1 FROM rangos_mediopago rm WHERE rm.blacklist =  0 AND rm.idmediopago = mb.idmediopago) AS whitelist "
					+ "FROM spsmediopago mp, payment_method_operations mpo, mediopago_backend mb, marcatarjeta mt "
					+ "where mp.idmediopago = mpo.id and "
					+ "mt.idmarcatarjeta = mp.idmarcatarjeta and "
					+ "mp.idmediopago = mb.idmediopago";

			List<MedioPago> vector = new ArrayList<MedioPago>();
			ResultSet rs = dba.select(sql);
			while (rs.next()) {
				MedioPago medioPago = new MedioPago(rs.getString("MP_idMedioPago"),
						rs.getString("MP_descripcion"),
						mapaMonedas.get(rs.getString("MP_idMoneda")),
						getMarcaTarjeta(DBA.rs2Int(rs, "MP_idMarcaTarjeta")),
						rs.getDouble("MP_limite"),
						(rs.getString("MP_validaBines").equalsIgnoreCase("S")) ? true : false,
						rs.getInt("MB_idbackend"),
						rs.getInt("MB_idprotocolo"),
						rs.getBoolean("MPO_annulment"),
						rs.getBoolean("MPO_annulment_pre_approved"),
						rs.getBoolean("MPO_refund_partial_beforeclose"),
						rs.getBoolean("MPO_refund_partial_beforeclose_annulment"),
						rs.getBoolean("MPO_refund_partial_afterclose"),
						rs.getBoolean("MPO_refund_partial_afterclose_annulment"),
						rs.getBoolean("MPO_refund"),
						rs.getBoolean("MPO_refund_annulment"),
						rs.getBoolean("MPO_two_steps"),
						rs.getString("MP_bin_regex"),
						rs.getBoolean("blacklist"),
						rs.getBoolean("whitelist"),
						rs.getBoolean("MP_validate_luhn"),
						rs.getString("MT_template_suffix"),
						(rs.getString("MP_cybersourceapifield").equalsIgnoreCase("S")) ? true : false,
						rs.getBoolean("MP_tokenized"),
						rs.getString("MP_esAgro"));
				vector.add(medioPago);
			}

			return vector;
		}
		catch(SQLException e) {
			throw new RuntimeException(e);
		}finally {
			if(dba != null) try {dba.cerrar();} catch(Exception e){logger.error("Error cerrando conexion con la base", e);}
		}
	}

	public List<String> getAllBins() throws SQLException {
		List<String> bins = new ArrayList<String>();
		String sql = "SELECT bin_regex FROM spsmediopago";
		DBA dba = new DBA(logger, dataSource);
		try
		{
			ResultSet rs = dba.select(sql);
			while (rs.next())
				bins.add(rs.getString("bin_regex"));
			return bins;
		}
		finally
		{
			if(dba != null)
			{
				dba.cerrar();
			}
		}
	}

	public List<Banco> getAllBancos() throws SQLException {
		Banco banco;

		String sql = "SELECT b.idbanco AS IDBANCO, b.descri AS DESCRI, " +
				"b.codbanco AS CODBANCO, bm.tipodescri AS TIPO " +
				"FROM bancos b, banco_medio bm " +
				"where b.tipo=bm.idtipo " +
				"and bm.tipodescri = 'PMC' " +
				"order BY idbanco";

		DBParametros p = new DBParametros();

		DBA dba = new DBA(logger, dataSource);
		try
		{
			List<Banco> bancos = new ArrayList<Banco>();
			ResultSet rs = dba.select(sql, p);
			while(rs.next()) {

				banco = new Banco(	DBA.rs2Int(rs, "IDBANCO"),
								  	rs.getString("DESCRI"),
									rs.getString("CODBANCO"));

				bancos.add(banco);

			}
			return bancos;
		}
		finally
		{
			if(dba != null)
			{
				dba.cerrar();
			}
		}
	}

	public Banco getBanco(String id)
			throws SQLException {
		Banco ta = null;
		String sql = "SELECT b.idbanco AS IDBANCO, b.descri AS DESCRI, " +
				"b.codbanco AS CODBANCO, bm.tipodescri AS TIPO " +
				"FROM bancos b, banco_medio bm " +
				"where b.tipo=bm.idtipo " +
				"and bm.tipodescri = 'PMC' " +
				"and b.idbanco = ? " +
				"order BY idbanco";

		DBA dba = new DBA(logger, dataSource);
		try
		{
			ResultSet rs = dba.select(sql, new Object[] { id },
					new int[] { Types.SMALLINT });
			if (rs.next())
				ta = new Banco(	DBA.rs2Int(rs, "IDBANCO"),
						rs.getString("DESCRI"),
						rs.getString("CODBANCO"));
			return ta;
		}
		finally
		{
			if(dba != null)
			{
				dba.cerrar();
			}
		}
	}

	public MarcaTarjeta getMarcaTarjeta(Integer idMarcaTarjeta)
			throws SQLException {
		MarcaTarjeta marcaTarjeta = null;

		String sql = "SELECT mt.idmarcatarjeta id, mt.descri descripcion, "
					+ "mt.codigoalfanumerico codigoAlfaNum, mt.urlServicio urlservicio, "
					+ "mt.sufijoplantilla sufijoPlantilla, "
					+ "mt.verificabin verificabin "
					+ "from marcatarjeta mt " //, spsmediopago mp , spsmedpagotienda mpt "
					+ "WHERE mt.idMarcaTarjeta = ? "; //and (mt.idmarcatarjeta = mp.idmarcatarjeta) and (mp.idmediopago = mpt.idmediopago) ";


		DBA dba = new DBA(logger, dataSource);
		try {
		ResultSet rs = dba.select(sql, new Object[] { idMarcaTarjeta },
				new int[] { Types.SMALLINT });
		if (rs.next())
			marcaTarjeta = new MarcaTarjeta(DBA.rs2Int(rs, "id"),
					rs.getString("descripcion"), rs.getString("codigoAlfaNum"), rs.getString("urlservicio"), rs.getString("sufijoPlantilla"),rs.getString("verificabin"));

		}finally {
			if(dba != null) try {dba.cerrar();} catch(Exception e){logger.error("Error cerrando conexion con la base", e);}
		}

		return marcaTarjeta;
	}


	public List<String []> getRangosTarjetaNacional(Integer idMarcaTarjeta)
			throws SQLException {
		List<String []> rangos = new ArrayList<String []>();
		String sql = "SELECT limiteinferior, limitesuperior "
				+ "FROM rangostarjetanacional WHERE idmarcatarjeta = ?";

		DBA dba = new DBA(logger, dataSource);
		try
		{
			ResultSet rs = dba.select(sql, new Object[] { idMarcaTarjeta },
					new int[] { Types.SMALLINT });
			while (rs.next())
				rangos.add(new String[] { rs.getString("limiteinferior"),
						rs.getString("limitesuperior") });
			return rangos;
		}
		finally
		{
			if(dba != null)
			{
				dba.cerrar();
			}
		}
	}

	public List<String> getMerchants(Site site) throws SQLException {

 		String sql = "SELECT merchant_id FROM site_merchant where site_id = ?";
 		DBA dba = new DBA(logger, dataSource);
 		try
 		{
 			ResultSet rs = dba.select(sql, new Object[] { site.getIdSite() },
 					new int[] { Types.VARCHAR });

 			LinkedList<String> ll = new LinkedList<String>();
 			while (rs.next()){
 				ll.add(rs.getString("merchant_id"));
 			}
 			return ll;
 		}
 		finally
 		{
 			if(dba != null)
 			{
 				dba.cerrar();
 			}
 		}


 	}

	public List<InfoSite> getSubSites(Site site) throws SQLException {
		String sql = "SELECT ss.idsubsite AS IDSITE, s.descri AS SITE_DESCRI, "
				+ "ss.porcentaje AS PORCENTAJE, ss.activo AS ACTIVO, "
				+ "s.idvalidator AS IDVALIDATOR, s.tipoid AS TIPOID, "
				+ "s.sinservicioid AS SINSERVICIOID, s.pasaid AS PASAID "
				+ "FROM spssites s, spssites_subsites ss "
				+ "WHERE s.idsite = ss.idsubsite and (ss.activo is null or ss.activo = 'S' ) and ss.idsite = ?";

		DBA dba = new DBA(logger, dataSource);
		try
		{
			ResultSet rs = dba.select(sql, new Object[] { site.getIdSite() },
					new int[] { Types.VARCHAR });

			LinkedList<InfoSite> ll = new LinkedList<InfoSite>();
			while (rs.next())
				ll.add(new InfoSite(rs.getString("IDSITE"), rs
						.getString("SITE_DESCRI"), rs
						.getFloat("PORCENTAJE"), rs
						.getString("ACTIVO"), rs
						.getString("IDVALIDATOR"), rs
						.getString("TIPOID"), rs
						.getString("PASAID"), rs
						.getString("SINSERVICIOID")));
			return ll;
		}
		finally
		{
			if(dba != null)
			{
				dba.cerrar();
			}
		}
	}


	public List<String []> getRangosPermitidosTarjeta(String idSite) throws SQLException {
		Vector<String []> rangos = new Vector<String []>();
		String sql = "SELECT idmarcatarjeta, limiteinferior, limitesuperior "
				+ "FROM rangospermitidostarjeta "
				+ "WHERE idsite = ?";

		DBA dba = new DBA(logger, dataSource);
		try
		{
			ResultSet rs = dba.select(sql, new Object[] { idSite },
					new int[] { Types.VARCHAR });
			while (rs.next())
				rangos.add(new String[] { String.valueOf(rs.getInt("idmarcatarjeta")), rs.getString("limiteinferior"),
						rs.getString("limitesuperior") });
			return rangos;
		}
		finally
		{
			if(dba != null)
			{
				dba.cerrar();
			}
		}
	}


	/**
	 * Devuelve el motivo para el idProtocolo, idTipoOperacion e idMotivo
	 * especificados. Devuelve getMotivoErrorDefault si no existe un motivo en
	 * la base con el codigo idMotivo
	 */
	public List<Motivo> getAllMotivos() throws SQLException {
		Motivo motivo;

		String sql = "SELECT m.idmotivo idMotivo, m.idprotocolo, m.idtipooperacion, "
				+ "m.descri descripcion, m.descri_display descripcion_d "
				+ "FROM spsmotivo m ";

		DBParametros p = new DBParametros();
//		p.add(idProtocolo, Types.INTEGER);
//		p.add(idTipoOperacion, Types.SMALLINT);
//		p.add(idMotivo, Types.INTEGER);

		DBA dba = new DBA(logger, dataSource);
		try
		{
			List<Motivo> motivos = new ArrayList<Motivo>();
			ResultSet rs = dba.select(sql, p);
			while(rs.next()) {

				motivo = new Motivo(DBA.rs2Int(rs, "idMotivo"), DBA.rs2Int(rs, "idprotocolo"), DBA.rs2Int(rs, "idtipooperacion"),
						rs.getString("descripcion"), rs.getString("descripcion_d"));

				motivos.add(motivo);

			}

//			if(motivos.isEmpty())
//				motivos.add(Motivo.getMotivoErrorDefaultParaProtocolo(idProtocolo));

			return motivos;
		}
		finally
		{
			if(dba != null)
			{
				dba.cerrar();
			}
		}
	}

	static private String StringSelectTransaccion = "spstransac.idtransaccion TR_idTransaccion, "
			+ "spstransac.idtipooperacion TR_idTipoOperacion, "
			+ "spstransac.idsite TR_idSite, "
			+ "spstransac.idmediopago TR_idMedioPago, "
			+ "spstransac.idbackend TR_idBackEnd, "
			+ "spstransac.idprotocolo TR_idProtocolo, "
			+ "spstransac.idtransaccionsite TR_nroOperacionSite, "
			+ "spstransac.estarjetanacional TR_estarjetanacional, "
			+ "spstransac.monto TR_monto, "
			+ "spstransac.montofinal TR_montoFinal, "
			+ "spstransac.montooriginal TR_montoOriginal, "
			+ "spstransac.cantcuotas TR_cuotas, "
			+ "spstransac.idplan TR_idPlan, "
			+ "spstransac.paramsitio TR_paramsitio, "
			+ "spstransac.plann TR_sePagoConPlanN, "
			+ "spstransac.titular TR_titular, "
			+ "spstransac.idtipodoc TR_idTipoDoc, "
			+ "spstransac.nrodoc TR_nroDocumento, "
			+ "spstransac.ipcomprador TR_ipComprador, "
			+ "spstransac.mailusu TR_mailUsu, "
			+ "spstransac.nrotarj TR_nroTarj4, "
			+ "spstransac.nrotarjetaencriptado TR_nroTarjetaEncriptado, "
			+ "spstransac.enc_keyid enc_keyid, "
			+ "spstransac.venctarj TR_vencimientoTarjeta, "
			+ "spstransac.idestado TR_idEstado, "
			+ "spstransac.idmotivo TR_idMotivo, "
			+ "spstransac.fecha TR_fecha, "
			+ "spstransac.fechaoriginal TR_fechaOriginal, "
			+ "spstransac.codaut TR_codigoAutorizacion, "
			+ "spstransac.idopbackend TR_idOperacionBackEnd, "
			+ "spstransac.idopmediopago TR_idOperacionMedioPago, "
			+ "spstransac.nroticket TR_nroTicket, "
			+ "spstransac.resultadovalidaciondomicilio TR_validacionDomicilio, "
			+ "spstransac.esautorizadaendospasos TR_esAutorizadaEnDosPasos, "
			+ "spstransac.nrotrace TR_nroTrace, "
			+ "spstransac.codart TR_codArt, "
			+ "spstransac.intentos TR_intentos, "
			+ "spstransac.utilizavbv TR_utilizavbv, "
			+ "spstransac.fechainicio TR_fechainicio, "
			+ "spstransac.sexotitular TR_sexotitular, "
			+ "spstransac.celular TR_celular, "
			+ "spstransac.pin TR_pin, "
			+ "spstransac.fechaenvio TR_fechaenvio, "
			+ "spstransac.diaspagodiferido TR_diaspagodiferido, "
			+ "spstransac.urldinamica TR_urldinamica, "
			+ "spstransac.tipoencripcion TR_tipoencripcion, "
			+ "spstransac.fechavtocuota1 TR_fechavtocuota1, "
			+ "spstransac.distribuida TR_distribuida, "
			+ "spstransac.terminal TR_terminal, "
			+ "spstransac.idcliente TR_idcliente ";

	/**
	 * Devuelve una transaccion por su requesId
	 */

//	public static OperationResource getTransaccionPorRequestID(String requestId)
//			throws SQLException, GeneralSecurityException {
//		OperationResource op = null;
//
//		String sql = "SELECT " + StringSelectTransaccion
//				+ "FROM spstransac " 
//				+ "WHERE spstransac.idtransaccion =  ? ";
//		
//
//		DBA dba = new DBA(logger, dataSource);
//		try {
//			ResultSet rs = dba.select(sql, new Object[] { requestId },
//					new int[] { Types.VARCHAR });
////			while (rs.next()) {
////				op = new OperationResource(requestId,rs.getString("TR_nroOperacionSite") ,
////						rs.getString("TR_fechavtocuota1"),
////						rs.getInt("TR_monto"),
////						rs.getInt("TR_cuotas"),
////						null,
////						null,
////						null,
////						null);
////			}
//		} 
//		finally
//		{
//			if(dba != null)
//			{
//				dba.cerrar();
//			}
//		}
//		return tr;
//	}


	public List<CryptoData> obtenerTablaEncripcion() throws SQLException,
			GeneralSecurityException {

			int id_clave_actual = -1;

//			Map<Integer, CryptoWrapper> nueva_tabla = new HashMap<Integer, CryptoWrapper>();
			List<CryptoData> nueva_tabla = new ArrayList<CryptoData>();
			DBA dba = new DBA(logger, dataSource);
			try {
				String sql = "SELECT id, algoritmo, validacion, encoding, llave "
						+ "FROM encripcion";
				ResultSet rs = dba.select(sql);

				while (rs.next()) {
					int id = rs.getInt(1);
					if (id > id_clave_actual)
						id_clave_actual = id;
//					CryptoWrapper fila = new CryptoWrapper(rs.getString(2), // algoritmo
//							rs.getString(3), // validacion
//							rs.getString(4), // encoding
//							rs.getBytes(5), true); // llave
//					nueva_tabla.put(new Integer(id), fila);
					CryptoData vo =
							new CryptoData(
									id,
									rs.getString(2), // algoritmo
									rs.getString(3), // validacion
									rs.getString(4), // encoding
									rs.getBytes(5),
									true);
					nueva_tabla.add(vo);
				}
			} finally {
				if (dba != null) {
					dba.cerrar();
				}
			}
//			if (!nueva_tabla.isEmpty()) {
//				/* La tabla no est� vac�a. Si est� vac�a evito el error */
//				Integer Id = new Integer(id_clave_actual);
//				String algoritmo = nueva_tabla.get(Id).getAlgoritmo();
//				CryptoWrapper maestra = new CryptoWrapper(algoritmo,
//						"checksum", "hex", llave_maestra, false);
//
//				try {
////					for (Enumeration<CryptoWrapper> claves = nueva_tabla.elements(); claves.hasMoreElements();)
////						claves.nextElement().habilitar(maestra);
//					for(Iterator<CryptoWrapper> claves = nueva_tabla.values().iterator();claves.hasNext();) 
//						claves.next().habilitar(maestra);
//						
//				} catch (GeneralSecurityException e) {
//					llave_maestra = null;
//					logger.error("Error desencriptando la tabla Encripcion",
//							e);
//					throw e;
//				}
//			}
//			else {
//				String msg = "Error obtenienda tabla de Encripcion";
//				logger.error(msg);
//				throw new RuntimeException(msg);
//			}

//			tabla_claves = nueva_tabla;
//			cache_time = System.currentTimeMillis();
//		}

		return nueva_tabla;
	}

	public List<SiteTemplate> getSiteTemplates(Site site) throws SQLException {
		List<SiteTemplate> templates = new ArrayList<SiteTemplate>();

		String sql = "SELECT site_id, template_id, alias, signed, CAST(template AS CHAR(2000)) AS template, state "
				+ "FROM site_template "
				+ "WHERE site_id = ? ";

		DBA dba = new DBA(logger, dataSource);
		try
		{
			ResultSet rs = dba.select(sql, new Object[] { site.getIdSite() },
					new int[] { Types.SMALLINT });
			while (rs.next())
				templates.add(new SiteTemplate(
						rs.getString("site_id"),
						rs.getLong("template_id"),
						rs.getString("alias"),
						rs.getBoolean("signed"),
						rs.getString("template"),
						rs.getInt("state")
						));
			return templates;
		}
		finally
		{
			if(dba != null)
			{
				dba.cerrar();
			}
		}
	}


	public List<SitePaymentType> getSitePaymentTypes() throws SQLException {
		List<SitePaymentType> paymentTypes = new ArrayList<SitePaymentType>();

		String sql = "SELECT idsite, payment_type "
				+ "FROM site_payment_type ";

		DBA dba = new DBA(logger, dataSource);
		try
		{
			ResultSet rs = dba.select(sql);
			while (rs.next())
				paymentTypes.add(new SitePaymentType(rs.getString("idsite"),rs.getString("payment_type")));
			return paymentTypes;
		}
		finally
		{
			if(dba != null)
			{
				dba.cerrar();
			}
		}
	}
}
