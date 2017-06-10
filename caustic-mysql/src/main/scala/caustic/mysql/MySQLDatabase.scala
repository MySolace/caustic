package caustic.mysql

import caustic.runtime._
import caustic.runtime.local.SQLDatabase
import javax.sql.DataSource

/**
 * A MySQL-backed database.
 *
 * @param underlying Underlying database.
 */
class MySQLDatabase private[mysql](
  underlying: DataSource
) extends SQLDatabase(underlying) {

  override def select(keys: Iterable[Key]): String =
    s""" SELECT `key`, `revision`, `value`
       | FROM `schema`
       | WHERE `key` IN (${ keys.map("\"" + _ + "\"").mkString(",") })
     """.stripMargin

  override def update(key: Key, revision: Revision, value: Value): String =
    s""" INSERT INTO `schema` (`key`, `revision`, `value`)
       | VALUES ("$key", $revision, "$value")
       | ON DUPLICATE KEY UPDATE revision = $revision, value = "$value"
     """.stripMargin

}

object MySQLDatabase {

  /**
   * Constructs a MySQL database backed by the specified data source.
   *
   * @param source Data source.
   * @return MySQL database.
   */
  def apply(source: DataSource): MySQLDatabase = {
    // Construct the database tables if they do not already exist.
    val con = source.getConnection()
    val smt = con.createStatement()

    smt.execute(
      s""" CREATE TABLE IF NOT EXISTS `schema`(
         |   `key` varchar (200) NOT NULL,
         |   `revision` BIGINT DEFAULT 0,
         |   `value` TEXT,
         |   PRIMARY KEY(`key`)
         | )
       """.stripMargin)

    // Construct a MySQL Database.
    con.close()
    new MySQLDatabase(source)
  }

}