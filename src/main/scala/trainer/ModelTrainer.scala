package trainer

import com.typesafe.config.ConfigFactory
import smile.base.cart.SplitRule
import smile.data.{DataFrame, SparseDataset, Tuple}
import smile.data.formula._
import smile.classification._
import smile.math.kernel.{GaussianKernel, LinearKernel}
import smile.write

object ModelTrainer {
  def main(args: Array[String]): Unit = {
    val modelsConfig = ConfigFactory.load("2train")
    val formula = Formula.lhs("y")
    modelsConfig.getConfigList("models").forEach (
      x=> {
        val params = x.getConfig("params")
        val data = smile.read.parquet(x.getString("train_data"))
        if (x.getString("algorithm") == "dt") {
          val rule = if (params.getString("criterion") == "entropy") SplitRule.ENTROPY else SplitRule.GINI
          val model = cart(formula, data, maxDepth = params.getInt("max_depth"), splitRule = rule)
          write(model, f"models/${x.getInt("event")}")
        }
        if (x.getString("algorithm") == "rf") {
          val rule = if (params.getString("criterion") == "entropy") SplitRule.ENTROPY else SplitRule.GINI
          val model = randomForest(formula, data, ntrees = params.getInt("n_estimators"), maxDepth = params.getInt("max_depth"), splitRule = rule)
          write(model, f"models/${x.getInt("event")}")
        }
        if (x.getString("algorithm") == "gbt") {
          val model = gbm(formula, data, ntrees = params.getInt("n_estimators"), maxDepth = params.getInt("max_depth"), shrinkage = params.getDouble("learning_rate"))
          write(model, f"models/${x.getInt("event")}")
        }
        if (x.getString("algorithm") == "svm") {
          val kernel = new LinearKernel()
          val y = formula.y(data).toIntArray.map(x => if (x == 0) -1 else 1)
          val model: SVM[Array[Double]] = svm(formula.x(data).toArray, y, kernel, C = params.getDouble("C"))
          write(model, f"models/${x.getInt("event")}")
        }

      })

  }
}
