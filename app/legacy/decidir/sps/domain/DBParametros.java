package legacy.decidir.sps.domain;


import java.util.List;
import java.util.Vector;

public class DBParametros
{
  private Vector valores = null;
  private Vector tipos = null;

  public DBParametros() {
    this.valores = new Vector();
    this.tipos = new Vector();
  }

  public DBParametros add(Object valor, int tipo)
  {
    this.valores.add(valor);
    this.tipos.add(new Integer(tipo));
    return this;
  }

  public List getValores() {
    return this.valores;
  }

  public List getTipos() {
    return this.tipos;
  }

  public DBParametros limpiar() {
    this.valores.clear();
    this.tipos.clear();
    return this;
  }
}