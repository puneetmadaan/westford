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
package org.westmalle.wayland.platform.newt.eglkms.drm;

public interface drmModeSubPixel {
    int DRM_MODE_SUBPIXEL_UNKNOWN        = 1;
    int DRM_MODE_SUBPIXEL_HORIZONTAL_RGB = 2;
    int DRM_MODE_SUBPIXEL_HORIZONTAL_BGR = 3;
    int DRM_MODE_SUBPIXEL_VERTICAL_RGB   = 4;
    int DRM_MODE_SUBPIXEL_VERTICAL_BGR   = 5;
    int DRM_MODE_SUBPIXEL_NONE           = 6;
}
