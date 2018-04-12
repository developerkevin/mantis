package io.iohk.ethereum.mallet.service


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Json}
import io.iohk.ethereum.domain.Address
import io.iohk.ethereum.jsonrpc.{JsonRpcError, TransactionReceiptResponse}
import io.iohk.ethereum.mallet.common.{Err, RpcClientError, Util}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

object RpcClient {
  // TODO: CL option to enable akka logging
  private val akkaConfig = ConfigFactory.load("mallet")

  implicit val actorSystem = ActorSystem("mallet_rpc", akkaConfig)
  implicit val materializer = ActorMaterializer()
}

//TODO: validate node URI
/**
  * Talks to a node over HTTP(S) JSON-RPC
  * Note: the URI schema determins whether HTTP or HTTPS is used
  */
class RpcClient(node: String) {
  import CommonJsonCodecs._
  import RpcClient._
  import actorSystem.dispatcher


  //TODO: CL option
  private val httpTimeout = 5.seconds

  def sendTransaction(rawTx: ByteString): Either[Err, ByteString] =
    doRequest[ByteString]("eth_sendRawTransaction", List(rawTx.asJson))

  def getNonce(address: Address): Either[Err, BigInt] =
    doRequest[BigInt]("eth_getTransactionCount", List(address.asJson, "latest".asJson))


  def getBalance(address: Address): Either[Err, BigInt] =
    doRequest[BigInt]("eth_getBalance", List(address.asJson, "latest".asJson))

  def getReceipt(txHash: ByteString): Either[Err, TransactionReceiptResponse] =
    doRequest[TransactionReceiptResponse]("eth_getTransactionReceipt", List(txHash.asJson))


  private def doRequest[T: Decoder](method: String, args: Seq[Json]): Either[Err, T] = {
    val jsonRequest = prepareJsonRequest(method, args)
    makeRpcCall(jsonRequest).flatMap(getResult[T])
  }

  private def getResult[T: Decoder](jsonResponse: Json): Either[Err, T] = {
    jsonResponse.hcursor.downField("error").as[JsonRpcError] match {
      case Right(error) =>
        Left(RpcClientError(s"Node returned an error: ${error.message} (${error.code})"))
      case Left(_) =>
        jsonResponse.hcursor.downField("result").as[T].left.map(f => RpcClientError(f.message))
    }
  }

  private def makeRpcCall(jsonRequest: Json): Either[Err, Json] = {
    val entity = HttpEntity(ContentTypes.`application/json`, jsonRequest.noSpaces)
    val request = HttpRequest(method = HttpMethods.POST, uri = node, entity = entity)

    val responseF: Future[Either[Err, Json]] = Http().singleRequest(request)
      .flatMap(_.entity.toStrict(httpTimeout))
      .map(e => parse(e.data.utf8String).left.map(e => RpcClientError(e.message)))
      .recover { case ex =>
        Left(RpcClientError("RPC request failed: " + Util.exceptionToString(ex)))
      }

    Try(Await.result(responseF, httpTimeout)) match {
      case Success(res) => res
      case Failure(_) => Left(RpcClientError(s"RPC request to '$node' timed out after $httpTimeout"))
    }
  }

  private def prepareJsonRequest(method: String, args: Seq[Json]): Json = {
    Map(
      "jsonrpc" -> "2.0".asJson,
      "method" -> method.asJson,
      "params" -> args.asJson,
      "id" -> "mallet".asJson
    ).asJson
  }

}
