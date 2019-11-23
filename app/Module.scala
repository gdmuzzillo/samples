import java.time.Clock
import javax.inject.Inject

import com.google.inject.AbstractModule
import com.typesafe.config.ConfigFactory
import controllers.MDCHelperTrait
import play.api.Configuration
import services.cybersource.CybersourceListenerFactory
import services.payments._
import services.replication.{DirectReplication, LegacyReplicationListener, ReplicationNotificationService}
import services.transaction.processing.{LegacyTransactionProcessingErrorStream, LegacyTransactionStream}
import services.{ApplicationTimer, AtomicCounter, Counter, HungTerminalsReleaser}

/**
 * This class is a Guice module that tells Guice how to bind several
 * different types. This Guice module is created when the Play
 * application starts.

 * Play will automatically use any class called `Module` that is in
 * the root package. You can create modules in other locations by
 * adding `play.modules.enabled` settings to the `application.conf`
 * configuration file.
 */
class Module extends AbstractModule with MDCHelperTrait {

  val config = ConfigFactory.load()
  val allowPersist = config.getBoolean("sps.allow-persist")

  override def configure() = {
    // Use the system clock as the default implementation of Clock
    bind(classOf[Clock]).toInstance(Clock.systemDefaultZone)
    // Ask Guice to create an instance of ApplicationTimer when the
    // application starts.
    bind(classOf[ApplicationTimer]).asEagerSingleton()
    // Set AtomicCounter as the implementation for Counter.
    bind(classOf[Counter]).to(classOf[AtomicCounter])
    
    bind(classOf[LegacyReplicationListener]).asEagerSingleton()
    
    bind(classOf[ReplicationNotificationService]).to(classOf[DirectReplication]).asEagerSingleton()

    bind(classOf[HungTerminalsReleaser]).asEagerSingleton()

    //bind(classOf[LegacyTransactionListener]).asEagerSingleton()

    logger.info(s"PERSISTOR - Allow Persist: $allowPersist")
    if(allowPersist){
      bind(classOf[LegacyTransactionStream]).asEagerSingleton()

      bind(classOf[LegacyTransactionProcessingErrorStream]).asEagerSingleton()
    }

    bind(classOf[CybersourceListenerFactory]).asEagerSingleton()
    
    bind(classOf[LegacyTransactionServiceClient]).to(classOf[LegacyTransactionServiceProducer]).asEagerSingleton()
    
    bind(classOf[LegacyOperationServiceClient]).to(classOf[LegacyOperationServiceProducer]).asEagerSingleton()
    
    bind(classOf[LegacyNroTraceListener]).asEagerSingleton()
    
//    bind(classOf[LegacyOperationListener]).asEagerSingleton()
    
  }

}
