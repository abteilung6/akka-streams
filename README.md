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