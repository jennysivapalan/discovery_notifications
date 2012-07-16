package controllers

import db.{Blog, User, DBConnection}
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

  def userUpdate(id:String) =  Action {
    request =>{
      val path = request.body.asFormUrlEncoded.get("path").head
      val lastViewedId = request.body.asFormUrlEncoded.get("lastViewedId").head
      val blog = new Blog(path, lastViewedId)
      DBConnection.save(id, blog)
      Ok("Got request [" + request.body.asFormUrlEncoded + "]")
    }

  }

  def getLiveBlogCount(blog : Blog) : Int = {
    val url = "http://gnm41146.int.gnl:9081/api/preview/%s?%s".format(blog.id, blog.lastViewedId)
    println(url)
    val response = WS.url(url).get().value.get
    val body = response.body
    val count = (Json.parse(body)\"content"\"liveBlocks").asInstanceOf[JsArray].value.size
    count
  }
  
}