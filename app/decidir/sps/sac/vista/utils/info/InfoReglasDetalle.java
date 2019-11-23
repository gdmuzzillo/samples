/*
 * Created on Jul 20, 2011
 */
package decidir.sps.sac.vista.utils.info;

import java.util.List;

/**
 * @author egattelet
 */
public class InfoReglasDetalle{

	private String idregla;
	private String campo;
	private String descricampo;
	private String operador;
	private String valor;
	private List<String> descri;

	public InfoReglasDetalle(String idregla, String campo, String operador, String valor, List<String> descri,
			String descricampo) {
		this.idregla = idregla ;
		this.campo = campo;
		this.descricampo = descricampo;
		this.operador = operador;
		this.valor = valor;
		this.descri = descri;

	}
	public InfoReglasDetalle(String idregla, String campo, String operador, String valor) {
		this.idregla = idregla ;
		this.campo = campo;
		this.operador = operador;
		this.valor = valor;
	}
	
	public void setCampo(String campo) {
		this.campo = campo;
	}
	public String getCampo() {
		return this.campo;
	}
	public String getOperador() {
		return this.operador;
	}
	public void setValor(String valor) {
		this.valor = valor;
	}
	public String getValor() {
		return this.valor;
	}
	public void setIdregla(String idregla) {
		this.idregla = idregla;
	}
	public String getIdregla() {
		return idregla;
	}
	public List<String> getDescri() {
		return descri;
	}
	public void setDescri(List<String> descri) {
		this.descri = descri;
	}
	public String getDescricampo() {
		return descricampo;
	}
	public void setDescricampo(String descricampo) {
		this.descricampo = descricampo;
	}
	
}
