/*
 * Westmalle Wayland Compositor.
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
package org.westmalle.wayland.dispmanx;


import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import org.westmalle.wayland.core.Connector;
import org.westmalle.wayland.core.Renderer;
import org.westmalle.wayland.protocol.WlOutput;

import javax.annotation.Nonnull;

@AutoFactory(allowSubclasses = true,
             className = "DispmanxConnectorFactory")
public class DispmanxConnector implements Connector {

    private final WlOutput wlOutput;
    private final int      dispmanxElement;
    private final Renderer renderer;

    DispmanxConnector(@Nonnull @Provided final Renderer renderer,
                      final WlOutput wlOutput,
                      final int dispmanxElement) {
        this.wlOutput = wlOutput;
        this.dispmanxElement = dispmanxElement;
        this.renderer = renderer;
    }

    @Nonnull
    @Override
    public WlOutput getWlOutput() {
        return this.wlOutput;
    }

    public int getDispmanxElement() {
        return this.dispmanxElement;
    }

    @Override
    public void render() {
        this.renderer.visit(this);
    }
}
