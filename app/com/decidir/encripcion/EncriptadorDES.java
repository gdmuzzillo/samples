/*
 * Created on Jun 2, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package com.decidir.encripcion;

import play.Logger;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

/**
 * @author aquereilhac
 * 
 */
public class EncriptadorDES extends Encriptador {
	// "DES/ECB/NoPadding"
	private String padding = "NoPadding";
	private String mode = "ECB";
	protected Key SKSkey;
	private Cipher cipher;

	public EncriptadorDES() {
		super("DES");
		makeCipher();
	}
	
	protected EncriptadorDES(String alg) {
		super(alg);
		makeCipher();
	}
	protected void makeCipher(){
		try {
			this.cipher = Cipher.getInstance(this.algoritmo + "//" + this.mode
					+ "//" + this.padding);
		} catch (NoSuchPaddingException e1) {
			Logger.error("Excepcion en EncriptadorDES", e1);
		} catch (NoSuchAlgorithmException e2) {
			Logger.error("Excepcion en EncriptadorDES", e2);
		}
	}
	// creacion de la llave de encriptacion
	public void makeKey(byte[] llave) {
		this.llave = llave;
		this.SKSkey = new SecretKeySpec(this.llave, this.algoritmo);
	}

	protected byte[] aplicarAlgoritmo(byte[] cadena, int tipoDeAccion) {

		if ((cadena == null) || (cadena.length == 0))
			return cadena;

		if (tipoDeAccion == Cipher.ENCRYPT_MODE) {
			try {
				this.cipher.init(Cipher.ENCRYPT_MODE, this.SKSkey);
				// se calcula el checksum
				byte checksum = checksum(cadena);
				// Se le agrega un padding de 0s al final en caso de que el
				// ultimo bloque de 8 no este completo
				byte[] textoplano = agregarPaddingDeCeros(cadena);
				byte[] cipherText = cipher.doFinal(textoplano);
				byte[] result = agregarChecksum(cipherText, checksum);
				return byteArrayToHexString(result).getBytes();
			} catch (Exception e) {
				Logger.error("Excepcion en EncriptadorDES", e);
				return null;
			}
		}
		if (tipoDeAccion == Cipher.DECRYPT_MODE) {
			try {
				this.cipher.init(Cipher.DECRYPT_MODE, this.SKSkey);
				// se saca el byte de verificacion que no es parte del texto
				// encriptado al principio de la cadena
				byte[] cadenasinverif = sacarbytedeverificacion(cadena);
				// transfroma la cadena encriptada de Ascii a Hexa
				byte[] textoencriptado = hexStringToByteArray(new String(
						cadenasinverif));
				cadenasinverif = null;
				byte[] desencriptado = this.cipher.doFinal(textoencriptado);
				// libero lo que ya no uso
				textoencriptado = null;
				// se quita el padding de ceros del ultimo bloque del texto
				// desencriptado
				return sacarPaddingDeCeros(desencriptado);
			} catch (Exception e) {				
				return null;
			}
		}
		return null;
	}

	// agrega padding de ceros a una cadena de bytes a encriptar para completar
	// el ultimo bloque de 8 bytes
	protected byte[] agregarPaddingDeCeros(byte[] cadena) {
		byte[] padded;
		if (cadena.length % 8 == 0) {
			padded = cadena;
		} else {
			padded = new byte[cadena.length + 8 - (cadena.length % 8)];
			System.arraycopy(cadena, 0, padded, 0, cadena.length);
			for (int i = cadena.length; i < padded.length; i++) {
				padded[i] = 0;
			}
		}
		return padded;
	}

	// quita el padding de ceros del ultimo bloque de la cadena
	protected byte[] sacarPaddingDeCeros(byte[] cadena) {
		int size = 0;
		for (; (size < cadena.length) && (cadena[size] != 0); size++) {
		}
		byte[] salida = new byte[size];
		System.arraycopy(cadena, 0, salida, 0, size);
		return salida;
	}

	// se elimina el primer byte de la cadena encriptada que es un byte de
	// verificacion y no es parte del texto encriptado
	protected byte[] sacarbytedeverificacion(byte[] cadena) {
		byte[] encriptado = new byte[cadena.length - 2];
		// el texto encriptado del modulo c tiene 1 caracter hexa (2 caracteres
		// Ascii) de control de mas
		System.arraycopy(cadena, 2, encriptado, 0, cadena.length - 2);
		return encriptado;
	}

	protected byte[] hexStringToByteArray(String hexa)
			throws IllegalArgumentException {

		String hexDigits = "0123456789abcdef";
		// verifica se a String possui uma quantidade par de elementos
		if (hexa.length() % 2 != 0) {
			throw new IllegalArgumentException("String hexa invalida");
		}
		byte[] b = new byte[hexa.length() / 2];

		for (int i = 0; i < hexa.length(); i += 2) {
			b[i / 2] = (byte) ((hexDigits.indexOf(hexa.charAt(i)) << 4) | (hexDigits.indexOf(hexa.charAt(i + 1))));
		}
		return b;
	}

	public static String byteArrayToHexString(byte[] b) {
		StringBuffer buf = new StringBuffer();
		String hexDigits = "0123456789abcdef";

		for (int i = 0; i < b.length; i++) {
			int j = ((int) b[i]) & 0xFF;
			buf.append(hexDigits.charAt(j / 16));
			buf.append(hexDigits.charAt(j % 16));
		}

		return buf.toString();
	}

	public byte checksum(byte[] textoplano) {

		int checksum = 0;

		for (int i = 0; i < textoplano.length; i++) {
			checksum += textoplano[i];
		}

		return (byte) (checksum % 256);
	}

	public byte[] agregarChecksum(byte[] cipherText, byte checksum) {

		byte[] resultado = new byte[cipherText.length + 1];
		resultado[0] = checksum;
		for (int i = 1; i < resultado.length; i++) {
			resultado[i] = cipherText[i - 1];
		}
		return resultado;
	}
}
