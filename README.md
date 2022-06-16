## First principles

Source = "publisher"
- emits elements asynchronously
- may or may not terminate

Sink = "subscriber"
- receives elements
- terminates only when the publisher terminates

Flow = "processor"
- transforms elements

Build streams by connecting components

Directory
- upstream = to the source
- downstream = to the sink

Running a graph = materializing all components
- each component produces a materialized value
- graph produces a singled materialized value

Stream components are fused
- run on the same actor 

Async boundaries
- components run on different actors
- better throughput
- (when operations are expensive)

Data flows through streams in response to demand
Streams can slow down fast producers