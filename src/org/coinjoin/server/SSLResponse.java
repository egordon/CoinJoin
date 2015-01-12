package org.coinjoin.server;

import java.util.ArrayList;

import org.coinjoin.server.SSLListener.SSLStatus;

public class SSLResponse {
	public SSLStatus retStatus;
	public ArrayList<Object> retObjects;
	
	public SSLResponse() {
		retStatus = SSLStatus.ERR_SERVER;
		retObjects = new ArrayList<Object>();
	}
	
}
