# Server side #
## Hacking ###
To use build HTTP server with `../gradlew build` in `httpserver/` directory.
You may need to install required native dependencies (HTTP server, JSON parser, HTTP client) with

    brew install libmicrohttpd jansson curl

Afterwards, start HTTP server with `./build/bin/host/releaseExecutable/HttpServer.kexe -a <passwd>`.
`-a` switch defines the password used for administrative tasks. 
Then in another terminal build CLI client in `clients/cli` directory with `../../gradlew build`.
Then, CLI queries to the local server (or any server specified with `-s <url>`) could be executed 
using CLI client at `./build/bin/host/releaseExecutable/CliClient.kexe`.

Further on we'll use shortcut `cli` for running command with CLI client.

## Finder protocol ##

Finder commands are executed with `-f` CLI switch, for example try:

    cli -f status

To run privileged commands use `-p <passwd>`, where `passwd` is secret word used to start the server.
For example:

    cli -f start -p <passwd>

Following commands are available in the protocol

| *Command*   | *Privileged* | *Description*           |
|:------------|--------------|:------------------------|
| start       |     Y        | Start new game          |
| stop        |     Y        | Stop current game       |
| addBeacon   |     Y        | Adds new beacon         |
| addQuestion |     Y        | Adds pair hint/info     |
| status      |     N        | Current game status     |
| register    |     N        | Register client(unused) |
| proximity   |     N        | Proximity message       |
| config      |     N        | Gets a question list    |
 
 Handy commands:
 
 Start new game with 5 winners:

    cli -f start -P 'start=5|||Please come to the booth to get prize|||Maybe next time' -p <passwd>

 Add couple questions/hints associated with the code:
    
    cli -f addQuestion -P 'question=1|||Who?|||Me!' -p <passwd>
    cli -f addQuestion -P 'question=2|||Where?|||There!' -p <passwd>
  
 Add couple beacons:

    cli -f addBeacon -P 'beacon=1,beacon1,-20,1' -p <passwd>
    cli -f addBeacon -P 'beacon=2,beacon2,-23,1' -p <passwd>

  Show game config:
 
    cli -f config

 Response will look like:

    Got {"index": 1, "activeBeacons": 2, "winnerCount": 2, "config": [{"code": 1, "question": "Who?", "hint": "Me!"}, {"code": 2, "question": "Where?", "hint": "There!"}], "result": "OK"}

 Server returns list of code/questions/hint pairs, along with recommended index of the question to ask.
 `active` shows how many beacons are currently active. When a person clicks on 
 certain undiscovered letter in the UI he/she shall be shown a question with the `code` assigned to this UI.
 When beacon with the given code is discovered - any `hint` for that code shall be shown. 

 Send proximity message:

    cli -f proximity -P 'proximity=beacon0:-10,beacon1:-20,beacon2:-55' 
 
 Response will look like:
 
    Got {"discovered": [1], "near": [{"code": 2, "strength": 70}], "result": "OK"}
 
 We just found `beacon1`, and are somewhat near to `beacon2`. As we'll get closer to `beacon2` -
 strength field will increase until we'll get to `100`, when it will become discovered.
 
 Mobile client shall periodically send `proximity` messages to the server, and based on response shall
 update rendered data.

 When all beacons (as per `active` in `config` query) are discovered, `register` message
 shall be sent.

 Once client believes it has found all beacons, it should send `register` request (once), like this:

    cli -f register -n "John Smith"

 Response could be

    Got {"message": "You won", "winner": 1, "place": 2, "winnerCount": 2, "result": "OK"}

 or

    Got {"message": "Not now", "winner": 0, "place": 3, "winnerCount": 2, "result": "OK"}

 Second register request during the same game with same cookie will result in error.
