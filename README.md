# CameraServer

The **eyes** of the [Distributed Chess System](../). Opens a webcam, grabs frames with
**OpenCV**, and streams them over TCP sockets to any client — the board, the players, any feed
— so the visual data can flow into the neural layers (Test3 → Test4) or trainingGround's
neural processing.

## What it does

- `cam.WebcamServer` — opens a camera via OpenCV `VideoCapture`, encodes frames, and serves
  them over a `ServerSocket`. It listens on **port 5000**, with a second stream on **5050**.
- `cam.WebcamClient` — opens a `Socket` to the host and displays/consumes the incoming frames
  (a small Swing viewer).

## Dependencies

- A **JDK** + **Maven**.
- **OpenCV Java bindings** (`org.opencv.*`) **plus the native library** must be on the path.
  > Note: the OpenCV dependency is *not* declared in `pom.xml`; provide it via your local
  > OpenCV install (jar on the classpath + `-Djava.library.path` to the native `.so`/`.dll`),
  > or through the Eclipse `.classpath`. `System.loadLibrary` loads the native core at startup.

## Build & run

```bash
mvn compile

# terminal 1 — the server
java -cp target/classes cam.WebcamServer

# terminal 2 — a client
java -cp target/classes cam.WebcamClient
```

(If running outside Eclipse, append the OpenCV jar to the classpath and point
`-Djava.library.path` at the native library.)

## Structure

```
CameraServer/
├── pom.xml                       Maven (groupId: CameraServer)
└── src/main/java/cam/
    ├── WebcamServer.java         OpenCV capture → socket stream (ports 5000 / 5050)  [main]
    └── WebcamClient.java         socket → frame viewer                                [main]
```

---

*Part of the Distributed Chess System: the camera sees, the SOM layers organize, the vector
server relays, the board renders.*
