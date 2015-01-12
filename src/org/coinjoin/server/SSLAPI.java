package org.coinjoin.server;

import java.io.IOException;
import java.io.ObjectOutputStream;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
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
	public static SSLStatus getPublicRSA(ObjectOutputStream output) throws IOException{
		Integer currentOpenID = server.currentOpenID();
		if (currentOpenID == null) {
			output.writeObject("Error: no currently open transaction. Please try again later.");
			return SSLStatus.ERR_SERVER;
		}
		TxWrapper currentOpen = server.lockTransaction(currentOpenID);
		if (currentOpen == null) {
			output.writeObject("Error: transaction does not exist");
			return SSLStatus.ERR_CLIENT;
		}
		output.writeObject(currentOpen.rsa.getPublic());
		
		
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
	public static SSLStatus registerInput(ObjectOutputStream output, int txid, 
			TransactionOutput inputBuilder, TransactionOutput changeOut, byte[] blindedOutput) throws IOException{
		
		// Check that input/change address add up to CHUNK_SIZE or bigger
		if(inputBuilder.getValue().subtract(changeOut.getValue()).value < MainServer.CHUNK_SIZE) {
			output.writeObject("Error: Input and change address do not add upt to chunk size.");
			return SSLStatus.ERR_CLIENT;
		}
		
		// Check that transaction is open
		TxWrapper wrapper = server.lockTransaction(txid);
		if (wrapper == null) {
			output.writeObject("Error: transaction does not exist.");
			return SSLStatus.ERR_CLIENT;
		} else if (wrapper.status != TxStatus.OPEN) {
			output.writeObject("Error: transaction is not longer open.");
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
	 * If  the number of outputs equals the number of
	 * 	inputs, return the fully signed transaction and set the transaction to SIGNING.
	 * 
	 * If the Transaction is SIGNING, return the fully signed transaction.
	 * 
	 * If the Transaction is OPEN or FAILED, return error.
	 * 
	 * @param output: On success, write the completed transaction hash for signing , else
	 * 	write the status of the Transaction (OPEN, PENDING, SIGNING, FAILED, CLEARED) and an
	 * 	error message.
	 * @param txid: Transaction ID
	 * @param outputAddr: Unblinded output address to add to transaction.
	 * @param outputSig: RSA Signature of the Output Address.
	 * @return Status
	 */
	public static SSLStatus registerOutput(ObjectOutputStream output, int txid, Address outputAddr, byte[] outputSig) throws IOException{
		
		// Check Transaction Status
		TxWrapper wrapper = server.lockTransaction(txid);
		if (wrapper == null) {
			output.writeObject("Error: transaction does not exist.");
			return SSLStatus.ERR_CLIENT;
		} else if (wrapper.status == TxStatus.OPEN || wrapper.status == TxStatus.FAILED) {
			output.writeObject("Error: transaction is not PENDING or SIGNING.");
			server.releaseTransaction(wrapper);
			return SSLStatus.ERR_SERVER;
		}
		
		// Check Output Signature
		if (!RSABlindSignUtil.verifyData(wrapper.rsa.getPublic(), outputAddr.getHash160(), outputSig)) {
			output.writeObject("Error: Signature does not match given txid.");
			server.releaseTransaction(wrapper);
			return SSLStatus.ERR_CLIENT;
		}
		
		// If PENDING, add output to transaction.
		if (wrapper.status == TxStatus.PENDING) {
			wrapper.tx.addOutput(Coin.valueOf(MainServer.CHUNK_SIZE), outputAddr);
			wrapper.regOutputs++;
			if (wrapper.regOutputs >= wrapper.tx.getInputs().size()) {
				String err = server.feeTransaction(wrapper);
				if (err != null) {
					wrapper.status = TxStatus.FAILED;
					System.err.println("Error: Not enough fee for transaction.");
					output.writeObject("Error: transaction has FAILED.");
					server.releaseTransaction(wrapper);
					return SSLStatus.ERR_SERVER;
				}
				wrapper.status = TxStatus.SIGNING;
			}
		}
		
		// If SIGNING, return full transaction.
		if (wrapper.status == TxStatus.SIGNING) {
			output.writeObject(wrapper.tx);
		}
		
		server.releaseTransaction(wrapper);
		return SSLStatus.OK;
	}
	
	/**
	 * If the transaction is SIGNING, verify the signed input, then add the
	 * 	input to the transaction and remove the unsigned input. If all inputs
	 * 	are signed. Broadcast the transaction and set its state to BROADCAST.
	 * @param output: Write the status of the Transaction (OPEN, PENDING, SIGNING, FAILED, BROADCAST, CLEARED)
	 * 	and an error message.
	 * @param txid: Transaction ID
	 * @param signedInput: Signed Input for the Transaction
	 * @return HTTP Status (200 on Success)
	 */
	public static SSLStatus registerSignature(ObjectOutputStream output, int txid, int inputIndex, TransactionInput signedInput) throws IOException{
		// Check Transaction Status
		TxWrapper wrapper = server.lockTransaction(txid);
		if (wrapper == null) {
			output.writeObject(TxStatus.CLEARED);
			output.writeObject("Transaction doesn't exist or has been cleared.");
			return SSLStatus.ERR_CLIENT;
		} else if (wrapper.status != TxStatus.SIGNING) {
			output.writeObject(wrapper.status);
			output.writeObject("Error: transaction is not SIGNING.");
			server.releaseTransaction(wrapper);
			return SSLStatus.ERR_SERVER;
		}
		
		// Add signature to current transaction
		TransactionInput unsigned = wrapper.tx.getInput(inputIndex);
		unsigned.setScriptSig(signedInput.getScriptSig());
		try {
			unsigned.verify();
		} catch (Exception e) {
			e.printStackTrace();
			output.writeObject(wrapper.status);
			output.writeObject("Error: signature invalid.");
			server.releaseTransaction(wrapper);
			return SSLStatus.ERR_CLIENT;
		}
		
		wrapper.signedInputs++;
		
		// If all inputs signed, broadcast transaction
		if (wrapper.signedInputs >= wrapper.tx.getInputs().size()) {
			String err = server.broadcastTransaction(wrapper);
			if (err != null) {
				wrapper.status = TxStatus.FAILED;
				System.err.println("Broadcast Error: " + err);
				output.writeObject("Error: transaction has FAILED.");
				server.releaseTransaction(wrapper);
				return SSLStatus.ERR_SERVER;
			}
			wrapper.status = TxStatus.BROADCAST;
		}
		
		// Write status and success message.
		output.writeObject(wrapper.status);
		output.writeObject("Success");
		server.releaseTransaction(wrapper);
		return SSLStatus.OK;
	}
	
	/**
	 * Return the transaction status. If the transaction doesn't exist, its status is CLEARED.
	 * @param output: Write the Status of the transaction (OPEN, PENDING, SIGNING, FAILED, BROADCAST, CLEARED).
	 * @param txid: Transaction ID
	 * @return Status
	 */
	public static SSLStatus txidStatus(ObjectOutputStream output, int txid) throws IOException {
		TxWrapper wrapper = server.lockTransaction(txid);
		if (wrapper == null) {
			output.writeObject(TxStatus.CLEARED);
		} else output.writeObject(wrapper.status);
		
		server.releaseTransaction(wrapper);
		return SSLStatus.OK;
	}

}
