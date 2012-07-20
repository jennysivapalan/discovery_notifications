package controllers


import db.{Comment, DBConnection, Blog, User, Tag, Content}
import play.api._
import play.api.mvc._
import play.api.libs.json._
import com.codahale.jerkson.Json._
import play.api.libs.ws.WS
import play.api.Play.current
import java.util.Date
import org.joda.time.format.{ISODateTimeFormat, DateTimeFormat}
import java.util
import org.joda.time.{DateTimeZone, DateTime}


object Application extends Controller with DefaultWrites {

  val flexibleContentUrl = Play.configuration.getString("flexible.content.url").getOrElse("")
  val contentApiUrlLive = "http://content.guardianapis.com/"
  val contentApiUrlCode = "http://slrmst.gucode.gnl:8700/content-api/api/"

  val timeRightNow = ISODateTimeFormat.dateTime().print(new DateTime(DateTimeZone.UTC))
  
  def index = Action {
    Ok(views.html.index("Your new application is ready.")).withHeaders("Access-Control-Allow-Origin" -> "*")
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
          getTagUpdates(u)
        })


      callback match {
        case Some(func:String) => Ok(func+"("+generate(user)+")").as("application/json").withHeaders("Access-Control-Allow-Origin" -> "*")
        case _ => Ok(generate(user)).as("application/json").withHeaders("Access-Control-Allow-Origin" -> "*")
      }

    }
  }


  /**
   * POST
   */
  def userSubscribesToBlog(id:String) =  Action {
    request =>{
      val path = request.body.asFormUrlEncoded.get("path").head
      val lastViewedId = request.body.asFormUrlEncoded.get("lastViewedId").head
      val url = contentApiUrlCode + path +  "?format=json&show-fields=trail-text,thumbnail"
      println("Code url:- " + url)
      val response = WS.url(url).get().value.get
      var body = response.body
      var title = (Json.parse(body)\"response"\"content"\"webTitle").toString().replaceAll("\"", "")
      var trailText = (Json.parse(body)\"response"\"content"\"fields"\"trailText").toString().replaceAll("\"", "")
      var thumbnail = (Json.parse(body)\"response"\"content"\"fields"\"thumbnail").toString().replaceAll("\"", "")

      //as using code we need to replace the thumbnail host
      thumbnail = thumbnail.replace("guim", "guimcode")

      val blog = new Blog(path, lastViewedId, title, trailText, thumbnail)
      DBConnection.save(id, blog)
      Ok("Got request [" + request.body.asFormUrlEncoded + "]").withHeaders("Access-Control-Allow-Origin" -> "*")
    }
  }

  /**
   * POST
   */
  def userSubscribesToTag(id:String) = Action {
    request => {
         val tagId = request.body.asFormUrlEncoded.get("tag").head
      val tag = new Tag(tagId,ISODateTimeFormat.dateTime().print(new DateTime(2012, 7, 14,1,4, DateTimeZone.UTC)))
      //val tag = new Tag(tagId,ISODateTimeFormat.dateTime().print(new DateTime(DateTimeZone.UTC)))
      println(tag)
      DBConnection.save(id, tag)
      Ok("Got request [" + request.body.asFormUrlEncoded + "]").withHeaders("Access-Control-Allow-Origin" -> "*")
    }

  }


  /**
   * POST (Clear all notifications)
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
        user.subscribedTags.foreach(
          _.timeLastViewed = timeRightNow
        )

        DBConnection.save(user)
      }

    Ok("Updated").withHeaders("Access-Control-Allow-Origin" -> "*")
    }
  }


  def unsubscribeFromBlog(id: String) = Action {
    request => {
      val optionUser: Option[User] = DBConnection.query(id)
      if (optionUser.isDefined) {
        val user = optionUser.get
        val blogId = request.body.asFormUrlEncoded.get("blog").head
        println(blogId)
        user.subscribedBlogs = user.subscribedBlogs.filterNot(b=>{b.id==blogId})
        DBConnection.save(user)
      }
    }
    Ok("ok").withHeaders("Access-Control-Allow-Origin" -> "*")
  }

  def unsubscribeFromTag(id: String) = Action {
    request => {
      val optionUser: Option[User] = DBConnection.query(id)
      if (optionUser.isDefined) {
        val user = optionUser.get
        val tagId = request.body.asFormUrlEncoded.get("tag").head
        println(tagId)
        user.subscribedTags = user.subscribedTags.filterNot(t=>{t.id==tagId})
        DBConnection.save(user)
      }
    }
    Ok("ok").withHeaders("Access-Control-Allow-Origin" -> "*")
  }

  /**
   * POST
   */
  def createNewUser(userId: String) = Action{
    DBConnection.createNewUser(userId)
    Ok("Done.").withHeaders("Access-Control-Allow-Origin" -> "*")
  }

  /**
   * POST
   */
  def clearCommentNotification(userId: String) = Action {
    request => {
      val commentId = request.body.asFormUrlEncoded.get("commentId").head
      val notificationType = request.body.asFormUrlEncoded.get("notificationType").head
      println(userId)
      println(commentId)
      println(notificationType)
      val optionUser: Option[User] = DBConnection.query(userId)
      if (optionUser.isDefined) {
        val user = optionUser.get
        user.subscribedComments.find(c => c.id==commentId).foreach(c => {
          if (notificationType.contains("highlight")) {
            c.highlightUpdated=false
          }else if (notificationType.contains("recommend"))
            c.recommendCountUpdated=false
          else if (notificationType.contains("reply"))
            c.replyCountUpdated=false
          else if (notificationType.contains("all")){
            c.highlightUpdated=false
            c.recommendCountUpdated=false
            c.replyCountUpdated=false
          }
        })
        DBConnection.save(user)
      }
    }
    Ok("Updated").withHeaders("Access-Control-Allow-Origin" -> "*")
  }

  /**
   * POST
   */
  def clearTagNotification(userId: String) = Action {
    request => {
      val tagId = request.body.asFormUrlEncoded.get("tag").head
      println(tagId)
      val optionUser: Option[User] = DBConnection.query(userId)
      if (optionUser.isDefined) {
        val user = optionUser.get
        val tagsFound = user.subscribedTags.filter(t => t.id == tagId)
        tagsFound.foreach(t=> {
          t.timeLastViewed = timeRightNow
        })

        DBConnection.save(user)
      }
    }
    Ok("Updated").withHeaders("Access-Control-Allow-Origin" -> "*")
  }


  def getLiveBlogCount(blog : Blog) : Int = {
    val url = "%s%s?offset=%s".format(flexibleContentUrl, blog.id, blog.lastViewedId)
    println("Flexible content url:- " + url)
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

  def getTagUpdates(user:User) {

    val basicQueryParams = "?format=json&show-fields=headline,standfirst,thumbnail&order-by=newest"

    user.subscribedTags.foreach({
      tag=>
        val lastViewedTime = tag.timeLastViewed
        val id= tag.id
        val url = "%s%s%s&from-date=%s".format(contentApiUrlLive, id, basicQueryParams, lastViewedTime)
        println(url)
        val response = WS.url(url).get().value.get
        val body = response.body

        var tagJson = (Json.parse(body)\"response"\"tag")
        var byLineImageUrl = tagJson.\("bylineImageUrl").toString().replaceAll(("\""), "")
        tag.byLineImageUrl = byLineImageUrl

        var contributor = tagJson.\("webTitle").toString().replaceAll(("\""), "")
        tag.contributor = contributor


        val resultsJson = (Json.parse(body)\"response"\"results")

        if(resultsJson.isInstanceOf[JsArray]){
          resultsJson.asInstanceOf[JsArray].value.foreach(r => {
            val path = r.\("id").toString().replace("\"","")
            val webTitle = r.\("webTitle").toString().replace("\"","")
            val webUrl = r.\("webUrl").toString().replace("\"","")
            val headline = (r.\("fields")\("headline")).toString().replace("\"","")
            val standfirst = (r.\("fields")\("standfirst")).toString().replace("\"","")
            val thumbnail = (r.\("fields")\("thumbnail")).toString().replace("\"","")


            tag.content = tag.content :+ new Content(path, webTitle, webUrl, headline, standfirst, thumbnail)
          })
        }

        tag.count = tag.content.size



    })


  }

  def getCommentUpdates(user: User) {
    val url = "http://discussionapi.gucode.co.uk/discussion-api/profile/%s/comments".format(user.id)
    println(url)
    val response = WS.url(url).get().value.get
    val body = response.body
    val commentJson = (Json.parse(body)\"comments")
    var changed = false

    val userName = Json.parse(body)\("userProfile")\("displayName").toString.replace("\"","")
    if(commentJson.isInstanceOf[JsArray]){
      commentJson.asInstanceOf[JsArray].value.foreach(c=>{
        val commentId = c.\("id").toString
        val numResponses = c.\("numResponses").toString.toInt
        val numRecommends = c.\("numRecommends").toString.toInt
        val isHighlighted = c.\("isHighlighted").toString.toBoolean
        val discussionUrl = c.\("discussion").\("url").toString.replace("\"","")
        val discussionTitle = c.\("discussion").\("title").toString.replace("\"","")
        val commentBody = c.\("body").toString.replace("\"","")
        val commentUrlOnProfilePage = "http://www.gucode.co.uk/discussion/user-comments/id/"+user.id+"#comment-"+commentId

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
            val newComment = Comment(commentId, userName.toString.replace("\"",""), commentBody, commentUrlOnProfilePage, discussionTitle, discussionUrl, numResponses, numRecommends,
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
    val url = "%s%s".format(flexibleContentUrl, path)
    println(url)
    val response = WS.url(url).get().value.get
    val body = response.body
    val lastLiveBlockId = (Json.parse(body) \ "content" \ "blocks" \\ "id").head
    lastLiveBlockId.toString().replaceAll("\"", "")
  }
  
}