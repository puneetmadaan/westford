/*
 * Westford Wayland Compositor.
 * Copyright (C) 2016  Erik De Rijcke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.westford.compositor.protocol

import com.google.auto.factory.AutoFactory
import com.google.auto.factory.Provided
import org.freedesktop.wayland.server.*
import org.freedesktop.wayland.shared.WlOutputTransform
import org.freedesktop.wayland.shared.WlSurfaceError
import org.westford.compositor.core.Rectangle
import org.westford.compositor.core.Surface
import org.westford.compositor.core.Transforms
import org.westford.compositor.core.calc.Mat4
import java.util.*
import javax.annotation.Nonnegative

@AutoFactory(className = "WlSurfaceFactory",
             allowSubclasses = true) class WlSurface(@param:Provided private val wlCallbackFactory: WlCallbackFactory,
                                                     val surface: Surface) : WlSurfaceRequestsV4, ProtocolObject<WlSurfaceResource> {

    override val resources: MutableSet<WlSurfaceResource> = Collections.newSetFromMap(WeakHashMap<WlSurfaceResource, Boolean>())

    override fun create(client: Client,
                        @Nonnegative version: Int,
                        id: Int): WlSurfaceResource {
        val wlSurfaceResource = WlSurfaceResource(client,
                                                  version,
                                                  id,
                                                  this)
        wlSurfaceResource.register {
            surface.role?.afterDestroy(wlSurfaceResource)
        }
        return wlSurfaceResource
    }

    override fun destroy(resource: WlSurfaceResource) {
        resource.destroy()
        surface.markDestroyed()
    }

    override fun attach(requester: WlSurfaceResource,
                        buffer: WlBufferResource?,
                        x: Int,
                        y: Int) {
        if (buffer == null) {
            surface.detachBuffer()
        }
        else {
            surface.attachBuffer(buffer,
                                 x,
                                 y)
        }
    }

    override fun damage(resource: WlSurfaceResource,
                        x: Int,
                        y: Int,
                        @Nonnegative width: Int,
                        @Nonnegative height: Int) {
        if (width < 0 || height < 0) {
            //TODO protocol error
            throw IllegalArgumentException("Got negative width or height")
        }

        surface.markDamaged(Rectangle(x,
                                      y,
                                      width,
                                      height))
    }

    override fun frame(resource: WlSurfaceResource,
                       callbackId: Int) {
        val callbackResource = this.wlCallbackFactory.create().add(resource.client,
                                                                   resource.version,
                                                                   callbackId)
        surface.addCallback(callbackResource)
    }

    override fun setOpaqueRegion(requester: WlSurfaceResource,
                                 region: WlRegionResource?) {
        if (region == null) {
            surface.removeOpaqueRegion()
        }
        else {
            surface.setOpaqueRegion(region)
        }
    }

    override fun setInputRegion(requester: WlSurfaceResource,
                                regionResource: WlRegionResource?) {
        if (regionResource == null) {
            surface.removeInputRegion()
        }
        else {
            surface.setInputRegion(regionResource)
        }
    }

    override fun commit(requester: WlSurfaceResource) {
        val surface = surface
        surface.role?.beforeCommit(requester)
        surface.commit()
    }

    override fun setBufferTransform(resource: WlSurfaceResource,
                                    transform: Int) {
        this.surface.setBufferTransform(getMatrix(resource,
                                                  transform))
    }

    private fun getMatrix(resource: WlSurfaceResource,
                          transform: Int): Mat4 {
        if (WlOutputTransform.NORMAL.value == transform) {
            return Transforms.NORMAL
        }
        else if (WlOutputTransform._90.value == transform) {
            return Transforms._90
        }
        else if (WlOutputTransform._180.value == transform) {
            return Transforms._180
        }
        else if (WlOutputTransform._270.value == transform) {
            return Transforms._270
        }
        else if (WlOutputTransform.FLIPPED.value == transform) {
            return Transforms.FLIPPED
        }
        else if (WlOutputTransform.FLIPPED_90.value == transform) {
            return Transforms.FLIPPED_90
        }
        else if (WlOutputTransform.FLIPPED_180.value == transform) {
            return Transforms.FLIPPED_180
        }
        else if (WlOutputTransform.FLIPPED_270.value == transform) {
            return Transforms.FLIPPED_270
        }
        else {
            resource.postError(WlSurfaceError.INVALID_TRANSFORM.value,
                               String.format("Invalid transform %d. Supported values are %s.",
                                             transform,
                                             Arrays.asList(*WlOutputTransform.values())))
            return Transforms.NORMAL
        }
    }

    override fun setBufferScale(resource: WlSurfaceResource,
                                @Nonnegative scale: Int) {
        if (scale > 0) {
            surface.setScale(scale)
        }
        else {
            resource.postError(WlSurfaceError.INVALID_SCALE.value,
                               String.format("Invalid scale %d. Scale must be positive integer.",
                                             scale))
        }
    }

    override fun damageBuffer(requester: WlSurfaceResource,
                              x: Int,
                              y: Int,
                              width: Int,
                              height: Int) {
        //TODO
    }
}
