// Configure some basic settings
scalaVersion := "2.13.5"
name := "Agents in scala"
organization := "Hennns"
version := "1.0"

// Set versions for dependencies
val akkaVersion    = "2.6.14"
val logbackVersion = "1.2.3"
val scalaFxVersion = "15.0.1-R21"
val javaFxVersion  = "15.0.1"

// Determine OS version of JavaFX binaries
lazy val osName = System.getProperty("os.name") match {
  case n if n.startsWith("Linux")   => "linux"
  case n if n.startsWith("Mac")     => "mac"
  case n if n.startsWith("Windows") => "win"
  case _                            => throw new Exception("Unknown platform!")
}

// Add dependency on JavaFX libraries, OS dependent.
// This is needed for scalaFx
lazy val javaFXModules = Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
libraryDependencies ++= javaFXModules.map(m => "org.openjfx" % s"javafx-$m" % javaFxVersion classifier osName)

// Add other libraries
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "org.scalafx"       %% "scalafx"          % scalaFxVersion,
  "ch.qos.logback"     % "logback-classic"  % logbackVersion
)
