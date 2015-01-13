# CoinJoin
Java Implementation of Server and Client for CoinJoin Algorithm

# Dependencies
* For the Server: Any Local Bitcoin Node (https://bitcoin.org/en/download)
* Java 8 with JavaFX (http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)

# Demonstration
* Start the local Bitcoin node, then start server and two clients:
* ```cd server; java -jar CoinJoin_Server.jar```
* ```cd ../client; java -jar CoinJoin_Client.jar```
* ```cd ..; java -jar CoinJoin_Client.jar```
* Wait for wallets to synchronize with Bitcoin network
* Start mixing coins!

# Hard-Coded Values
* ip = "localhost"
* port = 4444
* CHUNK_SIZE = 0.01 BTC
* MIN_PARTICIPANTS = 2
