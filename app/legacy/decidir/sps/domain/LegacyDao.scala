package legacy.decidir.sps.domain

import java.util

import com.decidir.coretx.api.SitesNotifications
import com.decidir.coretx.utils.JdbcDaoUtils
import play.api.db.Database
import javax.inject.Inject
import org.springframework.jdbc.core.BeanPropertyRowMapper
import decidir.sps.core.TipoActividad
import scala.collection.JavaConverters._
import org.springframework.jdbc.core.ResultSetExtractor
import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import com.decidir.coretx.domain._
import scala.util.Try


class LegacyDao @Inject() (db: Database) extends JdbcDaoUtils(db) {
  def getAllTipoDocumento() = {
    val sql = "select idtipodoc, descri from spstipodoc"
    val jsonMap = evalWithJdbcTemplate {_.queryForList(sql)}.asScala.toList
    jsonMap.map(jsonM => convertToTipoDocumentoFromJson(jsonM).get)
  }

  private def convertToTipoDocumentoFromJson(jsonMap: java.util.Map[String, Object]): Try[TipoDocumento] = Try {
    TipoDocumento(
      id = jsonMap.get("idtipodoc").toString.toInt,
      description = jsonMap.get("descri").toString
    )
  }

  def findAllComercioLocationIds(): List[String] = {
    val sql = "select distinct idsite as idsitelocator FROM spsmedpagotienda WHERE idmediopago = 53"
    evalWithJdbcTemplate {_.queryForList[String](sql, Array[Object](), classOf[String]).asScala.toList}
  }

  def findAllNrosTraceSitio(idSite: String): List[NrosTraceSite] = {
    
    val sql = "select idsite, idbackend, nroterminal, nrotrace, nroticket, nrobatch from numerostrace where idsite = ?"
    
    val nros = evalWithJdbcTemplate {_.query(sql, 
                            Array[Object](idSite.asInstanceOf[Object]), 
                            new BeanPropertyRowMapper(classOf[NrosTraceModeloLegacy]))}.asScala.toList
                            
    val nrostrace = nros.map{nro => 
      NrosTraceSite(nro.getIdSite(), nro.getIdBackend(), nro.getNroTerminal(), nro.getNroTrace(), nro.getNroTicket(), nro.getNroBatch())
    }.toList

    nrostrace
  }  
  
  def findIdentificadorDePagos() = {
    val sql = "select max(charge_id) from transaccion_operacion_xref"
    evalWithJdbcTemplate {_.queryForObject(sql, classOf[Long])}
  }

  def convertToSitesNotificationsFromJson(jsonMap: java.util.Map[String, Object]): Try[SitesNotifications] = Try {
    SitesNotifications(
      siteId = jsonMap.get("site_id").toString,
      notificationType = jsonMap.get("notification_type").toString,
      url = Option(jsonMap.get("url")).map(_.toString),
      enabled = jsonMap.get("enabled").toString.toBoolean
    )
  }

  def findAllNotificationsBySiteId(siteId: String): List[SitesNotifications] = {
    val sql = """select n.site_id, n.notification_type, n.url, n.enabled from sites_notifications n where n.site_id = ? and n.enabled = 1"""

    val jsonMap = evalWithJdbcTemplate {_.queryForList(sql, siteId)}.asScala.toList

    jsonMap.map(jsonM => convertToSitesNotificationsFromJson(jsonM).get)
  }
  
  def findAllTerminalesSitio(idSite: String): List[TerminalesSite] = {
    
    val sql = """select  s.idsite, s.idmediopago, s.idprotocolo, s.nroterminal as terminal 
        from spsmedpagotienda s 
        where s.nroterminal is not null
        and trim(s.nroterminal) <> ''
        and s.habilitado = 'S'
    		and s.idsite = ?"""
  
    val terminalTienda = evalWithJdbcTemplate {_.query(sql, 
                            Array[Object](idSite.asInstanceOf[Object]), 
                            new BeanPropertyRowMapper(classOf[TerminalModeloLegacy]))}.asScala.toList
                            
    val sqlterms = "select idsite, idmediopago, idprotocolo, terminal from terminalsitio where idsite = ? and estado = '0';"
    val terminales = terminalTienda ::: evalWithJdbcTemplate {_.query(sqlterms, 
                             Array[Object](idSite.asInstanceOf[Object]), 
                             new BeanPropertyRowMapper(classOf[TerminalModeloLegacy]))}.asScala.toList
               
                             
    val ts = terminales.groupBy(t => (t.getIdSite(), t.getIdMedioPago(), t.getIdProtocolo())).map{kv => 
      val (group, coleccion) = kv
      TerminalesSite(group._1, group._2, group._3, coleccion.map(_.getTerminal()).toSet)
//      TerminalesSite(group._1, group._2, group._3, coleccion.filter(_.getEstado() == 0).map(_.getTerminal()).toSet)
    }.toList
              
    ts
  }

  
  def findAllBinesForSiteAndTarjeta(idSite: String, marcaTarjeta: Int): List[String] = {
    val sql = """select b.nrobin from filtrosbinset fbs 
    join binsets bs on fbs.idbinset = bs.idbinset 
    join bines b on bs.idbinset = b.idbinset 
    where fbs.idsite = ? and fbs.idmarcatarjeta = ?""" 
    evalWithJdbcTemplate {_.queryForList[String](sql, Array[Object](idSite, new Integer(marcaTarjeta)), classOf[String])}.asScala.toList
  }
  
