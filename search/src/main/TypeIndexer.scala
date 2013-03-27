package lila.search

import actorApi._

import scalastic.elasticsearch.{ Indexer ⇒ EsIndexer }

import akka.actor._
import akka.pattern.{ ask, pipe }
import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import play.api.libs.concurrent._
import play.api.libs.json._
import play.api.Play.current

final class TypeIndexer(
    es: EsIndexer,
    indexName: String,
    typeName: String,
    mapping: JsObject,
    indexQuery: JsObject ⇒ Unit) extends Actor {

  def receive = {

    case Search(request) ⇒ sender ! SearchResponse(
      request.in(indexName, typeName)(es)
    )

    case Count(request) ⇒ sender ! CountResponse(
      request.in(indexName, typeName)(es)
    )

    case Clear ⇒ doClear

    case RebuildAll ⇒ {
      self ! Clear
      indexQuery(Json.obj())
    }

    case Optimize ⇒ es.optimize(Seq(indexName))

    case InsertOne(id, doc) ⇒ es.index(
      indexName,
      typeName,
      id,
      Json stringify doc
    )

    case InsertMany(list) ⇒ es bulk {
      list map {
        case (id, doc) ⇒ es.index_prepare(
          indexName,
          typeName,
          id,
          Json stringify doc
        ).request
      }
    }

    case RemoveOne(id) ⇒ es.delete(indexName, typeName, id)
  }

  private def doClear {
    try {
      es.createIndex(indexName, settings = Map())
    }
    catch {
      case e: org.elasticsearch.indices.IndexAlreadyExistsException ⇒
    }
    try {
      es.deleteByQuery(Seq(indexName), Seq(typeName))
      es.waitTillActive()
      es.deleteMapping(indexName :: Nil, typeName.some)
    }
    catch {
      case e: org.elasticsearch.indices.TypeMissingException ⇒
    }
    es.putMapping(indexName, typeName, Json stringify Json.obj(typeName -> mapping))
    es.refresh()
  }
}
