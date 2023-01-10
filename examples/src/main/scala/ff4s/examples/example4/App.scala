/*
 * Copyright 2022 buntec
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ff4s.examples.example4

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import ff4s.Store
import org.http4s.Uri

// This example demonstrates the built-in router functionality.
class App[F[_]: Async] {

  val F = Async[F]

  case class State(
      uri: Option[Uri] = None
  )

  sealed trait Action

  object Action {
    case class SetUri(uri: Uri) extends Action
    case class NavigateTo(uri: Uri) extends Action
  }

  implicit val store: Resource[F, Store[F, State, Action]] =
    ff4s.Store.withRouter[F, State, Action](State())(uri => Action.SetUri(uri))(
      (state, router) =>
        (action: Action) =>
          action match {
            case Action.NavigateTo(uri) => router.navigateTo(uri)
            case Action.SetUri(uri)     => state.update(_.copy(uri = Some(uri)))
          }
    )

  val dsl = ff4s.Dsl[F, State, Action]

  import dsl._
  import dsl.syntax.html._

  val heading = h1(cls := "m-4 text-4xl", "A Router App")

  val currentRoute = useState { state =>
    div(
      cls := "m-1",
      s"Current route: ${state.uri.map(_.renderString).getOrElse("")}"
    )
  }

  val app = useState { state =>
    val matchedPath = state.uri.flatMap { uri =>
      uri.path.segments match {
        case Vector(path) => Some(path.toString)
        case _            => None
      }
    }

    div(
      cls := "flex flex-col items-center h-screen",
      heading,
      currentRoute,
      div(
        cls := "m-2 flex flex-col items-center",
        p("Navigation"),
        List("foo", "bar", "baz").map { path =>
          div(
            cls := s"m-1 w-full rounded text-center cursor-pointer border ${if (matchedPath == Some(path)) "bg-purple-300"
              else ""}",
            path,
            onClick := (_ =>
              Some(Action.NavigateTo(Uri.unsafeFromString(path)))
            )
          )
        }
      )
    )
  }

  def run: F[Nothing] = app.renderInto("#app")

}