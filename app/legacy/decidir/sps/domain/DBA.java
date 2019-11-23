package legacy.decidir.sps.domain;


//import decidir.log.Log;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
//import org.apache.commons.dbcp.BasicDataSource;


public class DBA
{
  private Logger log;
  private DataSource ds;
  private PreparedStatement pst;
  private ResultSet rs;
  private Connection con;
  private boolean transaccional;
  private boolean commit_pendiente;

  public DBA(Logger logger, DataSource datasource)
  {
    this.log = logger;
    this.ds = datasource;
    this.con = null;
    this.pst = null;
    this.rs = null;
    this.transaccional = false;
    this.commit_pendiente = false;

//    if ((this.ds instanceof BasicDataSource)) {
//      BasicDataSource bds = (BasicDataSource)this.ds;
//      this.log.debug("Nuevo objeto DBA, conexiones activas: " + 
//        bds.getNumActive() + ", idles: " + bds.getNumIdle());
//    }
  }

  public static Double rs2Double(ResultSet rs, String col)
    throws SQLException
  {
    double d = rs.getDouble(col);
    return rs.wasNull() ? null : new Double(d);
  }

  public static Long rs2Long(ResultSet rs, String col) throws SQLException
  {
    long l = rs.getLong(col);
    return rs.wasNull() ? null : new Long(l);
  }

  public static Integer rs2Int(ResultSet rs, String col)
    throws SQLException
  {
    int i = rs.getInt(col);
    return rs.wasNull() ? null : new Integer(i);
  }

  public static Boolean rs2Bool(ResultSet rs, String col)
    throws SQLException
  {
    String st = rs.getString(col);
    return (st != null) && (st.equals("S") || st.equals("1"));
  }

  private void logQuery(String sql) {
    StackTraceElement[] stackTrace = new Throwable().getStackTrace();
    String caller1 = "DESCONOCIDO"; String caller2 = "DESCONOCIDO";
    if (stackTrace.length > 1)
      caller1 = stackTrace[1].toString();
    if (stackTrace.length > 2) {
      caller2 = stackTrace[2].toString();
    }
    this.log.debug(caller1 + " (invocado por " + caller2 + "): " + 
      sql.replaceAll("\\s*(\n|\r|\n\r|\r\n)\\s*", " "));
  }

  private void logValores(Object[] valores) {
    StackTraceElement[] stackTrace = new Throwable().getStackTrace();
    String caller1 = "DESCONOCIDO"; String caller2 = "DESCONOCIDO";
    if (stackTrace.length > 1)
      caller1 = stackTrace[1].toString();
    if (stackTrace.length > 2) {
      caller2 = stackTrace[2].toString();
    }
    StringBuffer buf = new StringBuffer();
    buf.append(caller1).append(" (invocado por ").append(caller2).append(
      "): Valores = [");
    for (int i = 0; i < valores.length; i++)
      buf.append(" ").append(valores[i]);
    this.log.debug(" ]");
  }

  private void logWarnings() throws SQLException
  {
    StackTraceElement[] stackTrace = new Throwable().getStackTrace();
    String caller1 = "DESCONOCIDO"; String caller2 = "DESCONOCIDO";

    if (stackTrace.length > 1)
      caller1 = stackTrace[1].toString();
    if (stackTrace.length > 2) {
      caller2 = stackTrace[2].toString();
    }
    String prefix = caller1 + " (invocado por " + caller2 + ") SQLWarning: ";
    if (this.con != null) {
      SQLWarning w = this.con.getWarnings();
      while (w != null) {
        this.log.warn(prefix + w.getMessage());
        w = w.getNextWarning();
      }
    }
    if (this.pst != null) {
      SQLWarning w = this.pst.getWarnings();
      while (w != null) {
        this.log.warn(prefix + w.getMessage());
        w = w.getNextWarning();
      }
    }
    if (this.rs != null) {
      SQLWarning w = this.rs.getWarnings();
      while (w != null) {
        this.log.warn(prefix + w.getMessage());
        w = w.getNextWarning();
      }
    }
  }

