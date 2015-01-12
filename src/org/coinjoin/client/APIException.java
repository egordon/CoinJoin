package org.coinjoin.client;

import org.coinjoin.server.SSLListener.APICall;

public class APIException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4005747465054785457L;
	
	public APIException(APICall call) {
		System.err.println("Error in call: " + call.toString());
	}

}
