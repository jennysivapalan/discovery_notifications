import com.mongodb.casbah.Imports._
import com.mongodb.casbah.conversions.scala._


object DBConnection{
  val mongoConn = MongoConnection() // default localhost, 27017
  val mongoDB = mongoConn("notifications")("users")
}

