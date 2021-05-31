
name := "Simulator"

version := "0.2"
mainClass in assembly := Some("Main")
scalaVersion := "2.13.1"
libraryDependencies  ++= Seq(
  "org.scalanlp" %% "breeze" % "1.0",
  "org.scalanlp" %% "breeze-natives" % "1.0",
  "com.typesafe.akka" %% "akka-actor" % "2.6.5",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.5",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % Runtime,
  "com.github.pureconfig" %% "pureconfig" % "0.12.3",
  "com.github.haifengl" % "smile-core" % "2.4.0",
  "com.github.haifengl" %% "smile-scala" % "2.4.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.11.1",
  "org.apache.hadoop" % "hadoop-common" % "3.3.0",
  "org.apache.parquet" % "parquet-hadoop" % "1.11.0"

)

assemblyJarName in assembly := "simulator.jar"
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x =>         val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)     }