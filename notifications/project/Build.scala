import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "notifications"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      // Add your project dependencies here,
      "com.mongodb.casbah" %% "casbah" % "2.1.5-1",
      "com.novus" %% "salat-core" % "0.0.8"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      resolvers ++= Seq(
        "repo.novus snaps" at "http://repo.novus.com/snapshots/",
        "repo.novus rels" at "http://repo.novus.com/releases/")
    )

}
