package org.softnetwork.sbt.build

import com.typesafe.sbt.SbtGit.git
import sbt.Keys._
import sbtbuildinfo.{BuildInfoOption, BuildInfoKey}
import sbtbuildinfo.BuildInfoKeys._

/**
  * Created by smanciot on 23/05/2018.
  */
object BuildInfoSettings {

  val settings = Seq(
    buildInfoKeys ++= Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      sbtVersion,
      git.gitHeadCommit,
      git.gitCurrentBranch,
      "packageBase" -> s"org.softnetwork.build.info.${name.value.replace('-', '.').replace("import", "metadata")}"
    ),
    buildInfoPackage := s"org.softnetwork.build.info.${name.value.replace('-', '.').replace("import", "metadata")}",
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoOptions += BuildInfoOption.ToMap
  )
}
