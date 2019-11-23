package services.cybersource

import com.decidir.coretx.api.Item

trait ProductMapping {
    
  protected def mapItems(items: List[Item]) = {
    items.zipWithIndex.map(p => mapItem(p._1, p._2))
  }
  
  private def mapItem(item: Item, ndx: Int) = {  
    <urn:item id={ndx.toString}>  
			{item.unit_price.map(up => <urn:unitPrice>{formatAmount(up)}</urn:unitPrice>).getOrElse(Nil)}
      {item.quantity.map(q => <urn:quantity>{q}</urn:quantity>).getOrElse(Nil)}
		  {item.code.map(c => <urn:productCode>{c}</urn:productCode>).getOrElse(Nil)}
		  {item.name.map(c => <urn:productName>{c}</urn:productName>).getOrElse(Nil)}
		  {item.sku.map(c => <urn:productSKU>{c}</urn:productSKU>).getOrElse(Nil)}
		  {item.total_amount.map(c => <urn:totalAmount>{formatAmount(c)}</urn:totalAmount>).getOrElse(Nil)}
		  {item.description.map(c => <urn:productDescription>{c}</urn:productDescription>).getOrElse(Nil)}
		</urn:item>
  }  
  
  private def formatAmount(amount: Long) = {
    val formatted = "%.2f".format(BigDecimal(amount)/100)
    formatted.replace(',', '.') // Para evitar manejar el Locale
  }
}