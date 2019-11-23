/*
 * Created on Aug 30, 2006
 */
package decidir.sps.exception;

/**
 * @author ldubiau Esta Excepcion se crea cuando alg�n dato de la transacci�n no
 *         pasa una validaci�n.
 */
public class InvalidTransactionDataException extends Exception {
	
	private static final long serialVersionUID = 1L;

	public InvalidTransactionDataException(String reason) {
		super(reason);
	}
}
