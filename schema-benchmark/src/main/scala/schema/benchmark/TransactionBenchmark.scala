package schema.benchmark

import schema.runtime._
import org.scalameter.api._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

object TransactionBenchmark extends Bench.LocalTime {

  // Fake database to "mock" database performance.
  val database: Database = new FakeDatabase

  // Benchmark transactions containing sequential reads.
  val transactions: Gen[Transaction] = Gen
    .exponential("size")(2, 1 << 10, 2)
    .map(size => Seq.fill(size)(read(literal("x"))).reduce((a, b) => cons(a, b)))

  performance of "Database" in {
    measure method "execute" in {
      using(transactions) curve "Execute Latency" in { txn =>
        Await.result(database.execute(txn), Duration.Inf)
      }
    }
  }

}