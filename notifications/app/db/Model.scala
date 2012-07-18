package db
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import com.novus.salat.annotations._
import com.novus.salat.dao._
import org.joda.time.DateTime
import java.util.Date


object DBConnection{
  val mongoConn = MongoConnection() // default localhost, 27017
  val mongoColl = mongoConn("notifications")("users")

  object UserDAO extends SalatDAO[User, String](collection = mongoColl)

  def query(id: String): Option[User] = {
    val user = UserDAO.findOneByID(id)
    user
  }


  def createNewUser(id: String) {
    if (UserDAO.findOneByID(id).isEmpty){
      val _id = UserDAO.insert(new User(id))
    }
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

  def save(id: String, tagUpdate: Tag) {
    UserDAO.findOneByID(id) match {
      case Some(user: User) => {
        user.subscribedTags.find(t => tagUpdate.id==t.id) match {
          case Some(tag: Tag) => {
            tag.timeLastViewed = tagUpdate.timeLastViewed
          }
          case None => {
            user.subscribedTags = user.subscribedTags :+ tagUpdate
          }
        }
        UserDAO.save(user)
      }
      case None => {
        UserDAO.insert(new User(id, Nil, Nil, List(tagUpdate)))
      }


    }
  }

  def save(user : User) {
    UserDAO.save(user)
  }



}


case class User(@Key("_id") id: String, var subscribedBlogs: List[Blog]=List(), var subscribedComments: List[Comment]=List(), var subscribedTags: List[Tag]=List())
case class Blog(@Key("_id") id: String, var lastViewedId: String, @Ignore var count: Int = 0)
case class Tag(@Key("_id") id: String, var timeLastViewed: String, @Ignore var byLineImageUrl:String ="", @Ignore var contributor:String="", @Ignore var count: Int = 0, @Ignore var content: List[Content] = List())
case class Content(path:String ="", webTitle: String = "", webUrl:String = "", headline:String="", standfirst:String="", thumbnail:String="")
case class Comment(@Key("_id") id: String,
                   discussionTitle: String,
                   discussionUrl: String,
                   var responseCount: Int,
                   var recommendCount: Int,
                   var isHighlighted: Boolean,
                   var replyCountUpdated: Boolean,
                   var recommendCountUpdated: Boolean,
                   var highlightUpdated: Boolean)

