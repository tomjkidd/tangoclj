# Tango Runtime

Each Tango Object uses a Tango Runtime client to provide services to sync
with the shared log.

The runtime is responsible for maintaining the shared log.

## Interface

update-helper appends changes to Tango Objects to the log
query-helper applies changes from the log to Tango Objects

```
update-helper :: TengoRuntime -> Oid -> OpaqueBuffer -> Either UpdateError Position
query-helper :: TangoRuntime -> Oid -> ()
```
These two methods should be sufficient to describe the operations that a
Tango Object supports.

From the point of view of the Tango Runtime, update-helper will allow a 
a Tango Object to write to the log and query-helper allows a Tango Object
to get the latest changes from the log for a given time.

A key point to note is that a write does not update the RAM view of the 
Tango Object. Any number of writes will not advance the log until a read
is performed. Calling query-helper is how Tango Objects tell the runtime
that they want the current value, and then the runtime will read the log
and each entry will be applied to the Tango Objects that are connected 
to the oid's that each entry contains.

## Transactions

Speculative transaction commit records are used to write transactions to
the shared log.

Each commit record needs a
* read-set, a list of objects modified, along with their versions. A 
version is just the position in the log file of the most recent update 
that modified the object
* write-set, a list of objects modified, along with their versions.

* A transaction only succeeds if none of it's reads are stale when the
commit is encountered (ie object hasn't changed since it was read).
* begin-tx and end-tx methods should be provided by the runtime to signify
the entries that the transaction wraps over.
* begin-tx creates a transaction in thread-local storage
* end-tx appends a commit record to the shared log, plays the log forward
until the commit point, then makes a commit/abort decision
* Each client that encounters the commit record decides (independently,
but deterministically) whether it should commit or abort by comparing the
versions in the read set with the current versions of the objects. If none
of the read objects have changed since they were read, the transaction
commits and the objects in the write-set are updated with the apply
function.
* The tango runtime substitutes update-helper and query-helper when in the
context of a transaction. update-helper buffers updates, instead of
applying them. Suggests buffering them and flushing to log marked with
'speculative'.
* When another tango runtime client encounters speculative reads/writes,
it buffers them until it reaches a commit (where it then has to decide
whether or not to apply them).
* query-helper for transactions updates the read-set instead of applying
changes. 
* read-only transactions don't perform a commit message
* write-only transactions require an append on the shared log, but can 
commit immediately without playing the log forward.