  private String getCallSP(String nombreSP, int size)
  {
    StringBuffer buf = new StringBuffer();
    buf.append("{Call " + nombreSP + "(");
    for (int i = 0; i < size; i++) {
      if (i > 0)
        buf.append(",");
      buf.append("?");
    }
    String st = buf.toString() + ")}";

    String callStoreProcedure = st;

    return callStoreProcedure;
  }

  private void crearConexion() throws SQLException
  {
    if (this.con == null) {
      this.con = this.ds.getConnection();
      this.con.setAutoCommit(!this.transaccional);
      this.commit_pendiente = false;
    }
    if (this.rs != null)
      this.rs.close();
    this.rs = null;
    if (this.pst != null)
      this.pst.close();
    this.pst = null;
  }

  public void hacerTransaccional(boolean t)
    throws SQLException
  {
    rollback();
    this.transaccional = t;
    if (this.con == null)
      crearConexion();
    this.con.setAutoCommit(!t);
  }

  public void commit()
    throws SQLException
  {
    if ((this.transaccional) && (this.commit_pendiente))
      this.con.commit();
    this.commit_pendiente = false;
  }

  public void rollback()
    throws SQLException
  {
    if ((this.transaccional) && (this.commit_pendiente))
      this.con.rollback();
    this.commit_pendiente = false;
  }

  public ResultSet select(String sql)
    throws SQLException
  {
    logQuery(sql);
    crearConexion();
    try {
      this.pst = this.con.prepareStatement(sql, 1);
      this.rs = this.pst.executeQuery();
    } catch (SQLException ex) {
      cerrar();
      throw ex;
    }
    if (this.transaccional) {
      this.commit_pendiente = true;
    }

    return this.rs;
  }

  public ResultSet select(String nombreSP, Object[] valores, int[] tipos, Object[] valoresOut, int[] tiposOut)
    throws SQLException
  {
    CallableStatement stmt = null;

    int cantParams = 0;
    boolean paramOut = (valoresOut != null) && (tiposOut != null);

    if ((valores.length != tipos.length) || 
      ((paramOut) && (valoresOut.length != tiposOut.length)) || 
      (nombreSP.length() <= 0)) {
      throw new RuntimeException("ParÃ¡metros invÃ¡lidos");
    }
    cantParams = paramOut ? valores.length + valoresOut.length : 
      valores.length;
    String sql = getCallSP(nombreSP, cantParams);

    logQuery(sql);
    logValores(valores);
    crearConexion();
    try
    {
      int i = 0;

      stmt = this.con.prepareCall(sql);

      for (i = 0; i < valores.length; i++) {
        stmt.setObject(i + 1, valores[i], tipos[i]);
      }
      if (paramOut) {
        for (int j = i; j < i + tiposOut.length; j++)
          stmt.registerOutParameter(j + 1, tiposOut[(j - i)]);
      }
      boolean hayRegistros = stmt.execute();

      if (hayRegistros) {
        this.rs = stmt.getResultSet();
      }
      stmt.getMoreResults(2);
      if (paramOut)
        for (int j = i; j < i + tiposOut.length; j++)
          valoresOut[(j - i)] = stmt.getObject(j + 1);
    }
    catch (SQLException ex)
    {
//      Log.logErrorGral(ex, "Error el ejecutar sp " + nombreSP);
      log.error("Error el ejecutar sp " + nombreSP, ex);
      cerrar();
      throw ex;
    }

    if (this.transaccional)
      this.commit_pendiente = true;
    logWarnings();

    return this.rs;
  }

  public ResultSet select(String sql, Object[] valores, int[] tipos)
    throws SQLException
  {
    if (valores.length != tipos.length)
      throw new RuntimeException("ParÃ¡metros invÃ¡lidos");
    logQuery(sql);
    logValores(valores);
    crearConexion();
    try {
      this.pst = this.con.prepareStatement(sql, 1);
      for (int i = 0; i < valores.length; i++)
        this.pst.setObject(i + 1, valores[i], tipos[i]);
      this.rs = this.pst.executeQuery();
    } catch (SQLException ex) {
      cerrar();
      throw ex;
    }
    if (this.transaccional) {
      this.commit_pendiente = true;
    }

    return this.rs;
  }

