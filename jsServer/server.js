// these settings can be changed for testing purposes
const VERSION = "1.5"
const PORT = 1337
const BIND_ADDRESS = "127.0.0.1" // use '0.0.0.0' to accept remote connections, e.g. on VPS
const SHOULD_PING = true
const PING_FREQ_MS = 10_000
const PING_TIMEOUT_MS = 3_000
const MAX_PENDING = 1024

import net from "net"

const server = net.createServer((socket) => {
  // accept socket connection from new client
  clients.add(new Client(socket))
})

console.log(`Starting server version ${VERSION} on port ${PORT}`)
console.log("Press Ctrl-C to quit the server")
server.listen(PORT, BIND_ADDRESS)

// level 1 commands
const BROADCAST = "BROADCAST"
const BROADCAST_REQ = "BROADCAST_REQ"
const DSCN = "DSCN"
const WELCOME = "WELCOME"
const LOGIN = "LOGIN"
const PING = "PING"
const PONG = "PONG"
const BYE = "BYE"
const LEFT = "LEFT"

const UNKNOWN_COMMAND = "UNKNOWN_COMMAND"
const PARSE_ERROR = "PARSE_ERROR"
const LOGIN_RESP = "LOGIN_RESP"
const BYE_RESP = "BYE_RESP"
const BROADCAST_RESP = "BROADCAST_RESP"
const PONG_ERROR = "PONG_ERROR"

// match a line of text, including the line separator character(s)
const LINE = /([^\n\r]*)(?:\r\n|\r|\n)/g //follows the rules of Java BufferedReader.readLine()
// test validity of username
const USERNAME = /^[A-Z0-9_]{3,14}$/i

// all clients
const clients = new Set()
// map unique user names to clients
const users = Object.create(null)

/**
 * @param {string} message
 * @returns {[string, Object]} command and payload
 * @throws {SyntaxError} When JSON could not be parsed
 */
function parseMessage(message) {
  const command = message.split(" ", 1)[0]
  let payload = message.substring(command.length + 1)
  if (payload === "") {
    //accept empty payload
    payload = "{}"
  }
  const parsedPayload = JSON.parse(payload)
  return [command, parsedPayload]
}

function printStats() {
  console.log(`${clients.size} client(s) / ${Object.keys(users).length} user(s)`)
}

// client object encapsulates socket connection with a client
class Client {
  // socket connection with client
  #socket
  // unique user name (empty if client hasn't been identified yet)
  #username
  // pending data has not yet been processed
  #pending
  // (interval) timer to initiate heartbeat
  #pingTimer
  // timer to detect PONG timeout (nonzero if timer is running)
  #pongTimer

  /**
   * @param {net.Socket} socket
   */
  constructor(socket) {
    this.#socket = socket
    this.#username = ""
    this.#pending = ""
    this.#pingTimer = 0
    this.#pongTimer = 0
    // send and receive UTF-8 strings
    socket.setEncoding("utf8")
    // install event handlers
    socket.on("close", this.#handleCloseEvent.bind(this))
    socket.on("data", this.#handleDataEvent.bind(this))
    socket.on("end", this.#handleEndEvent.bind(this))
    socket.on("error", this.#handleErrorEvent.bind(this))
    // server starts with INIT message

    this.#sendMessage(WELCOME, { msg: `Welcome to the server ${VERSION}` })
  }

  get #remoteConnection() {
    const { remoteAddress, remotePort } = this.#socket
    return remoteAddress ? `${remoteAddress}:${remotePort}` : ""
  }

  /**
   * @param {string} text
   */
  #log(text) {
    console.log(`${this.#remoteConnection} (${this.#username}) ${text}`)
  }

  /**
   * @param {boolean} hadError
   */
  #handleCloseEvent(hadError) {
    if (hadError) {
      this.#log("closing connection due to transmission error")
    }
    // clean up when client resources
    clearInterval(this.#pingTimer)
    clearTimeout(this.#pongTimer)
    delete users[this.#username]
    for (const username in users) {
      users[username].#sendMessage(LEFT, { username: this.#username })
    }
    clients.delete(this)
    this.#log("removed client")
    printStats()
  }

  /**
   * @param {string} data
   */
  #handleDataEvent(data) {
    let unprocessed = 0
    this.#pending += data
    for (const match of this.#pending.matchAll(LINE)) {
      // first group captures a single line, without the line separator characters
      this.#processMessage(match[1])
      // advance to start of next line, skipping over line separator characters
      unprocessed = match.index + match[0].length
    }
    // keep unprocessed data as pending for next data event
    this.#pending = this.#pending.substring(unprocessed)
    // avoid denial-of-service by exhausting memory
    if (this.#pending.length > MAX_PENDING) {
      this.#log("too many pending characters")
      this.#sendMessage(DSCN, { reason: 7001 })
      this.#socket.destroy()
    }
  }

  #handleEndEvent() {
    this.#log("client closed connection unexpectedly")
    this.#socket.destroy()
  }

  /**
   * @param {Error} error
   */
  #handleErrorEvent(error) {
    this.#log(error.stack ?? error.message)
  }

  /**
   * @param {string} header
   * @param {object} body
   */
  #sendMessage(header, body) {
    body = JSON.stringify(body)
    this.#log(`<-- ${header} ${body}`)
    // write message with trailing line separator
    this.#socket.write(`${header} ${body}\n`)
  }

