package org.coinjoin.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;

/**
 * Class HttpsListener
 * @author egordon
 * Listens on provided port for HTTPS requests, 
 * and directs them to the corresponding api function.
 */
public class SSLListener extends Thread {
	private int port;
	
	public static enum SSLStatus {
		OK, ERR_CLIENT, ERR_SERVER
	}
	
	public static enum APICall {
		GET_RSA, REG_IN, REG_OUT, REG_SIGN, STATUS
	}
	
	/**
	 * @param newPort on which to listen for connections
	 */
	public SSLListener(int newPort) {
		this.port = newPort;
		System.out.println("Starting Server on Port: " + port);
	}
	
	/**
	 * Creates new SSL server running in given port.
	 * For each new connection, send it to its own dedicated SSLRunner Thread.
	 */
	@Override
	public void run() {
		SSLServerSocket sslserversocket = null;
		SSLServerSocketFactory sslserversocketfactory = null;
		try {
		 sslserversocketfactory =
                 (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
         sslserversocket =
                 (SSLServerSocket) sslserversocketfactory.createServerSocket(port);
		} catch (Exception e) {
			// Fatal Error
			e.printStackTrace();
			System.exit(1);
		}
		
		// Listen Forever
		SSLSocket sslsocket;
		ArrayList<SSLRunner> runners = new ArrayList<SSLRunner>();
		while(true) {
			try {
				sslsocket = (SSLSocket) sslserversocket.accept();
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
			// Add New Runner
			runners.add(new SSLRunner(sslsocket));
			runners.get(runners.size()-1).start();
			
			// Clean Up Dead Threads
			for (Object obj : runners.toArray()) {
				SSLRunner runner = (SSLRunner) obj;
				if (!runner.isAlive()) runners.remove(obj);
			}
		}
         
		
	}

	protected class SSLRunner extends Thread {
		
		private SSLSocket socket;
		
		public SSLRunner(SSLSocket socket) {
			this.socket = socket;
		}
		
		/**
		 * Checks that the request is valid, then redirects it to the proper
		 * API call.
		 */
		@Override
		public void run() {
			ObjectInputStream ois = null;
			ObjectOutputStream oos = null;
			SSLResponse response;
			try {
				// Get Streams
				ois = new ObjectInputStream(socket.getInputStream());
				oos = new ObjectOutputStream(socket.getOutputStream());
				
				APICall call = (APICall) ois.readObject();
				
				// Call API Function
				switch(call) {
				case GET_RSA:
					response = SSLAPI.getPublicRSA();
					break;
				case REG_IN:
					response = SSLAPI.registerInput(ois.readInt(), (TransactionOutput)ois.readObject(), 
							(TransactionOutput)ois.readObject(), (byte[])ois.readObject());
					break;
				case REG_OUT:
					response = SSLAPI.registerOutput(ois.readInt(), (Address)ois.readObject(), 
							(byte[])ois.readObject());
					break;
				case REG_SIGN:
					response = SSLAPI.registerSignature(ois.readInt(), ois.readInt(), (TransactionInput)ois.readObject());
					break;
				case STATUS:
					response = SSLAPI.txidStatus(ois.readInt());
					break;
				default:
					response = new SSLResponse();
					response.retStatus = SSLStatus.ERR_CLIENT;
					response.retObjects.add("Error: Invalid API Call");
					break;
				}
			} catch (IOException e) {
				// Nothing to do! Wait for thread to die.
				e.printStackTrace();
				return;
			} catch (ClassNotFoundException e) {
				// Return error
				e.printStackTrace();
				response = new SSLResponse();
				response.retStatus = SSLStatus.ERR_CLIENT;
				response.retObjects.add("Error: Could not parse request");
			}
			
			try {
				// Return Response
				oos.writeObject(response.retStatus);
				for (Object obj : response.retObjects) {
					oos.writeObject(obj);
				}
				
				// Flush and Disconnect
				oos.flush();
				oos.close();
				ois.close();
				socket.close();
			} catch (Exception e) {
				// Nothing to do! Wait for thread to die...
				e.printStackTrace();
			}
		}
	}
	
}
