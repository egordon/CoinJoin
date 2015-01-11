package org.coinjoin.server;

import java.util.HashMap;

import org.bitcoinj.core.Transaction;

import local.rsa.RSAKeyPair;

public class MainServer {

	public final static double CHUNK_SIZE = 0.01;
	
	public enum TxStatus {
	    OPEN, PENDING, SIGNING, FAILED, CLEARED
	}
	
	/*
	 * Data class wrapping Transactions
	 */
	public class TxWrapper {
		public Transaction tx;
		public RSAKeyPair rsa;
		public TxStatus status;
	}
	
	// Map TXIDs to Transactions
	private HashMap<String, TxWrapper> transactionMap;
	private HttpsListener httpsServer;
	
	public MainServer(int port) {
		httpsServer = new HttpsListener(port);
		transactionMap = new HashMap<String, TxWrapper>();
		
		// TODO: Initialize BitcoinJ Wallet
	}
	
	public void start() {
		httpsServer.start();
		boolean finished = false;
		
		// TODO: Create First OPEN Transaction
		
		// Main Server Loop, executed once per second
		while (!finished) {
			/*
			 * TODO:
			 * 1. Loop through Transactions, locking each one.
			 * 2. If transaction is OPEN and has at least 3 participants, mark it PENDING,
			 * 		and create a new OPEN transaction.
			 * 3. If transaction has been PENDING for 5 seconds, mark it FAILED.
			 * 4. If transaction has been SIGNING for 10 seconds, mark it FAILED.
			 * 5. If transaction has been FAILED for 5 seconds, erase it from memory.
			 */
			
			
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
	public TxWrapper lockTransaction(String txid) {
		// TODO: Complete
		return null;
	}
	
	/**
	 * Unlocks transaction so it can be used by another thread.
	 * @param txid: Transaction ID
	 */
	public void releaseTransaction(String txid) {
		// TODO: Complete
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		MainServer server = new MainServer(443);
		HttpsAPI.server = server;
		
		server.start();
	}

}
