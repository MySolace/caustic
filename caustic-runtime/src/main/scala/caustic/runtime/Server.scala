package caustic.runtime

import caustic.runtime.service.{Address, Registry}

import org.apache.thrift.server.TNonblockingServer
import org.apache.thrift.transport.TNonblockingServerSocket
import pureconfig._

import java.io.{Closeable, File}
import java.net.{URL, URLClassLoader}

/**
 * An executable, runtime instance.
 *
 * @param database Underlying database.
 * @param port Port number.
 * @param registry Optional service registry.
 */
case class Server(
  database: Database,
  port: Int,
  registry: Option[Registry] = scala.None
) extends Closeable {

  // Construct a Thrift server.
  private val transport = new TNonblockingServerSocket(this.port)
  private val processor = new thrift.Database.AsyncProcessor(this.database)
  private val arguments = new TNonblockingServer.Args(this.transport).processor(this.processor)
  private val server = new TNonblockingServer(this.arguments)
  private val thread = new Thread(() => this.server.serve())

  // Asynchronously serve the database.
  this.thread.start()

  // Announce the server in the registry.
  this.registry.foreach(_.register(Address.local(this.port)))

  override def close(): Unit = {
    // Remove the server from the registry.
    this.registry.foreach(_.unregister(Address.local(this.port)))

    // Close the Thrift server and underlying database.
    this.server.stop()
    this.database.close()
  }

}

object Server {

  /**
   * A Server configuration.
   *
   * @param port Port number.
   * @param caches List of cache names.
   * @param database Database name.
   * @param discoverable Service discovery trigger.
   */
  case class Config(
    port: Int,
    caches: List[String],
    database: String,
    discoverable: Boolean
  )

  /**
   * Constructs a Server by loading the configuration from the classpath.
   *
   * @return Classpath-configured Server.
   */
  def apply(): Server =
    Server(loadConfigOrThrow[Config]("caustic.server"))

  /**
   * Constructs a Server from the provided configuration.
   *
   * @param config Configuration.
   * @return Dynamically-configured Server.
   */
  def apply(config: Config): Server = {
    val underlying = config.caches.foldRight(Database.forName(config.database))(Cache.forName)
    val registry = Option(config.discoverable) collect { case true => Registry() }
    Server(underlying, config.port, registry)
  }

  /**
   * An entry-point that bootstraps and serves a Server. Server configurations are loaded from the
   * classpath, but may be overridden by providing a path to a configuration file path or by
   * explicitly setting the value of system properties.
   */
  object Main extends App {

    if (args.length > 1) {
      println("Usage: ./pants run caustic-runtime/src/main/scala:server [config] -- -Dprop=value")
      System.exit(1)
    }

    // Add configuration file to classpath. https://stackoverflow.com/a/7884406/1447029
    if (args.nonEmpty) {
      val uri = new File(args(0)).toURI
      val classLoader = ClassLoader.getSystemClassLoader.asInstanceOf[URLClassLoader]
      val method = classOf[URLClassLoader].getDeclaredMethod("addURL", classOf[URL])
      method.setAccessible(true)
      method.invoke(classLoader, Array(uri.toURL))
    }

    // Asynchronously bootstrap server, and tear it down on shutdown.
    val server = Server()
    sys.addShutdownHook {
      this.server.close()
    }

  }

}