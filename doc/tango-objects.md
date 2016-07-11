# Tango Objects


## Notes from reading the paper

* Tango Objects exist in 2 forms, a history in the log and any number of views
* views are the representation of a Tango Object in RAM
* linearizability is provided by the order of appends to the log
* A Tango Object is indistinguishable from an SMR (State Machine Replication
object, where shared log is a total-ordering engine.
* By having mutliple views on an object, different 'querries' and sort
orders can be maintained.


## Interface

Tango Object is free to create it's own interface, intended for use by
clients of the object.

Each Tango Object uses a Tango Runtime as a service. From the point of 
view of a Tango Object, update-helper is used for write operation to apply
changes to the object and thus, to the log. Then, query-helper is used 
to read the state at a given time, which will advance the log to be current.


 ## Transactions
