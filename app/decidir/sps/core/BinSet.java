package decidir.sps.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BinSet {
	private Integer id;
	private MarcaTarjeta marcaTarjeta;
	private String nombre;
	private Set<String> elSet;
	
	public BinSet(Integer id, MarcaTarjeta marcaTarjeta, String nombre, List<String> bins)
	{
		this.id = id;
		this.marcaTarjeta = marcaTarjeta;
		this.nombre = nombre;
		this.elSet = bins == null? new HashSet<String>() : new HashSet<String>(bins);
	}

	public boolean contiene(String bin) {
		return elSet.contains(bin);
	}

	public Integer getId() {
		return this.id;
	}
	
	public MarcaTarjeta getMarcaTarjeta() {
		return this.marcaTarjeta;
	}

	public String getNombre() {
		return this.nombre;
	}

	public List<String> getBins() {
		return new ArrayList<String>(elSet);
	}
}
