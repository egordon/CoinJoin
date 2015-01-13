package org.coinjoin.util;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public class RSABlindSignUtil {
	
	private static SecureRandom random;
	private static KeyPairGenerator kpg;
	
	static {
		// Initialize RSA KeyPair Generator and RNG
		random = new SecureRandom();
		try {
			kpg = KeyPairGenerator.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			System.exit(1);
		}
		kpg.initialize(2048, new SecureRandom());
	}
	
	public static KeyPair freshRSAKeyPair() {
		return kpg.genKeyPair();
	}
	
	/**
	 * Given m, calculates (m^d)modN
	 * @param p: RSA Private Key
	 * @param blinded: Raw Byte Array representing "m"
	 * @return
	 */
	public static byte[] signData(PrivateKey p, byte[] blinded) {
		BigInteger data = new BigInteger(1, blinded);
		RSAPrivateKey privKey = (RSAPrivateKey)p;
		BigInteger sig = data.modPow(privKey.getPrivateExponent(), privKey.getModulus());
		return sig.toByteArray();
	}
	
	public static boolean verifyData(PublicKey p, byte[] data, byte[] signature) {
		RSAPublicKey pub = (RSAPublicKey)p;
		
		BigInteger s = new BigInteger(1, signature);
		BigInteger e = pub.getPublicExponent();
		BigInteger n = pub.getModulus();
		BigInteger m = new BigInteger(1, data);
		
		return s.modPow(e, n).equals(m);
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
				.multiply(new BigInteger(1, data))).mod(pub.getModulus());
		
		return new RSABlindedData(r, bData.toByteArray());
	}
	
	public static byte[] unblindSignature(PublicKey p, RSABlindedData bData, byte[] bSig) {
		RSAPublicKey pub = (RSAPublicKey)p;
		BigInteger sig = bData.GetMultiplier().modInverse(pub.getModulus())
				.multiply(new BigInteger(1, bSig)).mod(pub.getModulus());
		
		return sig.toByteArray();
	}
	
	public static void main(String[] args) {
		KeyPair keys1 = RSABlindSignUtil.freshRSAKeyPair();
		KeyPair keys2 = RSABlindSignUtil.freshRSAKeyPair();
		byte[] data = new byte[256];
		for(int i = 0; i < data.length; i++) {
			data[i] = (byte)i;
		}
		byte[] signature = RSABlindSignUtil.signData(keys2.getPrivate(), data);
		boolean good = RSABlindSignUtil.verifyData(keys2.getPublic(), data, signature);
		if(good) System.out.println("Yay! It worked!");
		else System.out.println("Boo! It did not work!");
	}

}
