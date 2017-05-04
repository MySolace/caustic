# Schema
Most backend architectures are composed of an *application cluster* and a *database cluster*. The stateless *application servers* in the cluster receive and respond to client requests by performing accesses and modifications to data stored in the underlying, stateful *database cluster*. Each *application server* has to be carefully designed to guarantee that its behavior is consistent across the cluster. For example, it is easy to see that a cluster of *application servers* that, say, retrieve data from the *database cluster*, check if the data matches some value, and then persist an update to the data to the *database cluster*, may lead to race conditions and inconsistent behavior. Traditional databases in use today have solved this problem by inventing transactions. However, it can be argued that database transactions today are fundamentally lacking for three reasons:

1. __Disparate Interfaces__: Every database has a different language for specifying transactions. Table 1 highlights the vastly different transaction interfaces for a few well-known databases. Thus, the design of an application server requires upfront knowledge of the choice of underlying database. Whenever a decision is made to change the database, all existing transactions must be rewritten to conform to the new specification. Indeed, the tremendous expense of rewriting transactions is a significant reason why large companies are locked into their existing database infrastructure and are unable to modernize them.

2. __Deficient Functionality__: In particular, there are two key areas where databases are typically lacking in functionality. First, databases generally do not support cross-shard transactions. Thus, the design of an application server requires upfront knowledge of data placement. This limits the kinds of transactions that may be performed, since transactions must operate on data within a single shard. Second, databases generally do not permit arbitrary computation and conditional branching within transactions. As a result, transactions are restricted to simple, linear control flow, and are hence limited in scope. 

3. __Performance Penalties__: Most databases that support transactions do so at a significant cost. For example, Redis guarantees that “All the commands in a transaction are serialized and executed sequentially. It can never happen that a request issued by another client is served in the middle of the execution of a Redis transaction. This guarantees that the commands are executed as a single isolated operation.” This means that transactions may only be performed one-at-a-time, so the application server may be forced to sacrifice write performance for transactional safety.

The purpose of this library is to properly decouple the *application cluster* and the *database cluster* components of any backend architecture by providing a consistent, high-performance transactional interface over arbitrary key-value stores.
