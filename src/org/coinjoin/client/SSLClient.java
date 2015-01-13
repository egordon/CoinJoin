package org.coinjoin.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.PublicKey;

import javax.net.SocketFactory;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.coinjoin.server.MainServer.TxStatus;
import org.coinjoin.server.SSLListener.APICall;
import org.coinjoin.server.SSLListener.SSLStatus;

public class SSLClient {
	private static SocketFactory socketfactory;
	private static String ip;
	private static int port;
	
	static {
		 socketfactory = (SocketFactory) SocketFactory.getDefault();
		 ip = "localhost";
		 port = 4444;
	}
	
	public static PublicKey getPublicRSA() throws APIException {
		Socket socket;
		try {
			socket = (Socket) socketfactory.createSocket(ip, port);
			ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
			ObjectInputStream ois = null;
			try {
				// Write Request
				oos.writeObject(APICall.GET_RSA);
				oos.flush();
				
				ois = new ObjectInputStream(socket.getInputStream());
				
				// Read Response
				SSLStatus status = (SSLStatus) ois.readObject();
				if(status != SSLStatus.OK) {
					System.err.println(ois.readObject().toString());
					ois.close();
					oos.close();
					socket.close();
					throw new APIException(APICall.GET_RSA);
				}
				
				PublicKey retData = (PublicKey) ois.readObject();
				ois.close();
				oos.close();
				socket.close();
				return retData;
			} catch (ClassNotFoundException e1) {			
				e1.printStackTrace();
				ois.close();
				oos.close();
				socket.close();
				throw new APIException(APICall.GET_RSA);
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new APIException(APICall.GET_RSA);
		}
	}
	
	public static byte[] registerInput(int txid, 
			TransactionOutput inputBuilder, TransactionOutput changeOut, byte[] blindedOutput) throws APIException {
		
		Socket socket;
		try {
			socket = (Socket) socketfactory.createSocket(ip, port);
			ObjectInputStream ois = null;
			ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
			try {
				// Write Request
				oos.writeObject(APICall.REG_IN);
				oos.writeInt(txid);
				oos.writeObject(inputBuilder);
				oos.writeObject(changeOut);
				oos.writeObject(blindedOutput);
				oos.flush();
				
				ois = new ObjectInputStream(socket.getInputStream());
				
				// Read Response
				SSLStatus status = (SSLStatus) ois.readObject();
				if(status != SSLStatus.OK) {
					System.err.println(ois.readObject().toString());
					ois.close();
					oos.close();
					socket.close();
					throw new APIException(APICall.REG_IN);
				}
				
				byte[] retData = (byte[]) ois.readObject();
				ois.close();
				oos.close();
				socket.close();
				return retData;
			} catch (ClassNotFoundException e1) {
				e1.printStackTrace();
				ois.close();
				oos.close();
				socket.close();
				throw new APIException(APICall.REG_IN);
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new APIException(APICall.REG_IN);
		}
	}
	
	public static Transaction registerOutput(int txid, Address outputAddr, byte[] outputSig) throws APIException {
		Socket socket;
		try {
			socket = (Socket) socketfactory.createSocket(ip, port);
			ObjectInputStream ois = null;
			ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
			try {
				// Write Request
				oos.writeObject(APICall.REG_OUT);
				oos.writeInt(txid);
				oos.writeObject(outputAddr);
				oos.writeObject(outputSig);
				oos.flush();
				
				ois = new ObjectInputStream(socket.getInputStream());
				
				// Read Response
				SSLStatus status = (SSLStatus) ois.readObject();
				if(status != SSLStatus.OK) {
					System.err.println(ois.readObject().toString());
					ois.close();
					oos.close();
					socket.close();
					throw new APIException(APICall.REG_OUT);
				}
				
				Transaction retData = (Transaction) ois.readObject();
				ois.close();
				oos.close();
				socket.close();
				return retData;
			} catch (ClassNotFoundException e1) {
				e1.printStackTrace();
				ois.close();
				oos.close();
				socket.close();
				throw new APIException(APICall.REG_OUT);
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new APIException(APICall.REG_OUT);
		}
	}
	
	public static TxStatus registerSignature(int txid, int inputIndex, TransactionInput signedInput) throws APIException {
		Socket socket;
		try {
			socket = (Socket) socketfactory.createSocket(ip, port);
			ObjectInputStream ois = null;
			ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
			try {
				// Write Request
				oos.writeObject(APICall.REG_SIGN);
				oos.writeInt(txid);
				oos.writeInt(inputIndex);
				oos.writeObject(signedInput);
				oos.flush();
				
				ois = new ObjectInputStream(socket.getInputStream());
				
				// Read Response
				SSLStatus status = (SSLStatus) ois.readObject();
				if(status != SSLStatus.OK) {
					System.err.println(ois.readObject().toString());
					ois.close();
					oos.close();
					socket.close();
					throw new APIException(APICall.REG_SIGN);
				}
				
				TxStatus retData = (TxStatus) ois.readObject();
				System.out.println("Status (" + retData.toString() + "): " + ois.readObject().toString());
				ois.close();
				oos.close();
				socket.close();
				return retData;
			} catch (ClassNotFoundException e1) {
				e1.printStackTrace();
				ois.close();
				oos.close();
				socket.close();
				throw new APIException(APICall.REG_SIGN);
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new APIException(APICall.REG_SIGN);
		}
	}
	
	public static TxStatus txidStatus(int txid) throws APIException {
		Socket socket;
		try {
			socket = (Socket) socketfactory.createSocket(ip, port);
			ObjectInputStream ois = null;
			ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
			try {
				// Write Request
				oos.writeObject(APICall.STATUS);
				oos.writeInt(txid);
				oos.flush();
				
				ois = new ObjectInputStream(socket.getInputStream());
				
				// Read Response
				SSLStatus status = (SSLStatus) ois.readObject();
				if(status != SSLStatus.OK) {
					System.err.println(ois.readObject().toString());
					ois.close();
					oos.close();
					socket.close();
					throw new APIException(APICall.STATUS);
				}
				
				TxStatus retData = (TxStatus) ois.readObject();
				ois.close();
				oos.close();
				socket.close();
				return retData;
			} catch (ClassNotFoundException e1) {			
				e1.printStackTrace();
				ois.close();
				oos.close();
				socket.close();
				throw new APIException(APICall.STATUS);
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new APIException(APICall.STATUS);
		}
	}
}