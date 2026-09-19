# Architecture

## Request lifecycle

TCP client -> ServerSocket -> client worker -> RESP decoder -> command dispatcher -> RedisStore/AOF -> RESP encoder -> TCP client.

1. RedisServer accepts a TCP connection.
2. A worker from a fixed ExecutorService owns that client session.
3. RespDecoder converts RESP2-style bytes into command arguments.
4. CommandDispatcher validates the command and invokes the datastore.
5. RespEncoder serializes the result.
6. Successful mutating commands are appended to the AOF.
7. On restart, the AOF is replayed through the same command dispatcher.

## Concurrency model

Each client is handled by a worker in a fixed-size thread pool. The primary key/value map is a ConcurrentHashMap.

Read-modify-write operations such as INCR and list mutations use per-key synchronization rather than one global datastore lock. This keeps consistency local to the affected key while allowing unrelated keys to proceed concurrently.

## Storage model

The engine currently supports strings and linked lists of strings. The Value sealed interface makes the supported type boundary explicit.

## Expiration

Each expiring key stores a monotonic deadline derived from System.nanoTime(). Expiration is enforced lazily on access and by a daemon cleanup task.

## Persistence

The append-only file stores the public command representation in RESP format. Recovery can therefore replay commands through the normal dispatcher instead of understanding internal Java objects.

The current implementation flushes each appended command but does not fsync after every write. A sudden process or machine failure can therefore lose the most recently buffered data.

## Protocol boundary

The protocol package is independent of the datastore. This separation keeps wire framing and command semantics independently evolvable.

## Deliberate limitations

This is an educational Redis-inspired server, not a production Redis replacement. It does not currently implement clustering, replication, TLS, ACLs, RESP3, Lua, transactions, or advanced eviction policies.
