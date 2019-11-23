package controllers.utils;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

public class HashtableOrdenada<K,V> extends Hashtable<K,V> {
	Vector<K> elementos = new Vector<K>();

	private static final long serialVersionUID = 1L;

	public Enumeration<K> keys() {
		return elementos.elements();
	}

	public V put(K key, V value) {
		elementos.addElement(key);
		return super.put(key, value);
	}
}