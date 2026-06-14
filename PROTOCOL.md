# KeyBridge wire protocol

This document defines the protocol the app speaks with the paired computer over the
WebSocket connection. It is the contract: the app and the server must agree on the
envelope shape exactly.

Transport is a single WebSocket connection carrying UTF-8 JSON text frames.

## Two planes

The connection carries two kinds of messages:

- **Input / delivery** — the versioned envelope described below. Every keystroke,
  combination, and block of text the app sends travels in an envelope, and the server
  confirms each one with an acknowledgement (`ack`). This is what lets the app show
  *confirmed* delivery instead of guessing: the text field is cleared only once the
  server has acknowledged what was sent.
- **Session control** — connection setup and keep-alive: the handshake the server sends
  on connect, the app's authentication message, and keep-alive ping/pong. These are
  flat JSON objects (keyed by `command` / `type`) and are **not** wrapped in the
  envelope. They are described under [Session control](#session-control).

A receiver tells the two apart by shape: a message that carries `v`, `type`, and
`payload` is an envelope; anything else is session control.

## The envelope

```jsonc
{
  "v": 1,            // protocol version (integer)
  "id": "uuid",      // unique per logical message; idempotency key
  "seq": 0,          // 0-based chunk index within the message
  "total": 1,        // total number of chunks in the message
  "type": "type",    // type | key_press | key_release | key_combo | ack
  "payload": { }     // type-specific body (absent on ack)
}
```

| Field | Meaning |
|---|---|
| `v` | Envelope version. This protocol is `v: 1`. The server rejects any other `v` with an error ack and does not apply the message. |
| `id` | Identifies one logical message (a key, a combo, or one block of text). All chunks of the same message share the same `id`. |
| `seq` | Chunk index, `0`-based. A single-chunk message uses `seq: 0`. |
| `total` | How many chunks make up the message. A single-chunk message uses `total: 1`. |
| `type` | The input family (or `ack`). |
| `payload` | The body for that `type`. |

`v`, `id`, `seq`, `total`, and `type` are required on every input envelope the app
sends.

## Input types

### `type` — type text

```json
{ "v": 1, "id": "…", "seq": 0, "total": 1, "type": "type",
  "payload": { "text": "Hello, World!", "delay_ms": 0 } }
```

`payload.text` is the text to type (full Unicode). `payload.delay_ms` is an optional
per-character delay in milliseconds, driven by the app's typing-delay setting; `0` or
absent means full speed.

### `key_press` / `key_release` — one key down / up

```json
{ "v": 1, "id": "…", "seq": 0, "total": 1, "type": "key_press",
  "payload": { "key": "shift" } }
```

`payload.key` is a key name (see [Supported key codes](README.md#supported-key-codes))
or a single character.

### `key_combo` — a chord

```json
{ "v": 1, "id": "…", "seq": 0, "total": 1, "type": "key_combo",
  "payload": { "keys": ["ctrl", "c"] } }
```

The server presses the keys in order, then releases them in reverse order.

## Acknowledgement

The server sends one ack per chunk it has applied:

```jsonc
{ "v": 1, "type": "ack", "id": "…", "seq": 0, "status": "ok" }
{ "v": 1, "type": "ack", "id": "…", "seq": 0, "status": "error", "error": "…" }
```

- `status: "ok"` — the chunk was applied. When every chunk of a message is acked `ok`,
  the message is delivered, and only then does the app clear the sent text.
- `status: "error"` — the chunk could not be applied; `error` is a human-readable
  reason. The app keeps the input and surfaces the failure so the user can retry.

An ack carries no `payload`. It echoes the `id` and `seq` of the chunk it confirms so
the app can match it to the chunk it sent.

## Long text: chunking and progress

The app splits a long block of text into ordered chunks that share one `id`, with `seq`
running `0 … total-1`, splitting on Unicode code-point boundaries so multi-byte
characters are never cut in half. The server applies the chunks in order and acks each
one as it lands, so the app advances a progress indicator toward `total`.

## Delivery state and bounded retry

The app tracks each message it sends through `sending → delivered → failed`:

- **sending** — at least one chunk is still un-acked; progress reflects acked chunks out
  of `total`.
- **delivered** — every chunk was acked `ok`; the text field clears at this point and
  not before.
- **failed** — a chunk was acked `error`, retries were exhausted, or the connection
  dropped mid-send. The text is retained and the user is offered a retry.

Retries are **bounded and idempotent**. The app resends only un-acked chunks, reusing
the **same** `id` and `seq`, with a small number of attempts and backoff. Because the
server remembers the `(id, seq)` pairs it has already applied and **re-acks duplicates
without applying them again**, a resend can never cause text to be typed twice. (An
earlier design retried without this guarantee, which could double-type on a flaky
connection; per-chunk idempotency on the server is what makes retry safe, and the retry
count is capped so it can never loop unbounded.)

## Session control

These messages are flat (not enveloped) and handle connection lifecycle.

On connect, the server sends a handshake the app reads for its session id and feature
flags:

```json
{ "type": "handshake", "protocol_version": "2.0",
  "features": { "authentication": true, "encryption": true, "compression": true },
  "session_id": "…" }
```

If authentication is enabled, the app authenticates with the token carried in the
pairing QR code:

```json
{ "command": "authenticate", "token": "…" }
```

Keep-alive: the app sends a ping periodically and the server replies with a pong:

```json
{ "command": "ping", "timestamp": 1701234567890 }
```

## Encryption

When message encryption is enabled, the serialized envelope (or control message) is the
plaintext that is encrypted before being sent and decrypted on receipt. The envelope
shape above describes the decrypted message. Encryption is a transport concern layered
around the protocol; it does not change the envelope.
