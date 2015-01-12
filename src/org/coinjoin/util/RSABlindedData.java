package org.coinjoin.util;

import java.math.BigInteger;

public class RSABlindedData {
	private BigInteger r;
	private byte[] m;
	
	public BigInteger GetMultiplier() {
		return r;
	}
	
	public byte[] GetData() {
		return m;
	}
	
	public RSABlindedData(BigInteger r, byte[] m) {
		this.m = m;
		this.r = r;
	}

}
