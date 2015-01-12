package org.coinjoin.server;

import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.Wallet.SendRequest;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.store.UnreadableWalletException;
import org.coinjoin.util.RSABlindSignUtil;

public class MainServer {

	public final static long CHUNK_SIZE = 1000000;
	public final static int MIN_PARTICIPANTS = 3;
	private final static NetworkParameters params = new TestNet3Params();
	
	private boolean finished;
	
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
		public int regOutputs;
		public int signedInputs;
	}
	
	// Map TXIDs to Transactions
	private HashMap<Integer, TxWrapper> transactionMap;
	// Incoming HTTPS Server
	private SSLListener httpsServer;
	
	// BTC Network Interaction Classes
	private PeerGroup peerGroup;
	private Wallet wallet;
	private BlockChain bChain;
	private File walletFile;
	
	
	@SuppressWarnings("deprecation")
	public MainServer(int port, File walletFile, File blockFile) {
		httpsServer = new SSLListener(port);
		transactionMap = new HashMap<Integer, TxWrapper>();
		this.walletFile = walletFile;
		
		// Set up Local Wallet
		wallet = null;
		if (walletFile.exists())
			try {
				System.out.println("Reading Wallet from File: " + walletFile.getName());
				wallet = Wallet.loadFromFile(walletFile);
			} catch (UnreadableWalletException e1) {
				e1.printStackTrace();
				System.exit(1);
			}
		else {
			System.out.println("Creating new wallet.");
			wallet = new Wallet(params);	
		}
		wallet.autosaveToFile(walletFile, 1000, TimeUnit.MILLISECONDS, null);
		
		// Set Up Local SPV BlockChain and BlockStore
		System.out.println("Setting up blockchain from File: " + blockFile.getName());
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
		System.out.println("Setting Up PeerGroup Connections...");
		peerGroup = new PeerGroup(params, bChain);
		peerGroup.addWallet(wallet);
		peerGroup.start();
		
		System.out.println("Bitcoin Initialized! Send Fee Donations to: " + wallet.currentReceiveAddress().toString());
		
	}
	
	public Integer currentOpenID() {
		for (int txid : transactionMap.keySet()) {
			if (transactionMap.get(txid).status == TxStatus.OPEN)
				return txid;
		}
		return null;
	}
	
	/**
	 * Does one final verification, broadcasts the transaction, and sends it on
	 * 	its merry way!
	 * @param wrapper: Previously locked TxWrapper from transactionMap
	 * @return null on success, or error message
	 */
	public String broadcastTransaction(TxWrapper wrapper) {
		try {
			wrapper.tx.verify();
			for(TransactionInput i : wrapper.tx.getInputs())
				i.verify();
		} catch (Exception e) {
			e.printStackTrace();
			return e.toString();
		}
		peerGroup.broadcastTransaction(wrapper.tx);
		wrapper.status = TxStatus.BROADCAST;
		return null;
	}
	
	/**
	 * Calculates the proper fee for the (already locked) transaction and adds/signs the fee.
	 * @param wrapper: a previously locked transaction in transactionMap
	 * @return: null on Success, or an Error Message
	 */
	public String feeTransaction(TxWrapper wrapper) {
		// Calculate current fee from users.
		Coin fee = wrapper.tx.getFee();
		
		// Calculate minimum fee for transaction
		long minFee = (wrapper.tx.bitcoinSerialize().length / 1000) * 10000;
		
		ArrayList<TransactionInput> inputsToSign = new ArrayList<TransactionInput>();
		// Loop through wallet looking for fee money
		for (TransactionOutput t : wallet.calculateAllSpendCandidates(true)) {
			if(minFee <= fee.value) break;
			inputsToSign.add(wrapper.tx.addInput(t));
			fee = wrapper.tx.getFee();
		}
		if (minFee > fee.value) {
			// Not enough fee in wallet!
			return "Cannot cover transaction fee!";
		}
		
		// Add change for fee money.
		wrapper.tx.addOutput(Coin.valueOf(fee.value - minFee), wallet.currentReceiveAddress());
		
		// Sign fee inputs
		wallet.signTransaction(SendRequest.forTx(wrapper.tx));
		
		// Verify fee inputs
		for (TransactionInput i : inputsToSign)
			try {
				i.verify();
			} catch (ScriptException e) {
				e.printStackTrace();
				return e.toString();
			}
		
		return null;
	}
	
	public void start() {
		httpsServer.start();
		finished = false;
		
		TxWrapper currentTx = new TxWrapper();
		
		currentTx.rsa = RSABlindSignUtil.freshRSAKeyPair();
		currentTx.status = TxStatus.OPEN;
		currentTx.tx = new Transaction(params);
		currentTx.mutex = new ReentrantLock();
		currentTx.statusTime = 0;
		currentTx.regOutputs = 0;
		currentTx.signedInputs = 0;
		
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
							
							currentTx.rsa = RSABlindSignUtil.freshRSAKeyPair();
							currentTx.status = TxStatus.OPEN;
							currentTx.tx = new Transaction(params);
							currentTx.mutex = new ReentrantLock();
							currentTx.statusTime = 0;
							currentTx.regOutputs = 0;
							currentTx.signedInputs = 0;
							
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
	
	@SuppressWarnings("deprecation")
	public void shutdown() {
		finished = true;
		// Shutdown Services
		try {
			wallet.saveToFile(walletFile);
			System.out.println("Saved wallet to File: " + walletFile.getAbsolutePath());
			peerGroup.stopAndWait();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		
		final MainServer server = new MainServer(4444, new File("wallet.dat"), new File("blockchain.dat"));
		SSLAPI.server = server;
		
		Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                server.shutdown();
            }
        });
		
		server.start();
	}

}
