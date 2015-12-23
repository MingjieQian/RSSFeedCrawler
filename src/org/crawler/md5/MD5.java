package org.crawler.md5;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5 {

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		String input = "0000soihtoiweowjfiwjefwflskdofiwejf";
		long start = System.currentTimeMillis();
		System.out.println("MD5 string: " + toMD5(input));
		System.out.format(System.getProperty("line.separator") + "Elapsed time: %.3f seconds.\n", 
				(System.currentTimeMillis() - start) / 1000F);
		
	}
	
	public static String toMD5(String input) {
		return toHexString(toMD5Bytes(input));
	}
	
	public static byte[] toMD5Bytes(String input) {
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5");
			md.reset();
			md.update(input.getBytes());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			System.exit(1);
		}
        byte[] md5 = md.digest();
        return md5;
	}

	public static String toHexString(byte[] bytes) {
	    char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
	    char[] hexChars = new char[bytes.length * 2];
	    int v;
	    for ( int j = 0; j < bytes.length; j++ ) {
	        v = bytes[j] & 0xFF;
	        hexChars[j*2] = hexArray[v >> 4];
	        hexChars[j*2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}

}
