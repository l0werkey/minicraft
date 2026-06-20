![Minicraft](/media/banner.png)

**Minicraft** is a first-person 3D block game written in Kotlin, built from scratch using only the Java standard library. No game engine, no OpenGL - just Java AWT and math.

> **Download the latest release from the [Releases](https://github.com/l0werkey/minicraft/releases) page.**

---

## What it is

You spawn in a procedurally generated infinite world. Walk around, place blocks, break blocks, explore - that's it.

The world generates infinitely in all directions as you explore.

## Features

- **Infinite procedural world** - terrain, trees, and structures generate on the fly as you move
- **Full 3D raycasting renderer** - multithreaded (to simulate a gpu), runs at 60fps on modern hardware
- **Block interaction** - left click to break, right click to place
- **Physics** - gravity, jumping, coyote time, jump buffering, air strafing
- **Structures** - **2** randomly generating structures in the world
- **Ambient audio** - procedurally generated background music

## Controls

| Key / Button | Action |
|---|---|
| `W A S D` | Move |
| `Space` | Jump |
| Mouse | Look |
| Left Click | Break block |
| Right Click | Place block |

## Running

Requires **Java 17+**.

Download `minicraft-VERSION.jar` from [Releases](https://github.com/l0werkey/minicraft/releases) and run:

```sh
java -jar minicraft-VERSION.jar
```

Replace `VERSION` with the version you downloaded.

## Building from source

Requires **JDK 17+** and **Maven**.

```sh
mvn package
```

The output jar will be at `target/minicraft-VERSION.jar`.