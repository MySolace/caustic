# Schema
Schema is a library for executing transactions over arbitrary key-value stores. Schema provides a Turing-complete language for specifying database transactions ([unlike SQL](http://stackoverflow.com/a/900062/1447029)) and utilizes [Multiversion Concurrency Control](https://en.wikipedia.org/wiki/Multiversion_concurrency_control) to optimistically and efficiently execute transactions.

## Overview
Schema provides a more comprehensible syntax for expressing transactions and guarantees safe execution of transactions on any arbitrary key-value store (that implements the simple ```Database``` trait). Contrast the syntactic difference between the following two equivalent transactions, the first written in MySQL and the second in Schema. 

```sql
START TRANSACTION;
UPDATE posts SET status='approved' where post_id='postId' AND status != 'approved';
COMMIT;
```

```scala
Schema { implicit ctx =>
  val post = Select("postId")
  If (post.status != "approved") {
    post.status = "approved"
  }
}
```

## Documentation
Refer to the [User Guide](https://github.com/ashwin153/schema/wiki/User-Guide) for more detail about how to use the system and the [Implementation](https://github.com/ashwin153/schema/wiki/Implementation) for more detail about how the system works.
