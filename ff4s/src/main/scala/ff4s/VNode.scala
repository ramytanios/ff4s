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

package ff4s

import cats.effect.std.Dispatcher

import org.scalajs.dom

trait VNode[F[_]] {

  private[ff4s] def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode

}

private[ff4s] object VNode {

  def create[F[_], Action](
      tag: String,
      children: Seq[VNode[F]],
      cls: Option[String],
      key: Option[String],
      props: Map[String, Any],
      attrs: Map[String, snabbdom.AttrValue],
      style: Map[String, String],
      handlers: Map[String, dom.Event => Option[Action]],
      onInsert: Option[dom.Element => Action],
      onDestroy: Option[dom.Element => Action],
      actionDispatch: Action => F[Unit]
  ) = new VNode[F] {
    override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode = {

      val insertHook = onInsert.map { hook =>
        new snabbdom.InsertHook {
          override def apply(vNode: snabbdom.VNode): Any =
            dispatcher.unsafeRunAndForget(
              actionDispatch(hook(vNode.elm.get.asInstanceOf[dom.Element]))
            )
        }
      }

      val destroyHook = onDestroy.map { hook =>
        new snabbdom.DestroyHook {
          override def apply(vNode: snabbdom.VNode): Any =
            dispatcher.unsafeRunAndForget(
              actionDispatch(hook(vNode.elm.get.asInstanceOf[dom.Element]))
            )
        }
      }

      val data = snabbdom.VNodeData(
        attrs = cls.fold(attrs)(cls => attrs + ("class" -> cls)),
        props = props,
        style = style,
        key = key,
        hook = Some(snabbdom.Hooks(insert = insertHook, destroy = destroyHook)),
        on = handlers.map { case (eventName, handler) =>
          (eventName -> ((e: dom.Event) =>
            handler(e).fold(())(action =>
              dispatcher.unsafeRunAndForget(actionDispatch(action))
            )
          ))
        }
      )

      snabbdom.h(tag, data, children.map(_.toSnabbdom(dispatcher)).toArray)

    }
  }

  def empty[F[_]](tag: String) = new VNode[F] {
    override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode =
      snabbdom.h(tag)
  }

  def parentNode[F[_]](tag: String, children: VNode[F]*) = new VNode[F] {
    override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode =
      snabbdom.h(
        tag,
        children.map(_.toSnabbdom(dispatcher)).toArray
      )
  }

  implicit def fromString[F[_]](text: String) = new VNode[F] {
    override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode =
      text
  }

  implicit class VNodeOps[F[_]](vnode: VNode[F]) {

    def withClass(cls: String): VNode[F] = setClass(vnode, cls)

    def withStyle(style: Map[String, String]): VNode[F] = setStyle(vnode)(style)

    def withProps(props: Map[String, Any]): VNode[F] = setProps(vnode)(props)

    def withAttrs(attrs: Map[String, snabbdom.AttrValue]): VNode[F] =
      setAttrs(vnode)(attrs)

    def withKey(key: String): VNode[F] =
      setKey(vnode)(key)

    def withEventHandler(
        eventName: String,
        handler: dom.Event => F[Unit]
    ): VNode[F] =
      setEventHandler(vnode, eventName)(handler)

    def withOnInsertHook(onInsert: dom.Element => F[Unit]): VNode[F] =
      setOnInsertHook(vnode)((v: snabbdom.VNode) =>
        onInsert(v.elm.get.asInstanceOf[dom.Element])
      )

    def withDestroyHook(onDestroy: dom.Element => F[Unit]): VNode[F] =
      setDestroyHook(vnode)((v: snabbdom.VNode) =>
        onDestroy(v.elm.get.asInstanceOf[dom.Element])
      )

  }

  def setClass[F[_]](vnode: VNode[F], cls: String): VNode[F] = new VNode[F] {

    override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode = {
      val vp = vnode.toSnabbdom(dispatcher)
      vp.data = vp.data.copy(attrs = vp.data.attrs + ("class" -> cls))
      vp
    }

  }

  def setEventHandler[F[_]](vnode: VNode[F], eventName: String)(
      handler: dom.Event => F[Unit]
  ): VNode[F] = new VNode[F] {
    override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode = {
      val vp = vnode.toSnabbdom(dispatcher)
      vp.data = vp.data.copy(on =
        vp.data.on + (eventName ->
          ((e: dom.Event) => dispatcher.unsafeRunAndForget(handler(e))))
      )
      vp
    }
  }

  def setKey[F[_]](vnode: VNode[F])(key: String): VNode[F] = new VNode[F] {

    override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode = {
      val vp = vnode.toSnabbdom(dispatcher)
      vp.data = vp.data.copy(key = Some(key))
      vp
    }

  }

  def setOnInsertHook[F[_]](
      vnode: VNode[F]
  )(onInsert: snabbdom.VNode => F[Unit]): VNode[F] = new VNode[F] {

    override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode = {
      val vp = vnode.toSnabbdom(dispatcher)
      vp.data = vp.data.copy(
        hook = Some(vp.data.hook match {
          case Some(hooks) =>
            hooks.copy(insert =
              Some((n: snabbdom.VNode) =>
                dispatcher.unsafeRunAndForget(onInsert(n))
              )
            )
          case None =>
            snabbdom
              .Hooks()
              .copy(insert =
                Some((n: snabbdom.VNode) =>
                  dispatcher.unsafeRunAndForget(onInsert(n))
                )
              )
        })
      )
      vp
    }

  }

  def setDestroyHook[F[_]](
      vnode: VNode[F]
  )(onDestroy: snabbdom.VNode => F[Unit]): VNode[F] = new VNode[F] {

    override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode = {
      val vp = vnode.toSnabbdom(dispatcher)
      vp.data = vp.data.copy(hook = Some(vp.data.hook match {
        case Some(hooks) =>
          hooks.copy(destroy =
            Some((n: snabbdom.VNode) =>
              dispatcher.unsafeRunAndForget(onDestroy(n))
            )
          )
        case None =>
          snabbdom
            .Hooks()
            .copy(destroy =
              Some((n: snabbdom.VNode) =>
                dispatcher.unsafeRunAndForget(onDestroy(n))
              )
            )
      }))
      vp
    }

  }

  def setProps[F[_]](vnode: VNode[F])(props: Map[String, Any]): VNode[F] =
    new VNode[F] {

      override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode = {
        val vp = vnode.toSnabbdom(dispatcher)
        vp.data = vp.data.copy(props = props)
        vp
      }

    }

  def setAttrs[F[_]](
      vnode: VNode[F]
  )(attrs: Map[String, snabbdom.AttrValue]): VNode[F] =
    new VNode[F] {

      override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode = {
        val vp = vnode.toSnabbdom(dispatcher)
        vp.data = vp.data.copy(attrs = attrs)
        vp
      }

    }

  def setStyle[F[_]](vnode: VNode[F])(style: Map[String, String]): VNode[F] =
    new VNode[F] {

      override def toSnabbdom(dispatcher: Dispatcher[F]): snabbdom.VNode = {
        val vp = vnode.toSnabbdom(dispatcher)
        vp.data = vp.data.copy(style = style)
        vp
      }

    }

}