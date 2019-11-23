package decidir.sps.core;

public class VerazidLogin {

	private String matriz;
	private String usuario;
	private String password;
	private String sucursal;
	private String sector;

	public VerazidLogin(String matriz, String usuario, String password,
			String sucursal, String sector)
	{
		this.matriz = matriz;
		this.usuario = usuario;
		this.password = password;
		this.sucursal = sucursal;
		this.sector = sector;		
	}

	public String getMatriz() {
		return matriz;
	}

	public String getUsuario() {
		return usuario;
	}

	public String getPassword() {
		return password;
	}

	public String getSucursal() {
		return sucursal;
	}

	public String getSector() {
		return sector;
	}
}
