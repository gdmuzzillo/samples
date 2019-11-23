package com.decidir.encripcion;

import javax.crypto.Cipher;
import java.security.GeneralSecurityException;

/**
 * @author scherny, May 18th, 2005
 * 
 */
public abstract class Encriptador {
	protected String algoritmo = null;
	byte[] llave = null;

	protected Encriptador(String algoritmo) {
		this.algoritmo = algoritmo;
	}

	public String encriptar(String cadenaAEncriptar)
			throws GeneralSecurityException {
		return new String(aplicarAlgoritmo(cadenaAEncriptar.getBytes(), Cipher.ENCRYPT_MODE));
	}

	public String desencriptar(String cadenaADesEncriptar)
			throws GeneralSecurityException {
			return new String(aplicarAlgoritmo(cadenaADesEncriptar.getBytes(), Cipher.DECRYPT_MODE));
	}

	public byte[] getllave() {
		return this.llave;
	}

	public String getAlgoritmo() {
		return this.algoritmo;
	}

	public void makeKey(byte[] llave) {
	};

	/**
	 */
	protected abstract byte[] aplicarAlgoritmo(byte[] cadena, int tipoDeAccion)
			throws GeneralSecurityException;

}
