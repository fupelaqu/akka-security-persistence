name := "security-api"

mainClass in Compile := Some("org.softnetwork.security.launch.Application")

dockerfile in docker := {
  val appDir: File = stage.value
  val targetDir = "/opt/docker"

  new Dockerfile {
    from("java:latest")
    copy(appDir, targetDir)
    run("mkdir", "-p", s"$targetDir/conf")
    run("mkdir", "-p", s"$targetDir/logs")
    entryPoint(s"$targetDir/bin/${executableScriptName.value}")
    volume(s"$targetDir/conf")
    expose(9000)
    expose(5050)
  }
}

imageNames in docker := Seq(
  // Sets the latest tag
  ImageName(
    namespace = Some(sys.props.getOrElse("repo", "localhost:443")),
    repository = name.value.toLowerCase,
    tag = Some("latest")),
  // Sets a name with a tag that contains the project version
  ImageName(
    namespace = Some(sys.props.getOrElse("repo", "localhost:443")),
    repository = name.value.toLowerCase,
    tag = Some(version.value)
  )
)

scriptClasspath in bashScriptDefines ~= (cp => "../conf" +: cp)
