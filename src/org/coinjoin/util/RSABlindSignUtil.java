package org.coinjoin.util;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public class RSABlindSignUtil {
	
	private static SecureRandom random;
	
	static {
		// Initialize RSA KeyPair Generator and RNG
		random = new SecureRandom();
	}
	
	public static KeyPair freshRSAKeyPair() {
		// TODO: Complete
		return null;
	}
	
	/**
	 * Given m, calculates (m^d)modN
	 * @param p: RSA Private Key
	 * @param blinded: Raw Byte Array representing "m"
	 * @return
	 */
	public static byte[] signData(PrivateKey p, byte[] blinded) {
		BigInteger sig = new BigInteger(blinded);
		RSAPrivateKey privKey = (RSAPrivateKey)p;
		sig = sig.modPow(privKey.getPrivateExponent(), privKey.getModulus());
		return sig.toByteArray();
	}
	
	public static boolean verifyData(PublicKey p, byte[] data, byte[] signature) {
		RSAPublicKey pub = (RSAPublicKey)p;
		
		return (new BigInteger(signature).modPow(pub.getPublicExponent(), pub.getModulus()))
				.equals(new BigInteger(data));
	}
	
	public static RSABlindedData blindData(PublicKey p, byte[] data) {
		RSAPublicKey pub = (RSAPublicKey)p;
		byte[] rand = new byte[pub.getModulus().bitLength() / 8];
		BigInteger r = BigInteger.ONE;
		while(!r.gcd(pub.getModulus()).equals(BigInteger.ONE) || r.equals(BigInteger.ONE) || r.equals(pub.getModulus())) {
			random.nextBytes(rand);
			r = new BigInteger(1, rand);
		}
		
		BigInteger bData = ((r.modPow(pub.getPublicExponent(),pub.getModulus()))
				.multiply(new BigInteger(data))).mod(pub.getModulus());
		
		return new RSABlindedData(r, bData.toByteArray());
	}
	
	public static byte[] unblindSignature(PublicKey p, RSABlindedData bData, byte[] bSig) {
		RSAPublicKey pub = (RSAPublicKey)p;
		BigInteger sig = bData.GetMultiplier().modInverse(pub.getModulus())
				.multiply(new BigInteger(bSig)).mod(pub.getModulus());
		
		return sig.toByteArray();
	}

}
