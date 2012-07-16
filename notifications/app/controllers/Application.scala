package controllers

import db.{Blog, User, DBConnection}
import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.json.Writes._
import com.codahale.jerkson.Json._


object Application extends Controller with DefaultWrites {
  
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def userSubscriptions(id: String) = Action {
    val user: Option[User] = DBConnection.query(id)

    Ok(generate(user)).as("application/json")
  }

  def userUpdate(id:String) =  Action {
    request =>{
      val path = request.queryString.get("path").get.head
      val lastViewedId = request.queryString.get("lastViewedId").get.head
      println(path)
      println(lastViewedId)
      val blog = new Blog(path, lastViewedId)
      val user = new User(id, List(blog))
      DBConnection.save(user)
      Ok("Got request [" + request.queryString + "]")
    }

  }
  
}