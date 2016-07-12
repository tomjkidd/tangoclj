### Atomic Runtime 

I decided to create a branch that would push the atoms that were being
stored in each tango object to be the responsibility of the runtime.

The reason I made this change was an effort to make it so that in order
to allow transactions to read their own rights, I would need copies of
the objects to be created. In the old system, this would mean that the
runtime would have to create copies of the objects. The would require
a read through the whole log to put the copies into the correct state 
for the start of a transaction.

By making it so that the runtime is responsible for the state of each
object, it should be able to start with the current state and put that
in a new atom with a reference to the current tail of the log. This will
allow a transaction runtime to read the shared log from that known 
starting point with the correct state of the tango objects as they are
involved in the transaction. The transaction runtime can then stream 
the log, including applying speculative writes (which are buffered and
not immediately applied in the main runtime) to give the transaction 
it's own view of it's changes, regardless of if ultimatelythe transaction 
is aborted.

This makes it so that within transaction you can refer to values and 
perform arbitrary computation, in much the same way that you would
through a tool like SQL Server Management Studio. If the transaction
is aborted, the shared log will not use the changes, but you will have
accurate data locally if you choose to use it.

### Changes

The tango objects now use an apply with a different type signature,
the previous state and the log entry
