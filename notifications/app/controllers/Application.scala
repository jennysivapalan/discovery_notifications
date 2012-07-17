package controllers

import db.{Comment, Blog, User, DBConnection}
import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.json.Writes._
import com.codahale.jerkson.Json._
import play.api.libs.ws.WS



object Application extends Controller with DefaultWrites {
  
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  //GET
  def userSubscriptions(id: String) = Action {
    request =>{
      val callback = request.queryString.get("callback").map(_.head)
      val user: Option[User] = DBConnection.query(id)

      user.map(u =>
        {
          val subscribedBlogs: List[Blog] = u.subscribedBlogs
          subscribedBlogs.foreach( blog => {
            blog.count = getLiveBlogCount(blog)
          })
          getCommentUpdates(u)
        })


      callback match {
        case Some(func:String) => Ok(func+"("+generate(user)+")").as("application/json")
        case _ => Ok(generate(user)).as("application/json")
      }

    }
  }

  //POST - BLOG SUBSCRIPTION / LAST-VIEWED UPDATE
  def userUpdate(id:String) =  Action {
    request =>{
      val path = request.body.asFormUrlEncoded.get("path").head
      val lastViewedId = request.body.asFormUrlEncoded.get("lastViewedId").head
      val blog = new Blog(path, lastViewedId)
      DBConnection.save(id, blog)
      Ok("Got request [" + request.body.asFormUrlEncoded + "]")
    }
  }

  //POST - COMMENT VIEWED UPDATE

  def userUpdatesMultipleNotifications(id: String) = Action {
    request => {

      var numberOfNotificationsToUpdate = request.body.asFormUrlEncoded.get("numberOfNotifications").head.toInt
      for (i <- 1 to numberOfNotificationsToUpdate) {
        val path = request.body.asFormUrlEncoded.get("path%s".format(i)).head
        val lastViewedId = request.body.asFormUrlEncoded.get("lastViewedId%s".format(i)).head
        val blog = new Blog(path, lastViewedId)
        DBConnection.save(id, blog)
      }
      Ok("Saved")
    }

  }

  def getLiveBlogCount(blog : Blog) : Int = {
    val url = "http://flxapi.gucode.gnl:8080/api/live/%s?offset=%s".format(blog.id, blog.lastViewedId)
    println(url)
    val response = WS.url(url).get().value.get
    if (response.status==200){
      val body = response.body
      val blocks = (Json.parse(body)\"content"\"blocks")
      if(blocks.isInstanceOf[JsArray])
        blocks.asInstanceOf[JsArray].value.size
      else
        0
    }else 0
  }

  def getCommentUpdates(user: User) {
    val url = "http://discussionapi.gucode.co.uk/discussion-api/profile/%s/comments".format("3853811")
    println(url)
    val response = WS.url(url).get().value.get
    val body = response.body
    val commentJson = (Json.parse(body)\"comments")
    val user = new User("3853811")

    if(commentJson.isInstanceOf[JsArray]){
      commentJson.asInstanceOf[JsArray].value.foreach(c=>{
        val commentId = c.\("id").toString
        val numResponses = c.\("numResponses").toString.toInt
        val numRecommends = c.\("numRecommends").toString.toInt
        val isHighlighted = c.\("isHighlighted").toString.toBoolean

        user.comments.find(_.id==commentId) match {
          case Some(commentInDB) => {
            if (commentInDB.lastViewedResponseCount != numResponses)
              commentInDB.replyCountUpdated = true//numResponses - commentInDB.lastViewedResponseCount
              //commentInDB.lastViewedResponseCount = numResponses

            if (commentInDB.lastViewedRecommendCount != numRecommends)
              commentInDB.recommendCountUpdated = true//numRecommends - commentInDB.lastViewedRecommendCount
              //commentInDB.lastViewedRecommendCount = numRecommends

            if (commentInDB.isHighlighted != isHighlighted)
              commentInDB.highlightUpdated = true
              //commentInDB.isHighlighted = isHighlighted

          }
          case None => {
            val newComment = Comment(commentId, numResponses, numRecommends, isHighlighted, numResponses!=0, numRecommends!=0, isHighlighted)
            user.comments = user.comments :+ newComment
          }
        }
      })
    }
  }
  
}