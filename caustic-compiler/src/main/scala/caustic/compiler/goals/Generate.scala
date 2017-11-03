package caustic.compiler.goals

import caustic.compiler.Goal
import caustic.compiler.types._
import caustic.grammar.{CausticBaseVisitor, CausticParser}
import java.io.{BufferedWriter, FileOutputStream, PrintWriter}
import java.nio.file.Path
import scala.util.Try

/**
 * A code generator. Compiles a Caustic program into Scala. Generated code takes a hard dependency
 * on Spray Json (version 1.3.3) and on various components of the Caustic runtime.
 *
 * @param output Output directory.
 */
case class Generate(output: Path) extends CausticBaseVisitor[Unit] with Goal[Unit] {

  override def execute(parser: CausticParser): Try[Unit] =
    Try(visitProgram(parser.program()))

  override def visitProgram(ctx: CausticParser.ProgramContext): Unit = {
    // Determine the Scala package of the program.
    val namespace = if (ctx.Module() != null) ctx.module(0).getText else ""
    val universe = Declare(Universe.root).visitProgram(ctx)

    // Convert Caustic records to Scala case classes.
    val records = universe.aliases.values collect {
      case Alias(name, record: Record) =>
        s"""// TODO: Copy block comment from *.acid file.
           |case class $name(
           |  ${ record.fields map { case (n, t) => s"$n: ${ toScala(t) }" } mkString ",\n|  " }
           |)
           |
           |/**
           | * A Spray Json serialization protocol for instances of [[$name]].
           | */
           |object $name$$JsonProtocol extends DefaultJsonProtocol {
           |  implicit val ${ name }Format = jsonFormat${ record.fields.size }($name)
           |}
           |
           |import $name$$JsonProtocol._
         """.stripMargin
    }

    // Convert Caustic services to Scala case classes.
    val services = universe.services.values collect {
      case Service(name, functions) =>
        s"""// TODO: Copy block comment from *.acid file.
           |case class $name(client: Client) {
           |
           |  ${ functions.map(toFunction).mkString("\n").replaceAll("\n", "\n|  ") }
           |
           |}
         """.stripMargin
    }

    // Generate Scala source code.
    val source =
      s"""// Autogenerated by Caustic Compiler
         |package $namespace
         |
         |import caustic.runtime.service._
         |import spray.json._
         |import scala.util.Try
         |
         |${ records.mkString("\n") }
         |${ services.mkString("\n") }
     """.stripMargin

    // Write source code to file.
    val file = output.resolve(namespace.replaceAll("\\.", "/") + ".scala").toFile
    file.createNewFile()

    val writer = new FileOutputStream(file)
    writer.write(source.getBytes("UTF-8"))
    writer.close()
  }

  /**
   * Converts a Caustic [[Alias]] into a Scala type.
   *
   * @param alias Type [[Alias]].
   * @return Corresponding Scala type.
   */
  def toScala(alias: Alias): String = alias match {
    case Alias(_, Null)       => "Unit"
    case Alias(_, String)     => "String"
    case Alias(_, Double)     => "Double"
    case Alias(_, Int)        => "Int"
    case Alias(_, Boolean)    => "Boolean"
    case Alias(_, Pointer(_)) => "String"
    case Alias(n, Record(_))  => n
  }

  /**
   * Converts a Caustic [[Function]] to a Scala function.
   *
   * @param function [[Function]] declaration.
   * @return Scala function.
   */
  def toFunction(function: Function): String = {
    // Pass Scala arguments to the underlying Caustic function.
    val body = function.args
      .map(x => toCaustic(x.name, Result(x.alias.datatype, x.key)))
      .foldRight(toJson(function.body))((a, b) => s"cons($a, $b)")

    // Construct a Scala function.
    s"""
       |// Pre-compute function body to reduce allocations and improve performance.
       |private val ${ function.name }$$Body = $body
       |
       |// TODO: Copy block comment from *.acid file.
       |def ${ function.name }(
       |  ${ function.args.map(x => s"""${ x.name }: ${ toScala(x.alias) }""").mkString(",\n|  ") }
       |): Try[${ toScala(function.returns) }] = {
       |  this.client.execute(${ function.name }$$Body) map { result =>
       |    // Extract a Json string from the result.
       |    if (result.isSetText)
       |      result.getText
       |    else if (result.isSetReal)
       |      result.getReal.toString
       |    else if (result.isSetFlag)
       |      result.getFlag.toString
       |    else
       |      ""
       |  } map {
       |    // Deserialize the result using Spray Json.
       |    _.parseJson.convertTo[${ toScala(function.returns) }]
       |  }
       |}
     """.stripMargin
  }

  /**
   * Converts a Scala variable into a Caustic [[Result]].
   *
   * @param variable Scala variable name.
   * @param result Result location.
   * @return Copy [[caustic.runtime.thrift.Transaction]].
   */
  def toCaustic(variable: String, result: Result): String = result.tag match {
    case String =>
      s"""store(${ result.value }, text($variable))"""
    case Double =>
      s"""store(${ result.value }, real($variable))"""
    case Int =>
      s"""store(${ result.value }, real($variable))"""
    case Boolean =>
      s"""store(${ result.value }, flag($variable))"""
    case Pointer(_) =>
      s"""store(${ result.value }, text($variable))"""
    case Record(fields) =>
      fields.zip(Simplify.fields(result)).map {
        case (f, r) => toCaustic(s"""$variable.$f""", r)
      }.foldLeft("Empty")((a, b) => s"""cons($a, $b)""")
  }

  /**
   * Serializes a Caustic [[Result]] to JSON.
   *
   * @param result Caustic [[Result]].
   * @return Serialized representation.
   */
  def toJson(result: Result): String = result.tag match {
    case Record(fields) =>
      // Serialize the fields of the record.
      val json = fields.keys.zip(Simplify.fields(result)) map {
        case (n, f) => s"""add(text("\\"$n\\":"), ${ toJson(f) })"""
      } reduce((a, b) => s"""add(add($a, text(",")), $b)""")

      // Serialize records as json objects.
      s"""add(add(text("{ "), $json), text(" }"))"""
    case String =>
      // Serialize string and pointer fields as quoted values.
      s"""add(add(text("\\""), load(${ result.value })), text("\\""))"""
    case Pointer(_) =>
      // Serialize string and pointer fields as quoted values.
      s"""add(add(text("\\""), ${ result.value }), text("\\""))"""
    case _ =>
      // Serialize all other types normally.
      s"""load(${ result.value })"""
  }

}
