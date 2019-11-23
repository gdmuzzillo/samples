package services

import scala.concurrent.Future
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import scala.concurrent.ExecutionContext

object FutureCompanionOps {

    /** Given a list of futures `fs`, returns the future holding the list of Try's of the futures from `fs`.
      * The returned future is completed only once all of the futures in `fs` have been completed.
      */
    def allAsTrys[T](fItems: /* future items */ List[Future[T]])(implicit ec: ExecutionContext): Future[List[Try[T]]] = {
      val listOfFutureTrys: List[Future[Try[T]]] = fItems.map(futureToFutureTry)
      Future.sequence(listOfFutureTrys)
    }

    def futureToFutureTry[T](f: Future[T])(implicit ec: ExecutionContext): Future[Try[T]] = {
      f.map(Success(_)).recover({case x => Failure(x)})
    }

    def allFailedAsTrys[T](fItems: /* future items */ List[Future[T]])(implicit ec: ExecutionContext): Future[List[Try[T]]] = {
      allAsTrys(fItems).map(_.filter(_.isFailure))
    }

    def allSucceededAsTrys[T](fItems: /* future items */ List[Future[T]])(implicit ec: ExecutionContext): Future[List[Try[T]]] = {
      allAsTrys(fItems).map(_.filter(_.isSuccess))
    }
    
    
    
    
}