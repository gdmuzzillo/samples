package legacy.decidir.sps.domain

import javax.inject.Inject
import play.api.db.Database
import play.Logger


class DBSPSProvider @Inject() (db: Database) {

  val dbsps = new LegacyDBSPS(Logger.underlying(), db.dataSource)
  val dbsac = new LegacyDBSAC(Logger.underlying(), db.dataSource)
  
}

