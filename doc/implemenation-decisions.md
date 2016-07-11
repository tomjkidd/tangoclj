### Differences from Tango paper

#### Apply interface is different

The apply interface that the runtime uses to update objects will call
the provided with a log entry, rather than just a value. This choice was
made to facilitate any modifications I couldn't foresee, leaving the Tango
Object free to use any information available to the log. There may be
a point at which it makes sense for the Tango Objects to know nothing
about the structure of the log entries, but I wanted to be able to make
that decision later. 
