# akka-security-persistence

## Event Sourcing

Event sourcing is a way of persisting your application's state by storing the complete history of the events that determines the current state of your application.

### Entity Behavior

An entity behavior is a stateful, event sourced (also called persistent) actor that receives a (non-persistent) command.

After command validation, event(s) are generated from the command, representing the effect of the command.

The events are persisted by appending to storage which allows for very high transaction rates and efficient replication.

The stateful actor is recovered by replaying the stored events to the actor, allowing it to rebuild its state.
This can be either the full history of changes or starting from a checkpoint in a snapshot.

```scala
trait CommandTypeKey[C <: Command] {
  def TypeKey(implicit tTag: ClassTag[C]): EntityTypeKey[C]
}

trait EntityBehavior[C <: Command, S <: State, E <: Event, R <: CommandResult] extends CommandTypeKey[C] {
...
}
```

Every persistent actor has a stable unique identifier: a persistence id.

### Command

```scala
  trait Command

  /**
    * a command which includes a reference to the actor identity to whom a reply has to be sent
    *
    * @tparam R - type of command result
    */
  trait CommandWithReply[R <: CommandResult] extends Command {
    def replyTo: ActorRef[R]
  }

  /**
    * a wrapper arround a command and its reference to the actor identity to whom a reply has to be sent
    * @tparam C - type of command
    * @tparam R - type of command result
    */
  trait CommandWrapper[C <: Command, R <: CommandResult] extends CommandWithReply[R] {
    def command: C
  }

  /**
    * CommandWrapper companion object
    */
  object CommandWrapper {
    def apply[C <: Command, R <: CommandResult](aCommand: C, aReplyTo: ActorRef[R]) = new CommandWrapper[C, R] {
      override val command = aCommand
      override val replyTo = aReplyTo
    }.asInstanceOf[C]
  }

  /**
    * a command that should be handled by a specific entity
    */
  trait EntityCommand extends Command {
    def id: String
  }

  /**
    * when a command is not intended to be handled by a specific entity
    */
  val ALL = "*"

  /**
    * allow a command to be handled by no specific entity
    */
  trait AllEntities extends EntityCommand {_: Command =>
    override val id: String = ALL
  }

```
 

### Event

* Events happen in the past

* Events are immutable

* Events are one-way messages

* Events should describe business intent

```scala
  trait Event

  trait CrudEvent extends Event

  trait Created[T <: Timestamped] extends CrudEvent {
    def document: T
  }

  trait Updated[T <: Timestamped] extends CrudEvent {
    def document: T
    def upsert: Boolean = true
  }

  trait Loaded[T <: Timestamped] extends CrudEvent {
    def document: T
  }

  trait Upserted extends CrudEvent {
    def uuid: String
    def data: String
  }

  trait UpsertedDecorator { _: Upserted =>
    import org.softnetwork.akka.serialization._
    implicit def formats: Formats = commonFormats
    override lazy val data = serialization.write(this)
  }

  trait Deleted extends CrudEvent {
    def uuid: String
  }

```

## CQRS

## Serialization

```json
    serializers {
      proto = "akka.remote.serialization.ProtobufSerializer"
      chill  = "com.twitter.chill.akka.AkkaSerializer"
    }

    serialization-bindings {
      "org.softnetwork.akka.message.package$Command" = chill
      "org.softnetwork.akka.message.package$CommandResult" = chill
      "org.softnetwork.akka.message.package$Event" = chill
      "org.softnetwork.akka.model.package$State" = chill

      "org.softnetwork.akka.model.package$Timestamped" = proto
      "org.softnetwork.akka.message.package$ProtobufEvent" = proto # protobuf events
      "org.softnetwork.akka.model.package$ProtobufDomainObject" = proto # protobuf domain objects

      "org.softnetwork.akka.message.package$CborEvent" = jackson-cbor # cbor events
      "org.softnetwork.akka.model.package$CborDomainObject" = jackson-cbor # cbor domain objects
    }
 ```

### Chill

### Protobuf

### Jackson-cbor
