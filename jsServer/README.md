# README

This is the level 1 implementation of the server for the module internet technology.

To run the server make sure to install the latest version of nodejs:
https://nodejs.org/en/download/

Open the command line terminal and type:
```
$ node sever.js
```

## Parameters

The server.js has two parameters that can be changed for development purposes:

- PORT: the port on which the server listens (default port 1337 is used)
- SHOULD_PING: a boolean which determines whether the server should send a PING periodically (default is true). When starting the development of the client it is useful to set it to false in order to keep the connection open.
