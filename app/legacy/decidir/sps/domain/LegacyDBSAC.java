package legacy.decidir.sps.domain;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedList;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;

import decidir.sps.core.MarcaTarjeta;
import decidir.sps.sac.vista.utils.info.InfoFiltros;
import decidir.sps.sac.vista.utils.info.InfoReglas;
import decidir.sps.sac.vista.utils.info.InfoReglasDetalle;

public class LegacyDBSAC {

	private DataSource dataSource;
	private Logger logger;
	
	public LegacyDBSAC(Logger logger, DataSource datasource) {
		this.dataSource = datasource;
		this.logger  = logger;
	}

	
	public String getIdComercioLocation(String idSiteLocator) throws SQLException {
		String sql = "SELECT nroid FROM spsmedpagotienda WHERE idmediopago = 53 and idsite = ? ";
		DBParametros param = new DBParametros();
		param.add(idSiteLocator, Types.VARCHAR);
		final DBA dba = new DBA(logger, dataSource);
		String nroid = "";
		try {
			ResultSet rs = dba.select(sql, param);
			if (rs.next()) {
				nroid = rs.getString("nroid");
			}

		} finally {
			if (dba != null) {
				dba.cerrar();
			}
		}
		return nroid;
	}	
	

	/**
	 * Lee filtro de bines para un sitio / marca tarjeta, sin detalle de binsets
	 * asociados.
	 * 
	 * @param idSite
	 * @param idMarcaTarjeta
	 * @return objeto InfoFiltros o null si no tiene
	 * @throws SQLException
	 */
	public InfoFiltros getInfoFiltros(String idSite,
			MarcaTarjeta marcaTarjeta) throws SQLException {
		final String sql = "select f.idsite AS IDSITE, "
				+ "f.tipolista AS TIPOLISTA, "
				+ "f.filtra AS FILTRA, "
				+ "f.idmarcatarjeta AS IDMARCATARJETA "
				+ "from filtros f "
				+ "left join marcatarjeta mt on f.idmarcatarjeta = mt.idmarcatarjeta "
				+ "where f.idsite = ? " + "and f.idmarcatarjeta = ?";

		final DBA dba = new DBA(logger, dataSource);
		try {
			final ResultSet rs = dba.select(sql, new Object[] { idSite,
					marcaTarjeta.getId() },
					new int[] { Types.VARCHAR, Types.INTEGER });
			if (rs.next()) {
				final String filtra = rs.getString("FILTRA");
				final String tipoLista = rs.getString("TIPOLISTA");

//				final MarcaTarjeta marcaTarjeta = StableDataServer
//						.findMarcaTarjeta(idMarcaTarjeta);
				final InfoFiltros info = new InfoFiltros(idSite, marcaTarjeta,
						filtra.equalsIgnoreCase("S"),
						tipoLista.equalsIgnoreCase("N"), null);

				return info;
			} else {
				return null;
			}

		} finally {
			dba.cerrar();
		}
	}
	
	
	
	/**
	 * NO SE USA
	 * @param idsite
	 * @return
	 * @throws SQLException
	 */
	@Deprecated
	// informacion de reglas para backend en protocolo.java
	public LinkedList<InfoReglas> getInfoReglasProtocolo(String idsite)
			throws SQLException {

		final DBA dba = new DBA(logger, dataSource);

		final LinkedList<InfoReglas> list = new LinkedList<InfoReglas>();

		String sql = "SELECT ssr.idsite as IDSITE, "
				+ "s.descri AS DESCSITE, "
				+ "sr.idsite AS SITESALIDA, "
				+ "s2.descri AS DESCSITESALIDA, "
				+ "sr.idregla AS IDREGLA, "
				+ "sr.idestado as ESTADO, "
				+ "sr.orden as ORDEN "
				+ "from spssites_reglas ssr, spsreglas sr, spsestadosreglas ser, spssites s, spssites s2 "
				+ "WHERE ssr.idregla = sr.idregla  "
				+ "and ser.idestado = sr.idestado "
				+ "and sr.idsite = s.idsite " + "and ssr.idsite = s2.idsite  "
				+ "and sr.idestado = '4' " + "and ssr.idsite = '" + idsite
				+ "' " + "ORDER BY sr.orden ASC ";
		try {
			ResultSet rs = dba.select(sql);
			while (rs.next()) {

				list.add(new InfoReglas(rs.getString("IDSITE"), rs
						.getString("SITESALIDA"), rs.getString("IDREGLA"), rs
						.getInt("ORDEN"), rs.getString("ESTADO"),

				getInfoReglasDetalle(rs.getString("IDREGLA")), rs
						.getString("DESCSITE"), rs.getString("DESCSITESALIDA")));
			}

			return list;

		} finally {
			if (dba != null) {
				dba.cerrar();
			}
		}

	}	
	
	
	
	public List<InfoReglasDetalle> getInfoReglasDetalle(String idregla)
			throws SQLException {
		List<InfoReglasDetalle> detalle = new LinkedList<InfoReglasDetalle>();
		final DBA dba = new DBA(logger, dataSource);

		String sql = "SELECT rd.idregla as IDREGLA, " + "rd.campo as CAMPO, "
				+ "rd.operador AS OPERADOR, " + "rd.valor AS VALOR, "
				+ "rc.descri AS DESCRICAMPO "
				+ "from spsreglas_detalle rd, spsreglas_campos rc "
				+ "WHERE rd.campo = rc.idcampo and " + "rd.idregla = "
				+ idregla;
		try {
			ResultSet rs = dba.select(sql);

			while (rs.next()) {

				detalle.add(new InfoReglasDetalle(rs.getString("IDREGLA"), rs
						.getString("CAMPO"), rs.getString("OPERADOR"), rs
						.getString("VALOR"), getDescriValor(
						rs.getString("CAMPO"), rs.getString("VALOR")), rs
						.getString("DESCRICAMPO")));
			}
			return detalle;
		} finally {
			if (dba != null) {
				dba.cerrar();
			}
		}

	}	
	
	
	public List<String> getDescriValor(String campo, String idValor)
			throws SQLException {

		final DBA dba = new DBA(logger, dataSource);
		List<String> descri = new LinkedList<String>();
		ResultSet rs;

		String sql = "";

		if (campo.equals("3")) {
			if (!idValor.contains("(")) {
				sql = "SELECT b.descri DESCRI FROM BANCOS b where idbanco in ("
						+ idValor + ")";
			} else {
				sql = "SELECT b.descri DESCRI FROM BANCOS b where idbanco in "
						+ idValor;
			}

		} else {
			if (campo.equals("4")) {
				if (!idValor.contains("(")) {
					sql = "SELECT b.descri DESCRI FROM SPSMEDIOPAGO b where idmediopago in ("
							+ idValor + ")";
				} else {
					sql = "SELECT b.descri DESCRI FROM SPSMEDIOPAGO b where idmediopago in"
							+ idValor;
				}
			}
		}

		if (sql != null && !sql.equals("")) {
			rs = dba.select(sql);
			while (rs.next()) {
				descri.add(rs.getString("DESCRI"));
			}
			dba.cerrar();
		}
		if (descri.isEmpty()) {
			descri.add(idValor);
		}

		return descri;

	}	
	
	
	
	
}
