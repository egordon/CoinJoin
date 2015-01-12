package org.coinjoin.server;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.coinjoin.server.MainServer.TxStatus;
import org.coinjoin.server.MainServer.TxWrapper;
import org.coinjoin.server.SSLListener.SSLStatus;
import org.coinjoin.util.RSABlindSignUtil;

public class SSLAPI {
	
	public static MainServer server;
	
	/**
	 * @param output: Write the RSA Public Key for a currently OEPN transaction.
	 * 	and the ID of that transaction.
	 * @return Status
	 * @throws IOException 
	 */
	public static SSLStatus getPublicRSA(BufferedOutputStream output) throws IOException{
		Integer currentOpenID = server.currentOpenID();
		if (currentOpenID == null) {
			output.write("Error: no currently open transaction. Please try again later.".getBytes());
			return SSLStatus.ERR_SERVER;
		}
		TxWrapper currentOpen = server.lockTransaction(currentOpenID);
		if (currentOpen == null) {
			output.write("Error: transaction does not exist".getBytes());
			return SSLStatus.ERR_CLIENT;
		}
		output.write(currentOpen.rsa.getPublic().getEncoded());
		
		
		server.releaseTransaction(currentOpen);
		return SSLStatus.OK;
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
	 * @return Status
	 */
	public static SSLStatus registerInput(BufferedOutputStream output, int txid, 
			TransactionOutput inputBuilder, TransactionOutput changeOut, byte[] blindedOutput) throws IOException{
		
		// Check that input/change address add up to CHUNK_SIZE or bigger
		if(inputBuilder.getValue().subtract(changeOut.getValue()).value < MainServer.CHUNK_SIZE) {
			output.write("Error: Input and change address do not add upt to chunk size.".getBytes());
			return SSLStatus.ERR_CLIENT;
		}
		
		// Check that transaction is open
		TxWrapper wrapper = server.lockTransaction(txid);
		if (wrapper == null) {
			output.write("Error: transaction does not exist.".getBytes());
			return SSLStatus.ERR_CLIENT;
		} else if (wrapper.status != TxStatus.OPEN) {
			output.write("Error: transaction is not longer open.".getBytes());
			server.releaseTransaction(wrapper);
			return SSLStatus.ERR_SERVER;
		}
		
		// Add Input and Output to Transaction and write signed output
		wrapper.tx.addInput(inputBuilder);
		wrapper.tx.addOutput(changeOut);
		
		byte[] retSig = RSABlindSignUtil.signData(wrapper.rsa.getPrivate(), blindedOutput);
		output.write(retSig);
		
		server.releaseTransaction(wrapper);
		return SSLStatus.OK;
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
	 * @return Status
	 */
	public static int registerOutput(BufferedWriter output, int txid, String outputAddr, String outputSig){
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
