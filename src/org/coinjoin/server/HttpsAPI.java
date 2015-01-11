package org.coinjoin.server;

import java.io.BufferedWriter;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;

public class HttpsAPI {
	
	public static MainServer server;
	
	/**
	 * @param output: Write the RSA Public Key for a currently OEPN transaction.
	 * 	and the ID of that transaction.
	 * @return HTTP Status (200 on Success)
	 */
	public static int getPublicRSA(BufferedWriter output){
		// TODO: Complete
		return 200;
	};
	
	/**
	 * If the Transaction is OPEN and 
	 * 	the difference between Input and Change Address is no smaller than CHUNK_SIZE, 
	 * 	add the input and change output to the corresponding
	 * 	transaction and sign the blinded output address.
	 * Otherwise, return the status of the Transaction and an error message.
	 * @param txid: Transaction ID
	 * @param outputAddr: Blinded output address for signing.
	 * @param inputs: A transaction containing the unsigned input and the change output.
	 * @param output: Write RSA signed output address if successful.
	 * @return HTTP Status (200 on Success)
	 */
	public static int registerInput(BufferedWriter output, String txid, Transaction inputs, String outputAddr){
		// TODO: Complete
		return 200;
	};
	
	/**
	 * If the Transaction is PENDING or OPEN and the signature is correct for the key
	 * 	corresponding to the txid, add the outputAddress to that transaction.
	 * 
	 * If the Transaction is PENDING and the number of outputs equals the number of
	 * 	inputs, return the fully signed transaction and set the transaction to SIGNING.
	 * 
	 * If the Transaction is SIGNING or FAILED, return status 409 (Conflict).
	 * 
	 * @param output: On success, write the completed transaction hash for signing , else
	 * 	write the status of the Transaction (OPEN, PENDING, SIGNING, FAILED, CLEARED) and an
	 * 	error message.
	 * @param txid: Transaction ID
	 * @param outputAddr: Unblinded output address to add to transaction.
	 * @param outputSig: RSA Signature of the Output Address.
	 * @return HTTP Status (200 on Success)
	 */
	public static int registerOutput(BufferedWriter output, String txid, String outputAddr, String outputSig){
		return 200;
	}
	
	/**
	 * If the transaction is SIGNING, verify the signed input, then add the
	 * 	input to the transaction and remove the unsigned input. If all inputs
	 * 	are signed. Broadcast the transaction and set its stateto BROADCAST.
	 * @param output: Write the status of the Transaction (OPEN, PENDING, SIGNING, FAILED, BROADCAST, CLEARED)
	 * 	and an error message.
	 * @param txid: Transaction ID
	 * @param signedInput: Signed Input for the Transaction
	 * @return HTTP Status (200 on Success)
	 */
	public static int registerSignature(BufferedWriter output, String txid, TransactionInput signedInput, String signature){
		// TODO: Complete
		return 200;
	}
	
	/**
	 * Return the transaction status. If the transaction doesn't exist, its status is CLEARED.
	 * @param output: Write the Status of the transaction (OPEN, PENDING, SIGNING, FAILED, BROADCAST, CLEARED)
	 * 	and an optional error message.
	 * @param txid: Transaction ID
	 * @return HTTP Status (200 on Success)
	 */
	public static int txidStatus(BufferedWriter output, String txid) {
		// TODO: Complete
		return 200;
	}

}
