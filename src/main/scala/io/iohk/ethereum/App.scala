package io.iohk.ethereum

import java.net.URI

import akka.actor.ActorSystem
import akka.agent._
import akka.util.ByteString
import io.iohk.ethereum.crypto._
import io.iohk.ethereum.network.{PeerManagerActor, ServerActor}
import io.iohk.ethereum.utils.{BlockchainStatus, Config, NodeStatus, ServerStatus}
import org.spongycastle.util.encoders.Hex

import scala.concurrent.ExecutionContext.Implicits.global

object App {

  import Config.{Network => NetworkConfig}

  val nodeKey = generateKeyPair()

  def main(args: Array[String]): Unit = {
    val actorSystem = ActorSystem("etc-client_system")

    val nodeStatus =
      NodeStatus(
        key = nodeKey,
        serverStatus = ServerStatus.NotListening,
        blockchainStatus = BlockchainStatus(Config.Blockchain.genesisDifficulty, Config.Blockchain.genesisHash))

    val nodeStatusHolder = Agent(nodeStatus)

    val peerManager = actorSystem.actorOf(PeerManagerActor.props(nodeStatusHolder), "peer-manager")
    val server = actorSystem.actorOf(ServerActor.props(nodeStatusHolder, peerManager), "server")

    server ! ServerActor.StartServer(NetworkConfig.Server.listenAddress)

    val bootstrapNodes = NetworkConfig.Discovery.bootstrapNodes.map(new URI(_))
    bootstrapNodes.foreach { node =>
      peerManager ! PeerManagerActor.ConnectToPeer(node)
      //TODO change to CLI command?
      Thread.sleep(2 * 1000)
      peerManager ! PeerManagerActor.StartFastDownload(node, ByteString(Hex.decode("ce786b7fd215741eee1958bfa36a558ed275c8f2ad3dd459b6f0f33ff48103d1")))
    }
  }

}