package db
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import com.novus.salat.annotations._
import com.novus.salat.dao._


object DBConnection{
  val mongoConn = MongoConnection() // default localhost, 27017
  val mongoColl = mongoConn("notifications")("users")

  object UserDAO extends SalatDAO[User, String](collection = mongoColl)

  def query(id: String): Option[User] = {
    val q = MongoDBObject("id" -> id)
    val user = mongoColl.findOne(q).map(grater[User].asObject(_))
    print(user)
    user
  }

  def save(user: User) {
    val _id = UserDAO.insert(user)
  }



}


case class User(@Key("_id") id: String, subscribedBlogs: List[Blog])
case class Blog(@Key("_id") id: String, lastViewedId: String)

