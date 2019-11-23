package com.decidir.encripcion;

import play.Logger;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class EncriptadorTDES extends EncriptadorDES{

	public EncriptadorTDES() {
		super("DESede");
		makeCipher();
	}

	public void makeKey(byte[] llave) {
		this.llave = llave;
		try{
			SecretKeyFactory desFactory = SecretKeyFactory.getInstance("DESede");
			DESedeKeySpec des3KeySpec = new DESedeKeySpec(llave);
			SKSkey = desFactory.generateSecret(des3KeySpec);
		}catch (NoSuchAlgorithmException e)
		{
			Logger.error("Excepcion en EncriptadorTDES ", e);
		}catch (InvalidKeyException e)
		{
			Logger.error("Excepcion en EncriptadorTDES ", e);
		}catch (InvalidKeySpecException e)
		{
			Logger.error("Excepcion en EncriptadorTDES ", e);
		}
	}
}
