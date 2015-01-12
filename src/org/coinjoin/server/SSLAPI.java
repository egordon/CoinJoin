package org.coinjoin.server;

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
	 * @  
	 */
	public static SSLResponse getPublicRSA()  {
		SSLResponse response = new SSLResponse();
		Integer currentOpenID = server.currentOpenID();
		if (currentOpenID == null) {
			response.retObjects.add("Error: no currently open transaction. Please try again later.");
			response.retStatus = SSLStatus.ERR_SERVER;
			return response;
		}
		TxWrapper currentOpen = server.lockTransaction(currentOpenID);
		if (currentOpen == null) {
			response.retObjects.add("Error: transaction does not exist");
			response.retStatus = SSLStatus.ERR_CLIENT;
			return response;
		}
		response.retObjects.add(currentOpen.rsa.getPublic());
		
		
		server.releaseTransaction(currentOpen);
		response.retStatus = SSLStatus.OK;
		return response;
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
	public static SSLResponse registerInput(int txid, 
			TransactionOutput inputBuilder, TransactionOutput changeOut, byte[] blindedOutput)  {
		SSLResponse response = new SSLResponse();
		
		// Check that input/change address add up to CHUNK_SIZE or bigger
		if(inputBuilder.getValue().subtract(changeOut.getValue()).value < MainServer.CHUNK_SIZE) {
			response.retObjects.add("Error: Input and change address do not add upt to chunk size.");
			response.retStatus = SSLStatus.ERR_CLIENT;
			return response;
		}
		
		// Check that transaction is open
		TxWrapper wrapper = server.lockTransaction(txid);
		if (wrapper == null) {
			response.retObjects.add("Error: transaction does not exist.");
			response.retStatus = SSLStatus.ERR_CLIENT;
			return response;
		} else if (wrapper.status != TxStatus.OPEN) {
			response.retObjects.add("Error: transaction is not longer open.");
			server.releaseTransaction(wrapper);
			response.retStatus = SSLStatus.ERR_SERVER;
			return response;
		}
		
		// Add Input and Output to Transaction and write signed output
		wrapper.tx.addInput(inputBuilder);
		wrapper.tx.addOutput(changeOut);
		
		byte[] retSig = RSABlindSignUtil.signData(wrapper.rsa.getPrivate(), blindedOutput);
		response.retObjects.add(retSig);
		
		server.releaseTransaction(wrapper);
		response.retStatus = SSLStatus.OK;
		return response;
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
	public static SSLResponse registerOutput(int txid, Address outputAddr, byte[] outputSig)  {
		SSLResponse response = new SSLResponse();
		
		// Check Transaction Status
		TxWrapper wrapper = server.lockTransaction(txid);
		if (wrapper == null) {
			response.retObjects.add("Error: transaction does not exist.");
			response.retStatus = SSLStatus.ERR_CLIENT;
			return response;
		} else if (wrapper.status == TxStatus.OPEN || wrapper.status == TxStatus.FAILED) {
			response.retObjects.add("Error: transaction is not PENDING or SIGNING.");
			server.releaseTransaction(wrapper);
			response.retStatus = SSLStatus.ERR_SERVER;
			return response;
		}
		
		// Check Output Signature
		if (!RSABlindSignUtil.verifyData(wrapper.rsa.getPublic(), outputAddr.getHash160(), outputSig)) {
			response.retObjects.add("Error: Signature does not match given txid.");
			server.releaseTransaction(wrapper);
			response.retStatus = SSLStatus.ERR_CLIENT;
			return response;
		}
		
		// If PENDING, add output to transaction.
		if (wrapper.status == TxStatus.PENDING) {
			// Check for Duplicates
			boolean isDuplicate = false;
			for (TransactionOutput t : wrapper.tx.getOutputs()) {
				if (t.getAddressFromP2PKHScript(t.getParams()).equals(outputAddr)) {
					isDuplicate = true;
					break;
				}
			}
			if (!isDuplicate) {
				wrapper.tx.addOutput(Coin.valueOf(MainServer.CHUNK_SIZE), outputAddr);
				wrapper.regOutputs++;
				if (wrapper.regOutputs >= wrapper.tx.getInputs().size()) {
					String err = server.feeTransaction(wrapper);
					if (err != null) {
						wrapper.status = TxStatus.FAILED;
						System.err.println("Error: Not enough fee for transaction.");
						response.retObjects.add("Error: transaction has FAILED.");
						server.releaseTransaction(wrapper);
						response.retStatus = SSLStatus.ERR_SERVER;
						return response;
					}
					wrapper.status = TxStatus.SIGNING;
				}
			}
		}
		
		// If SIGNING, return full transaction.
		if (wrapper.status == TxStatus.SIGNING) {
			response.retObjects.add(wrapper.tx);
		} else {
			response.retObjects.add(null);
		}
		
		server.releaseTransaction(wrapper);
		response.retStatus = SSLStatus.OK;
		return response;
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
	public static SSLResponse registerSignature(int txid, int inputIndex, TransactionInput signedInput)  {
		SSLResponse response = new SSLResponse();
		// Check Transaction Status
		TxWrapper wrapper = server.lockTransaction(txid);
		if (wrapper == null) {
			response.retObjects.add(TxStatus.CLEARED);
			response.retObjects.add("Transaction doesn't exist or has been cleared.");
			response.retStatus = SSLStatus.ERR_CLIENT;
			return response;
		} else if (wrapper.status != TxStatus.SIGNING) {
			response.retObjects.add(wrapper.status);
			response.retObjects.add("Error: transaction is not SIGNING.");
			server.releaseTransaction(wrapper);
			response.retStatus = SSLStatus.ERR_SERVER;
			return response;
		}
		
		// Add signature to current transaction
		TransactionInput unsigned = wrapper.tx.getInput(inputIndex);
		unsigned.setScriptSig(signedInput.getScriptSig());
		try {
			unsigned.verify();
		} catch (Exception e) {
			e.printStackTrace();
			response.retObjects.add(wrapper.status);
			response.retObjects.add("Error: signature invalid.");
			server.releaseTransaction(wrapper);
			response.retStatus = SSLStatus.ERR_CLIENT;
			return response;
		}
		
		wrapper.signedInputs++;
		
		// If all inputs signed, broadcast transaction
		if (wrapper.signedInputs >= wrapper.tx.getInputs().size()) {
			String err = server.broadcastTransaction(wrapper);
			if (err != null) {
				wrapper.status = TxStatus.FAILED;
				System.err.println("Broadcast Error: " + err);
				response.retObjects.add("Error: transaction has FAILED.");
				server.releaseTransaction(wrapper);
				response.retStatus = SSLStatus.ERR_SERVER;
				return response;
			}
			wrapper.status = TxStatus.BROADCAST;
		}
		
		// Write status and success message.
		response.retObjects.add(wrapper.status);
		response.retObjects.add("Success");
		server.releaseTransaction(wrapper);
		response.retStatus = SSLStatus.OK;
		return response;
	}
	
	/**
	 * Return the transaction status. If the transaction doesn't exist, its status is CLEARED.
	 * @param output: Write the Status of the transaction (OPEN, PENDING, SIGNING, FAILED, BROADCAST, CLEARED).
	 * @param txid: Transaction ID
	 * @return Status
	 */
	public static SSLResponse txidStatus(int txid)   {
		SSLResponse response = new SSLResponse();
		TxWrapper wrapper = server.lockTransaction(txid);
		if (wrapper == null) {
			response.retObjects.add(TxStatus.CLEARED);
		} else response.retObjects.add(wrapper.status);
		
		server.releaseTransaction(wrapper);
		response.retStatus = SSLStatus.OK;
		return response;
	}

}