  def findAllMarcaTarjetasForSite(idSite: String): List[Int] = {
    val sql = "select distinct f.idmarcatarjeta AS IDMARCATARJETA from filtros f where f.idsite = ?"
    evalWithJdbcTemplate {_.queryForList[Int](sql, Array[Object](idSite), classOf[Int]).asScala.toList}
  }
  
  
  def getAllTipoActividad() = 
    evalWithJdbcTemplate {_.query("select idtipoactividad, descri as descripcion from tipoactividad", Array[Object](), new BeanPropertyRowMapper(classOf[TipoActividad])).asScala.toList}

  def retrieveTipoActividad(id: Int) = 
    evalWithJdbcTemplate {_.queryForObject("select idtipoactividad, descri as descripcion from tipoactividad where idtipoactividad = ?", 
                                            Array[Object](id.asInstanceOf[Object]), 
                                            new BeanPropertyRowMapper(classOf[TipoActividad]))}
  
  def getAllMarcaTarjetaIds = {
    val sql = """SELECT idmarcatarjeta id from marcatarjeta"""
    evalWithJdbcTemplate { _.queryForList(sql, classOf[Integer]) }.asScala.toList.map(_.toInt)
  }
  
  def getAllSiteIds() = {
    val siteIds = """
      SELECT st.idsite  
      FROM spssites st"""    
    
    evalWithJdbcTemplate { _.queryForList(siteIds, classOf[String]) }.asScala
    
  }
  
  def retrieveSiteUrl(siteId: String) = {
    
    val siteUrls = """
      SELECT url FROM spssites st, habilitacionsite ht 
      WHERE ht.idaplicacion = 1 
      AND ht.idestadosite = 1 and ht.idsite = st.idsite 
      AND st.IDSITE = ?"""    
    
    evalWithJdbcTemplate { _.queryForList(siteUrls, Array[Object](siteId.asInstanceOf[Object]), classOf[String]) }.asScala.headOption
    
  }
  
  def retrieveBinesFilter(idMedioPago: String) = {
      val jsonMap = evalWithJdbcTemplate { JdbcTemplate =>
        JdbcTemplate.queryForList("select * from rangos_mediopago where idmediopago = ?", idMedioPago)
      }.asScala.toList
      
      jsonMap.map(jsonM => convertToRangosMedioPagoFromJson(jsonM).get)
  }
  
  private def convertToRangosMedioPagoFromJson(jsonMap: java.util.Map[String, Object]): Try[RangosMedioPago] = Try {   
      RangosMedioPago(
          limiteinferior = jsonMap.get("limiteinferior").toString,
          limitesuperior = jsonMap.get("limitesuperior").toString, 
          idmediopago = jsonMap.get("idmediopago").toString,
          idbackend = jsonMap.get("idbackend").toString, 
          idprotocolo = jsonMap.get("idprotocolo").toString.toInt, 
          descri = jsonMap.get("descri").toString, 
          blacklist = jsonMap.get("blacklist").toString.toBoolean
      )
  }
  
  def retrieveTransactionsStatus(siteId: String) = {
      val jsonMap = evalWithJdbcTemplate { JdbcTemplate =>
        JdbcTemplate.queryForList("select idtransaccionsite, idestado from spstransac where idsite = ?", siteId)
      }.asScala.toList
      
      jsonMap.map(jsonM => convertToTransactionStatusFromJson(jsonM).get)
  }
  
  private def convertToTransactionStatusFromJson(jsonMap: java.util.Map[String, Object]): Try[TransactionStatus] = Try {   
      TransactionStatus(
          siteTransactionId = jsonMap.get("idtransaccionsite").toString,
          status = jsonMap.get("idestado").toString.toInt
      )
  }
  
}


class TerminalModeloLegacy {
  @BeanProperty var terminal: String = null
  @BeanProperty var idSite: String = null
  @BeanProperty var idMedioPago: Int = -1
  @BeanProperty var idProtocolo: Int = -1
  @BeanProperty var estado: Int = -1
}  


class NrosTraceModeloLegacy {
  @BeanProperty var idSite: String = null
  @BeanProperty var idBackend: Int = 0
  @BeanProperty var nroTerminal: String = null
  @BeanProperty var nroTrace: Long = 0
  @BeanProperty var nroTicket: Long = 0
  @BeanProperty var nroBatch: Long = 0
}



