package caustic.benchmarks

import caustic.benchmarks.util.{Measure, Probability, Statistic}
import caustic.runtime._
import caustic.runtime.sql.SQLDatabase
import java.io.PrintWriter
import scala.util.Random
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

/**
 *
 */
object ContentionBenchmark {

  /**
   * Returns the likelihood that two randomized transactions conflict. Let K be the set of all
   * possible keys, and let |K| = n. Given two transactions A and B each containing l keys drawn
   * uniformly at random from K, what is the probability that they conflict? Equivalently, we may
   * find the complement of the probability that A and B are disjoint which is the likelihood that B
   * is drawn from K - A.
   *
   * @param n
   * @param l
   * @return
   */
  def contention(n: BigInt, l: BigInt): BigDecimal =
    1 - BigDecimal(Probability.combinations(n - l, l)) / BigDecimal(Probability.combinations(n, l))

  /**
   * Returns the average number of attempts required for a transaction to complete. We may model
   * the number of attempts A as the negative binomial distribution A ~ 1 + NB(p, 1) where p is the
   * contention probability. Therefore, the average number of attempts is 1 + p / (1 - p).
   *
   * @param p
   * @return
   */
  def attempts(p: BigDecimal): BigDecimal =
    1 + p / (1 - p)

  /**
   *
   * @param n
   * @param l
   * @return
   */
  def random(n: Int, l: Int): Transaction =
    Random.shuffle(Seq.range(0, n)).take(l)
      .map(i => text(i.toString))
      .map(k => write(k, branch(equal(read(k), None), real(1), add(real(1), read(k)))))
      .reduce(cons)

  /**
   *
   * @param i
   * @param l
   * @return
   */
  def disjoint(i: Int, l: Int): Transaction =
    Seq.range(i * l, i * l + l)
      .map(i => text(i.toString))
      .map(k => write(k, branch(equal(read(k), None), real(1), add(real(1), read(k)))))
      .reduce(cons)

  /**
   *
   * @param database
   * @param gen
   * @param times
   * @return
   */
  def execute(
    database: Database,
    gen: => Transaction,
    times: Int
  ): Int =
    (0 until times) count { _ =>
      val result = Await.ready(database.execute(gen), Duration.Inf)
      result.value.get.isFailure
    }

  /**
   *
   * @param n
   * @param l
   * @param times
   * @return
   */
  def simulation(n: Int, l: Int, times: Int): Double = {
    // Construct the database.
    val database = SQLDatabase()
    val total = l * times * 2

    // Delete existing data from the database.
    val connection = database.underlying.getConnection()
    connection.createStatement().execute("TRUNCATE `caustic`")
    connection.close()

    // Measure throughput under contention.
    val conflict = Measure.throughput {
      val tasks = Future.sequence(Seq.fill(2)(Future(execute(database, random(n, l), times))))
      total - Await.result(tasks, Duration.Inf).sum
    }

    // Measure throughput in isolation.
    val isolation = Measure.throughput {
      val tasks = Future.sequence(Seq.tabulate(2)(i => Future(execute(database, disjoint(i, l), times))))
      total - Await.result(tasks, Duration.Inf).sum
    }

    // Close the database.
    database.close()

    // Determine the actual reduction in throughput.
    isolation / conflict
  }

  def main(args: Array[String]): Unit = {
    val N = 100 to 1000 by 10
    val L = 1 to 25
    val times = 2500

    // Generate a CSV file from the simulation.
    val csv = N map { n =>
      L map { l =>
        val res = Seq.fill(5)(simulation(n, l, times))
        s"$n,$l,${attempts(contention(n, l))},${Statistic.mean(res)},${ Statistic.variance(res)}"
      } mkString "\n"
    } mkString "\n"

    // Dump the CSV file to disk.
    new PrintWriter("/Users/ashwin/Documents/data.csv") { write(csv); close }
  }

}
