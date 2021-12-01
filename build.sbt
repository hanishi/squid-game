lazy val root = (project in file("."))
  .enablePlugins(ScalablyTypedConverterPlugin)
  .configure(baseSettings, bundlerSettings, nodeProject)
  .settings(
    useYarn := true,
    name := "squid-game",
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies += ("org.akka-js" %%% "akkajsactortyped" % "2.2.6.14").cross(CrossVersion.for3Use2_13),
    libraryDependencies += ("org.akka-js" %%% "akkajstypedtestkit" % "2.2.6.14" % "test").cross(CrossVersion.for3Use2_13),
    libraryDependencies += ("org.akka-js" %%% "akkajsactorstreamtyped" % "2.2.6.14").cross(CrossVersion.for3Use2_13),
    Compile / npmDependencies ++= Seq(
      "puppeteer" -> "11.0.0",
      "@types/express" -> "4.17.13",
      "express" -> "4.17.1",
      "express-async-handler" -> "1.2.0",
      "cheerio" -> "1.0.0-rc.10"
      )
  )

lazy val baseSettings: Project => Project =
  _.enablePlugins(ScalaJSPlugin)
    .settings(scalaVersion := "3.1.0",
      version := "0.1.0-SNAPSHOT",
      scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
      scalaJSUseMainModuleInitializer := true,
      scalaJSLinkerConfig ~= (_
        /* disabled because it somehow triggers many warnings */
        .withSourceMap(false)
        .withModuleKind(ModuleKind.CommonJSModule))
    )

lazy val bundlerSettings: Project => Project =
  _.settings(
    Compile / fastOptJS / webpackExtraArgs += "--mode=development",
    Compile / fullOptJS / webpackExtraArgs += "--mode=production",
    Compile / fastOptJS / webpackDevServerExtraArgs += "--mode=development",
    Compile / fullOptJS / webpackDevServerExtraArgs += "--mode=production"
  )

val nodeProject: Project => Project =
  _.settings(
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv,
    stStdlib := List("esnext"),
    stUseScalaJsDom := false,
    Compile / npmDependencies ++= Seq(
      "@types/node" -> "16.11.7"
    )
  )