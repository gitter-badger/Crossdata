/*
 * Licensed to STRATIO (C) under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.  The STRATIO (C) licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.stratio.crossdata.server.actors

import java.util.{Random, UUID}

import akka.actor.{Actor, Props, ReceiveTimeout}
import akka.cluster.{Cluster, MemberStatus}
import akka.routing.RoundRobinRouter
import com.stratio.crossdata.common.ask.{Command, Connect, Query}
import com.stratio.crossdata.common.result.{ConnectResult, DisconnectResult, Result}
import com.stratio.crossdata.communication.{Disconnect, ReroutedCommand, ReroutedQuery}
import com.stratio.crossdata.core.engine.Engine
import com.stratio.crossdata.core.loadWatcher.LoadWatcherManager
import com.stratio.crossdata.server.config.ServerConfig
import org.apache.log4j.Logger
import collection.JavaConversions._

object ServerActor {


  def props(engine: Engine,cluster: Cluster): Props = Props(new ServerActor(engine,cluster))
}

class ServerActor(engine: Engine,cluster: Cluster) extends Actor with ServerConfig {
  override lazy val logger = Logger.getLogger(classOf[ServerActor])
  val random=new Random
  val hostname=config.getString("akka.remote.netty.tcp.hostname")
  
  val loadWatcherActorRef = context.actorOf(LoadWatcherActor.props(hostname), "loadWatcherActor")
  val connectorManagerActorRef = context.actorOf(ConnectorManagerActor.props(cluster).
    withRouter(RoundRobinRouter(nrOfInstances = num_connector_manag_actor)), "ConnectorManagerActor")
  val coordinatorActorRef = context.actorOf(CoordinatorActor.props(connectorManagerActorRef, engine.getCoordinator()).
    withRouter(RoundRobinRouter(nrOfInstances = num_coordinator_actor)), "CoordinatorActor")
  val plannerActorRef = context.actorOf(PlannerActor.props(coordinatorActorRef, engine.getPlanner).
    withRouter(RoundRobinRouter(nrOfInstances = num_planner_actor)), "PlannerActor")
  val validatorActorRef = context.actorOf(ValidatorActor.props(plannerActorRef, engine.getValidator).
    withRouter(RoundRobinRouter(nrOfInstances = num_validator_actor)), "ValidatorActor")
  val parserActorRef = context.actorOf(ParserActor.props(validatorActorRef, engine.getParser()).
    withRouter(RoundRobinRouter(nrOfInstances = num_parser_actor)), "ParserActor")
  val APIActorRef = context.actorOf(APIActor.props(engine.getAPIManager()).
    withRouter(RoundRobinRouter(nrOfInstances = num_api_actor)), "APIActor")


  def chooseReroutee(): String={
    //TODO: what happens if there is old data in infinispan?
    //TODO: clean infinispan every time we start a node?
    //TODO: clean infinispan every once in a while?
    //TODO: what happens if the values can not be cast to double
    logger.info("choosing reroutee")
    val n=cluster.state.members.collect{
        case m if m.status == MemberStatus.Up => s"${m.address}/user/crossdata-server"
    }.toSeq
    val infinispanNodes=LoadWatcherManager.MANAGER.getData.filter(i=>n.exists(nd=>nd.contains(i._1.toString)))
    val min=infinispanNodes.minBy(_._2.toString.toDouble)
    //n(random.nextInt(n.length))
    n.filter(_.contains(min._1))(0)
  }


  def reroute(message: Command): Unit ={
    val reroutee = chooseReroutee()
    logger.info("reroutee=" + reroutee)
    context.actorSelection(reroutee) forward ReroutedCommand(message)
  }
  def reroute(message: Query): Unit ={
    val reroutee = chooseReroutee()
    logger.info("reroutee=" + reroutee)
    context.actorSelection(reroutee) forward ReroutedQuery(message)
  }

  def receive : Receive= {
    case query: Query =>{
      logger.info("Query rerouted : " + query.statement.toString)
      reroute(query)
    } 
    case ReroutedQuery(query)=> {
      logger.info("query: " + query + " sender: " + sender.path.address)
      parserActorRef forward query
    }
    case Connect(user) => {
      logger.info("Welcome " + user + "! from " + sender.path.address)
      sender ! ConnectResult.createConnectResult(UUID.randomUUID().toString)
    }
    case Disconnect(user) => {
      logger.info("Goodbye " + user + ".")
      sender ! DisconnectResult.createDisconnectResult(user)
    }
    case cmd: Command =>{
      logger.info("API Command call rerouted " + cmd.commandType)
      reroute(cmd)
    }
    case ReroutedCommand(cmd) => {
      logger.info("API Command call " + cmd.commandType)
      APIActorRef forward cmd
    }
    case ReceiveTimeout => {
      logger.warn("ReceiveTimeout")
      //TODO Process ReceiveTimeout
    }
    case _ => {
      logger.error("Unknown message received by ServerActor");
      sender ! Result.createUnsupportedOperationErrorResult("Not recognized object")
    }
  }
}
