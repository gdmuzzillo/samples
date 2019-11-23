package decidir.sps.sac.vista.utils.info;

import java.util.List;

import decidir.sps.core.BinSet;
import decidir.sps.core.MarcaTarjeta;

public class InfoFiltros 
{
	private String idsite;
	private MarcaTarjeta marcaTarjeta;
	private boolean filtraXBin;
	private boolean listaNegra;
	private List<BinSet> binsets;
	
	public InfoFiltros(String idsite, MarcaTarjeta marcaTarjeta, boolean filtraXBin, boolean listaNegra, List<BinSet> binsets)
	{
		this.idsite = idsite;
		this.marcaTarjeta = marcaTarjeta;
		this.filtraXBin = filtraXBin;
		this.listaNegra = listaNegra;
		this.binsets = binsets;
	}

	public String getIdsite() {
		return idsite;
	}

	public MarcaTarjeta getMarcaTarjeta() {
		return marcaTarjeta;
	}

	public boolean getListaNegra() {
		return this.listaNegra;
	}
	
	public boolean getListaBlanca() {
		return !this.listaNegra;
	}

	public List<BinSet> getBinsets() {
		return binsets;
	}

	public boolean getFiltraXBin() {
		return filtraXBin;
	}
	
	public boolean equals(Object o)
	{
		if(o == null || !(o instanceof InfoFiltros)) return false;
		
		InfoFiltros that = (InfoFiltros)o;
		
		return this.idsite.equals(that.idsite) && this.getMarcaTarjeta().equals(that.marcaTarjeta);
	}
}
