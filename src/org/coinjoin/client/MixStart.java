package org.coinjoin.client;

import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Transaction.SigHash;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Wallet.SendRequest;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.ScriptBuilder;
import org.coinjoin.server.MainServer.TxStatus;
import org.coinjoin.util.RSABlindSignUtil;
import org.coinjoin.util.RSABlindedData;

import wallettemplate.Main;
import wallettemplate.MainController;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.TextArea;

public class MixStart extends Task {
	
	private final long CHUNK_SIZE = 1000000;
	
	private StringProperty debugger;
	private TransactionOutput inputBuilder;
	private Address destination;
	private Address change;
	private MainController main;
	
	public MixStart(MainController main, StringProperty debugger, TransactionOutput inputBuilder, Address destination, Address change) {
		this.debugger = debugger;
		this.inputBuilder = inputBuilder;
		this.destination = destination;
		this.change = change;
		this.main = main;
	}
	
	private void log(String message) {
		Platform.runLater(new Runnable() {
	        @Override
	        public void run() {
	        	debugger.setValue(message + "\n" + debugger.getValue());
	        }
	   });
	}
	
	private void finish() {
		Platform.runLater(new Runnable() {
	        @Override
	        public void run() {
	        	main.finishMix();
	        }
	   });
	}


	@Override
	protected Object call() throws Exception {
		// Get RSA Key
		PublicKey pub = null;
		byte[] outputSig = null;
		byte[] blindSig  = null;
		TxStatus status = TxStatus.OPEN;
		
		Transaction toSign = null;
		
		log("[INFO] Getting RSA Key...");
		
		try {
			pub = SSLClient.getPublicRSA();
		} catch (APIException e) {
			log("[ERROR] Server failed to respond.\n");
			e.printStackTrace();
			finish();
			return null;
		}
		
		// Generate Blinded Output Address and Change TransactionOutput
		log("[INFO] Blinding Output Address");
		RSABlindedData blindAddr = RSABlindSignUtil.blindData(pub, destination.getHash160());
		Transaction dummy = new Transaction(wallettemplate.Main.params);
		dummy.addOutput(inputBuilder.getValue().subtract(Coin.valueOf(CHUNK_SIZE)), change);
		
		// Register Blinded Output and Change Transaction
		
		log("[INFO] Registering Input Address...");
		try {
			blindSig = SSLClient.registerInput(pub.hashCode(), inputBuilder, dummy.getOutput(0), blindAddr.GetData());
		} catch (APIException e) {
			log("[ERROR] Server failed to respond.\n");
			e.printStackTrace();
			finish();
			return null;
		}
				
		// Unblind Signature
		log("[INFO] Unblinding Signature...");
		outputSig = RSABlindSignUtil.unblindSignature(pub, blindAddr, blindSig);
				
		// Wait Until PENDING
		log("[INFO] Waiting until PENDING...");
		while(status != TxStatus.PENDING) {
			try {
				status = SSLClient.txidStatus(pub.hashCode());
			} catch (APIException e) {
				log("[ERROR] Server failed to respond.\n");
				e.printStackTrace();
				finish();
				return null;
			}
			Thread.sleep(1000);
		}
				
		// Register Output Address
		log("[INFO] Registering Output Address...");
		try {
			toSign = SSLClient.registerOutput(pub.hashCode(), destination, outputSig);
		} catch (APIException e) {
			log("[ERROR] Server failed to respond.\n");
			e.printStackTrace();
			finish();
			return null;
		}
		
		// If Transaction is null, wait until SIGNING, then request again
		if (toSign == null) {
			// Wait Until Signing
			log("[INFO] Waiting until SIGNING...");
			status = TxStatus.PENDING;
			while(status != TxStatus.SIGNING) {
				try {
					status = SSLClient.txidStatus(pub.hashCode());
				} catch (APIException e) {
					log("[ERROR] Server failed to respond.\n");
					e.printStackTrace();
					finish();
					return null;
				}
				Thread.sleep(1000);
			}
			log("[INFO] Requesting Full Transaction...");
			try {
				toSign = SSLClient.registerOutput(pub.hashCode(), destination, outputSig);
			} catch (APIException e) {
				log("[ERROR] Server failed to respond.\n");
				e.printStackTrace();
				finish();
				return null;
			}
		}
				
		// Sign Transaction
		log("[INFO] Checking for Output among " + toSign.getOutputs().size() + "...");
		boolean isThere = false;
		for(TransactionOutput output : toSign.getOutputs()) {
			
			if (output.getAddressFromP2PKHScript(output.getParams()).equals(destination)
					&& output.getValue().equals(Coin.valueOf(CHUNK_SIZE))) {
				isThere = true;
			}
		}
		
		log("[INFO] Signing Transaction...");
		int index = -1;
		if (isThere) {
			// find input to sign
			for (index = 0; index < toSign.getInputs().size(); index++) {
				TransactionInput i = toSign.getInput(index);
				if (i.getOutpoint().getHash().equals(inputBuilder.getParentTransaction().getHash())) {
					ECKey signKey = Main.bitcoin.wallet().findKeyFromPubHash(inputBuilder.getScriptPubKey().getPubKeyHash());
					Sha256Hash sighash = toSign.hashForSignature(index, inputBuilder.getScriptPubKey(), SigHash.ALL, false);
					ECKey.ECDSASignature mySignature = signKey.sign(sighash);
					i.setScriptSig(ScriptBuilder.createInputScript(new TransactionSignature(mySignature, SigHash.ALL, false), signKey));
					break;
				}
			}
		} else {
			log("[WARN] Output Not Found! Aborting...");
			finish();
			return null;
		}
		
		// Search for signed statement
		log("[INFO] Verifying Transaction...");
		TransactionInput finalInput = toSign.getInput(index);
		int finalIndex = index;
		finalInput = toSign.getInput(index);
		try {
			finalInput.verify(inputBuilder);
		} catch (ScriptException e) {
			log("[WARN] Could Not Verify Signature! Aborting...");
			finish();
			return null;
		}
				
		// Register Signature
		log("[INFO] Sending Signature...");
		
		try {
			status = SSLClient.registerSignature(pub.hashCode(), finalIndex, finalInput);
		} catch (APIException e) {
			log("[ERROR] Server failed to respond.\n");
			e.printStackTrace();
			finish();
			return null;
		}
				
		// Wait for BROADCAST
		log("[INFO] Waiting until BROADCAST...");
		while(status != TxStatus.BROADCAST) {
			try {
				status = SSLClient.txidStatus(pub.hashCode());
			} catch (APIException e) {
				log("[ERROR] Server failed to respond.\n");
				e.printStackTrace();
				finish();
				return null;
			}
			Thread.sleep(1000);
		}
		
		log("[INFO] Transaction Successful!");
		
		finish();
		return null;
	}
	
}
