# Etch Gate

Staff approval for player-supplied audio links on a public server.

Etched lets any player type a URL and have it played to everyone around them — on a disc
from the Etching Table, or live from a Radio block. This mod puts a review step in front
of that. Built for **NeoForge 1.21.1** against **Etched 5.1.0**.

## How it works

Every URL a player submits — etching table *and* radio, they share one packet — is held
until a staff member clears it. The player is told their link was sent for review; staff
online get a chat line with clickable **[Approve]** / **[Deny]** buttons.

An approval is per-link and applies to everyone, so a popular track is only reviewed once.
Staff skip the gate entirely: ops (permission level 2) and, in singleplayer or on a LAN world,
the world's owner (who isn't an op when cheats are off). Each player can have 3 links waiting.

## Commands

All are for staff (see above) and the server console.

| Command | What it does |
|---|---|
| `/etchgate list` | Pending requests, each with Approve/Deny buttons |
| `/etchgate approve <id>` | Approve a pending request |
| `/etchgate deny <id>` | Deny a pending request |
| `/etchgate allow <url>` | Approve a link directly, without a request |
| `/etchgate block <url>` | Deny a link directly |
| `/etchgate forget <url>` | Remove a link from both lists — the next player to try it raises a fresh request |
| `/etchgate approved` / `denied` | Show the lists |
| `/etchgate blockhost <host>` | Block an entire host, e.g. `example.com` |
| `/etchgate unblockhost <host>` | Undo that |
| `/etchgate hosts` | Show blocked hosts |

The lists are saved with the world (`data/etchgate.dat`), so they survive restarts.

## Two decisions worth knowing about

**Approvals match the link exactly; denials are broader.** That asymmetry is deliberate.
The gate checks one string and Etched then fetches it, so if approval keys were tidied up
at all — trailing slash removed, `user@` stripped, port normalised — someone could get
`https://host/song` approved with innocent audio and then serve whatever they liked at
`https://host/song/`, which the tidied key would treat as already approved. Approvals are
therefore byte-exact. Denials go the other way: a narrow deny lets someone re-submit
`?a`, `?b`, `?c` forever, each a new link and a new ping for staff, so `blockhost` exists
to shut down a whole domain at once.

**Denying revokes.** A denied link stops playing on discs that already exist, including
ones crafted before the deny, copies made with Etched's disc-cloning recipe, and discs
tucked inside album covers. Without that, "deny" would only ever mean "no new discs".

## What this does not stop

Being straight about the edges:

- **Music label text.** Artist and title are typed by the player and shown to everyone
  nearby as the now-playing toast. That is a separate Etched packet with no gate on it —
  a chat/name filter mod is the right tool.
- **Creative mode.** The gate hooks the packet a survival player uses. Anyone in creative
  can write the data component directly. Fine if creative means staff on your server.
- **Approved links whose content changes later.** Approval covers a URL, not the bytes
  behind it. Whoever owns the host can swap the file afterwards. `blockhost` is the answer
  when that happens.
- **The server fetches approved URLs.** Etched has the *server* make an HTTP request to
  check the link before it will build a disc. The gate means that only happens for links
  staff cleared, which is a real improvement over stock Etched — but be aware it happens.

## Install

Server-side: put it in the server's `mods` folder next to Etched 5.x. Players don't need it (it adds no
packets, blocks or items). In singleplayer it works as well; you are the staff there.

## Changes

- **1.0.1** - the world's owner counts as staff in singleplayer/LAN (without cheats your own links used to wait
  for staff that wasn't there); the "approved" message is worded for the Radio too; MIT licence and a logo
  in the jar. Tested on a dedicated server with players who aren't ops (Etching Table, Radio, approve, deny,
  revoke, host blocks, the 3-link limit, saving).
- **1.0.0** - first version.

## Building

Java 21. Etched isn't on a public Maven repository, so it's compiled against as a local, compile-only jar
(never bundled; the copy in your mods folder is what loads at runtime):

1. Put the Etched 5.x jar (e.g. `etched-5.1.0.jar`) in `libs/`.
2. Run `gradlew build` on Windows, or `sh gradlew build` on Linux/macOS.

The build also looks in `.minecraft/mods` and `Downloads`, and only accepts a jar that really contains the class
this mod hooks. Or point it at one: `gradlew build -Petched_jar=/path/to/etched-5.1.0.jar`.

Output lands in `build/libs/etchgate-1.0.1.jar`. Install it alongside Etched: it's required, and the mod
won't load without it.

## License

MIT, see [LICENSE](LICENSE).
