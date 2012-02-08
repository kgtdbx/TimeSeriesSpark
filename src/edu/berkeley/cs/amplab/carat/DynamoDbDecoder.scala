package edu.berkeley.cs.amplab.carat

import com.amazonaws.services.dynamodb.model.AttributeValue
import com.amazonaws.services.dynamodb.model.GetItemRequest
import com.amazonaws.services.dynamodb.model.Key
import com.amazonaws.services.dynamodb.model.ScanRequest
import com.amazonaws.services.dynamodb.model.QueryRequest
import collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import scala.collection.immutable.HashMap
import java.util.List
import java.util.Map
import com.amazonaws.services.dynamodb.model.DeleteItemRequest
import com.amazonaws.services.dynamodb.model.Condition
import com.amazonaws.services.dynamodb.model.QueryResult

object DynamoDbDecoder {
  
  val THROUGHPUT_LIMIT = 30
/**
 * Test program. Lists contents of tables.
 */
  def main(args: Array[String]) {
    val tables = DynamoDbEncoder.dd.listTables().getTableNames()
    S3Decoder.printList(tables)
    val it = tables.iterator()
    while (it.hasNext) {
      S3Decoder.printList(getAllItems(it.next)._2)
    }
    
    debug_deleteSystemVersion()
  }
  
  def debug_deleteSystemVersion(){
    deleteItems(registrationTable, regsUuid, regsTimestamp, ("systemVersion", "5.0.1"))
    deleteItems(registrationTable, regsUuid, regsTimestamp, ("systemVersion", "7.0.1RC1"))
  }
  
  def deleteItems(table:String, keyName:String, vals: (String, Any)*){
    val items = filterItems(table, vals: _*)
    for (k <- items._2){
      val key = k.get(keyName).getS()
      if (key != ""){
        println("Going to delete " +keyName +" = " + key + ": " + k + " from " + table)
        //deleteItem(key)
      }
    }
  }
  
  def deleteItems(table:String, hashKeyName:String, rangeKeyName:String, vals: (String, Any)*){
    val items = filterItems(table, vals: _*)
    for (k <- items._2){
      val hkey = k.get(hashKeyName).getS()
      val rkey = k.get(rangeKeyName).getS()
      if (hkey != null && rkey != ""){
        println("Going to delete " +hashKeyName +" = " + hkey +", " +rangeKeyName +" = " + rkey + ": " + k + " from " + table)
        deleteItem(table, hkey, rkey)
      }
    }
  }
  
  def deleteItem(tableName:String, keyPart:String) {
    val getKey = new Key()
    val ks = getKey.withHashKeyElement(new AttributeValue(keyPart))
    val d = new DeleteItemRequest(tableName, ks)
    DynamoDbEncoder.dd.deleteItem(d)
  }

  def deleteItem(tableName:String, keyPart: String, rangeKeyPart: Any) {
    val ks = new Key().withHashKeyElement(new AttributeValue(keyPart))
    .withRangeKeyElement(DynamoDbEncoder.toAttributeValue(rangeKeyPart))
    val d = new DeleteItemRequest(tableName, ks)
    DynamoDbEncoder.dd.deleteItem(d)
  }
  
  def filterItems(table:String, vals: (String, Any)*) = {
    val s = new ScanRequest(table)
    val conds = DynamoDbEncoder.convertToMap[Condition](vals.map(x => {
      (x._1, new Condition().withComparisonOperator("EQ").withAttributeValueList(new AttributeValue(x._2.toString())))
    }))
    s.setScanFilter(conds)
    val sr = DynamoDbEncoder.dd.scan(s)
    (sr.getLastEvaluatedKey(), sr.getItems())
  }
  
  
  def getAllItems(table:String) = {
    println("Getting all items from table " + table)
    val s = new ScanRequest(table)
    //s.setLimit(THROUGHPUT_LIMIT) 
    val sr = DynamoDbEncoder.dd.scan(s)
    (sr.getLastEvaluatedKey(), sr.getItems())
  }
  
  def getAllItems(table:String, firstKey:Key) = {
    println("Getting all items from table " + table + " starting with " + firstKey)
    val s = new ScanRequest(table)
    s.setExclusiveStartKey(firstKey)
    //s.setLimit(THROUGHPUT_LIMIT)
    val sr = DynamoDbEncoder.dd.scan(s)
    (sr.getLastEvaluatedKey(), sr.getItems())
  }

