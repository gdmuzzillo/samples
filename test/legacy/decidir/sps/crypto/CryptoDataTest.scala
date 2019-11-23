package legacy.decidir.sps.crypto

import org.scalatest.Matchers
import org.scalatest.FlatSpec
import org.scalatest._
import decidir.crypto.CryptoWrapper
import java.util.Base64
import com.decidir.encrypt.CryptoData

/**
 * @author martinpaoletta
 */
class CryptoDataTest  extends FlatSpec with Matchers {
  
  val llave1 = "b8c2ca4a7baed8e334dc49c01c7ea22d016f83bf66b288333a163233ed46565e"
  //val llaveEncriptada = "10e45e972204abbc06f505ed4bc96bcf65294c72bb04ded3e8fdd2f02eac5b2d9eab865575d090b3f05924a3a31de766c657ae818631fdeed5dfcd132e57b587fb"
  val llaveEncriptada = Base64.getDecoder.decode("MTBlNDVlOTcyMjA0YWJiYzA2ZjUwNWVkNGJjOTZiY2Y2NTI5NGM3MmJiMDRkZWQzZThmZGQyZjAyZWFjNWIyZDllYWI4NjU1NzVkMDkwYjNmMDU5MjRhM2EzMWRlNzY2YzY1N2FlODE4NjMxZmRlZWQ1ZGZjZDEzMmU1N2I1ODdmYg==")
  
  
//  def hexStringToByteArray(s: String) = s.grouped(2).map(cc => (Character.digit(cc(0),16) << 4 | Character.digit(cc(1),16)).toByte).toArray

//  "crypto wrapper" should "encrypt and decrypt" in {
//    
//    val llaveBytes = CryptoWrapper.hexa2byte(llave1.getBytes)
//    val cw1 = new CryptoWrapper("AES/CBC/PKCS5Padding", "checksum", "hex", llaveBytes, false)
//    val llaveEncriptadaBytes = llaveEncriptada// CryptoWrapper.hexa2byte(llaveEncriptada)
//    val cw2 = new CryptoWrapper("AES/CBC/PKCS5Padding", "checksum", "hex", llaveEncriptadaBytes, true)
//    cw2.habilitar(cw1)
//    println(cw2)
//    
//  }
  
//  "crypto data" should "encrypt and decrypt" in {
//    
//    val llave = CryptoData.keyEnabler(llave1)
//    println(llave)
//    val claveEncriptada = llaveEncriptada//hexStringToByteArray("10e45e972204abbc06f505ed4bc96bcf65294c72bb04ded3e8fdd2f02eac5b2d9eab865575d090b3f05924a3a31de766c657ae818631fdeed5dfcd132e57b587fb")
//    val fromDB = CryptoData(-1, "AES/CBC/PKCS5Padding", "checksum", "hex", llaveEncriptada, true)
//    println(fromDB)
//    val cw = fromDB.toEnabledCryptoWrapper(llave)
//    val str = "Martin Paoletta 123456"
//    val cypher = cw.encrypt(str.getBytes)
//    val decrypted = new String(cw.decrypt(cypher))
//    
//    decrypted shouldBe str
//    
//  }
  
//  "crypto 2 equals card_number" should "is not equals" in {
//    
//    val llave = CryptoData.keyEnabler(llave1)
//    println(llave)
//    val fromDB = CryptoData(-1, "AES/CBC/PKCS5Padding", "checksum", "hex", llaveEncriptada, true)
//    println(fromDB)
//    val cw = fromDB.toEnabledCryptoWrapper(llave)
//    val str = "4507990000004905"
//    
//    val cypher1 = cw.encrypt(str.getBytes)
//    val cyphers1 = cypher1.toString()
//    val decrypted1 = new String(cw.decrypt(cypher1))
//    
//    val cypher2 = cw.encrypt(str.getBytes)
//    val cyphers2 = cypher2.toString()
//    val decrypted2 = new String(cw.decrypt(cypher2))
//    
//    decrypted1 shouldBe decrypted2
//    cypher1 shouldBe cypher2
//  }
  
}