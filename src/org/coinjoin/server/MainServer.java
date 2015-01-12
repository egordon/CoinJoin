package org.coinjoin.server;

import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.store.UnreadableWalletException;
import org.coinjoin.rsa.BlindSignUtil;

public class MainServer {

	public final static double CHUNK_SIZE = 0.01;
	public final static int MIN_PARTICIPANTS = 3;
	private final static NetworkParameters params = new TestNet3Params();
	
	public enum TxStatus {
	    OPEN, PENDING, SIGNING, FAILED, BROADCAST, CLEARED
	}
	
	/*
	 * Data class wrapping Transactions
	 */
	public class TxWrapper {
		public Transaction tx;
		public KeyPair rsa;
		public TxStatus status;
		public Lock mutex;
		public int statusTime;
	}
	
	// Map TXIDs to Transactions
	private HashMap<Integer, TxWrapper> transactionMap;
	// Incoming HTTPS Server
	private HttpsListener httpsServer;
	
	// BTC Network Interaction Classes
	private PeerGroup peerGroup;
	private Wallet wallet;
	private BlockChain bChain;
	
	
	@SuppressWarnings("deprecation")
	public MainServer(int port, File walletFile, File blockFile) {
		httpsServer = new HttpsListener(port);
		transactionMap = new HashMap<Integer, TxWrapper>();
		
		// Set up Local Wallet
		wallet = null;
		if (walletFile.exists())
			try {
				wallet = Wallet.loadFromFile(walletFile);
			} catch (UnreadableWalletException e1) {
				e1.printStackTrace();
				System.exit(1);
			}
		else {
			wallet = new Wallet(params);
			wallet.autosaveToFile(walletFile, 1000, TimeUnit.MILLISECONDS, null);
		}
		
		// Set Up Local SPV BlockChain and BlockStore
		try {
			bChain = new BlockChain(params, wallet, new SPVBlockStore(params, blockFile));
		} catch (BlockStoreException e) {
			e.printStackTrace();
			try {
				wallet.saveToFile(walletFile);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			System.exit(1);
		}
		
		// Create PeerGroup to connect to BTC Network
		peerGroup = new PeerGroup(params, bChain);
		peerGroup.addWallet(wallet);
		peerGroup.start();
		
	}
	
	/**
	 * Calculates the proper fee for the transaction, adds the fee, then
	 * 	broadcasts the transaction to the network and sets its status to BROADCAST.
	 * @param txid: The ID of a fully signed CoinJoin transaction.
	 * @return: null on Success, or an Error Message
	 */
	public String broadcastTransaction(String txid) {
		// TODO: complete
		return null;
	}
	
	public void start() {
		httpsServer.start();
		boolean finished = false;
		
		TxWrapper currentTx = new TxWrapper();
		
		currentTx.rsa = BlindSignUtil.freshRSAKeyPair();
		currentTx.status = TxStatus.OPEN;
		currentTx.tx = new Transaction(params);
		currentTx.mutex = new ReentrantLock();
		currentTx.statusTime = 0;
		
		transactionMap.put(currentTx.rsa.getPublic().hashCode(), currentTx);
		
		// Main Server Loop, executed once per second
		while (!finished) {
			/*
			 * 1. Loop through Transactions, locking each one.
			 * 2. If transaction is OPEN and has at least 3 participants, mark it PENDING,
			 * 		and create a new OPEN transaction.
			 * 3. If transaction has been PENDING for 5 seconds, mark it FAILED.
			 * 4. If transaction has been SIGNING for 5 seconds, mark it FAILED.
			 * 5. If transaction has been FAILED for 5 seconds, erase it from memory.
			 * 6. If the confidence level of a BROADCAST transaction is high, erase it
			 * 	from memory.
			 */
			
			for(TxWrapper wrapper : transactionMap.values()) {
				wrapper.mutex.lock();
				try {
					switch(wrapper.status) {
					case OPEN:
						if (wrapper.tx.getInputs().size() > MIN_PARTICIPANTS) {
							wrapper.status = TxStatus.PENDING;
							
							// Create New Open Transaction
							currentTx = new TxWrapper();
							
							currentTx.rsa = BlindSignUtil.freshRSAKeyPair();
							currentTx.status = TxStatus.OPEN;
							currentTx.tx = new Transaction(params);
							currentTx.mutex = new ReentrantLock();
							currentTx.statusTime = 0;
							
							transactionMap.put(currentTx.rsa.getPublic().hashCode(), currentTx);
						}
						break;
					case PENDING:
						if (wrapper.statusTime >= 5)
							wrapper.status = TxStatus.FAILED;
						else wrapper.statusTime++;
						break;
					case SIGNING:
						if (wrapper.statusTime >= 5)
							wrapper.status = TxStatus.FAILED;
						else wrapper.statusTime++;
						break;
					case FAILED:
						if (wrapper.statusTime >= 5) {
							transactionMap.remove(wrapper.rsa.getPublic().hashCode());
						}
						else wrapper.statusTime++;
						break;
					case BROADCAST:
						switch(wrapper.tx.getConfidence().getConfidenceType()) {
						case BUILDING: // Transaction Succeeded!
							transactionMap.remove(wrapper.rsa.getPublic().hashCode());
							break;
						case DEAD: // Transaction Failed
							wrapper.status = TxStatus.FAILED;
							break;
						default:
							break;
						}
						break;
					default:
						break;
					}
					
				} finally {
					wrapper.mutex.unlock();
				}
			}
			
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Locks transaction (to maintain thread safety) and returns it.
	 * @param txid: Transaction ID
	 * @return TxWrapper corresponding to transaction id.
	 */
	public TxWrapper lockTransaction(int txid) {
		TxWrapper wrapper = transactionMap.get(txid);
		wrapper.mutex.lock();
		return wrapper;
	}
	
	/**
	 * Unlocks transaction so it can be used by another thread.
	 * @param txid: Transaction ID
	 */
	public void releaseTransaction(TxWrapper wrapper) {
		wrapper.mutex.unlock();
	}
	
	public static void main(String[] args) {
		
		
		MainServer server = new MainServer(443, new File("wallet.dat"), new File("blockchain.dat"));
		HttpsAPI.server = server;
		
		server.start();
	}

}
