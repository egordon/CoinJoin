package org.coinjoin.server;

/**
 * Class HttpsListener
 * @author egordon
 * Listens on provided port for HTTPS requests, 
 * and directs them to the corresponding api function.
 */
public class HttpsListener extends Thread {
	private int port;
	
	/**
	 * @param newPort on which to listen for connections
	 */
	public HttpsListener(int newPort) {
		this.port = newPort;
	}
	
	/**
	 * Creates new HTTPS server running in given port.
	 * For each new POST connection, send it to its own dedicated HttpsRunner Thread.
	 */
	@Override
	public void run() {
		// TODO: Complete
		
	}

	protected class HttpsRunner extends Thread {
		/**
		 * Checks that the request is valid, then redirects it to the proper
		 * API call.
		 */
		@Override
		public void run() {
			// TODO: Complete
			
		}
	}
	
}
