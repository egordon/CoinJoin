package org.coinjoin.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.PublicKey;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.coinjoin.server.SSLListener.APICall;
import org.coinjoin.server.SSLListener.SSLStatus;

public class SSLClient {
	private static SSLSocketFactory sslsocketfactory;
	private static String ip;
	private static int port;
	
	static {
		 sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
		 ip = "localhost";
		 port = 4444;
	}
	
	public static PublicKey getPublicRSA() throws APIException {
		SSLSocket sslsocket;
		try {
			sslsocket = (SSLSocket) sslsocketfactory.createSocket(ip, port);
			ObjectInputStream ois = new ObjectInputStream(sslsocket.getInputStream());
			ObjectOutputStream oos = new ObjectOutputStream(sslsocket.getOutputStream());
			try {
				// Write Request
				oos.writeObject(APICall.GET_RSA);
				oos.flush();
				
				// Read Response
				SSLStatus status = (SSLStatus) ois.readObject();
				if(status != SSLStatus.OK) {
					System.err.println(ois.readObject().toString());
					ois.close();
					oos.close();
					sslsocket.close();
					throw new APIException(APICall.GET_RSA);
				}
				
				PublicKey retData = (PublicKey) ois.readObject();
				ois.close();
				oos.close();
				sslsocket.close();
				return retData;
			} catch (ClassNotFoundException e1) {			
				e1.printStackTrace();
				ois.close();
				oos.close();
				sslsocket.close();
				throw new APIException(APICall.GET_RSA);
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new APIException(APICall.GET_RSA);
		}
	}
	
	public static byte[] registerInput(int txid, 
			TransactionOutput inputBuilder, TransactionOutput changeOut, byte[] blindedOutput) throws APIException {
		
		SSLSocket sslsocket;
		try {
			sslsocket = (SSLSocket) sslsocketfactory.createSocket(ip, port);
			ObjectInputStream ois = new ObjectInputStream(sslsocket.getInputStream());
			ObjectOutputStream oos = new ObjectOutputStream(sslsocket.getOutputStream());
			try {
				// Write Request
				oos.writeObject(APICall.REG_IN);
				oos.writeInt(txid);
				oos.writeObject(inputBuilder);
				oos.writeObject(changeOut);
				oos.writeObject(blindedOutput);
				oos.flush();
				
				// Read Response
				SSLStatus status = (SSLStatus) ois.readObject();
				if(status != SSLStatus.OK) {
					System.err.println(ois.readObject().toString());
					ois.close();
					oos.close();
					sslsocket.close();
					throw new APIException(APICall.REG_IN);
				}
				
				byte[] retData = (byte[]) ois.readObject();
				ois.close();
				oos.close();
				sslsocket.close();
				return retData;
			} catch (ClassNotFoundException e1) {
				e1.printStackTrace();
				ois.close();
				oos.close();
				sslsocket.close();
				throw new APIException(APICall.REG_IN);
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new APIException(APICall.REG_IN);
		}
	}
	
	public static Transaction registerOutput(int txid, Address outputAddr, byte[] outputSig) throws APIException {
		SSLSocket sslsocket;
		try {
			sslsocket = (SSLSocket) sslsocketfactory.createSocket(ip, port);
			ObjectInputStream ois = new ObjectInputStream(sslsocket.getInputStream());
			ObjectOutputStream oos = new ObjectOutputStream(sslsocket.getOutputStream());
			try {
				// Write Request
				oos.writeObject(APICall.REG_OUT);
				oos.writeInt(txid);
				oos.writeObject(outputAddr);
				oos.writeObject(outputSig);
				oos.flush();
				
				// Read Response
				SSLStatus status = (SSLStatus) ois.readObject();
				if(status != SSLStatus.OK) {
					System.err.println(ois.readObject().toString());
					ois.close();
					oos.close();
					sslsocket.close();
					throw new APIException(APICall.REG_OUT);
				}
				
				Transaction retData = (Transaction) ois.readObject();
				ois.close();
				oos.close();
				sslsocket.close();
				return retData;
			} catch (ClassNotFoundException e1) {
				e1.printStackTrace();
				ois.close();
				oos.close();
				sslsocket.close();
				throw new APIException(APICall.REG_OUT);
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new APIException(APICall.REG_OUT);
		}
	}
	
	public static SSLStatus registerSignature(int txid, int inputIndex, TransactionInput signedInput) throws APIException {
		SSLSocket sslsocket;
		try {
			sslsocket = (SSLSocket) sslsocketfactory.createSocket(ip, port);
			ObjectInputStream ois = new ObjectInputStream(sslsocket.getInputStream());
			ObjectOutputStream oos = new ObjectOutputStream(sslsocket.getOutputStream());
			try {
				// Write Request
				oos.writeObject(APICall.REG_SIGN);
				oos.writeInt(txid);
				oos.writeInt(inputIndex);
				oos.writeObject(signedInput);
				oos.flush();
				
				// Read Response
				SSLStatus status = (SSLStatus) ois.readObject();
				if(status != SSLStatus.OK) {
					System.err.println(ois.readObject().toString());
					ois.close();
					oos.close();
					sslsocket.close();
					throw new APIException(APICall.REG_SIGN);
				}
				
				SSLStatus retData = (SSLStatus) ois.readObject();
				System.out.println("Status (" + retData.toString() + "): " + ois.readObject().toString());
				ois.close();
				oos.close();
				sslsocket.close();
				return retData;
			} catch (ClassNotFoundException e1) {
				e1.printStackTrace();
				ois.close();
				oos.close();
				sslsocket.close();
				throw new APIException(APICall.REG_SIGN);
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new APIException(APICall.REG_SIGN);
		}
	}
	
	public static SSLStatus txidStatus(int txid) throws APIException {
		SSLSocket sslsocket;
		try {
			sslsocket = (SSLSocket) sslsocketfactory.createSocket(ip, port);
			ObjectInputStream ois = new ObjectInputStream(sslsocket.getInputStream());
			ObjectOutputStream oos = new ObjectOutputStream(sslsocket.getOutputStream());
			try {
				// Write Request
				oos.writeObject(APICall.STATUS);
				oos.writeInt(txid);
				oos.flush();
				
				// Read Response
				SSLStatus status = (SSLStatus) ois.readObject();
				if(status != SSLStatus.OK) {
					System.err.println(ois.readObject().toString());
					ois.close();
					oos.close();
					sslsocket.close();
					throw new APIException(APICall.STATUS);
				}
				
				SSLStatus retData = (SSLStatus) ois.readObject();
				ois.close();
				oos.close();
				sslsocket.close();
				return retData;
			} catch (ClassNotFoundException e1) {			
				e1.printStackTrace();
				ois.close();
				oos.close();
				sslsocket.close();
				throw new APIException(APICall.STATUS);
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new APIException(APICall.STATUS);
		}
	}
}