  public ResultSet select(String sql, DBParametros parametros)
    throws SQLException
  {
    logQuery(sql);
    crearConexion();
    try {
      this.pst = this.con.prepareStatement(sql, 1);
      List v = parametros.getValores();
      logValores(v.toArray());
      List t = parametros.getTipos();
      for (int i = 0; i < v.size(); i++)
        this.pst.setObject(i + 1, v.get(i), ((Integer)t.get(i)).intValue());
      this.rs = this.pst.executeQuery();
    } catch (SQLException ex) {
      cerrar();
      throw ex;
    }
    if (this.transaccional) {
      this.commit_pendiente = true;
    }

    return this.rs;
  }

  public int update(String sql)
    throws SQLException
  {
    int res = 0;
    logQuery(sql);
    crearConexion();
    try {
      this.pst = this.con.prepareStatement(sql, 1);
      res = this.pst.executeUpdate();
    } catch (SQLException ex) {
      cerrar();
      throw ex;
    }
    if (this.transaccional)
      this.commit_pendiente = true;
    logWarnings();
    return res;
  }

  public int update(String sql, Object[] valores, int[] tipos)
    throws SQLException
  {
    int res = 0;
    if (valores.length != tipos.length)
      throw new RuntimeException("ParÃ¡metros invÃ¡lidos");
    logQuery(sql);
    logValores(valores);
    crearConexion();
    try {
      this.pst = this.con.prepareStatement(sql, 1);
      for (int i = 0; i < valores.length; i++)
        this.pst.setObject(i + 1, valores[i], tipos[i]);
      res = this.pst.executeUpdate();
    } catch (SQLException ex) {
      cerrar();
      throw ex;
    }
    if (this.transaccional)
      this.commit_pendiente = true;
    logWarnings();
    return res;
  }

  public int update(String sql, DBParametros parametros)
    throws SQLException
  {
    int res = 0;
    logQuery(sql);
    crearConexion();
    try {
      this.pst = this.con.prepareStatement(sql, 1);
      List v = parametros.getValores();
      logValores(v.toArray());
      List t = parametros.getTipos();
      for (int i = 0; i < v.size(); i++)
        this.pst.setObject(i + 1, v.get(i), ((Integer)t.get(i)).intValue());
      res = this.pst.executeUpdate();
    } catch (SQLException ex) {
      cerrar();
      throw ex;
    }
    if (this.transaccional)
      this.commit_pendiente = true;
    logWarnings();
    return res;
  }

  public ResultSet obtenerClavesGeneradas()
    throws SQLException
  {
    if (this.rs != null)
      this.rs.close();
    this.rs = this.pst.getGeneratedKeys();
    return this.rs;
  }

  public long obtenerClaveGenerada()
    throws SQLException
  {
    long res = -1L;
    ResultSet r = this.pst.getGeneratedKeys();
    if (r.next()) {
      res = r.getLong(1);
      logValores(new Object[] { new Long(res) });
    }
    r.close();
    return res;
  }

  public void cerrar()
    throws SQLException
  {
    try
    {
      if (this.rs != null)
        this.rs.close();
      if (this.pst != null)
        this.pst.close();
      if ((this.con != null) && (!this.con.isClosed())) {
        rollback();
        this.con.close();
      }
    } finally {
      this.rs = null;
      this.pst = null;
      this.con = null;
    }
    this.commit_pendiente = false;

//    if ((this.ds instanceof BasicDataSource)) {
//      BasicDataSource bds = (BasicDataSource)this.ds;
//      this.log.debug("Luego de DBA.cerrar(), conexiones activas: " + 
//        bds.getNumActive() + ", idles: " + bds.getNumIdle());
//    }
  }
}