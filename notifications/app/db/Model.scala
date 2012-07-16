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
    val user = UserDAO.findOneByID(id)
    user
  }

  def save(id: String, blogUpdate: Blog) {
      UserDAO.findOneByID(id) match  {
      case Some(user: User) =>{
        user.subscribedBlogs.find(b => blogUpdate.id==b.id) match {
          case Some(blog: Blog) => {
            blog.lastViewedId = blogUpdate.lastViewedId
          }
          case None => {
            user.subscribedBlogs = user.subscribedBlogs :+ blogUpdate
          }
        }
        val cr = UserDAO.save(user)
      }
      case None => {
        val _id = UserDAO.insert(new User(id, List(blogUpdate)))
      }
    }
  }



}


case class User(@Key("_id") id: String, var subscribedBlogs: List[Blog])
case class Blog(@Key("_id") id: String, var lastViewedId: String)

