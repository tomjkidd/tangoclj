# Shared Log

## Summary

The shared log supports an interface for managing it's entries.

It is based on the same interface described in [CORFU](http://www.cs.yale.edu/homes/mahesh/papers/corfumain-final.pdf)

CORFU used 64-bit tokens to represent position in the log

The Design and Implementation section of the paper talks about this interface
The fill function allowed an entry to be filled with junk, but for now
I don't include it. A junk value is a sentinal constant value.
[Hyder 7]
[Chain Replication 28]

Offset and position are both names for where an entry is in the log.

An Entry is a collection of information that is used to reconstruct the
update history of a TangoObject.

## Interface

A shared log (based on CORFU) provides the following interface

### Append

The append function appends an Entry to the log, returning it's position

### Read

The read function takes a position and returns an Entry (or an error code)

### Check

The check function returns the current tail position of the log

### Trim

The trim function indicates that a position in the log can be garbage
collected.

### Haskell-like signatures
```
type Position = Integer

append :: Log -> Entry -> Either AppendError Position
read :: Log -> Position -> Either ReadError Entry
tail :: Log -> Position -- "check"
trim :: Log -> Position -> ()
```

## What is needed for an Entry?

NOTE: The needs of an entry are not fully baked in my mind, this is rough

A :position Integer indicates where in the log the entry is located
An :is-junk boolean indicates whether or not the position is legit
An :trimmed boolean indicates that an entry can be garbage collected
A :datetime Date indicates when an entry was appended to the log
A :type keyword for the type of Entry
A :schema-type keyword indicates the type of value stored in the entry
An :oid 64-bit Integer indicates the Tango Object Id
A :payload indicates the serialized value that represents the entry's
    data. Usually this will be the value used to perform an update to the 
    Tango Object
    
A :speculative boolean indicate thats a transaction TangoRuntime is currently
    attempting to commit a transaction, and the entry is speculative 
