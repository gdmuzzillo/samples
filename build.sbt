import org.joda.time.DateTime
import ReleaseTransformations._

name := """coretx"""

organization := "com.decidir"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(sbtdocker.DockerPlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      BuildInfoKey.action("buildTime"){
        new DateTime().toLocalDateTime.toString
      }),
    buildInfoPackage := "controllers",
    buildInfoObject := "BuildInfo",
    buildInfoOptions ++= Seq(BuildInfoOption.ToJson, BuildInfoOption.Traits("ToJson"))
  )

val builderHost = "lapp-dvde004"
val registry = s"${builderHost}:5000"
val nexus = s"http://${builderHost}:8085/"

scalaVersion := "2.11.8"

val kamonVersion = "0.6.2"

val monocleLibraryVersion = "1.2.1"

val commonsVersion = "0.41.0"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0-RC1" % Test,
  "org.scala-lang" % "scala-reflect" % "2.11.7",
  "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.4",
  "org.springframework" % "spring-jdbc" % "4.2.5.RELEASE",
  "com.decidir" %% "commons" % commonsVersion withSources(),
  "mysql" % "mysql-connector-java" % "5.1.36",
  "com.github.julien-truffaut"  %%  "monocle-core"    % monocleLibraryVersion,
  "com.github.julien-truffaut"  %%  "monocle-generic" % monocleLibraryVersion,
  "com.github.julien-truffaut"  %%  "monocle-macro"   % monocleLibraryVersion,
  "com.github.julien-truffaut"  %%  "monocle-state"   % monocleLibraryVersion,
  "com.github.julien-truffaut"  %%  "monocle-refined" % monocleLibraryVersion,
  "com.github.julien-truffaut"  %%  "monocle-law"     % monocleLibraryVersion % "test",
  "commons-validator"          % "commons-validator"        % "1.5.1",
  "org.mockito" % "mockito-all"  % "1.10.19" % "test",
  "io.kamon"    %% "kamon-core"           % kamonVersion,
  "io.kamon"    %% "kamon-akka"           % kamonVersion,
  "io.kamon"    %% "kamon-play-25"        % kamonVersion,
  "io.kamon"    %% "kamon-log-reporter"   % kamonVersion,
  "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2" % "test",
  "net.logstash.logback" % "logstash-logback-encoder" % "4.7" intransitive(),
  "org.aspectj" % "aspectjweaver" % "1.8.1",
  "com.typesafe.akka" %% "akka-stream-kafka" % "0.16"
)

aspectjSettings
javaOptions <++= AspectjKeys.weaverOptions in Aspectj
javaOptions in Universal ++= Seq (
  "-J-javaagent:/opt/docker/lib/org.aspectj.aspectjweaver-1.8.1.jar",
  "-Dorg.aspectj.tracing.factory=default"
)

val publishDocker = ReleaseStep(action = st => {
  val extr: Extracted = Project.extract(st)
  val ref: ProjectRef = extr.get(thisProjectRef)
  extr.runAggregated(
    sbtdocker.DockerKeys.dockerBuildAndPush in sbtdocker.DockerPlugin.autoImport.docker in ref,
    st
  )
  st
})

lazy val publishSnapshot = taskKey[Unit]("Publish a Snapshot and it's Docker image")
publishSnapshot in Compile := Def.sequential(
  clean in Compile,
  publish in Compile,
  sbtdocker.DockerKeys.dockerBuildAndPush in sbtdocker.DockerPlugin.autoImport.docker
).value

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  publishDocker,
  setNextVersion,
  commitNextVersion,
  pushChanges
)

resolvers ++= Seq(
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  "google-sedis-fix" at "http://pk11-scratch.googlecode.com/svn/trunk",
  Resolver.url("Typesafe Ivy releases", url("https://repo.typesafe.com/typesafe/ivy-releases"))(Resolver.ivyStylePatterns),
  Resolver.bintrayRepo("cakesolutions", "maven"),
  "Redbee - DECD Snapshots" at nexus + "content/repositories/decidir-snapshots/",
  "Redbee - DECD Releases" at nexus + "content/repositories/decidir/",
  "Kamon Repository Snapshots" at "http://snapshots.kamon.io"
)

credentials += Credentials("Sonatype Nexus Repository Manager", builderHost, "builder", "123qWe")

publishTo := {
  if (isSnapshot.value)
    Some("snapshot" at nexus + "content/repositories/decidir-snapshots")
  else
    Some("releases" at nexus + "content/repositories/decidir")
}

updateOptions := updateOptions.value.withCachedResolution(true)

EclipseKeys.withSource := true

compileOrder := CompileOrder.Mixed
javacOptions ++= Seq("-source", "1.6")

imageNames in docker := Seq(
  ImageName(
    namespace = Some(registry),
    repository = name.value,
    tag = Some(version.value)
  )
)

dockerfile in docker := {
  val appDir: File = stage.value
  val finder: PathFinder = (appDir / "conf") * "jce_policy-8.tar.gz"
  new Dockerfile {
    from(s"${registry}/java-alpine:latest")
    maintainer("redbee studios")
    expose(9000, 9443)
    workDir("/opt/docker")
    add(appDir, "/opt/docker")
    add(finder.get, "/usr/lib/jvm/default-jvm/jre/lib/security/")
    entryPoint("/opt/docker/conf/wrapper.sh")
  }
}
