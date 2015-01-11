package org.coinjoin.rsa;

import java.math.BigInteger;

public class BlindedData {
	private BigInteger r;
	private byte[] m;
	
	public BigInteger GetMultiplier() {
		return r;
	}
	
	public byte[] GetData() {
		return m;
	}
	
	public BlindedData(BigInteger r, byte[] m) {
		this.m = m;
		this.r = r;
	}

}
