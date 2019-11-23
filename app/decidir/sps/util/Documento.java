package decidir.sps.util;

public class Documento {
	public Integer tipoDocumento;
	private String nroDocumento;

	public Documento() {
	}

	public Documento(Integer tipoDocumento, String nroDocumento) {
		this.tipoDocumento = tipoDocumento;
		this.nroDocumento = nroDocumento;
	}

	public static String getDescTipoDocumento(Integer idTipo) {
		if (idTipo == null)
			return "";
		switch (idTipo.intValue()) {
		case 1:
			return "DNI";
		case 2:
			return "CI";
		case 3:
			return "LE";
		case 4:
			return "LC";
		default:
			return "";
		}
	}

	public String getNroDocumento() {
		return nroDocumento;
	}

	public Integer getTipoDocumento() {
		return tipoDocumento;
	}

	public String toString() {
		return "<TipoDocumento:" + tipoDocumento + "|NroDocumento:"
				+ nroDocumento + ">";
	}

	public void setNroDocumento(String newNroDocumento) {
		nroDocumento = newNroDocumento;
	}

	public void setTipoDocumento(Integer newTipoDocumento) {
		tipoDocumento = newTipoDocumento;
	}
}