  /**
   * @param {string} header
   */
  #sendMessageNoPayload(header) {
    this.#log(`<-- ${header}`)
    // write message with trailing line separator
    this.#socket.write(`${header}\n`)
  }

  /**
   * @param {string} message
   *
   */
  #processMessage(message) {
    this.#log(`--> ${message}`)
    try {
      const [command, payload] = parseMessage(message)
      switch (command) {
        case BROADCAST_REQ:
          this.#processBroadcast(payload)
          break
        case LOGIN:
          this.#processLogin(payload)
          break
        case PONG:
          this.#processPong(payload)
          break
        case BYE:
          this.#processBye(payload)
          break
        default:
          this.#sendMessageNoPayload(UNKNOWN_COMMAND)
      }
    } catch (e) {
      //console.log(e)
      this.#sendMessageNoPayload(PARSE_ERROR)
    }
  }

  /**
   * @param {object} payload
   */
  #processLogin(payload) {
    let username = payload["username"] || ""
    if (this.#username) {
      // cannot login twice
      this.#sendMessage(LOGIN_RESP, { status: "ERROR", code: 5002 })
    } else if (!USERNAME.test(username)) {
      // username must be syntactically valid
      this.#sendMessage(LOGIN_RESP, { status: "ERROR", code: 5001 })
    } else if (users[username]) {
      // username already used by other client
      this.#sendMessage(LOGIN_RESP, { status: "ERROR", code: 5000 })
    } else {
      this.#username = username
      users[username] = this
      if (SHOULD_PING) {
        this.#startHeartbeat()
      }
      this.#sendMessage(LOGIN_RESP, { status: "OK" })
      printStats()
    }
  }

  /**
   * @param {string} payload
   */
  #processBroadcast(payload) {
    let message = payload["message"] || ""
    if (!this.#username) {
      // need to login first
      this.#sendMessage(BROADCAST_RESP, { status: "ERROR", code: 6000 })
    } else {
      for (const username in users) {
        if (username === this.#username) {
          // confirm broadcast to this user
          this.#sendMessage(BROADCAST_RESP, { status: "OK" })
        } else {
          // broadcast from this user to other user
          users[username].#sendMessage(BROADCAST, { username: this.#username, message: message })
        }
      }
    }
  }

  /**
   * @param {string} payload
   */
  #processBye(_payload) {
    this.#sendMessage(BYE_RESP, { status: "OK" })
    this.#socket.destroy()
  }

  /**
   * @param {string} payload
   */
  #processPong(_payload) {
    if (!this.#pongTimer) {
      this.#sendMessage(PONG_ERROR, { code: 8000 })
    } else {
      this.#log("heartbeat success")
      clearTimeout(this.#pongTimer)
      // reset timer after clearing
      this.#pongTimer = 0
    }
  }

  #startHeartbeat() {
    this.#log("hearbeat initiated")
    this.#pingTimer = setInterval(() => {
      this.#sendMessageNoPayload(PING)
      this.#pongTimer = setTimeout(() => {
        this.#log("heartbeat failure")
        this.#sendMessage(DSCN, { reason: 7000 })
        this.#socket.destroy()
      }, PING_TIMEOUT_MS)
    }, PING_FREQ_MS)
  }
}
