# Introduction to tango

This project is meant to be a clojure-based working reference implementation
of [Tango](http://www.cs.cornell.edu/~taozou/sosp13/tangososp.pdf). Some
aspects of it may not be exactly the same, but the heart of it is meant to
demonstrate similar functionality.

This project works with the following components

1. Shared Log
2. Tango Runtime
3. Tango Objects

# Shared Log

The shared log is responsible for providing ACID properties to the Tango
Objects used by an end user as on-demand data structures.

# Tango Runtime

The Tango Runtime uses the shared log as a client in order to persist changes
to Tango Objects.

The Tango Runtime is used as a service by end users in order to communicate
the desire to change and read values from Tango Objects.

# Tango Objects

Tango objects are used directly by end users to provide data structures, ie
Register for single value, Map for a hash map/dictionary, List, and can
be extended to support new data structures.
