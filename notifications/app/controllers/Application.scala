package controllers


import db.{Comment, DBConnection, Blog, User}
import play.api._
import play.api.mvc._
import play.api.libs.json._
import com.codahale.jerkson.Json._
import play.api.libs.ws.WS
import play.api.Play.current



object Application extends Controller with DefaultWrites {

  val baseUrl = Play.configuration.getString("flexible.content.url").getOrElse("")
  
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }


  /**
   * GET
   */
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


  /**
   * POST
   */
  def userUpdate(id:String) =  Action {
    request =>{
      val path = request.body.asFormUrlEncoded.get("path").head
      val lastViewedId = request.body.asFormUrlEncoded.get("lastViewedId").head
      val blog = new Blog(path, lastViewedId)
      DBConnection.save(id, blog)
      Ok("Got request [" + request.body.asFormUrlEncoded + "]")
    }
  }


  /**
   * POST
   */
  def userUpdatesAllSubscriptions(id: String) = Action {
    request => {

      val optionUser: Option[User] = DBConnection.query(id)
      if (optionUser.isDefined) {
        val user = optionUser.get
        user.subscribedBlogs.foreach(blog => {
          blog.lastViewedId = try{
            getLastLiveBlogId(blog.id)
          } catch {
            case _=> blog.lastViewedId
          }
        })
        user.subscribedComments.foreach(comment => {
          comment.highlightUpdated = false
          comment.recommendCountUpdated = false
          comment.replyCountUpdated = false
        })

        DBConnection.save(user)
      }

    Ok("Updated")
    }
  }



  def getLiveBlogCount(blog : Blog) : Int = {
    val url = "%s%s?offset=%s".format(baseUrl, blog.id, blog.lastViewedId)
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
    val url = "http://discussionapi.gucode.co.uk/discussion-api/profile/%s/comments".format(user.id)
    println(url)
    val response = WS.url(url).get().value.get
    val body = response.body
    val commentJson = (Json.parse(body)\"comments")
    var changed = false

    if(commentJson.isInstanceOf[JsArray]){
      commentJson.asInstanceOf[JsArray].value.foreach(c=>{
        val commentId = c.\("id").toString
        val numResponses = c.\("numResponses").toString.toInt
        val numRecommends = c.\("numRecommends").toString.toInt
        val isHighlighted = c.\("isHighlighted").toString.toBoolean
        val discussionUrl = c.\("discussion").\("url").toString
        val discussionTitle = c.\("discussion").\("title").toString

        user.subscribedComments.find(_.id==commentId) match {
          case Some(commentInDB) => {
            if (commentInDB.responseCount != numResponses)
              commentInDB.replyCountUpdated = true//numResponses - commentInDB.lastViewedResponseCount
              commentInDB.responseCount = numResponses
              changed = true

            if (commentInDB.recommendCount != numRecommends)
              commentInDB.recommendCountUpdated = true//numRecommends - commentInDB.lastViewedRecommendCount
              commentInDB.recommendCount = numRecommends
            changed = true

            if (commentInDB.isHighlighted != isHighlighted)
              commentInDB.highlightUpdated = true
              commentInDB.isHighlighted = isHighlighted
            changed = true

          }
          case None => {
            val newComment = Comment(commentId, discussionTitle, discussionUrl, numResponses, numRecommends,
              isHighlighted, numResponses!=0, numRecommends!=0, isHighlighted)
            user.subscribedComments = user.subscribedComments :+ newComment
            changed = true
          }
        }
      })
    }
    if (changed)
      DBConnection.save(user)
  }


  def getLastLiveBlogId(path: String) : String = {
    val url = "%s%s".format(baseUrl, path)
    println(url)
    val response = WS.url(url).get().value.get
    val body = response.body
    val lastLiveBlockId = (Json.parse(body) \ "content" \ "blocks" \\ "id").head
    lastLiveBlockId.toString().replaceAll("\"", "")
  }
  
}