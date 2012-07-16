import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "notifications"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      // Add your project dependencies here,
      "runcom.mongodb.casbah" %% "casbah" % "2.1.5-1"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      // Add your own project settings here      
    )

}