  def getItems(table: String, keyName:String, keyPart: String) = {
    val q = new ScanRequest(table)
    val conds = new java.util.HashMap[String, Condition]
    conds += ((keyName, new Condition().withComparisonOperator("EQ").withAttributeValueList(new AttributeValue(keyPart))))
    //q.setLimit(THROUGHPUT_LIMIT)
    val sr = DynamoDbEncoder.dd.scan(q)
    (sr.getLastEvaluatedKey(), sr.getItems())
  }
  
  def getItemsIN(table: String, keyName:String, keyPart: String) = {
    val q = new ScanRequest(table)
    val conds = new java.util.HashMap[String, Condition]
    conds += ((keyName, new Condition().withComparisonOperator("IN").withAttributeValueList(new AttributeValue(keyPart))))
    //q.setLimit(THROUGHPUT_LIMIT)
    val sr = DynamoDbEncoder.dd.scan(q)
    (sr.getLastEvaluatedKey(), sr.getItems())
  }
  
  def getItems(table: String, keyName:String, keyPart: String, lastKey:Key) = {
    val q = new ScanRequest(table)
    val conds = new java.util.HashMap[String, Condition]
    conds += ((keyName, new Condition().withComparisonOperator("EQ").withAttributeValueList(new AttributeValue(keyPart))))
    q.setExclusiveStartKey(lastKey)
    //q.setLimit(THROUGHPUT_LIMIT)
    val sr = DynamoDbEncoder.dd.scan(q)
    (sr.getLastEvaluatedKey(), sr.getItems())
  }
  
  def getItemsIN(table: String, keyName:String, keyPart: String, lastKey:Key) = {
    val q = new ScanRequest(table)
    val conds = new java.util.HashMap[String, Condition]
    conds += ((keyName, new Condition().withComparisonOperator("IN").withAttributeValueList(new AttributeValue(keyPart))))
    q.setExclusiveStartKey(lastKey)
    //q.setLimit(THROUGHPUT_LIMIT)
    val sr = DynamoDbEncoder.dd.scan(q)
    (sr.getLastEvaluatedKey(), sr.getItems())
  }

  
  def getItems(table: String, keyPart: String): (Key, List[Map[String, AttributeValue]]) = {
    val q = new QueryRequest(table, new AttributeValue(keyPart))
    getItems(q)
  }

  def getItems(table: String, keyPart: String, lastKey: Key): (Key, List[Map[String, AttributeValue]]) = {
    val q = new QueryRequest(table, new AttributeValue(keyPart)).withExclusiveStartKey(lastKey)
    getItems(q)
  }
  
  def getItems(q: QueryRequest): (Key, List[Map[String, AttributeValue]]) = {
     var timedOut = true
    var sr: QueryResult = null
    while (timedOut) {
      try {
        sr = DynamoDbEncoder.dd.query(q)
        timedOut = false
      } catch {
        case timeout: java.net.SocketTimeoutException => {
          timedOut = true
          println(timeout + " trying again in 1s...")
          Thread.sleep(1000)
        }
        // Problem exception?
        case x => { throw x }
      }
    }
    (sr.getLastEvaluatedKey(), sr.getItems())
  }
  
  def getItem(table:String, keyPart:String) = {
    val key = new Key().withHashKeyElement(new AttributeValue(keyPart))
    val g = new GetItemRequest(table, key)
    DynamoDbEncoder.dd.getItem(g).getItem()
  }
  
  def getItem(table:String, keyParts:(String, String)) = {
    val key = new Key().withHashKeyElement(new AttributeValue(keyParts._1))
    .withRangeKeyElement(new AttributeValue(keyParts._2))
    val g = new GetItemRequest(table, key)
    getVals(DynamoDbEncoder.dd.getItem(g).getItem())
  }
  
   def getVals(map: Map[String, AttributeValue]) = {
     var stuff:Map[String, Any] = new HashMap[String, Any]()
    for (k <- map) {
      var num = k._2.getN()
      val nums = k._2.getNS()
      if (num != null) {
        if (num.indexOf('.') >= 0 || num.indexOf(',') >= 0)
          stuff += ((k._1, k._2.getN().toDouble))
        else
          stuff += ((k._1, k._2.getN().toLong))
      } else if (k._2.getS() != null) {
        stuff += ((k._1, k._2.getS()))
      } else if (nums != null) {
        if (nums.length > 0) {
          num = nums.get(0)
          if (num != null) {
            if (num.indexOf('.') >= 0 || num.indexOf(',') >= 0)
              stuff += ((k._1, nums.map(_.toDouble).toSeq))
            else
              stuff += ((k._1, nums.map(_.toLong).toSeq))
          }
        } else
          stuff += ((k._1, nums.map(_.toLong).toSeq))
      } else if (k._2.getSS() != null) {
        stuff += ((k._1, k._2.getSS().toSeq))
      }
    }
    stuff
  }
}
