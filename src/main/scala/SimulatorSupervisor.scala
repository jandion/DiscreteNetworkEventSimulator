
import MessagesPatterns._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector}
import com.typesafe.config.{Config, ConfigFactory, ConfigLoadingStrategy}
import smile.base.cart.SplitRule
import smile.classification.{cart, gbm, randomForest, svm}
import smile.data.formula.Formula
import smile.math.kernel.LinearKernel

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object SimulatorSupervisor {
  def apply(): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
      val simulationConfig = ConfigFactory.load()
      val modelsConfig = ConfigFactory.load("regression_full")
      context.log.warn("Spawning actors")
      //Spawning OutputManager
      val outputManager = context.spawn(OutputManager(), "OutputManager", DispatcherSelector.fromConfig("your-dispatcher"))
      //Spawning queue
      val queueActor = context.spawn(EventQueue(simulationConfig.getBoolean("batch"), simulationConfig.getLong("limit"), outputManager), "EventQueue", DispatcherSelector.fromConfig("your-dispatcher"))
      //Spawning model actors
      //TODO read models from files
      loadModels(context, modelsConfig, queueActor)

      context.log.warn("Adding startup sequence")
      simulationConfig.getLongList("start_sequence_times").asScala
        .zip(simulationConfig.getIntList("start_sequence_indexes").asScala)
        .foreach(ev=>queueActor ! AddEvent(Event(ev._2,ev._1-(3600*24*11))))




      context.log.warn("Simulation start")
      if(simulationConfig.getBoolean("batch"))
        queueActor ! GenerateEventBatch()
      else
        context.system.scheduler.scheduleAtFixedRate(0.seconds, 1.seconds)(() => queueActor ! GenerateEventRealTime())
      Behaviors.empty
    }


  private def loadModels(context: ActorContext[Nothing], modelsConfig: Config, queueActor: ActorRef[MessagesPatterns.Command]) = {
    modelsConfig.getConfigList("models").forEach {
      case x if x.getString("modelType") == "classification" =>
      //Fix read xtream models
      /*
        val r = context.spawn(ClassificationModel(
          x.getDouble("window").toLong,
          x.getDouble("lead").toInt,
          x.getDouble("event").toInt,
          x.getIntList("from").asScala.map(_.toInt).zipWithIndex.toMap,
          smile.read.xstream(x.getString("model")).asInstanceOf[Classifier[Tuple]]
        ), s"classificator_${x.getInt("event")}")
        cola ! EventQueue.RegisterModel(s"classificator_${x.getInt("event")}",r)
  */
      case x if x.getString("modelType") == "regression" =>
        val r = context.spawn(RegressionModel(
          x.getDouble("event").toInt,
          x.getDoubleList("x").asScala.map(_.toDouble).toArray,
          x.getDoubleList("y").asScala.map(_.toLong).toArray,
          queueActor
        ), s"regressor_${x.getInt("event")}", DispatcherSelector.fromConfig("your-dispatcher"))
        queueActor ! MessagesPatterns.RegisterModel(s"regressor_${x.getInt("event")}", r)
    }


    ConfigFactory.load("2train").getConfigList("models").forEach(
      x => {
        val formula = Formula.lhs("y")
        val params = x.getConfig("params")
        val data = smile.read.parquet(x.getString("train_data"))
        x.getString("algorithm") match {
          case "dt" => {
            val rule = if (params.getString("criterion") == "entropy") SplitRule.ENTROPY else SplitRule.GINI
            val model = cart(formula, data, maxDepth = params.getInt("max_depth"), splitRule = rule)
            val r = context.spawn(ClassificationModelDT(
              x.getDouble("window").toLong,
              x.getDouble("lead").toInt,
              x.getDouble("event").toInt,
              x.getIntList("from").asScala.map(_.toInt).zipWithIndex.toMap,
              model,
              queueActor
            ), s"classificator_${x.getInt("event")}", DispatcherSelector.fromConfig("your-dispatcher"))
            queueActor ! MessagesPatterns.RegisterModel(s"classificator_${x.getInt("event")}", r)
          }
          case "rf" => {
            val rule = if (params.getString("criterion") == "entropy") SplitRule.ENTROPY else SplitRule.GINI
            val model = randomForest(formula, data, ntrees = params.getInt("n_estimators"), maxDepth = params.getInt("max_depth"), splitRule = rule)
            val r = context.spawn(ClassificationModelRF(
              x.getDouble("window").toLong,
              x.getDouble("lead").toInt,
              x.getDouble("event").toInt,
              x.getIntList("from").asScala.map(_.toInt).zipWithIndex.toMap,
              model,
              queueActor
            ), s"classificator_${x.getInt("event")}", DispatcherSelector.fromConfig("your-dispatcher"))
            queueActor ! MessagesPatterns.RegisterModel(s"classificator_${x.getInt("event")}", r)
          }
          case "gbt" => {
            val model = gbm(formula, data, ntrees = params.getInt("n_estimators"), maxDepth = params.getInt("max_depth"), shrinkage = params.getDouble("learning_rate"))
            val r = context.spawn(ClassificationModelGBT(
              x.getDouble("window").toLong,
              x.getDouble("lead").toInt,
              x.getDouble("event").toInt,
              x.getIntList("from").asScala.map(_.toInt).zipWithIndex.toMap,
              model,
              queueActor
            ), s"classificator_${x.getInt("event")}", DispatcherSelector.fromConfig("your-dispatcher"))
            queueActor ! MessagesPatterns.RegisterModel(s"classificator_${x.getInt("event")}", r)
          }
          case "svm" => {
            val kernel = new LinearKernel()
            val y = formula.y(data).toIntArray.map(x => if (x == 0) -1 else 1)
            val model = svm(formula.x(data).toArray, y, kernel, C = params.getDouble("C"))
            val r = context.spawn(ClassificationModelSVM(
              x.getDouble("window").toLong,
              x.getDouble("lead").toInt,
              x.getDouble("event").toInt,
              x.getIntList("from").asScala.map(_.toInt).zipWithIndex.toMap,
              model,
              queueActor
            ), s"classificator_${x.getInt("event")}", DispatcherSelector.fromConfig("your-dispatcher"))
            queueActor ! MessagesPatterns.RegisterModel(s"classificator_${x.getInt("event")}", r)
          }
        }



      })

  }
}
