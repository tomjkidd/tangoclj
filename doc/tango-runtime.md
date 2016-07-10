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
