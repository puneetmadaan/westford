//Copyright 2015 Erik De Rijcke
//
//Licensed under the Apache License,Version2.0(the"License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing,software
//distributed under the License is distributed on an"AS IS"BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
package org.westmalle.wayland.protocol;

import com.google.auto.factory.AutoFactory;
import com.google.common.collect.Sets;
import org.freedesktop.wayland.server.Client;
import org.freedesktop.wayland.server.WlPointerRequestsV3;
import org.freedesktop.wayland.server.WlPointerResource;
import org.freedesktop.wayland.server.WlSurfaceResource;
import org.westmalle.wayland.core.PointerDevice;
import org.westmalle.wayland.core.Role;
import org.westmalle.wayland.core.Surface;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.WeakHashMap;

@AutoFactory(className = "WlPointerFactory")
public class WlPointer implements WlPointerRequestsV3, ProtocolObject<WlPointerResource> {

    private final Set<WlPointerResource> resources = Sets.newSetFromMap(new WeakHashMap<>());

    private final PointerDevice pointerDevice;

    WlPointer(final PointerDevice pointerDevice) {
        this.pointerDevice = pointerDevice;
    }

    @Override
    public void setCursor(final WlPointerResource wlPointerResource,
                          final int serial,
                          @Nullable final WlSurfaceResource wlSurfaceResource,
                          final int hotspotX,
                          final int hotspotY) {

        if (wlSurfaceResource == null) {
            getPointerDevice().removeCursor(wlPointerResource,
                                            serial);
        }
        else {
            final WlSurface wlSurface = (WlSurface) wlSurfaceResource.getImplementation();
            final Surface surface = wlSurface.getSurface();

            final Role role = surface.getSurfaceRole()
                                     .orElseGet(this::getPointerDevice);

            if (role.equals(getPointerDevice())) {
                final PointerDevice pointerDevice = (PointerDevice) role;
                surface.setRole(pointerDevice);
                pointerDevice.setCursor(wlPointerResource,
                                        serial,
                                        wlSurfaceResource,
                                        hotspotX,
                                        hotspotY);
            }
            else {
                //TODO raise protocol error, surface already has another role
//                Resource<?> wlDisplayResource = wlPointerResource.getClient().getObjectById(Display.OBJECT_ID);
//                wlDisplayResource.postError(code,msg);
            }
        }
    }

    @Override
    public void release(final WlPointerResource resource) {
        resource.destroy();
    }

    public PointerDevice getPointerDevice() {
        return this.pointerDevice;
    }

    @Nonnull
    @Override
    public Set<WlPointerResource> getResources() {
        return this.resources;
    }

    @Nonnull
    @Override
    public WlPointerResource create(@Nonnull final Client client,
                                    @Nonnegative final int version,
                                    final int id) {
        return new WlPointerResource(client,
                                     version,
                                     id,
                                     this);
    }
}
