package controllers

import _root_.db.{DBConnection, Blog, User}
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
          blog.lastViewedId = getLastLiveBlogId(blog.id)
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
    val body = response.body
    val count = (Json.parse(body)\"content"\"blocks").asInstanceOf[JsArray].value.size
    count
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